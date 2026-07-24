import math

import soundfile as sf
import torch
import torchaudio
from g2p_en import G2p
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

PHONEME_MODEL = "mostafaashahin/wav2vec2-base-timit-phoneme-arpa-39"
PHONE_FOLD = {"ao": "aa"}
TEXT = "I LIKE THIS BOOK"

PHONE_GROUPS = [
    {"iy", "ih", "ey", "eh", "ae"},
    {"ah", "er", "aa"},
    {"uw", "uh", "ow"},
    {"ay", "aw", "oy"},
    {"b", "d", "g"},
    {"p", "t", "k"},
    {"v", "dh", "z", "zh"},
    {"f", "th", "s", "sh"},
    {"ch", "jh"},
    {"m", "n", "ng"},
    {"l", "r"},
    {"w", "y", "hh"},
]


def confusion_set_for(phone):
    for g in PHONE_GROUPS:
        if phone in g:
            return g
    return {phone}


processor = Wav2Vec2Processor.from_pretrained(PHONEME_MODEL)
model = Wav2Vec2ForCTC.from_pretrained(PHONEME_MODEL).eval()
g2p = G2p()
vocab = processor.tokenizer.get_vocab()
id_to_phone = {v: k for k, v in vocab.items()}


def arpabet_to_model_id(phone):
    clean = phone.rstrip("012").lower()
    clean = PHONE_FOLD.get(clean, clean)
    return vocab.get(clean)


def pad_audio(wav, sr=16000, pad_seconds=0.4):
    pad_samples = int(sr * pad_seconds)
    pad = torch.zeros(wav.shape[0], pad_samples)
    return torch.cat([pad, wav, pad], dim=1)


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

import sys
wav_path = sys.argv[1] if len(sys.argv) > 1 else "phone_this_test.wav"
data, sr = sf.read(wav_path, dtype="float32", always_2d=True)
wav = torch.from_numpy(data.mean(axis=1)).unsqueeze(0)
wav = pad_audio(wav, sr=16000, pad_seconds=0.4)
inputs = processor(wav.squeeze().numpy(), sampling_rate=16000, return_tensors="pt")

with torch.no_grad():
    logits = model(inputs.input_values).logits
log_probs = torch.log_softmax(logits, dim=-1)[0]

targets_t = torch.tensor([target_ids], dtype=torch.int32)
aligned_tokens, scores = torchaudio.functional.forced_align(
    torch.log_softmax(logits, dim=-1), targets_t, blank=0
)
token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0], blank=0)


def detect_heard(span, target_phone, verbose=False):
    candidates = confusion_set_for(target_phone)
    best_phone, best_avg = target_phone, -1.0
    details = {}
    for cand in candidates:
        cand_id = vocab.get(cand)
        if cand_id is None:
            continue
        probs = [math.exp(log_probs[t, cand_id].item()) for t in range(span.start, span.end)]
        avg = sum(probs) / len(probs)
        details[cand] = avg
        if avg > best_avg:
            best_avg = avg
            best_phone = cand
    if verbose:
        print(f"    span=({span.start},{span.end}) adaylar: {details}")
    return best_phone, round(best_avg * 100)


print(f"{'word':<8}{'target':<8}{'conf':<6}{'confusion-set-heard':<20}")
for word_phones in words:
    for ph, idx in word_phones:
        if idx >= len(token_spans):
            continue
        target_phone = ph.rstrip("012").lower()
        span = token_spans[idx]
        heard, heard_conf = detect_heard(span, target_phone, verbose=True)
        target_conf = round(100 * math.exp(span.score))
        mark = "  <-- FARKLI" if heard != target_phone else ""
        print(f"{target_phone:<8}{target_phone:<8}%{target_conf:<5}{heard} (%{heard_conf}){mark}")
