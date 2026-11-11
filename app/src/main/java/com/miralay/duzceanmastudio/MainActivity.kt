package com.miralay.duzceanmastudio

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val photos = mutableListOf<Uri>()
    private lateinit var photoInfo: TextView
    private lateinit var titleInput: EditText
    private lateinit var closingInput: EditText

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selected ->
        if (selected != null) {
            photos.clear()
            photos.addAll(selected)
            photoInfo.text = "${photos.size} fotoğraf seçildi"
        }
    }

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
        val heading = label("DÜZCE ANMA VİDEO STÜDYOSU").apply {
            textSize = 21f
            gravity = Gravity.CENTER
        }
        root.addView(heading)
        root.addView(label("12 Kasım 1999 • Unutmadık, unutmayacağız"))
        titleInput = EditText(this).apply {
            hint = "Video başlığı"
            setText("12 KASIM 1999 – DÜZCE DEPREMİ")
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB0BEC5.toInt())
        }
        root.addView(titleInput, ViewGroup.LayoutParams(-1, -2))
        closingInput = EditText(this).apply {
            hint = "Kapanış yazısı"
            setText("UNUTMADIK, UNUTMAYACAĞIZ.")
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB0BEC5.toInt())
        }
        root.addView(closingInput, ViewGroup.LayoutParams(-1, -2))
        val choose = Button(this).apply {
            text = "Fotoğrafları telefondan seç"
            setOnClickListener { photoPicker.launch("image/*") }
        }
        root.addView(choose)
        photoInfo = label("Henüz fotoğraf seçilmedi")
        root.addView(photoInfo)
        root.addView(label("Video biçimi: Dikey 9:16 • Hedef: 1080 × 1920 • Süre: yaklaşık 45 sn"))
        root.addView(label("Bu ilk sürüm proje hazırlama ekranıdır. MP4 dışa aktarma sonraki aşamada eklenecek."))
        val footer = label("Arşiv fotoğraflarında kullanım iznini kontrol edin. Yapay görseller CANLANDIRMA olarak işaretlenmelidir.")
        root.addView(footer)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
