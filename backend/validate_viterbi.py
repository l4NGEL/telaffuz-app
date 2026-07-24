"""Kotlin ForcedAligner.kt ile birebir aynı mantığı Python'da uygulayıp
torchaudio.functional.forced_align referansıyla karşılaştırır."""
import math

import numpy as np
import torch
import torchaudio
from g2p_en import G2p
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor
import soundfile as sf

PHONEME_MODEL = "mostafaashahin/wav2vec2-base-timit-phoneme-arpa-39"
PHONE_FOLD = {"ao": "aa"}
TEXT = "ACTUALLY I'M LOOKING FORWARD TO THAT MYSELF"
NEG_INF = float("-inf")


def kotlin_style_align(log_probs, targets, blank=0):
    """ForcedAligner.kt'deki algoritmanın Python'a birebir portu."""
    t = len(log_probs)
    n = len(targets)
    if t == 0 or n == 0:
        return []

    l = 2 * n + 1
    extended = [0] * l
    for i in range(n):
        extended[2 * i] = blank
        extended[2 * i + 1] = targets[i]
    extended[l - 1] = blank

    alpha = [[NEG_INF] * l for _ in range(t)]
    backptr = [[0] * l for _ in range(t)]

    alpha[0][0] = log_probs[0][blank]
    if l > 1:
        alpha[0][1] = log_probs[0][extended[1]]

    for frame in range(1, t):
        prev = alpha[frame - 1]
        cur = alpha[frame]
        bp = backptr[frame]
        for s in range(l):
            best = prev[s]
            best_from = 0
            if s - 1 >= 0 and prev[s - 1] > best:
                best = prev[s - 1]
                best_from = 1
            if s - 2 >= 0 and extended[s] != blank and extended[s] != extended[s - 2]:
                if prev[s - 2] > best:
                    best = prev[s - 2]
                    best_from = 2
            bp[s] = best_from
            cur[s] = NEG_INF if best == NEG_INF else best + log_probs[frame][extended[s]]

    best_s = l - 1
    if l > 1 and alpha[t - 1][l - 2] > alpha[t - 1][l - 1]:
        best_s = l - 2

    path = [0] * t
    s = best_s
    for frame in range(t - 1, -1, -1):
        path[frame] = s
        if frame == 0:
            break
        s -= backptr[frame][s]

    target_idx_per_frame = [(pos // 2 if pos % 2 == 1 else -1) for pos in path]

    spans = []
    i = 0
    while i < t:
        idx = target_idx_per_frame[i]
        if idx == -1:
            i += 1
            continue
        j = i
        sum_lp = 0.0
        count = 0
        while j < t and target_idx_per_frame[j] == idx:
            sum_lp += log_probs[j][targets[idx]]
            count += 1
            j += 1
        avg_lp = sum_lp / count
        conf = min(100, max(0, round(100 * math.exp(avg_lp))))
        spans.append((targets[idx], i, j, conf))
        i = j
    return spans


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

data, sr = sf.read("test_sample.wav", dtype="float32", always_2d=True)
wav = torch.from_numpy(data.mean(axis=1)).unsqueeze(0)
wav = pad_audio(wav, sr=16000, pad_seconds=0.4)
inputs = processor(wav.squeeze().numpy(), sampling_rate=16000, return_tensors="pt")

with torch.no_grad():
    logits = model(inputs.input_values).logits
log_probs_t = torch.log_softmax(logits, dim=-1)

# --- Referans: torchaudio ---
targets_t = torch.tensor([target_ids], dtype=torch.int32)
aligned_tokens, scores = torchaudio.functional.forced_align(log_probs_t, targets_t, blank=0)
ref_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0], blank=0)
ref_confs = [round(100 * math.exp(sp.score)) for sp in ref_spans[: len(target_ids)]]
ref_starts = [sp.start for sp in ref_spans[: len(target_ids)]]
ref_ends = [sp.end for sp in ref_spans[: len(target_ids)]]

# --- Kotlin-portu Python simülasyonu ---
log_probs_np = log_probs_t[0].numpy().tolist()
my_spans = kotlin_style_align(log_probs_np, target_ids, blank=0)
my_confs = [sp[3] for sp in my_spans]
my_starts = [sp[1] for sp in my_spans]
my_ends = [sp[2] for sp in my_spans]

print(f"referans span sayısı: {len(ref_confs)}, benim span sayısı: {len(my_confs)}")
print(f"{'idx':<5}{'ref_conf':>10}{'my_conf':>10}{'ref_start':>11}{'my_start':>10}{'ref_end':>8}{'my_end':>8}")
for i in range(min(len(ref_confs), len(my_confs))):
    print(
        f"{i:<5}{ref_confs[i]:>10}{my_confs[i]:>10}"
        f"{ref_starts[i]:>11}{my_starts[i]:>10}{ref_ends[i]:>8}{my_ends[i]:>8}"
    )

conf_diffs = [abs(a - b) for a, b in zip(ref_confs, my_confs)]
start_diffs = [abs(a - b) for a, b in zip(ref_starts, my_starts)]
print(f"\nOrtalama conf farkı: {sum(conf_diffs)/len(conf_diffs):.3f}")
print(f"Ortalama start-frame farkı: {sum(start_diffs)/len(start_diffs):.3f}")
