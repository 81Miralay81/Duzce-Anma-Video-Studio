# Düzce Anma Video Stüdyosu

12 Kasım 1999 Düzce Depremi için Android telefonda ücretsiz ve yerel çalışan anma videosu hazırlama uygulaması.

## Uygulama özellikleri
- Birden fazla fotoğraf seçme; seçilen sırada videoya ekleme
- Başlangıç ve kapanış kartları ile başlık/kapanış metnini düzenleme
- İsteğe bağlı cihazdan arka plan müziği veya ses kaydı seçme
- 45 saniyelik, dikey 9:16 (1080 × 1920 hedefli), 30 FPS MP4 slayt gösterisi
- Fotoğrafları zaman çizelgesine eşit dağıtma; toplam süreyi 45 saniyeye sabitleme
- Dışa aktarma sırasında yüzde ilerleme çubuğu ve iptal düğmesi
- Videoyu Galeri'de Movies/Duzce Anma klasörüne kaydetme
- Projeyi JSON olarak içe/dışa aktarma ve cihazda yerel ayarları saklama
- Media3 Transformer ile cihaz üzerinde işleme; ücretli API/abonelik yok
- Düzce anma temalı uygulama simgesi

## Bilinen sınırlar
- Fotoğraflar seçildikleri sırayla kullanılır; henüz sürükle-bırak sıralama, sahne başına özel süre, geçiş efekti ve video önizleme yoktur.
- JSON proje dosyası fotoğraf/ses dosyalarını içine gömmez; yalnızca cihaz URI'lerini saklar. Bu nedenle medya erişimi aynı cihazda ve aynı dosyalar mevcutken çalışır.
- Uygulama şu an tek bir arka plan ses dosyası ekler. Ayrı seslendirme kaydı, otomatik Türkçe anlatıcı, ambiyans katmanları ve miksaj sonraki geliştirmelerdir.
- Video oluşturma hızı ve desteklenen kodlayıcılar telefonun donanımına bağlıdır.
- Minimum Android sürümü Android 10 (API 29).

## Telif ve tarihsel doğruluk
Arşiv fotoğraflarını yalnızca kullanım izni/lisansı uygunsa kullanın. Yapay zekâ ile üretilen sahneleri gerçek arşiv fotoğrafı gibi göstermeyin; videoda açıkça **CANLANDIRMA** olarak belirtin.

## APK derlemesi
GitHub Actions, ana dala her gönderimde ücretsiz debug APK derler ve `Duzce-Anma-Studio-debug-apk` adlı artifact olarak yükler. APK henüz cihaz üzerinde denenmeden kararlı sürüm olarak kabul edilmemelidir.
