"""
Basit örnek: ONNX fonem modelini çağırıp ham çıktıyı gösterir.

Kullanım:
    python example_call_model.py test_sample.wav
"""
import sys

import numpy as np
import onnxruntime as ort
import soundfile as sf

MODEL_PATH = "onnx_model/model_int8.onnx"

# Model çıktısı 0-39 arası id üretiyor, bu tablo id -> ARPABET fonem adı eşlemesi.
ID_TO_PHONE = {
    0: "<pad>", 1: "<unk>", 2: "k", 3: "jh", 4: "d", 5: "g", 6: "dh", 7: "iy",
    8: "z", 9: "l", 10: "ih", 11: "b", 12: "ay", 13: "r", 14: "oy", 15: "t",
    16: "zh", 17: "m", 18: "v", 19: "p", 20: "s", 21: "sh", 22: "ae", 23: "ah",
    24: "aw", 25: "w", 26: "hh", 27: "ow", 28: "th", 29: "er", 30: "n", 31: "y",
    32: "aa", 33: "ch", 34: "eh", 35: "ey", 36: "uh", 37: "ng", 38: "f", 39: "uw",
}
BLANK_ID = 0


def ctc_greedy_decode(pred_ids):
    """Ardışık tekrarları ve blank'i silip düz bir fonem dizisi çıkarır."""
    phones, prev = [], None
    for tid in pred_ids:
        if tid != prev and tid != BLANK_ID:
            phones.append(ID_TO_PHONE.get(tid, "?"))
        prev = tid
    return phones


def main(wav_path: str):
    # 1) Ses dosyasını oku (16kHz, mono olmalı)
    audio, sr = sf.read(wav_path, dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)  # mono'ya indir
    print(f"Ses: {wav_path}  ({len(audio)/sr:.2f} sn, {sr} Hz)")

    # 2) Modeli yükle ve çalıştır
    session = ort.InferenceSession(MODEL_PATH, providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    print(f"Girdi adı: {input_name}, beklenen şekil: {session.get_inputs()[0].shape}")

    logits = session.run(None, {input_name: audio[np.newaxis, :]})[0]
    print(f"Çıktı (logits) şekli: {logits.shape}  ->  [batch, zaman_karesi, fonem_sayısı]")

    # 3) En olası fonemi bul (her zaman karesi için argmax)
    pred_ids = logits[0].argmax(axis=-1)
    print(f"İlk 20 kare tahmini (id): {pred_ids[:20].tolist()}")

    # 4) Tekrarları/blank'i temizleyip okunabilir fonem dizisine çevir
    phones = ctc_greedy_decode(pred_ids.tolist())
    print(f"\nModelin duyduğu fonem dizisi ({len(phones)} fonem):")
    print(" ".join(phones))


if __name__ == "__main__":
    wav_file = sys.argv[1] if len(sys.argv) > 1 else "test_sample.wav"
    main(wav_file)
