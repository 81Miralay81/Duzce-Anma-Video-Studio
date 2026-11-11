package com.miralay.duzceanmastudio

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val photos = mutableListOf<Uri>()
    private lateinit var photoInfo: TextView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFFE8EDF2.toInt())
            setPadding(0, 16, 0, 8)
        }
        root.addView(label("DÜZCE ANMA VİDEO STÜDYOSU").apply {
            textSize = 21f
            gravity = Gravity.CENTER
        })
        root.addView(label("12 Kasım 1999 • Unutmadık, unutmayacağız"))
        val title = EditText(this).apply {
            hint = "Video başlığı"
            setText("12 KASIM 1999 – DÜZCE DEPREMİ")
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(title, ViewGroup.LayoutParams(-1, -2))
        val closing = EditText(this).apply {
            hint = "Kapanış yazısı"
            setText("UNUTMADIK, UNUTMAYACAĞIZ.")
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(closing, ViewGroup.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "Fotoğrafları telefondan seç"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, 410)
            }
        })
        photoInfo = label("Henüz fotoğraf seçilmedi")
        root.addView(photoInfo)
        root.addView(label("Dikey 9:16 • Hedef 1080 × 1920 • Yaklaşık 45 saniye"))
        root.addView(label("Bu başlangıç sürümü fotoğraf seçimi ve proje metinlerini hazırlar. MP4 oluşturma henüz eklenmedi."))
        root.addView(label("Arşiv fotoğraflarında kullanım iznini kontrol edin. Yapay görseller CANLANDIRMA olarak belirtilmelidir."))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    @Deprecated("Legacy document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 410 && resultCode == RESULT_OK && data != null) {
            photos.clear()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) photos.add(clip.getItemAt(i).uri)
            } ?: data.data?.let { photos.add(it) }
            photoInfo.text = "${photos.size} fotoğraf seçildi"
        }
    }
}
