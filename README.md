# Telaffuz Testi

İngilizce telaffuz pratiği için tamamen cihaz üzerinde (çevrimdışı) çalışan Android uygulaması.
wav2vec2 tabanlı bir fonem modeli + CTC forced-alignment ile hedef metnin her fonemi için
güven skoru üretir.

## Kurulum

Modeli boyutu (120MB+) nedeniyle repoya dahil edilmedi. Android projesini derlemeden önce:

```bash
cd backend
pip install -r requirements.txt
python export_onnx.py
cp onnx_model/model_int8.onnx ../android/app/src/main/assets/phoneme_model.onnx
```

Sonra `android/` klasörünü Android Studio ile açıp normal şekilde derleyip çalıştırabilirsiniz.

## Klasörler

- `android/` — Kotlin + Jetpack Compose Android uygulaması (tüm işleme cihaz üzerinde: ONNX Runtime + CMUdict + Kotlin'de yazılmış CTC forced-align)
- `backend/` — modeli ONNX'e çevirip doğrulamak için kullanılan Python script'leri (uygulamanın çalışması için gerekli değil, sadece geliştirme/model hazırlama aşamasında kullanılır)
