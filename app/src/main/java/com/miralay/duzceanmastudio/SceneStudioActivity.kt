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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Batch scene producer: 5 source photographs x 3 linked 4-second Wan clips.
 * It never reports a clip as complete until an MP4 container and changing frames are verified.
 * Final assembly and per-scene narration are separate pipeline stages; this screen deliberately
 * does not label the batch as a finished film.
 */
class SceneStudioActivity : Activity() {
    private val base = "https://cbensimon-wan2-2-fp8da-aoti-preview2.hf.space"
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var start: Button
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
        root.addView(TextView(this).apply {
            text = "5 fotoğraf × 3 devamlı klip = 15 kısa sahne\nHer klip ayrı ayrı doğrulanır. Ücretsiz sunucu sırası nedeniyle işlem uzun sürebilir."
            textSize = 14f; setTextColor(0xFFE5EAF8.toInt()); setPadding(0,18,0,18)
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 15; progress = 0 }
        root.addView(progress, LinearLayout.LayoutParams(-1, 12))
        status = TextView(this).apply { text = "SEÇİLEN FOTOĞRAF: ${photos.size}/5\n\nÜretimi başlatmak için BAŞLAT düğmesine dokun."; textSize = 16f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,22,0,22) }
        root.addView(status)
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
                if (photos.size != 5) {
                    status.text = "Bu üretim akışı için tam 5 fotoğraf seçmelisin. Şu an: ${photos.size}"
                } else runBatch(photos)
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

    private fun runBatch(photos: ArrayList<String>) {
        start.isEnabled = false
        cancelled = false
        clips.clear()
        Thread {
            val result = runCatching {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Kanatix-Scene-Project-${System.currentTimeMillis()}").apply { mkdirs() }
                outputDir = dir
                for (photoIndex in 0 until 5) {
                    var continuityImage = copyUriToFile(Uri.parse(photos[photoIndex]), File(cacheDir, "scene-source-$photoIndex.jpg"))
                    for (part in 0 until 3) {
                        if (cancelled) error("Kullanıcı işlemi iptal etti.")
                        val clipIndex = photoIndex * 3 + part
                        val scenePrompt = when (part) {
                            0 -> "$basePrompt Continue the exact photographed moment. Establish the scene with restrained, physically plausible environmental and human motion. Do not resolve the event yet."
                            1 -> "$basePrompt Continue naturally from the preceding clip's final frame. Preserve the same people, positions, clothing, damage and lighting. Advance the same action by one small, physically plausible step; no sudden changes."
                            else -> "$basePrompt Continue directly from the preceding clip's final frame. Preserve visual continuity and complete only the next believable beat of the same action; no abrupt rescue, teleportation, dragging or impossible body movement."
                        }
                        val output = generateClip(continuityImage, scenePrompt, clipIndex)
                        clips.add(output)
                        continuityImage = extractLastFrame(output, File(cacheDir, "scene-continuity-$clipIndex.jpg"))
                        val done = clipIndex + 1
                        runOnUiThread {
                            progress.progress = done
                            status.text = "Fotoğraf ${photoIndex + 1}/5 · klip ${part + 1}/3 tamamlandı. Toplam: $done/15"
                        }
                    }
                }
                dir
            }
            runOnUiThread {
                start.isEnabled = true
                result.onSuccess {
                    status.text = "15 doğrulanmış kısa klip üretildi. Henüz birleştirilmiş film ve sahne sesleri eklenmedi. Klip klasörü: ${it.absolutePath}"
                    Toast.makeText(this, "15 sahne üretimi tamamlandı", Toast.LENGTH_LONG).show()
                }.onFailure { status.text = "Üretim durdu: ${it.message}. Tamamlanan klip sayısı: ${clips.size}/15" }
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
            put(6.0)
            put(24)
            put("4x-UltraSharp")
            put(1.0)
            put(true)
            put(false)
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
        runOnUiThread { status.text = "Wan 2.2 klip ${index + 1}/15 üretiyor…" }
        val stream = (URL("$base/gradio_api/call/generate_video/$event").openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000; readTimeout = 300000; setRequestProperty("Accept", "text/event-stream")
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
                            val arr = JSONArray(raw); val obj = arr.optJSONObject(0)
                            resultUrl = obj?.optString("url")?.takeIf { it.startsWith("http") }
                                ?: obj?.optString("path")?.takeIf { it.isNotBlank() }?.let { "$base/gradio_api/file=$it" }
                                ?: arr.optString(0).takeIf { it.startsWith("http") }
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
