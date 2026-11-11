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
        val navy = 0xFF050B1C.toInt()
        val white = 0xFFF7F8FF.toInt()
        val muted = 0xFFB9C5E3.toInt()
        window.statusBarColor = navy
        window.navigationBarColor = navy

        fun gradient(colors: IntArray, radius: Float = 24f) =
            android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }
        fun label(value: String, size: Float, color: Int = white, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
        fun gap(parent: LinearLayout, h: Int = 14) {
            parent.addView(android.view.View(this), LinearLayout.LayoutParams(1, h))
        }
        fun button(value: String, colors: IntArray, action: () -> Unit) =
            Button(this).apply {
                text = value
                isAllCaps = false
                textSize = 15f
                setTextColor(white)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                background = gradient(colors, 20f)
                stateListAnimator = null
                elevation = 5f
                minHeight = 54
                setPadding(16, 8, 16, 8)
                setOnClickListener { action() }
            }
        fun panel(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            background = gradient(intArrayOf(0xFF111D3A.toInt(), 0xFF18264A.toInt()), 22f)
            elevation = 3f
        }
        fun actionTile(icon: String, title: String, subtitle: String, colors: IntArray, action: () -> Unit): LinearLayout {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(15, 16, 12, 16)
                background = gradient(colors, 22f)
                elevation = 5f
                setOnClickListener { action() }
            }
            val symbol = label(icon, 31f, white, true).apply { gravity = Gravity.CENTER }
            tile.addView(symbol, LinearLayout.LayoutParams(58, 62))
            val copy = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 0, 0, 0)
            }
            copy.addView(label(title, 16f, white, true))
            copy.addView(label(subtitle, 11f, 0xFFF1F3FF.toInt()))
            tile.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            tile.addView(label("›", 30f, white, true))
            return tile
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 24)
            setBackgroundColor(navy)
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "K"
            textSize = 30f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(0xFF11152A.toInt())
            background = gradient(intArrayOf(0xFFFFD34F.toInt(), 0xFFFF653D.toInt()), 18f)
        }
        top.addView(logo, LinearLayout.LayoutParams(54, 54))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 0, 0)
        }
        brand.addView(label("KANATIX", 22f, white, true))
        brand.addView(label("VİDEO OLUŞTURUCU", 10f, 0xFFFFC45D.toInt(), true))
        top.addView(brand)
        top.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(white)
            setOnClickListener { scroll.smoothScrollTo(0, page.height) }
        }
        top.addView(settings, LinearLayout.LayoutParams(48, 54))
        page.addView(top)
        gap(page, 16)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 15, 15, 18)
            background = gradient(intArrayOf(0xFF082B66.toInt(), 0xFF142A68.toInt(), 0xFF4B176D.toInt()), 25f)
            elevation = 8f
        }
        val banner = ImageView(this).apply {
            setImageResource(R.drawable.banner_kanatix)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        hero.addView(banner, LinearLayout.LayoutParams(-1, 142))
        gap(hero, 7)
        hero.addView(label("HAYALLERİNİ VİDEOYA DÖNÜŞTÜR", 19f, white, true))
        hero.addView(label("Fotoğraf, müzik ve metinle kendi hikâyeni oluştur.", 12f, 0xFFE0E8FF.toInt()))
        gap(hero, 13)
        hero.addView(button("▶   YENİ PROJE BAŞLAT", intArrayOf(0xFFFFD21F.toInt(), 0xFFFF7B00.toInt())) {
            titleInput.requestFocus()
            scroll.post { scroll.smoothScrollTo(0, titleInput.top + 950) }
            status.text = "Proje başlığı ve kapanış metnini düzenleyebilir, ardından medyalarını ekleyebilirsin."
        })
        page.addView(hero)
        gap(page, 16)

        page.addView(actionTile("▤", "Proje Paketi İçe Aktar", "Hazır projeni ZIP dosyasından aç", intArrayOf(0xFF7227C9.toInt(), 0xFF421C91.toInt())) { importProject() })
        gap(page, 10)
        page.addView(actionTile("⇧", "Proje Paketi Dışa Aktar", "Proje ve medyalarını tek dosyada sakla", intArrayOf(0xFF078C4A.toInt(), 0xFF075B48.toInt())) { exportProject() })
        gap(page, 10)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val media = actionTile("▧", "Medya Seç", "Fotoğraf ekle", intArrayOf(0xFFFF9A00.toInt(), 0xFFE44825.toInt())) { pickPhotos() }
        val music = actionTile("♫", "Müzik / Ses", "Ses dosyası ekle", intArrayOf(0xFF00AFCB.toInt(), 0xFF075E9C.toInt())) {
            val choices = arrayOf("Arka plan müziği seç", "Seslendirme seç")
            android.app.AlertDialog.Builder(this).setTitle("Ses dosyası").setItems(choices) { _, which ->
                if (which == 0) pickMusic() else pickVoice()
            }.show()
        }
        row.addView(media, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = 7 })
        row.addView(music, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = 7 })
        page.addView(row)
        gap(page, 18)

        page.addView(label("PROJE ŞABLONLARI", 17f, white, true))
        gap(page, 8)
        val templates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val templateScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        fun template(name: String, desc: String, emoji: String, title: String, closing: String, colors: IntArray) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 14, 14, 14)
                background = gradient(colors, 18f)
                setOnClickListener {
                    titleInput.setText(title)
                    closingInput.setText(closing)
                    status.text = name + " şablonu seçildi. Şimdi fotoğraf ve seslerini ekle."
                    scroll.smoothScrollTo(0, titleInput.top + 900)
                }
            }
            item.addView(label(emoji, 30f, white, true))
            item.addView(label(name, 14f, white, true))
            item.addView(label(desc, 10f, 0xFFE7EBFF.toInt()))
            templates.addView(item, LinearLayout.LayoutParams(150, 118).apply { marginEnd = 9 })
        }
        template("Düzce Anma", "Anma videosu", "🕯", "12 Kasım 1999 – Düzce", "UNUTMADIK, UNUTMAYACAĞIZ.", intArrayOf(0xFF303B59.toInt(), 0xFF111827.toInt()))
        template("10 Kasım", "Saygı ve anma", "🇹🇷", "10 Kasım Anma Videosu", "Saygı, özlem ve minnetle…", intArrayOf(0xFF9B1C32.toInt(), 0xFF3A1020.toInt()))
        template("Doğa & Gezi", "Anılarını paylaş", "🏞", "Doğa ve Gezi", "Biriktirdiğimiz güzel anılara…", intArrayOf(0xFF087A65.toInt(), 0xFF123F48.toInt()))
        template("Ürün Tanıtımı", "Markanı öne çıkar", "🎬", "Ürün Tanıtım Videosu", "Detaylarda farkı keşfedin.", intArrayOf(0xFF9A4D0B.toInt(), 0xFF51220D.toInt()))
        templateScroll.addView(templates)
        page.addView(templateScroll, LinearLayout.LayoutParams(-1, 122))
        gap(page, 18)

        val editor = panel()
        editor.addView(label("PROJE BİLGİLERİ", 14f, 0xFFFFC45D.toInt(), true))
        gap(editor, 8)
        titleInput = EditText(this).apply {
            hint = "Proje başlığı"
            setTextColor(white); setHintTextColor(muted); textSize = 14f
            setText(prefs.getString("title", ""))
            background = gradient(intArrayOf(0xFF263456.toInt(), 0xFF1B2947.toInt()), 13f)
            setPadding(14, 12, 14, 12)
        }
        editor.addView(titleInput, LinearLayout.LayoutParams(-1, -2))
        gap(editor, 8)
        closingInput = EditText(this).apply {
            hint = "Kapanış mesajı"
            setTextColor(white); setHintTextColor(muted); textSize = 14f
            setText(prefs.getString("closing", ""))
            background = gradient(intArrayOf(0xFF263456.toInt(), 0xFF1B2947.toInt()), 13f)
            setPadding(14, 12, 14, 12)
        }
        editor.addView(closingInput, LinearLayout.LayoutParams(-1, -2))
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { saveLocal() }
        }
        titleInput.addTextChangedListener(watcher)
        closingInput.addTextChangedListener(watcher)
        page.addView(editor)
        gap(page, 12)

        val mediaPanel = panel()
        photoInfo = label("Henüz fotoğraf seçilmedi", 12f, 0xFF7BE3C4.toInt())
        musicInfo = label("Müzik seçilmedi", 12f, 0xFF7BE3C4.toInt())
        voiceInfo = label("Seslendirme seçilmedi", 12f, 0xFF7BE3C4.toInt())
        mediaPanel.addView(label("MEDYA DURUMU", 14f, 0xFFFFC45D.toInt(), true))
        gap(mediaPanel, 6)
        mediaPanel.addView(photoInfo)
        mediaPanel.addView(musicInfo)
        mediaPanel.addView(voiceInfo)
        page.addView(mediaPanel)
        gap(page, 14)

        page.addView(button("▶   VİDEOYU OLUŞTUR", intArrayOf(0xFFFFB000.toInt(), 0xFFE52E71.toInt())) { createVideo() })
        gap(page, 9)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFFB000.toInt())
        }
        page.addView(progressBar, LinearLayout.LayoutParams(-1, 9))
        gap(page, 7)
        status = label("Stüdyo hazır. Bir şablon seç veya yeni proje başlat.", 12f, muted)
        page.addView(status)
        gap(page, 8)
        page.addView(button("İŞLEMİ İPTAL ET", intArrayOf(0xFF39415D.toInt(), 0xFF252C44.toInt())) { cancelVideo() })
        gap(page, 16)
        val foot = label("KANATIX  •  HAYAL ET. OLUŞTUR. PAYLAŞ.", 10f, 0xFF8B9CC5.toInt(), true)
        foot.gravity = Gravity.CENTER
        page.addView(foot)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            background = gradient(intArrayOf(0xFF101A35.toInt(), 0xFF071126.toInt()), 0f)
        }
        listOf("⌂\nAna Sayfa", "▧\nMedya", "▶\nOluştur", "ⓘ\nHakkında").forEachIndexed { index, caption ->
            val item = label(caption, 11f, if (index == 0) 0xFFFFBD22.toInt() else 0xFFDCE5FF.toInt(), true).apply {
                gravity = Gravity.CENTER
                setPadding(4, 5, 4, 5)
                setOnClickListener {
                    when (index) {
                        1 -> pickPhotos()
                        2 -> createVideo()
                        3 -> android.app.AlertDialog.Builder(this@MainActivity).setTitle("Kanatix Video Oluşturucu")
                            .setMessage("Fotoğraf, müzik ve metinlerle kendi videolarını hazırla. Projelerini ZIP paketi olarak saklayıp yeniden açabilirsin.")
                            .setPositiveButton("Tamam", null).show()
                        else -> scroll.smoothScrollTo(0, 0)
                    }
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
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
