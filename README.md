# Düzce Anma Video Stüdyosu

Android telefonlarda ücretsiz ve yerel çalışan, 12 Kasım 1999 Düzce Depremi anma videosu hazırlama uygulaması.

## Mevcut özellikler
- Telefonda birden fazla fotoğraf seçme ve erişim iznini saklama
- Başlık ve kapanış metni düzenleme
- İsteğe bağlı arka plan müziği seçme
- 45 saniyelik, 9:16 dikey MP4 slayt gösterisi oluşturma
- Başlangıç ve kapanış kartlarını otomatik üretme
- MP4 dosyasını cihazın Galeri/Movies/Duzce Anma klasörüne kaydetme
- Projeyi JSON olarak dışa ve içe aktarma
- Video işleme cihaz üzerinde Media3 Transformer ile yapılır; ücretli API gerekmez

## Bilinen sınırlar
- Fotoğraflar şu an seçildikleri sırayla eklenir; zaman çizelgesinde sürükle-bırak sıralama ve geçiş efektleri henüz yoktur.
- JSON proje dosyası medya dosyalarını içine gömmez; seçilen fotoğraf ve ses URI'lerini saklar. Bu nedenle içe aktarma esas olarak aynı cihazdaki kaynaklarla çalışır.
- Ses parçası arka plan müziği olarak kullanılır. Ses miksajı, seslendirme kaydı, gerçek arşiv fotoğrafı arşivi ve gelişmiş efektler sonraki geliştirmelerdir.
- Video oluşturma işlemi telefonun işlemci ve depolama performansına bağlıdır.

## Telif ve tarihsel doğruluk
Arşiv fotoğraflarını yalnızca kullanım izni/lisansı uygunsa kullanın. Yapay zekâ ile üretilen sahneleri gerçek arşiv fotoğrafı gibi göstermeyin; ekranda açıkça **CANLANDIRMA** olarak belirtin.

## Geliştirme ve APK
Proje Android/Kotlin tabanlıdır. GitHub Actions, her ana dala gönderimde ücretsiz debug APK derlemesi yapar ve çıktıyı workflow artifact olarak sunar.
