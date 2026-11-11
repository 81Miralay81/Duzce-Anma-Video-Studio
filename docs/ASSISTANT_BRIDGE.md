# Miralay Asistan Köprüsü — güvenli çalışma modeli

## Önemli sınır
Android uygulaması ChatGPT'ye kendi başına oturum açma, telefonda arka planda kontrol verme veya sınırsız cihaz erişimi sağlayamaz. Bu depodaki GitHub bağlantısı, asistanın kaynak kodu ve proje dosyaları üzerinde çalışmasını sağlar; kurulu APK'ya canlı cihaz bağlantısı sağlamaz.

## Amaçlanan kullanıcı akışı
1. Kullanıcı uygulamada video projesini ve medya listesini hazırlar.
2. Uygulama bir `.duzceproject.json` proje tanımı dışa aktarır.
3. Kullanıcı, seçtiği fotoğraf/ses dosyalarını ve proje tanımını ChatGPT konuşmasına kendisi ekler.
4. Asistan proje metnini, sahne sırasını, süreleri, altyazıları ve ses miksajı talimatlarını düzenler.
5. Kullanıcı düzenlenmiş proje paketini uygulamaya geri aktarır.
6. Uygulama yalnızca kullanıcının açıkça başlattığı işlemde, cihaz üzerinde MP4 üretir.

## Güvenlik kuralları
- Gizli API anahtarı, GitHub token'ı, parola veya kişisel erişim belirteci APK içine gömülmez.
- Asistan telefonun dosya sistemine, mikrofonuna, kamerasına veya diğer uygulamalarına sessizce erişemez.
- Fotoğraf ve ses seçimi Android sistem dosya seçicisiyle, kullanıcının açık seçimiyle yapılır.
- Dışa aktarma, medya yükleme ve video oluşturma kullanıcı eylemiyle başlatılır.
- Herhangi bir uzaktan komut özelliği eklenirse imzalı komut, kullanıcı onayı, kapsam sınırlaması ve iptal seçeneği zorunludur.
- Arşiv fotoğraflarının telif/kullanım izni kontrol edilir. Yapay görseller gerçek arşiv gibi gösterilmez; "CANLANDIRMA" olarak etiketlenir.

## Proje paketinde bulunması planlanan alanlar
- `schemaVersion`: proje şema sürümü
- `title`, `closingText`: başlık ve kapanış metni
- `aspectRatio`: 9:16
- `targetDurationSeconds`: hedef süre
- `scenes[]`: sahne sırası, medya dosyası adı, süre, geçiş ve ekrandaki yazı
- `audio[]`: ses dosyası adı, başlangıç zamanı ve ses seviyesi

Bu belge entegrasyon tasarımını tanımlar; tek başına canlı ChatGPT bağlantısı veya tamamlanmış video render özelliği anlamına gelmez.
