import math
import sys

import soundfile as sf
import torch
import torchaudio
from g2p_en import G2p
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

PHONEME_MODEL = "mostafaashahin/wav2vec2-base-timit-phoneme-arpa-39"
PHONE_FOLD = {"ao": "aa"}
TEXT = "I LIKE THIS BOOK"

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


wav_path = sys.argv[1] if len(sys.argv) > 1 else "phone_latest2.wav"

raw_phones = g2p(TEXT.lower())
words, current_word, target_ids = [], [], []
for ph in raw_phones:
    if ph == " ":
        if current_word:
            words.append(current_word)
        current_word = []
    elif ph[0].isalpha():
        tid = arpabet_to_model_id(ph)
        if tid is not None:
            target_ids.append(tid)
            current_word.append((ph, len(target_ids) - 1))
if current_word:
    words.append(current_word)

data, sr = sf.read(wav_path, dtype="float32", always_2d=True)
wav = torch.from_numpy(data.mean(axis=1)).unsqueeze(0)
wav = pad_audio(wav, sr=16000, pad_seconds=0.4)
inputs = processor(wav.squeeze().numpy(), sampling_rate=16000, return_tensors="pt")

with torch.no_grad():
    logits = model(inputs.input_values).logits
log_probs = torch.log_softmax(logits, dim=-1)

targets_t = torch.tensor([target_ids], dtype=torch.int32)
aligned_tokens, scores = torchaudio.functional.forced_align(log_probs, targets_t, blank=0)
token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0], blank=0)

print(f"{'word':<8}{'phone':<6}{'start':<7}{'end':<6}{'dur(frame)':<12}{'conf'}")
for word_phones in words:
    label = "".join(p.rstrip("012").lower() for p, _ in word_phones)
    for ph, idx in word_phones:
        if idx >= len(token_spans):
            continue
        span = token_spans[idx]
        dur = span.end - span.start
        conf = round(100 * math.exp(span.score))
        print(f"{label:<8}{ph.rstrip('012').lower():<6}{span.start:<7}{span.end:<6}{dur:<12}%{conf}")
