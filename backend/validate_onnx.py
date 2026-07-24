import math

import numpy as np
import onnxruntime as ort
import soundfile as sf
import torch
import torchaudio
from g2p_en import G2p
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

PHONEME_MODEL = "mostafaashahin/wav2vec2-base-timit-phoneme-arpa-39"
PHONE_FOLD = {"ao": "aa"}
TEXT = "ACTUALLY I'M LOOKING FORWARD TO THAT MYSELF"
WAV_PATH = "test_sample.wav"

processor = Wav2Vec2Processor.from_pretrained(PHONEME_MODEL)
model = Wav2Vec2ForCTC.from_pretrained(PHONEME_MODEL).eval()
g2p = G2p()
vocab = processor.tokenizer.get_vocab()


def arpabet_to_model_id(phone):
    clean = phone.rstrip("012").lower()
    clean = PHONE_FOLD.get(clean, clean)
    return vocab.get(clean)


def pad_audio(wav, sr=16000, pad_seconds=0.4):
    pad_samples = int(sr * pad_seconds)
    pad = torch.zeros(wav.shape[0], pad_samples)
    return torch.cat([pad, wav, pad], dim=1)


raw_phones = g2p(TEXT.lower())
target_ids = []
for ph in raw_phones:
    if ph == " " or not ph[0].isalpha():
        continue
    tid = arpabet_to_model_id(ph)
    if tid is not None:
        target_ids.append(tid)

data, sr = sf.read(WAV_PATH, dtype="float32", always_2d=True)
wav = torch.from_numpy(data.mean(axis=1)).unsqueeze(0)
wav = pad_audio(wav, sr=16000, pad_seconds=0.4)
inputs = processor(wav.squeeze().numpy(), sampling_rate=16000, return_tensors="pt")


def forced_align_confs(log_probs):
    targets = torch.tensor([target_ids], dtype=torch.int32)
    aligned_tokens, scores = torchaudio.functional.forced_align(log_probs, targets, blank=0)
    token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0], blank=0)
    confs = []
    for j, span in enumerate(token_spans):
        if j >= len(target_ids):
            break
        confs.append(round(100 * math.exp(span.score)))
    return confs


with torch.no_grad():
    torch_logits = model(inputs.input_values).logits
torch_log_probs = torch.log_softmax(torch_logits, dim=-1)
torch_confs = forced_align_confs(torch_log_probs)

sess = ort.InferenceSession("onnx_model/model_int8.onnx", providers=["CPUExecutionProvider"])
onnx_logits = sess.run(None, {"input_values": inputs.input_values.numpy()})[0]
onnx_log_probs = torch.log_softmax(torch.from_numpy(onnx_logits), dim=-1)
onnx_confs = forced_align_confs(onnx_log_probs)

print(f"{'phone':<6}{'torch':>8}{'onnx-int8':>12}")
phones_str = [p for p in raw_phones if p != " " and p[0].isalpha()]
for ph, tc, oc in zip(phones_str, torch_confs, onnx_confs):
    print(f"{ph.rstrip('012').lower():<6}{tc:>8}{oc:>12}")

diffs = [abs(a - b) for a, b in zip(torch_confs, onnx_confs)]
print(f"\nOrtalama mutlak fark: {sum(diffs)/len(diffs):.1f} puan (0-100 skala)")
