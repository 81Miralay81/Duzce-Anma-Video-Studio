package com.miralay.duzceanmastudio

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val photos = mutableListOf<Uri>()
    private lateinit var photoInfo: TextView
    private lateinit var titleInput: EditText
    private lateinit var closingInput: EditText
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("project", MODE_PRIVATE) }
    private var pendingJson: String? = null

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
        root.addView(action("FOTOĞRAFLARI SEÇ", ::pickPhotos))
        photoInfo = label("Henüz fotoğraf seçilmedi"); root.addView(photoInfo)
        root.addView(action("PROJEYİ DIŞA AKTAR (JSON)") { exportProject() })
        root.addView(action("PROJEYİ İÇE AKTAR (JSON)") { importProject() })
        root.addView(label("Dikey 9:16 • 1080 × 1920 • Yaklaşık 45 saniye"))
        status = label("Proje hazır. Fotoğrafları seçip proje dosyasını kaydedebilirsin.")
        root.addView(status)
        root.addView(label("Bu sürüm proje paketini kaydeder/açar. MP4 video oluşturma motoru henüz tamamlanmadı. Arşiv fotoğraflarının kullanım iznini kontrol et; yapay canlandırmaları CANLANDIRMA olarak işaretle."))
        setContentView(ScrollView(this).apply { addView(root) })
        restoreProject()
    }

    private fun pickPhotos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 410)
    }
    private fun exportProject() {
        pendingJson = JSONObject().apply {
            put("format", "miralay-duzce-project"); put("version", 1)
            put("title", titleInput.text.toString()); put("closing", closingInput.text.toString())
            put("aspectRatio", "9:16"); put("width", 1080); put("height", 1920); put("durationSeconds", 45)
            put("photos", JSONArray().also { a -> photos.forEach { a.put(it.toString()) } })
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
        updatePhotoInfo(); saveLocal()
        status.text = "Proje içe aktarıldı. Fotoğraf erişimi cihaz izinlerine bağlıdır."
    }
    private fun saveLocal() {
        prefs.edit().putString("title", titleInput.text.toString())
            .putString("closing", closingInput.text.toString())
            .putString("photos", JSONArray().also { a -> photos.forEach { a.put(it.toString()) } }.toString()).apply()
    }
    private fun restoreProject() {
        titleInput.setText(prefs.getString("title", titleInput.text.toString()))
        closingInput.setText(prefs.getString("closing", closingInput.text.toString()))
        val a = runCatching { JSONArray(prefs.getString("photos", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until a.length()) runCatching { photos.add(Uri.parse(a.getString(i))) }
        updatePhotoInfo()
    }
    private fun updatePhotoInfo() { photoInfo.text = "${photos.size} fotoğraf seçildi" }

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
                updatePhotoInfo(); saveLocal(); status.text = "Fotoğraflar seçildi ve yerel projeye kaydedildi."
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
