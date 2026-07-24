import numpy as np
import onnxruntime as ort
import soundfile as sf
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

PHONEME_MODEL = "mostafaashahin/wav2vec2-base-timit-phoneme-arpa-39"
OUT_DIR = "onnx_model"
RAW_ONNX = f"{OUT_DIR}/model.onnx"
QUANT_ONNX = f"{OUT_DIR}/model_int8.onnx"

import os
os.makedirs(OUT_DIR, exist_ok=True)

print("Model yükleniyor...")
processor = Wav2Vec2Processor.from_pretrained(PHONEME_MODEL)
model = Wav2Vec2ForCTC.from_pretrained(PHONEME_MODEL).eval()


class Wrapper(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, input_values):
        return self.m(input_values).logits


wrapper = Wrapper(model)

dummy = torch.randn(1, 16000 * 2)  # 2 saniyelik dummy ses

print("ONNX'e export ediliyor...")
torch.onnx.export(
    wrapper,
    (dummy,),
    RAW_ONNX,
    input_names=["input_values"],
    output_names=["logits"],
    dynamic_axes={
        "input_values": {0: "batch", 1: "sequence"},
        "logits": {0: "batch", 1: "time"},
    },
    opset_version=17,
    dynamo=False,
)
print("Kaydedildi:", RAW_ONNX)

print("Dynamic quantization (int8, sadece MatMul) uygulanıyor...")
quantize_dynamic(
    RAW_ONNX, QUANT_ONNX, weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul"]
)
print("Kaydedildi:", QUANT_ONNX)

print("\n--- Doğrulama: PyTorch vs ONNX (float) vs ONNX (int8) ---")
data, sr = sf.read("test_sample.wav", dtype="float32", always_2d=True)
wav = data.mean(axis=1)
pad = np.zeros(int(sr * 0.4), dtype=np.float32)
wav = np.concatenate([pad, wav, pad])
inputs = processor(wav, sampling_rate=16000, return_tensors="pt")

with torch.no_grad():
    torch_logits = model(inputs.input_values).logits.numpy()

for name, path in [("float32", RAW_ONNX), ("int8", QUANT_ONNX)]:
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    onnx_logits = sess.run(None, {"input_values": inputs.input_values.numpy()})[0]
    torch_pred = torch_logits.argmax(-1)[0]
    onnx_pred = onnx_logits.argmax(-1)[0]
    agree = (torch_pred == onnx_pred).mean()
    print(f"{name}: shape={onnx_logits.shape}, argmax eşleşme oranı={agree:.4f}")
