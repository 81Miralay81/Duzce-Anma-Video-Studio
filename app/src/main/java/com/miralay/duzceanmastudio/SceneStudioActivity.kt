package com.miralay.duzceanmastudio

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Çoklu sahne üretimi: her fotoğraf için kullanıcı kadar bağlantılı Wan klibi.
 *
 * Colab API iş kuyruğu mantığıyla çalışır:
 *   POST /jobs            -> hemen job_id döner (Cloudflare'in ~100 sn sınırına takılmaz)
 *   GET  /jobs/{id}       -> queued / running / done / error
 *   GET  /jobs/{id}/video -> hazır MP4
 * Eski tek istekli /generate yöntemi, üretim 100 saniyeyi geçtiği için Cloudflare
 * tarafından HTTP 524 ile kesiliyordu.
 */
class SceneStudioActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var start: Button
    private lateinit var sceneCountPicker: NumberPicker
    private lateinit var errorDetails: TextView
    private lateinit var copyErrorButton: Button
    private var lastErrorReport = ""
    private var scenesPerPhoto = 4
    private var selectedPhotoCount = 0
    private val clips = mutableListOf<File>()
    private lateinit var outputDir: File
    @Volatile private var cancelled = false
    @Volatile private var currentJobId: String? = null
    private val basePrompt = "create a realistic, respectful historical documentary reenactment of the moment in this photograph. Bring the people and the scene to life with natural human movement, subtle environmental motion and cinematic camera work. Preserve the original people, clothing, setting and historical period. Do not add text, logos, extra people or fantastical elements."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val photos = intent.getStringArrayListExtra("photos") ?: arrayListOf()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 24, 12, 24)
            setBackgroundColor(0xFF071126.toInt())
        }
        root.addView(TextView(this).apply {
            text = "KANATIX · ÇOKLU SAHNE STÜDYOSU"
            textSize = 20f; setTextColor(0xFFFFC45D.toInt())
        })
        selectedPhotoCount = photos.size
        root.addView(TextView(this).apply {
            text = "Eklediğin her fotoğraf ayrı bir hikâye dizisi olur. Her fotoğraf için kaç devam sahnesi üretileceğini aşağıdan seç. Üretim sürerken ekran açık tutulur; uygulamayı kapatma."
            textSize = 14f; setTextColor(0xFFE5EAF8.toInt()); setPadding(0, 18, 0, 10)
        })
        root.addView(TextView(this).apply {
            text = "HER FOTOĞRAF İÇİN DEVAM SAHNESİ"
            textSize = 14f; setTextColor(0xFFFFC45D.toInt())
        })
        sceneCountPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 10
            value = 4
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, newValue ->
                scenesPerPhoto = newValue
                if (::status.isInitialized) {
                    status.text = "SEÇİLEN FOTOĞRAF: $selectedPhotoCount\nHer fotoğraftan $scenesPerPhoto devam sahnesi · Toplam ${selectedPhotoCount * scenesPerPhoto} klip"
                }
                if (::progress.isInitialized) {
                    progress.max = (selectedPhotoCount * scenesPerPhoto).coerceAtLeast(1)
                }
            }
        }
        root.addView(sceneCountPicker, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = (photos.size * scenesPerPhoto).coerceAtLeast(1); progress = 0
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 12))
        status = TextView(this).apply {
            text = "SEÇİLEN FOTOĞRAF: ${photos.size}\nHer fotoğraftan $scenesPerPhoto devam sahnesi · Toplam ${photos.size * scenesPerPhoto} klip\n\nÖnce Colab bağlantısını ayarlayıp TEST ET, sonra BAŞLAT."
            textSize = 16f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 22, 0, 22)
        }
        root.addView(status)
        val colabSettingsButton = Button(this).apply {
            text = colabButtonText()
            isAllCaps = false
            setOnClickListener { showColabSettings { text = colabButtonText() } }
        }
        root.addView(colabSettingsButton, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        errorDetails = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFD6D6.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF321820.toInt())
            setTextIsSelectable(true)
            visibility = android.view.View.GONE
        }
        root.addView(errorDetails, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        copyErrorButton = Button(this).apply {
            text = "HATA RAPORUNU KOPYALA"
            isAllCaps = false
            visibility = android.view.View.GONE
            setOnClickListener { copyReport() }
        }
        root.addView(copyErrorButton, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        start = Button(this).apply {
            text = "▶  BAŞLAT"
            isAllCaps = false
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 22f; setColor(0xFF168447.toInt()) }
            minWidth = 0
            minHeight = 76
            elevation = 8f
            stateListAnimator = null
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                if (photos.isEmpty()) {
                    status.text = "Önce galeriden en az 1 fotoğraf seçmelisin."
                } else {
                    scenesPerPhoto = sceneCountPicker.value
                    if (colabBaseUrl().isBlank() || colabToken().isBlank()) {
                        status.text = "Ücretsiz Colab bağlantısı ayarlanmamış. Önce Colab notebook'unu başlatıp adres ve erişim anahtarını kaydet."
                        showColabSettings { colabSettingsButton.text = colabButtonText() }
                    } else {
                        runBatch(photos, scenesPerPhoto)
                    }
                }
            }
        }
        actionRow.addView(start, LinearLayout.LayoutParams(0, 76, 1f).apply { rightMargin = 4 })
        val cancel = Button(this).apply {
            text = "■  İPTAL"
            isAllCaps = false
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 22f; setColor(0xFFD12E2E.toInt()) }
            minWidth = 0
            minHeight = 76
            elevation = 8f
            stateListAnimator = null
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                cancelled = true
                status.text = "İptal isteği alındı. Colab'daki sıradaki iş iptal ediliyor; çalışan klip bitince duracak."
                val job = currentJobId
                if (job != null) Thread { runCatching { cancelRemoteJob(job) } }.start()
            }
        }
        actionRow.addView(cancel, LinearLayout.LayoutParams(0, 76, 1f).apply { leftMargin = 4 })
        root.addView(actionRow, LinearLayout.LayoutParams(-1, 84).apply { topMargin = 12 })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun colabButtonText() =
        if (colabBaseUrl().isNotBlank()) "☁ COLAB BAĞLANTISI · AYARLANDI" else "☁ ÜCRETSİZ COLAB BAĞLANTISINI AYARLA"

    private fun ui(text: String) = runOnUiThread { status.text = text }

    private fun copyReport() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KANATIX hata raporu", lastErrorReport))
        Toast.makeText(this, "Hata raporu kopyalandı. Buraya yapıştırabilirsin.", Toast.LENGTH_LONG).show()
    }

    private fun runBatch(photos: ArrayList<String>, sceneCount: Int) {
        start.isEnabled = false
        cancelled = false
        clips.clear()
        errorDetails.visibility = android.view.View.GONE
        copyErrorButton.visibility = android.view.View.GONE
        progress.max = (photos.size * sceneCount).coerceAtLeast(1)
        progress.progress = 0
        // Uzun üretim sırasında ekran kapanırsa Android arka plandaki ağ bağlantısını kesebilir.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread {
            var filmSaved = false
            val result = runCatching {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Kanatix-Scene-Project-${System.currentTimeMillis()}").apply { mkdirs() }
                outputDir = dir
                val totalClips = photos.size * sceneCount
                for (photoIndex in photos.indices) {
                    var continuityImage = preparePhoto(Uri.parse(photos[photoIndex]), File(cacheDir, "scene-source-$photoIndex.jpg"))
                    for (part in 0 until sceneCount) {
                        if (cancelled) error("Kullanıcı işlemi iptal etti.")
                        val clipIndex = photoIndex * sceneCount + part
                        val scenePrompt = when {
                            part == 0 -> "$basePrompt Recreate the exact photographed moment as the opening of a continuous sequence. Establish the scene with restrained, physically plausible human and environmental motion. This is scene 1 of $sceneCount for this photograph. Do not jump ahead or resolve the event."
                            part == sceneCount - 1 -> "$basePrompt Continue directly from the preceding clip's final frame. This is the final scene ($sceneCount of $sceneCount) in this photograph's sequence. Preserve the same people, clothing, positions, setting, damage and lighting. Advance the same action by one believable step and give this sequence a natural visual pause, without inventing a new event."
                            else -> "$basePrompt Continue directly from the preceding clip's final frame. This is scene ${part + 1} of $sceneCount in one continuous sequence. Preserve the same people, clothing, positions, setting, damage and lighting. Advance the same action by one small, physically plausible step. Do not restart the scene, change camera geography abruptly or introduce new people."
                        }
                        val label = "Fotoğraf ${photoIndex + 1}/${photos.size} · sahne ${part + 1}/$sceneCount · toplam ${clipIndex + 1}/$totalClips"
                        val output = generateClip(continuityImage, scenePrompt, clipIndex, label)
                        clips.add(output)
                        runCatching { saveToGallery(output, "Kanatix-AI-Sahne-${(clipIndex + 1).toString().padStart(2, '0')}-${System.currentTimeMillis()}.mp4") }
                        if (part < sceneCount - 1) {
                            continuityImage = extractLastFrame(output, File(cacheDir, "scene-continuity-$clipIndex.jpg"))
                        }
                        val done = clipIndex + 1
                        runOnUiThread {
                            progress.progress = done
                            status.text = "$label tamamlandı ve Galeri'ye kaydedildi (Movies/Kanatix AI)."
                        }
                    }
                }
                if (clips.size > 1) {
                    ui("Tüm klipler hazır. Tek film halinde birleştiriliyor…")
                    filmSaved = runCatching { mergeClipsIntoFilm(clips, File(dir, "Kanatix-AI-Film.mp4")) }.isSuccess
                }
                dir
            }
            runOnUiThread {
                start.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                result.onSuccess {
                    val filmNote = when {
                        clips.size <= 1 -> ""
                        filmSaved -> "\nBirleştirilmiş film de Galeri'ye kaydedildi."
                        else -> "\nKlipler birleştirilemedi; tek tek klipler Galeri'de duruyor."
                    }
                    status.text = "${clips.size} klip üretildi (${photos.size} fotoğraf × $sceneCount sahne).\nGaleri → Movies/Kanatix AI klasörüne bak.$filmNote"
                    Toast.makeText(this, "${clips.size} sahne üretimi tamamlandı", Toast.LENGTH_LONG).show()
                }.onFailure { error -> showFailure(error, photos.size, sceneCount) }
            }
        }.start()
    }

    private fun showFailure(error: Throwable, photoCount: Int, sceneCount: Int) {
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        val report = buildString {
            appendLine("KANATIX ÜRETİM HATA RAPORU")
            val installedPackage = packageManager.getPackageInfo(packageName, 0)
            appendLine("APK sürümü: ${installedPackage.versionName ?: "bilinmiyor"}")
            appendLine("APK build numarası (versionCode): ${installedPackage.longVersionCode}")
            appendLine("Tarih: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale("tr", "TR")).format(java.util.Date())}")
            appendLine("Colab adresi: ${colabBaseUrl()}")
            appendLine("Fotoğraf sayısı: $photoCount")
            appendLine("Fotoğraf başına sahne: $sceneCount")
            appendLine("Toplam hedef klip: ${photoCount * sceneCount}")
            appendLine("Tamamlanan klip: ${clips.size}")
            appendLine("Son iş numarası: ${currentJobId ?: "yok"}")
            appendLine("Hata sınıfı: ${error.javaClass.name}")
            appendLine("Hata mesajı: ${error.message ?: "(mesaj yok)"}")
            appendLine()
            appendLine("AYRINTILI STACK TRACE:")
            append(trace.toString())
        }
        lastErrorReport = report
        errorDetails.text = report
        errorDetails.visibility = android.view.View.VISIBLE
        copyErrorButton.visibility = android.view.View.VISIBLE
        status.text = "ÜRETİM DURDU · ${error.message ?: error.javaClass.simpleName}\nTamamlanan: ${clips.size}/${photoCount * sceneCount} (tamamlananlar Galeri'de)"
        android.app.AlertDialog.Builder(this)
            .setTitle("KANATIX ÜRETİM HATASI")
            .setMessage("Hata: ${error.message ?: error.javaClass.simpleName}\n\nTamamlanan klip: ${clips.size}/${photoCount * sceneCount}")
            .setPositiveButton("HATA RAPORUNU KOPYALA") { _, _ -> copyReport() }
            .setNeutralButton("RAPORU PAYLAŞ") { _, _ ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "KANATIX üretim hata raporu")
                    putExtra(Intent.EXTRA_TEXT, lastErrorReport)
                }
                startActivity(Intent.createChooser(share, "Hata raporunu paylaş"))
            }
            .setNegativeButton("KAPAT", null)
            .show()
    }

    /**
     * Galeri fotoğrafını (HEIC, PNG, WEBP, büyük JPEG…) gerçek JPEG'e çevirir ve en uzun kenarı
     * 1280 piksele indirir. Eski kod dosyayı olduğu gibi "image/jpeg" diye gönderiyordu; HEIC
     * fotoğraflarda Colab tarafı resmi açamıyordu.
     */
    private fun preparePhoto(uri: Uri, target: File): File {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > 1280) {
                val scale = 1280f / longest
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return target
    }

    private fun extractLastFrame(video: File, target: File): File {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Klip süresi okunamadı.")
            val frame = retriever.getFrameAtTime((duration - 150L).coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("Devamlılık karesi alınamadı.")
            target.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            frame.recycle()
        } finally { retriever.release() }
        return target
    }

    // ---------- Colab bağlantı ayarları ----------

    private fun colabBaseUrl(): String =
        getSharedPreferences("kanatix_colab", MODE_PRIVATE).getString("base_url", "")?.trim()?.trimEnd('/') ?: ""

    private fun colabToken(): String =
        getSharedPreferences("kanatix_colab", MODE_PRIVATE).getString("access_token", "")?.trim() ?: ""

    /** Kullanıcı "API ADRESİ: https://xxx.trycloudflare.com" satırını komple yapıştırsa da adresi ayıklar. */
    private fun normalizeUrl(raw: String): String {
        val match = Regex("https://[A-Za-z0-9.-]+(:[0-9]+)?").find(raw.trim())
        return (match?.value ?: raw.trim()).trimEnd('/')
    }

    private fun normalizeToken(raw: String): String =
        raw.trim().substringAfterLast(' ').trim()

    private fun showColabSettings(onSaved: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 12, 36, 0)
        }
        panel.addView(TextView(this).apply {
            text = "Colab notebook'unun son hücresinde yazan API ADRESİ ve ERİŞİM ANAHTARI'nı gir (Drive'da MyDrive/Kanatix/Config/colab_api.txt dosyasında da var). Colab her yeniden başladığında ikisi de değişir."
            textSize = 14f
        })
        val urlField = EditText(this).apply {
            hint = "https://....trycloudflare.com"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(colabBaseUrl())
        }
        val tokenField = EditText(this).apply {
            hint = "Colab erişim anahtarı"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setText(colabToken())
        }
        panel.addView(urlField)
        panel.addView(tokenField)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Ücretsiz Google Colab")
            .setView(panel)
            .setPositiveButton("KAYDET", null)
            .setNeutralButton("TEST ET", null)
            .setNegativeButton("KAPAT", null)
            .create()
        fun save() {
            getSharedPreferences("kanatix_colab", MODE_PRIVATE).edit()
                .putString("base_url", normalizeUrl(urlField.text.toString()))
                .putString("access_token", normalizeToken(tokenField.text.toString()))
                .apply()
            urlField.setText(colabBaseUrl())
            tokenField.setText(colabToken())
            onSaved()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                save()
                Toast.makeText(this, "Colab bağlantı bilgileri kaydedildi.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                save()
                Toast.makeText(this, "Colab'a bağlanılıyor…", Toast.LENGTH_SHORT).show()
                Thread {
                    val message = runCatching { testConnection() }.getOrElse { "BAĞLANTI BAŞARISIZ\n${it.message}" }
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this).setTitle("Colab bağlantı testi")
                            .setMessage(message).setPositiveButton("Tamam", null).show()
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun testConnection(): String {
        val endpoint = colabBaseUrl()
        require(endpoint.startsWith("https://")) { "Adres https:// ile başlamalı." }
        val (code, body) = request("GET", "$endpoint/health", 20000, 20000)
        if (code == 401) error("Erişim anahtarı yanlış (HTTP 401). Colab'daki son anahtarı gir.")
        val json = parseJson(code, body, "Sağlık kontrolü")
        if (code !in 200..299) error("HTTP $code: ${json.optString("error")}")
        if (json.optInt("api_version", 1) < 2) {
            error("Colab'da ESKİ notebook çalışıyor. GitHub'daki güncel notebook'u açıp hücreleri baştan çalıştır.")
        }
        return "BAĞLANTI TAMAM ✓\nGPU: ${json.optString("gpu")}\nModel hazır: ${if (json.optBoolean("ready")) "evet" else "hayır"}\nKuyrukta bekleyen iş: ${json.optInt("queue")}"
    }

    // ---------- HTTP yardımcıları ----------

    private fun request(method: String, url: String, connectTimeout: Int, readTimeout: Int): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            setRequestProperty("X-Kanatix-Token", colabToken())
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = c.responseCode
            val body = runCatching {
                (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: ""
            return code to body
        } finally { c.disconnect() }
    }

    /** Cloudflare tünel kapandığında JSON yerine HTML hata sayfası gelir; bunu anlaşılır mesaja çevirir. */
    private fun parseJson(code: Int, body: String, what: String): JSONObject {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("{")) return JSONObject(trimmed)
        val hint = when (code) {
            524 -> "Cloudflare zaman aşımı (524)."
            502, 503, 530, 1033 -> "Colab tüneline ulaşılamıyor (HTTP $code). Colab oturumu kapanmış ya da adres değişmiş olabilir; notebook'u yeniden çalıştırıp yeni adresi gir."
            404 -> "Adres bulunamadı (404). Colab'da eski notebook çalışıyor olabilir."
            else -> "Beklenmeyen yanıt (HTTP $code)."
        }
        error("$what: $hint ${trimmed.take(160)}")
    }

    private fun cancelRemoteJob(job: String) {
        request("DELETE", "${colabBaseUrl()}/jobs/$job", 15000, 15000)
    }

    // ---------- Üretim ----------

    private fun generateClip(image: File, prompt: String, index: Int, label: String): File {
        val endpoint = colabBaseUrl()
        require(endpoint.startsWith("https://") && colabToken().isNotBlank()) {
            "Ücretsiz Colab bağlantısı eksik. Colab adresini ve erişim anahtarını ayarlardan kaydet."
        }
        // 1) İşi gönder — sunucu hemen job_id döner.
        val boundary = "----KanatixColab${System.currentTimeMillis()}"
        val submit = (URL("$endpoint/jobs").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("X-Kanatix-Token", colabToken())
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        val (submitCode, submitBody) = try {
            submit.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
                }
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"kanatix-scene-$index.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
                FileInputStream(image).use { it.copyTo(out) }
                out.write("\r\n".toByteArray(Charsets.UTF_8))
                field("prompt", prompt)
                field("seed", (System.currentTimeMillis() % 2147483647L).toString())
                out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = submit.responseCode
            code to (runCatching { (if (code in 200..299) submit.inputStream else submit.errorStream)?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: "")
        } finally { submit.disconnect() }
        if (submitCode == 401) error("Erişim anahtarı yanlış (HTTP 401). Colab'daki son anahtarı ayarlara gir.")
        val submitJson = parseJson(submitCode, submitBody, "İş gönderme")
        if (submitCode !in 200..299) error("Colab işi kabul etmedi (HTTP $submitCode): ${submitJson.optString("error")}")
        val jobId = submitJson.optString("job_id").takeIf { it.isNotBlank() } ?: error("Colab iş numarası döndürmedi.")
        currentJobId = jobId

        // 2) Durumu sorgula. Geçici ağ kopmalarında hemen pes etme.
        val startedAt = System.currentTimeMillis()
        var consecutiveFailures = 0
        while (true) {
            if (cancelled) { runCatching { cancelRemoteJob(jobId) }; error("Kullanıcı işlemi iptal etti.") }
            val elapsedMin = (System.currentTimeMillis() - startedAt) / 60000
            if (elapsedMin >= 60) error("Klip 60 dakikada bitmedi. Colab GPU'su çok yavaş ya da takılmış olabilir.")
            val polled = try {
                request("GET", "$endpoint/jobs/$jobId", 20000, 30000)
            } catch (e: IOException) {
                consecutiveFailures++
                if (consecutiveFailures >= 20) error("Colab'a 20 kez üst üste ulaşılamadı: ${e.message}")
                ui("$label · bağlantı koptu, tekrar deneniyor ($consecutiveFailures)…")
                Thread.sleep(10000)
                continue
            }
            val (code, body) = polled
            if (code == 404) error("Colab bu işi tanımıyor (404). Colab oturumu yeniden başlamış olabilir.")
            if (code !in 200..299 || !body.trimStart().startsWith("{")) {
                consecutiveFailures++
                if (consecutiveFailures >= 20) {
                    val j = parseJson(code, body, "Durum sorgusu")
                    error("Durum sorgusu başarısız (HTTP $code): ${j.optString("error")}")
                }
                ui("$label · Colab yanıt vermiyor (HTTP $code), tekrar deneniyor ($consecutiveFailures)…")
                Thread.sleep(10000)
                continue
            }
            consecutiveFailures = 0
            val json = JSONObject(body)
            when (json.optString("status")) {
                "done" -> break
                "error" -> error("Colab üretim hatası: ${json.optString("error")}")
                "cancelled" -> error("İş Colab tarafında iptal edildi.")
                "queued" -> ui("$label · Colab kuyruğunda (${json.optInt("position")}. sırada) · $elapsedMin dk")
                else -> ui("$label · Wan 2.2 üretiyor · ${json.optString("stage", "çalışıyor")} · $elapsedMin dk")
            }
            Thread.sleep(8000)
        }

        // 3) Hazır MP4'ü indir.
        ui("$label · video indiriliyor…")
        val file = File(outputDir, "Kanatix-scene-${(index + 1).toString().padStart(2, '0')}.mp4")
        var lastError: Exception? = null
        for (attempt in 1..3) {
            val c = (URL("$endpoint/jobs/$jobId/video").openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 90000
                setRequestProperty("X-Kanatix-Token", colabToken())
            }
            try {
                val code = c.responseCode
                if (code !in 200..299) {
                    val body = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: ""
                    throw IOException("Video indirilemedi (HTTP $code): ${body.take(300)}")
                }
                val type = c.contentType ?: "bilinmiyor"
                val length = c.getHeaderField("Content-Length") ?: "bilinmiyor"
                c.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                validateMotionMp4(file, type, length)
                currentJobId = null
                return file
            } catch (e: IOException) {
                lastError = e
                Thread.sleep(5000)
            } finally { c.disconnect() }
        }
        throw lastError ?: IOException("Video indirilemedi.")
    }

    private fun validateMotionMp4(file: File, contentType: String = "bilinmiyor", contentLength: String = "bilinmiyor") {
        require(file.length() > 2048L) {
            "Model geçerli video dosyası döndürmedi. Boyut=${file.length()} bayt; Content-Type=$contentType"
        }
        val header = ByteArray(64)
        val read = FileInputStream(file).use { it.read(header) }
        require(read >= 12) { "Eksik MP4. Okunan=$read bayt; Content-Type=$contentType" }
        require(String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
            val signature = header.take(16).joinToString(" ") { "%02X".format(it) }
            "Model MP4 yerine başka dosya döndürdü. Content-Type=$contentType; Content-Length=$contentLength; ilk baytlar=$signature"
        }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Video süresi okunamadı.")
            require(duration in 1500L..12000L) { "Klip beklenen kısa sahne süresinde değil: ${duration}ms" }
            val first = r.getFrameAtTime(300000L) ?: error("İlk kare okunamadı.")
            val later = r.getFrameAtTime((duration / 2) * 1000L) ?: error("İkinci kare okunamadı.")
            var diff = 0
            for (y in 0 until 24) for (x in 0 until 24) {
                val a = first.getPixel(x * first.width / 24, y * first.height / 24)
                val b = later.getPixel(x * later.width / 24, y * later.height / 24)
                if (kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) > 40) diff++
            }
            first.recycle(); later.recycle()
            require(diff >= 3) { "Sabit fotoğraf algılandı; klip kabul edilmedi." }
        } finally { r.release() }
    }

    // ---------- Galeri ve birleştirme ----------

    private fun saveToGallery(file: File, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Kanatix AI")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Galeriye kayıt alanı oluşturulamadı.")
        contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { it.copyTo(out) } }
            ?: error("Video Galeri'ye yazılamadı.")
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
    }

    /** Üretilen klipleri sırayla tek MP4 filmde birleştirir (Media3 Transformer, cihaz üzerinde). */
    @OptIn(UnstableApi::class)
    private fun mergeClipsIntoFilm(parts: List<File>, output: File) {
        val items = parts.map { EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).build() }
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
        val latch = CountDownLatch(1)
        var failure: Exception? = null
        runOnUiThread {
            try {
                val transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { latch.countDown() }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        failure = exportException; latch.countDown()
                    }
                }).build()
                transformer.start(composition, output.absolutePath)
            } catch (e: Exception) { failure = e; latch.countDown() }
        }
        if (!latch.await(15, TimeUnit.MINUTES)) error("Film birleştirme zaman aşımına uğradı.")
        failure?.let { throw it }
        saveToGallery(output, "Kanatix-AI-Film-${System.currentTimeMillis()}.mp4")
    }
}
