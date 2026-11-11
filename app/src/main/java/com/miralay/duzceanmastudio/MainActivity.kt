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
    private var pendingPackageFile: File? = null
    private var exporting = false
    private var cancelRequested = false
    private val prefs by lazy { getSharedPreferences("project", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 15, 31)
        window.navigationBarColor = android.graphics.Color.rgb(12, 15, 31)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 22, 20, 28)
            setBackgroundColor(0xFF0C0F1F.toInt())
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        fun rounded(color: Int, radius: Float = 22f) = android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = radius
        }
        fun gradient(colors: IntArray, radius: Float = 24f) = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors
        ).apply { cornerRadius = radius }
        fun action(label: String, colors: IntArray = intArrayOf(0xFFFF6B35.toInt(), 0xFFE83F72.toInt()), run: () -> Unit) =
            Button(this).apply {
                text = label; isAllCaps = false; textSize = 15f
                setTextColor(android.graphics.Color.WHITE)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                background = gradient(colors, 18f)
                stateListAnimator = null
                elevation = 5f
                setPadding(18, 7, 18, 7)
                minHeight = 54
                setOnClickListener { run() }
            }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = rounded(0xFF171D35.toInt(), 22f)
            elevation = 2f
        }
        fun addGap(target: LinearLayout, height: Int = 14) {
            target.addView(android.view.View(this), LinearLayout.LayoutParams(1, height))
        }

        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "KX"; textSize = 22f; typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER; setTextColor(0xFF11152A.toInt())
            background = gradient(intArrayOf(0xFFFFD166.toInt(), 0xFFFF6B35.toInt()), 20f)
            layoutParams = LinearLayout.LayoutParams(58, 58)
        }
        brandRow.addView(mark)
        val brandCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13, 0, 0, 0)
        }
        brandCopy.addView(text("KANATIX", 23f, 0xFFFFFFFF.toInt(), true))
        brandCopy.addView(text("VIDEO OLUŞTURUCU", 11f, 0xFFFFB86B.toInt(), true))
        brandRow.addView(brandCopy)
        brandRow.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f))
        brandRow.addView(text("STÜDYO  •  01", 10f, 0xFF9CA8C7.toInt(), true))
        page.addView(brandRow)
        addGap(page, 20)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 22)
            background = gradient(intArrayOf(0xFF6D35D4.toInt(), 0xFFB52D83.toInt(), 0xFFFF713D.toInt()), 28f)
            elevation = 8f
        }
        hero.addView(text("HAYALİNDEKİ VİDEO,", 13f, 0xFFFFE6D8.toInt(), true))
        hero.addView(text("BİRKAÇ DOKUNUŞLA HAZIR.", 25f, android.graphics.Color.WHITE, true))
        addGap(hero, 8)
        hero.addView(text("Fotoğraflarını, videolarını ve seslerini bir araya getir. Hikâyeni kendi tarzında oluştur.", 14f, 0xFFFFF1ED.toInt()))
        addGap(hero, 17)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listOf("✦  1080P", "▣  9:16 / 16:9", "♫  MÜZİK + SES").forEach { value ->
            val chip = text(value, 10f, android.graphics.Color.WHITE, true).apply {
                setPadding(11, 9, 11, 9); background = rounded(0x44FFFFFF, 30f)
            }
            val lp = LinearLayout.LayoutParams(-2, -2); lp.marginEnd = 7
            chips.addView(chip, lp)
        }
        hero.addView(chips)
        page.addView(hero)
        addGap(page, 18)

        val projectCard = card()
        projectCard.addView(text("PROJE DETAYLARI", 12f, 0xFFFFB86B.toInt(), true))
        addGap(projectCard, 10)
        projectCard.addView(text("Videona bir isim ver", 15f, 0xFFF4F5FF.toInt(), true))
        titleInput = EditText(this).apply {
            hint = "Örn. Düzce Anma Videosu"; setTextColor(-1); setHintTextColor(0xFF9CA8C7.toInt())
            setText(prefs.getString("title", "")); textSize = 15f
            background = rounded(0xFF242C49.toInt(), 14f); setPadding(15, 13, 15, 13)
        }
        projectCard.addView(titleInput, LinearLayout.LayoutParams(-1, -2))
        addGap(projectCard, 12)
        projectCard.addView(text("Kapanış mesajı", 15f, 0xFFF4F5FF.toInt(), true))
        closingInput = EditText(this).apply {
            hint = "Videonun sonunda görünecek yazı"; setTextColor(-1); setHintTextColor(0xFF9CA8C7.toInt())
            setText(prefs.getString("closing", "")); textSize = 15f
            background = rounded(0xFF242C49.toInt(), 14f); setPadding(15, 13, 15, 13)
        }
        projectCard.addView(closingInput, LinearLayout.LayoutParams(-1, -2))
        val saveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { saveLocal() }
        }
        titleInput.addTextChangedListener(saveWatcher)
        closingInput.addTextChangedListener(saveWatcher)
        page.addView(projectCard)
        addGap(page)

        fun mediaCard(title: String, desc: String, buttonText: String, info: TextView, run: () -> Unit) {
            val c = card()
            c.addView(text(title, 16f, 0xFFFFFFFF.toInt(), true))
            addGap(c, 5)
            c.addView(text(desc, 12f, 0xFF9CA8C7.toInt()))
            addGap(c, 10)
            c.addView(info.apply { textSize = 12f; setTextColor(0xFF75E0C0.toInt()) })
            addGap(c, 10)
            c.addView(action(buttonText, intArrayOf(0xFF2878F0.toInt(), 0xFF19B6C8.toInt()), run))
            page.addView(c)
            addGap(page)
        }
        photoInfo = text("Henüz görsel eklenmedi", 12f, 0xFF75E0C0.toInt())
        musicInfo = text("İsteğe bağlı • Henüz seçilmedi", 12f, 0xFF75E0C0.toInt())
        voiceInfo = text("İsteğe bağlı • Henüz seçilmedi", 12f, 0xFF75E0C0.toInt())
        mediaCard("01  GÖRSELLERİNİ EKLE", "Fotoğraf ve video sahnelerini seçerek hikâyeni oluştur.", "＋  MEDYA SEÇ", photoInfo, ::pickPhotos)
        mediaCard("02  MÜZİĞİNİ BELİRLE", "Arka plan müziğiyle videona atmosfer kat.", "♫  MÜZİK SEÇ", musicInfo, ::pickMusic)
        mediaCard("03  SESLENDİRME EKLE", "Kendi ses kaydını veya anlatımını kullan.", "🎙  SES DOSYASI SEÇ", voiceInfo, ::pickVoice)

        val packageCard = card()
        packageCard.addView(text("HAZIR PROJE PAKETİ", 15f, 0xFFFFB86B.toInt(), true))
        addGap(packageCard, 5)
        packageCard.addView(text("Başka bir konuda hazırlanmış projeyi tek ZIP dosyasıyla içe aktar. Medyalar paketin içinden otomatik yerleştirilir.", 12f, 0xFFB8C2DD.toInt()))
        addGap(packageCard, 12)
        packageCard.addView(action("⇩  PROJE PAKETİ İÇE AKTAR", intArrayOf(0xFF7856E8.toInt(), 0xFFB34BCB.toInt())) { importProject() })
        addGap(packageCard, 8)
        packageCard.addView(action("⇧  PROJE PAKETİ DIŞA AKTAR", intArrayOf(0xFF34466F.toInt(), 0xFF536A9B.toInt())) { exportProject() })
        page.addView(packageCard)
        addGap(page, 18)

        val render = action("▶   VİDEOYU OLUŞTUR", intArrayOf(0xFFFF8A00.toInt(), 0xFFE52E71.toInt()), ::createVideo).apply {
            textSize = 17f; minHeight = 62
        }
        page.addView(render)
        addGap(page, 10)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF8A00.toInt())
        }
        page.addView(progressBar, LinearLayout.LayoutParams(-1, 9))
        addGap(page, 7)
        status = text("Stüdyo hazır. Önce görsellerini ekle veya hazır bir proje paketi aç.", 12f, 0xFFB8C2DD.toInt())
        page.addView(status)
        addGap(page, 8)
        page.addView(action("İŞLEMİ İPTAL ET", intArrayOf(0xFF39415D.toInt(), 0xFF252C44.toInt()), ::cancelVideo))
        addGap(page, 18)
        val footer = text("KANATIX STUDIO  •  FİKRİNİ GÖRÜNTÜYE DÖNÜŞTÜR", 10f, 0xFF7885A8.toInt(), true)
        footer.gravity = Gravity.CENTER
        page.addView(footer)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF0C0F1F.toInt())
            addView(page)
        }
        setContentView(scroll)
        restoreProject()
        window.decorView.systemUiVisibility = 0
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
        try {
            val pack = File(cacheDir, "kanatix-project-${System.currentTimeMillis()}.zip")
            val manifest = JSONObject().apply {
                put("format", "kanatix-video-project"); put("version", 1)
                put("title", titleInput.text.toString()); put("closing", closingInput.text.toString())
                put("aspectRatio", "9:16"); put("width", 1080); put("height", 1920); put("fps", 30)
                put("photos", JSONArray())
            }
            java.util.zip.ZipOutputStream(pack.outputStream().buffered()).use { zip ->
                fun addMedia(uri: Uri, entryName: String): String {
                    val mime = contentResolver.getType(uri) ?: ""
                    val ext = when {
                        mime.contains("png") -> "png"; mime.contains("webp") -> "webp"
                        mime.contains("mp4") -> "mp4"; mime.contains("wav") -> "wav"
                        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                        mime.contains("ogg") -> "ogg"; else -> "jpg"
                    }
                    val path = "$entryName.$ext"
                    zip.putNextEntry(java.util.zip.ZipEntry("media/$path"))
                    contentResolver.openInputStream(uri)?.use { it.copyTo(zip) } ?: error("Medya dosyası okunamadı")
                    zip.closeEntry()
                    return "media/$path"
                }
                photos.forEachIndexed { index, uri ->
                    manifest.getJSONArray("photos").put(addMedia(uri, "photo_${(index + 1).toString().padStart(3, '0')}"))
                }
                musicUri?.let { manifest.put("music", addMedia(it, "background_music")) }
                voiceUri?.let { manifest.put("voice", addMedia(it, "voiceover")) }
                zip.putNextEntry(java.util.zip.ZipEntry("project.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            pendingPackageFile = pack
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "Kanatix-Proje-Paketi.zip")
            }
            startActivityForResult(intent, 415)
            status.text = "Proje paketi hazır. Kaydedilecek konumu seç."
        } catch (e: Exception) { status.text = "Paket oluşturulamadı: ${e.message}" }
    }    private fun importProject() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        startActivityForResult(intent, 416)
    }    private fun applyProject(raw: String) {
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
    private fun applyPackageProject(raw: String, projectDir: File) {
        val obj = JSONObject(raw)
        require(obj.optString("format") == "kanatix-video-project") { "Bu dosya Kanatix video proje paketi değil." }
        titleInput.setText(obj.optString("title")); closingInput.setText(obj.optString("closing"))
        photos.clear()
        val a = obj.optJSONArray("photos") ?: JSONArray()
        for (i in 0 until a.length()) {
            val relative = a.getString(i)
            val file = File(projectDir, relative).canonicalFile
            require(file.path.startsWith(projectDir.canonicalPath + File.separator) && file.isFile) { "Paket içinde medya bulunamadı: $relative" }
            photos.add(Uri.fromFile(file))
        }
        fun mediaUri(key: String): Uri? {
            val relative = obj.optString(key).takeIf { it.isNotBlank() && it != "null" } ?: return null
            val file = File(projectDir, relative).canonicalFile
            require(file.path.startsWith(projectDir.canonicalPath + File.separator) && file.isFile) { "Paket içinde ses dosyası bulunamadı: $relative" }
            return Uri.fromFile(file)
        }
        musicUri = mediaUri("music"); voiceUri = mediaUri("voice")
        updateMediaInfo(); saveLocal()
        status.text = "Proje paketi açıldı. ${photos.size} görsel ve ses dosyaları yerleştirildi."
    }

    private fun extractProjectPackage(source: Uri) {
        val base = File(filesDir, "kanatix_projects").apply { mkdirs() }
        val destination = File(base, "project_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempZip = File(cacheDir, "kanatix-import.zip")
        contentResolver.openInputStream(source)?.use { input -> tempZip.outputStream().use { input.copyTo(it) } }
            ?: error("Seçilen ZIP dosyası okunamadı")
        var totalBytes = 0L
        var entryCount = 0
        java.util.zip.ZipInputStream(tempZip.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= 150) { "Paket çok fazla dosya içeriyor." }
                val name = entry.name.replace('\\', '/')
                require(!name.startsWith("/") && !name.split('/').any { it == ".." } && !name.contains(":")) { "Güvenli olmayan dosya yolu." }
                val out = File(destination, name).canonicalFile
                require(out.path.startsWith(destination.canonicalPath + File.separator) || out == destination) { "Paket dosya yolu güvenli değil." }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= 1024L * 1024L * 1024L) { "Paket boyutu 1 GB sınırını aşıyor." }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val manifest = File(destination, "project.json")
        require(manifest.isFile) { "ZIP içinde project.json bulunamadı." }
        applyPackageProject(manifest.readText(Charsets.UTF_8), destination)
        tempZip.delete()
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
        val startCard = makeTitleCard(titleInput.text.toString(), "kanatix-start.png")
        items.add(imageItem(startCard, cardMs))
        photos.forEachIndexed { index, uri ->
            val duration = eachPhotoMs + if (index == photos.lastIndex) photoRemainderMs else 0L
            items.add(imageItem(uri, duration))
        }
        val endCard = makeTitleCard(closingInput.text.toString(), "kanatix-end.png")
        items.add(imageItem(endCard, cardMs))
        // Media3 1.6.1 API: use the list constructors for sequences.
        val sequences = mutableListOf(EditedMediaItemSequence(items))
        musicUri?.let { uri ->
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
            sequences.add(EditedMediaItemSequence(listOf(audioItem), true))
        }
        voiceUri?.let { uri ->
            val voiceMedia = MediaItem.Builder().setUri(uri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(totalMs).build())
                .build()
            val voiceItem = EditedMediaItem.Builder(voiceMedia).build()
            sequences.add(EditedMediaItemSequence(listOf(voiceItem), false))
        }
        val composition = Composition.Builder(sequences).build()
        val output = File(cacheDir, "kanatix-video-${System.currentTimeMillis()}.mp4")
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
                        result.onSuccess { status.text = "MP4 hazır. Galeriye kaydedildi: Movies/Kanatix Video" }
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
            put(MediaStore.Video.Media.DISPLAY_NAME, "Kanatix-Video-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Kanatix Video")
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
            415 -> {
                val file = pendingPackageFile ?: return
                runCatching {
                    contentResolver.openOutputStream(data.data!!)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: error("Dosya hedefi açılamadı")
                    file.delete()
                    status.text = "Kanatix proje paketi dışa aktarıldı."
                }.onFailure { status.text = "Paket kaydedilemedi: ${it.message}" }
            }
            416 -> {
                runCatching { extractProjectPackage(data.data!!) }
                    .onFailure { status.text = "Paket içe aktarılamadı: ${it.message}" }
            }
        }
    }
}
