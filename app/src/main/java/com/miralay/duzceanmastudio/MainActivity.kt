package com.miralay.duzceanmastudio

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

class MainActivity : Activity() {
    private val photos = mutableListOf<Uri>()
    private var musicUri: Uri? = null
    private var voiceUri: Uri? = null
    private lateinit var photoInfo: TextView
    private lateinit var musicInfo: TextView
    private lateinit var voiceInfo: TextView
    private lateinit var titleInput: EditText
    private lateinit var closingInput: EditText
    private lateinit var status: TextView
    private lateinit var progressBar: ProgressBar
    private var transformer: Transformer? = null
    private var outputFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressTask: Runnable? = null
    private var pendingJson: String? = null
    private var exporting = false
    private var cancelRequested = false
    private val prefs by lazy { getSharedPreferences("project", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun label(value: String) = TextView(this).apply {
            text = value; textSize = 15f; setTextColor(0xFFE8EDF2.toInt())
            setPadding(0, 12, 0, 8)
        }
        fun action(text: String, run: () -> Unit) = Button(this).apply {
            this.text = text; setOnClickListener { run() }
        }
        root.addView(label("DÜZCE ANMA VİDEO STÜDYOSU").apply { textSize = 22f; gravity = Gravity.CENTER })
        root.addView(label("12 Kasım 1999 • UNUTMADIK, UNUTMAYACAĞIZ"))
        titleInput = EditText(this).apply {
            hint = "Video başlığı"; setTextColor(-1); setHintTextColor(0xFF9EADB9.toInt())
            setText("12 KASIM 1999 – DÜZCE DEPREMİ")
        }
        root.addView(titleInput, ViewGroup.LayoutParams(-1, -2))
        closingInput = EditText(this).apply {
            hint = "Kapanış yazısı"; setTextColor(-1); setHintTextColor(0xFF9EADB9.toInt())
            setText("UNUTMADIK, UNUTMAYACAĞIZ.")
        }
        root.addView(closingInput, ViewGroup.LayoutParams(-1, -2))
        val saveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { saveLocal() }
        }
        titleInput.addTextChangedListener(saveWatcher)
        closingInput.addTextChangedListener(saveWatcher)
        root.addView(action("FOTOĞRAFLARI SEÇ", ::pickPhotos))
        photoInfo = label("Henüz fotoğraf seçilmedi"); root.addView(photoInfo)
        root.addView(action("ARKA PLAN MÜZİĞİ SEÇ", ::pickMusic))
        musicInfo = label("İsteğe bağlı müzik seçilmedi"); root.addView(musicInfo)
        root.addView(action("SESLENDİRME / KONUŞMA KAYDI SEÇ", ::pickVoice))
        voiceInfo = label("İsteğe bağlı seslendirme seçilmedi"); root.addView(voiceInfo)
        root.addView(action("45 SANİYELİK MP4 OLUŞTUR", ::createVideo))
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        root.addView(progressBar, ViewGroup.LayoutParams(-1, -2))
        root.addView(action("VİDEO OLUŞTURMAYI İPTAL ET", ::cancelVideo))
        root.addView(action("PROJEYİ DIŞA AKTAR (JSON)") { exportProject() })
        root.addView(action("PROJEYİ İÇE AKTAR (JSON)") { importProject() })
        root.addView(label("Dikey 9:16 • 1080 × 1920 hedef • 45 saniye"))
        status = label("Fotoğrafları seç. İstersen cihazındaki müziği ekle. Video cihaz üzerinde hazırlanır.")
        root.addView(status)
        root.addView(label("Telif hakkı bulunan müzik ve arşiv fotoğraflarını izinsiz kullanma. Yapay canlandırmaları videoda CANLANDIRMA olarak belirt."))
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Insets.of(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            view.setPadding(24, bars.top + 28, 24, bars.bottom + 24)
            insets
        }
        setContentView(ScrollView(this).apply { addView(root) })
        restoreProject()
    }

    private fun pickPhotos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 410)
    }
    private fun pickMusic() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 413)
    }
    private fun pickVoice() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 414)
    }
    private fun exportProject() {
        pendingJson = JSONObject().apply {
            put("format", "miralay-duzce-project"); put("version", 2)
            put("title", titleInput.text.toString()); put("closing", closingInput.text.toString())
            put("aspectRatio", "9:16"); put("width", 1080); put("height", 1920); put("durationSeconds", 45)
            put("photos", JSONArray().also { a -> photos.forEach { a.put(it.toString()) } })
            put("music", musicUri?.toString() ?: JSONObject.NULL)
            put("voice", voiceUri?.toString() ?: JSONObject.NULL)
        }.toString(2)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "Duzce-Anma-Projesi.json")
        }
        startActivityForResult(intent, 411)
    }
    private fun importProject() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        }
        startActivityForResult(intent, 412)
    }
    private fun applyProject(raw: String) {
        val obj = JSONObject(raw)
        require(obj.optString("format") == "miralay-duzce-project") { "Bu dosya Düzce Anma projesi değil." }
        titleInput.setText(obj.optString("title")); closingInput.setText(obj.optString("closing"))
        photos.clear()
        val a = obj.optJSONArray("photos") ?: JSONArray()
        for (i in 0 until a.length()) runCatching { photos.add(Uri.parse(a.getString(i))) }
        musicUri = obj.optString("music").takeIf { it.isNotBlank() && it != "null" }?.let { Uri.parse(it) }
        voiceUri = obj.optString("voice").takeIf { it.isNotBlank() && it != "null" }?.let { Uri.parse(it) }
        updateMediaInfo(); saveLocal()
        status.text = "Proje içe aktarıldı. Medya erişimi bu cihazdaki izinlere bağlıdır."
    }
    private fun saveLocal() {
        prefs.edit().putString("title", titleInput.text.toString())
            .putString("closing", closingInput.text.toString())
            .putString("photos", JSONArray().also { a -> photos.forEach { a.put(it.toString()) } }.toString())
            .putString("music", musicUri?.toString())
            .putString("voice", voiceUri?.toString()).apply()
    }
    private fun restoreProject() {
        titleInput.setText(prefs.getString("title", titleInput.text.toString()))
        closingInput.setText(prefs.getString("closing", closingInput.text.toString()))
        val a = runCatching { JSONArray(prefs.getString("photos", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until a.length()) runCatching { photos.add(Uri.parse(a.getString(i))) }
        musicUri = prefs.getString("music", null)?.let { Uri.parse(it) }
        voiceUri = prefs.getString("voice", null)?.let { Uri.parse(it) }
        updateMediaInfo()
    }
    private fun updateMediaInfo() {
        photoInfo.text = "${photos.size} fotoğraf seçildi"
        musicInfo.text = if (musicUri == null) "İsteğe bağlı müzik seçilmedi" else "Arka plan müziği seçildi"
        voiceInfo.text = if (voiceUri == null) "İsteğe bağlı seslendirme seçilmedi" else "Seslendirme kaydı seçildi"
    }

    private fun makeTitleCard(text: String, fileName: String): Uri {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF101820.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF3F0E8.toInt(); textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val lines = text.split("\n")
        val size = if (lines.size > 2) 54f else 66f
        paint.textSize = size
        val lineHeight = size * 1.55f
        var y = 960f - (lines.size - 1) * lineHeight / 2
        lines.forEach { line ->
            val words = line.split(" ")
            var current = ""
            val wrapped = mutableListOf<String>()
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(test) > 900f && current.isNotEmpty()) {
                    wrapped.add(current); current = word
                } else current = test
            }
            if (current.isNotEmpty()) wrapped.add(current)
            wrapped.forEach { canvas.drawText(it, 540f, y, paint); y += lineHeight }
        }
        val file = File(cacheDir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    @OptIn(UnstableApi::class)
    private fun imageItem(uri: Uri, durationMs: Long): EditedMediaItem {
        val media = MediaItem.Builder().setUri(uri).setImageDurationMs(durationMs).build()
        val portrait = Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        return EditedMediaItem.Builder(media)
            .setFrameRate(30)
            .setEffects(Effects(emptyList(), listOf(portrait)))
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun createVideo() {
        if (exporting) return
        if (photos.isEmpty()) { status.text = "Önce en az bir fotoğraf seç."; return }
        if (photos.size > 37) { status.text = "45 saniyelik videoda en fazla 37 fotoğraf seçebilirsin."; return }
        exporting = true
        cancelRequested = false
        progressBar.progress = 0
        status.text = "Video hazırlanıyor… Bu işlem fotoğraf sayısına ve telefon hızına göre sürebilir."
        val totalMs = 45_000L
        val cardMs = 4_000L
        val photoMs = totalMs - cardMs * 2
        val eachPhotoMs = photoMs / photos.size
        val photoRemainderMs = photoMs % photos.size
        val items = mutableListOf<EditedMediaItem>()
        val startCard = makeTitleCard(titleInput.text.toString(), "duzce-start.png")
        items.add(imageItem(startCard, cardMs))
        photos.forEachIndexed { index, uri ->
            val duration = eachPhotoMs + if (index == photos.lastIndex) photoRemainderMs else 0L
            items.add(imageItem(uri, duration))
        }
        val endCard = makeTitleCard("12 KASIM 1999\nDÜZCE DEPREMİ\n${closingInput.text}", "duzce-end.png")
        items.add(imageItem(endCard, cardMs))
        val sequences = mutableListOf(EditedMediaItemSequence.withAudioAndVideoFrom(items))
        musicUri?.let { uri ->
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
            sequences.add(EditedMediaItemSequence.withAudioFrom(listOf(audioItem)).buildUpon().setIsLooping(true).build())
        }
        voiceUri?.let { uri ->
            val voiceItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
            sequences.add(EditedMediaItemSequence.withAudioFrom(listOf(voiceItem)).buildUpon().setIsLooping(false).build())
        }
        val composition = Composition.Builder(sequences).build()
        val output = File(cacheDir, "duzce-anma-${System.currentTimeMillis()}.mp4")
        outputFile = output
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                runOnUiThread {
                    exporting = false
                    progressTask?.let { mainHandler.removeCallbacks(it) }
                    progressBar.progress = 100
                    transformer = null
                    status.text = "Video tamamlandı. Galeriye kaydediliyor…"
                }
                Thread {
                    val result = runCatching { saveVideoToGallery(output) }
                    output.delete()
                    runOnUiThread {
                        result.onSuccess { status.text = "MP4 hazır. Galeriye kaydedildi: Movies/Duzce Anma" }
                            .onFailure { status.text = "Video oluştu ancak galeriye kaydedilemedi: ${it.message}" }
                    }
                }.start()
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                runOnUiThread {
                    exporting = false
                    progressTask?.let { mainHandler.removeCallbacks(it) }
                    status.text = if (cancelRequested) "Video oluşturma iptal edildi."
                        else "Video oluşturma hatası: ${exportException.message}"
                    transformer = null
                }
                output.delete()
            }
        }
        try {
            transformer = Transformer.Builder(this).addListener(listener).build()
            transformer!!.start(composition, output.absolutePath)
            val task = object : Runnable {
                override fun run() {
                    val current = transformer ?: return
                    val holder = ProgressHolder()
                    val state = current.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        progressBar.progress = holder.progress
                        status.text = "Video hazırlanıyor: ${holder.progress}%"
                    }
                    if (exporting && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 500)
                    }
                }
            }
            progressTask = task
            mainHandler.post(task)
        } catch (e: Exception) {
            exporting = false; transformer = null; status.text = "Başlatılamadı: ${e.message}"; output.delete()
        }
    }

    private fun cancelVideo() {
        if (!exporting) {
            status.text = "Devam eden video oluşturma işlemi yok."
            return
        }
        cancelRequested = true
        runCatching { transformer?.cancel() }
        exporting = false
        transformer = null
        progressTask?.let { mainHandler.removeCallbacks(it) }
        outputFile?.delete()
        status.text = "Video oluşturma iptal edildi."
    }

    private fun saveVideoToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Duzce-Anma-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Duzce Anma")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Galeri kaydı oluşturulamadı")
        contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { it.copyTo(out) } }
            ?: error("Video dosyası yazılamadı")
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
    }

    @Deprecated("Legacy document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            410 -> {
                photos.clear()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        val uri = clip.getItemAt(i).uri
                        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        photos.add(uri)
                    }
                } ?: data.data?.let {
                    runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    photos.add(it)
                }
                updateMediaInfo(); saveLocal(); status.text = "Fotoğraflar seçildi."
            }
            413 -> {
                musicUri = data.data
                musicUri?.let { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                updateMediaInfo(); saveLocal(); status.text = "Arka plan müziği seçildi."
            }
            414 -> {
                voiceUri = data.data
                voiceUri?.let { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                updateMediaInfo(); saveLocal(); status.text = "Seslendirme kaydı seçildi."
            }
            411 -> {
                val payload = pendingJson ?: return
                contentResolver.openOutputStream(data.data!!)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                status.text = "Proje JSON dosyası dışa aktarıldı."
            }
            412 -> {
                val raw = contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: return
                runCatching { applyProject(raw) }.onFailure { status.text = "İçe aktarma hatası: ${it.message}" }
            }
        }
    }
}
