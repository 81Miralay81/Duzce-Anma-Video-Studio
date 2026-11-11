# Kanatix Video Oluşturucu

Android üzerinde çalışan, fotoğraflardan dikey MP4 videolar hazırlayan ve taşınabilir proje paketleri kullanabilen yerel video stüdyosu.

## Sürüm 0.5.0
- Kanatix adı, canlı renkli arayüz, özel uygulama ikonu ve görsel başlık alanı
- Birden fazla fotoğrafı seçilen sırayla videoya ekleme
- Başlangıç/kapanış metinlerini düzenleme
- İsteğe bağlı arka plan müziği ve ayrı seslendirme kaydı
- 45 saniyelik, dikey 9:16 (1080 × 1920 hedefli), 30 FPS MP4 çıktısı
- İlerleme çubuğu ve dışa aktarmayı iptal etme
- Videoyu Galeri'de Movies/Kanatix Video klasörüne kaydetme
- **ZIP proje paketi içe/dışa aktarma:** proje başlığı, kapanış metni, fotoğraflar, müzik ve seslendirme tek dosyada taşınır
- Paket içe aktarımında ZIP yol geçişi denetimi, dosya adedi ve toplam açılan boyut sınırı
- Media3 Transformer ile cihaz üzerinde işleme; ücretli API/abonelik yok

## Proje paketi biçimi
Bir `.zip` dosyası aşağıdaki yapıyı taşır:

```
project.json
media/
  photo_001.jpg
  photo_002.jpg
  background_music.mp3
  voiceover.mp3
```

`project.json` içinde `format: "kanatix-video-project"`, `version`, `title`, `closing`, `photos` ve isteğe bağlı `music` / `voice` göreli dosya yolları bulunur. Medya dosyaları APK içine gömülmez; proje paketiyle taşınır. Yeni konu için yeni APK gerekmez.

## Bilinen sınırlar
- Bu sürüm fotoğraf tabanlı slayt videosu oluşturur; henüz video klipleri zaman çizelgesinde düzenleme, sahne sürükle-bırak sıralama, geçiş efektleri, katmanlı yazı/animasyon veya önizleme sunmaz.
- Çıktı şu an 45 saniye ve dikey 9:16 biçimindedir.
- Fotoğraflar seçildikleri sırayla kullanılır; en fazla 37 fotoğraf.
- Video kodlama hızı ve desteklenen codec'ler telefonun donanımına bağlıdır.
- Minimum Android sürümü Android 10 (API 29).

## Telif ve içerik doğruluğu
Arşiv fotoğraflarını yalnızca kullanım izni/lisansı uygunsa kullanın. Yapay zekâ ile üretilen sahneleri gerçek arşiv fotoğrafı gibi göstermeyin; videoda açıkça **CANLANDIRMA** olarak belirtin.

## APK derlemesi
GitHub Actions, ana dala her gönderimde ücretsiz debug APK üretir. Artifact adı: `Kanatix-Video-Creator-debug-apk`. APK'nın gerçek cihazda denenmesi gerekir; CI başarısı tek başına cihaz uyumluluğunu garanti etmez.
