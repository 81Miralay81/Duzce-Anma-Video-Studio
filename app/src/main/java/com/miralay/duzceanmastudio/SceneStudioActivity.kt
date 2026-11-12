package com.miralay.duzceanmastudio

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.widget.*
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Batch scene producer: any number of source photographs x user-selected linked Wan clips.
 * Each photograph starts a new sequence; every following clip continues from the previous clip's
 * final frame. Clips are verified before being counted as complete.
 */
class SceneStudioActivity : Activity() {
    private val base = "https://cbensimon-wan2-2-fp8da-aoti-preview2.hf.space"
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
            text = "Eklediğin her fotoğraf ayrı bir hikâye dizisi olur. Her fotoğraf için kaç devam sahnesi üretileceğini aşağıdan seç."
            textSize = 14f; setTextColor(0xFFE5EAF8.toInt()); setPadding(0,18,0,10)
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
            text = "SEÇİLEN FOTOĞRAF: ${photos.size}\nHer fotoğraftan $scenesPerPhoto devam sahnesi · Toplam ${photos.size * scenesPerPhoto} klip\n\nÜretimi başlatmak için BAŞLAT düğmesine dokun."
            textSize = 16f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,22,0,22)
        }
        root.addView(status)
        errorDetails = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFD6D6.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF321820.toInt())
            visibility = android.view.View.GONE
        }
        root.addView(errorDetails, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        copyErrorButton = Button(this).apply {
            text = "HATA RAPORUNU KOPYALA"
            isAllCaps = false
            visibility = android.view.View.GONE
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("KANATIX hata raporu", lastErrorReport))
                Toast.makeText(this@SceneStudioActivity, "Hata raporu kopyalandı. Buraya yapıştırabilirsin.", Toast.LENGTH_LONG).show()
            }
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
                    runBatch(photos, scenesPerPhoto)
                }
            }
        }
        actionRow.addView(start, LinearLayout.LayoutParams(0, 76, 1f).apply {
            rightMargin = 4
        })
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
                status.text = "İptal isteği alındı; devam eden model isteği bitince duracak."
            }
        }
        actionRow.addView(cancel, LinearLayout.LayoutParams(0, 76, 1f).apply {
            leftMargin = 4
        })
        root.addView(actionRow, LinearLayout.LayoutParams(-1, 84).apply {
            topMargin = 12
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun runBatch(photos: ArrayList<String>, sceneCount: Int) {
        start.isEnabled = false
        cancelled = false
        clips.clear()
        Thread {
            val result = runCatching {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Kanatix-Scene-Project-${System.currentTimeMillis()}").apply { mkdirs() }
                outputDir = dir
                val totalClips = photos.size * sceneCount
                for (photoIndex in photos.indices) {
                    var continuityImage = copyUriToFile(Uri.parse(photos[photoIndex]), File(cacheDir, "scene-source-$photoIndex.jpg"))
                    for (part in 0 until sceneCount) {
                        if (cancelled) error("Kullanıcı işlemi iptal etti.")
                        val clipIndex = photoIndex * sceneCount + part
                        val scenePrompt = when {
                            part == 0 -> "$basePrompt Recreate the exact photographed moment as the opening of a continuous sequence. Establish the scene with restrained, physically plausible human and environmental motion. This is scene 1 of $sceneCount for this photograph. Do not jump ahead or resolve the event."
                            part == sceneCount - 1 -> "$basePrompt Continue directly from the preceding clip's final frame. This is the final scene ($sceneCount of $sceneCount) in this photograph's sequence. Preserve the same people, clothing, positions, setting, damage and lighting. Advance the same action by one believable step and give this sequence a natural visual pause, without inventing a new event."
                            else -> "$basePrompt Continue directly from the preceding clip's final frame. This is scene ${part + 1} of $sceneCount in one continuous sequence. Preserve the same people, clothing, positions, setting, damage and lighting. Advance the same action by one small, physically plausible step. Do not restart the scene, change camera geography abruptly or introduce new people."
                        }
                        val output = generateClip(continuityImage, scenePrompt, clipIndex)
                        clips.add(output)
                        if (part < sceneCount - 1) {
                            continuityImage = extractLastFrame(output, File(cacheDir, "scene-continuity-$clipIndex.jpg"))
                        }
                        val done = clipIndex + 1
                        runOnUiThread {
                            progress.progress = done
                            status.text = "Fotoğraf ${photoIndex + 1}/${photos.size} · devam sahnesi ${part + 1}/$sceneCount tamamlandı. Toplam: $done/$totalClips"
                        }
                    }
                }
                dir
            }
            runOnUiThread {
                start.isEnabled = true
                result.onSuccess {
                    status.text = "${photos.size * sceneCount} doğrulanmış kısa klip üretildi (${photos.size} fotoğraf × $sceneCount devam sahnesi). Henüz birleştirilmiş film ve sahne sesleri eklenmedi. Klip klasörü: ${it.absolutePath}"
                    Toast.makeText(this, "${photos.size * sceneCount} sahne üretimi tamamlandı", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    val trace = StringWriter()
                    error.printStackTrace(PrintWriter(trace))
                    val report = buildString {
                        appendLine("KANATIX ÜRETİM HATA RAPORU")
                        appendLine("Tarih: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                        appendLine("Fotoğraf sayısı: ${photos.size}")
                        appendLine("Fotoğraf başına sahne: $sceneCount")
                        appendLine("Toplam hedef klip: ${photos.size * sceneCount}")
                        appendLine("Tamamlanan klip: ${clips.size}")
                        appendLine("Çıktı klasörü: ${runCatching { outputDir.absolutePath }.getOrDefault("oluşturulamadı")}")
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
                    status.text = "ÜRETİM DURDU · ${error.message ?: error.javaClass.simpleName}\nTamamlanan: ${clips.size}/${photos.size * sceneCount}"
                    android.app.AlertDialog.Builder(this@SceneStudioActivity)
                        .setTitle("KANATIX ÜRETİM HATASI")
                        .setMessage("Hata: ${error.message ?: error.javaClass.simpleName}\n\nTamamlanan klip: ${clips.size}/${photos.size * sceneCount}\n\nAyrıntılı rapor hazır. Aşağıdan kopyalayabilir veya paylaşabilirsin.")
                        .setPositiveButton("HATA RAPORUNU KOPYALA") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("KANATIX hata raporu", lastErrorReport))
                            Toast.makeText(this@SceneStudioActivity, "Hata raporu kopyalandı.", Toast.LENGTH_LONG).show()
                        }
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
            }
        }.start()
    }

    private fun copyUriToFile(uri: Uri, target: File): File {
        contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("Fotoğraf okunamadı: $uri")
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

    private fun generateClip(image: File, prompt: String, index: Int): File {
        val uploaded = upload(image)
        val fileData = JSONObject().apply {
            put("path", uploaded); put("url", "$base/gradio_api/file=$uploaded")
            put("orig_name", "kanatix-scene-$index.jpg"); put("size", JSONObject.NULL)
            put("mime_type", "image/jpeg"); put("meta", JSONObject().put("_type", "gradio.FileData"))
        }
        val args = JSONArray().apply {
            put(fileData)
            put(JSONObject.NULL)
            put(prompt)
            put(6)
            put("static image, frozen frame, blurry, deformed anatomy, extra limbs, duplicate people, modern objects, fantasy, sudden movement, dragging injured people")
            put(4.0)
            put(1.0)
            put(1.0)
            put(42)
            put(true)
            put(6)
            put("UniPCMultistep")
            put(3.0)
            put(16)
            put(true)
            put(true)
        }
        val request = (URL("$base/gradio_api/call/generate_video").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30000; readTimeout = 30000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        request.outputStream.use { it.write(JSONObject().put("data", args).toString().toByteArray(Charsets.UTF_8)) }
        val code = request.responseCode
        val body = (if (code in 200..299) request.inputStream else request.errorStream).bufferedReader().use { it.readText() }
        request.disconnect()
        if (code !in 200..299) error("Wan işi kabul etmedi ($code): $body")
        val event = JSONObject(body).optString("event_id").takeIf { it.isNotBlank() && it != "null" }
            ?: error("Wan sunucusu görev numarası döndürmedi. Yanıt: ${body.take(500)}")
        runOnUiThread { status.text = "Wan 2.2 klip ${index + 1}/${selectedPhotoCount * scenesPerPhoto} üretiyor…" }
        val stream = (URL("$base/gradio_api/call/generate_video/$event").openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000; readTimeout = 900000; setRequestProperty("Accept", "text/event-stream")
        }
        val streamCode = stream.responseCode
        if (streamCode !in 200..299) {
            val detail = runCatching { stream.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            stream.disconnect()
            error("Wan sonuç akışı açılamadı (HTTP $streamCode). ${detail?.take(400) ?: "Sunucu ayrıntı vermedi."}")
        }
        var eventName = ""; val data = mutableListOf<String>(); var resultUrl: String? = null
        stream.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (cancelled) break
                when {
                    line.startsWith("event:") -> eventName = line.substringAfter(":").trim()
                    line.startsWith("data:") -> data.add(line.substringAfter(":").trim())
                    line.isBlank() && data.isNotEmpty() -> {
                        val raw = data.joinToString("\n")
                        if (eventName == "complete") {
                            val arr = runCatching { JSONArray(raw) }.getOrElse {
                                error("Wan tamamlandı yanıtı çözümlenemedi: ${raw.take(500)}")
                            }
                            fun resolveVideoFile(value: Any?): String? {
                                return when (value) {
                                    is JSONObject -> {
                                        val directUrl = value.optString("url").takeIf { it.startsWith("http") && it != "null" }
                                        val path = value.optString("path").takeIf { it.isNotBlank() && it != "null" }
                                        val nested = value.opt("video").takeIf { it is JSONObject || it is String }
                                            ?: value.opt("file").takeIf { it is JSONObject || it is String }
                                        directUrl ?: path?.let { "$base/gradio_api/file=$it" } ?: resolveVideoFile(nested)
                                    }
                                    is String -> when {
                                        value.startsWith("http") -> value
                                        value.startsWith("/") -> "$base/gradio_api/file=$value"
                                        else -> null
                                    }
                                    else -> null
                                }
                            }
                            for (i in 0 until arr.length()) {
                                resultUrl = resolveVideoFile(arr.opt(i))
                                if (resultUrl != null) break
                            }
                            if (resultUrl != null) break
                         } else if (eventName == "error") {
                            val detail = raw.takeIf { it.isNotBlank() && it != "null" && it != "[]" && it != "undefined" }
                            error(if (detail != null) "Wan 2.2 hata verdi: ${detail.take(700)}"
                                else "Wan 2.2 sunucusu ayrıntısız hata döndürdü (null). Ücretsiz GPU alanı meşgul, uyuyor veya isteği işleyememiş olabilir. Bir süre sonra yeniden dene.")
                        }
                        eventName = ""; data.clear()
                    }
                }
            }
        }
        stream.disconnect()
        if (cancelled) error("İptal edildi.")
        val url = resultUrl ?: error("Wan işi tamamlandı ancak video dosyası adresi dönmedi. Sunucu boş/null sonuç verdi; ücretsiz model tarafındaki üretim tamamlanmamış olabilir.")
        val file = File(outputDir, "Kanatix-scene-${(index + 1).toString().padStart(2,'0')}.mp4")
        URL(url).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        validateMotionMp4(file)
        return file
    }

    private fun upload(file: File): String {
        val boundary = "----KanatixBatch${System.currentTimeMillis()}"
        val c = (URL("$base/gradio_api/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30000; readTimeout = 90000; doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        c.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"kanatix.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            FileInputStream(file).use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        c.disconnect()
        if (code !in 200..299) error("Fotoğraf yüklenemedi ($code).")
        return JSONArray(body).optString(0).takeIf { it.isNotBlank() } ?: error("Yükleme yolu boş.")
    }

    private fun validateMotionMp4(file: File) {
        require(file.length() > 2048L) { "Model geçerli video dosyası döndürmedi." }
        val header = ByteArray(12)
        FileInputStream(file).use { require(it.read(header) >= 12) { "Eksik MP4." } }
        require(String(header, 4, 4, Charsets.US_ASCII) == "ftyp") { "Model MP4 yerine başka dosya döndürdü." }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Video süresi okunamadı.")
            require(duration in 2500L..10000L) { "Klip beklenen kısa sahne süresinde değil: ${duration}ms" }
            val first = r.getFrameAtTime(300000L) ?: error("İlk kare okunamadı.")
            val later = r.getFrameAtTime((duration / 2) * 1000L) ?: error("İkinci kare okunamadı.")
            var diff = 0
            for (y in 0 until 24) for (x in 0 until 24) {
                val a = first.getPixel(x * first.width / 24, y * first.height / 24)
                val b = later.getPixel(x * later.width / 24, y * later.height / 24)
                if (kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b)) > 40) diff++
            }
            first.recycle(); later.recycle()
            require(diff >= 3) { "Sabit fotoğraf algılandı; klip kabul edilmedi." }
        } finally { r.release() }
    }
}
