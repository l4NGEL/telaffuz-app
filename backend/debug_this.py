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
id_to_phone = {v: k for k, v in vocab.items()}


def arpabet_to_model_id(phone):
    clean = phone.rstrip("012").lower()
    clean = PHONE_FOLD.get(clean, clean)
    return vocab.get(clean)


def pad_audio(wav, sr=16000, pad_seconds=0.4):
    pad_samples = int(sr * pad_seconds)
    pad = torch.zeros(wav.shape[0], pad_samples)
    return torch.cat([pad, wav, pad], dim=1)


def collapse_new(preds, blank=0, unk=1, min_run=2, min_conf_if_short=0.55):
    out = []
    i = 0
    while i < len(preds):
        cur = preds[i][0]
        j = i
        sum_p = 0.0
        while j < len(preds) and preds[j][0] == cur:
            sum_p += preds[j][1]
            j += 1
        run_len = j - i
        avg_p = sum_p / run_len
        if cur not in (blank, unk) and (run_len >= min_run or avg_p >= min_conf_if_short):
            out.append(cur)
        i = j
    return out


wav_path = sys.argv[1] if len(sys.argv) > 1 else "phone_this_test.wav"

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
print(f"ses uzunlugu: {len(data)/sr:.2f} sn, sr={sr}")
wav = torch.from_numpy(data.mean(axis=1)).unsqueeze(0)
wav = pad_audio(wav, sr=16000, pad_seconds=0.4)
inputs = processor(wav.squeeze().numpy(), sampling_rate=16000, return_tensors="pt")

with torch.no_grad():
    logits = model(inputs.input_values).logits
log_probs = torch.log_softmax(logits, dim=-1)

targets_t = torch.tensor([target_ids], dtype=torch.int32)
aligned_tokens, scores = torchaudio.functional.forced_align(log_probs, targets_t, blank=0)
token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0], blank=0)

phone_confs = [round(100 * math.exp(sp.score)) for sp in token_spans[: len(target_ids)]]

pred_ids = log_probs[0].argmax(dim=-1).tolist()
pred_probs = [math.exp(log_probs[0, t, pred_ids[t]].item()) for t in range(len(pred_ids))]
preds = list(zip(pred_ids, pred_probs))

print(f"\n{'word':<8}{'hedef':<15}{'confs':<20}{'sen(greedy)':<15}")
for word_phones in words:
    idxs = [i for _, i in word_phones]
    valid_idxs = [i for i in idxs if i < len(token_spans)]
    if not valid_idxs:
        continue
    w_start = token_spans[valid_idxs[0]].start
    w_end = token_spans[valid_idxs[-1]].end
    confs = [phone_confs[i] for i in idxs if i < len(phone_confs)]
    avg_conf = round(sum(confs) / len(confs)) if confs else 0
    heard_ids = collapse_new(preds[w_start:w_end], blank=0, unk=1, min_run=2, min_conf_if_short=0.55)
    label = "".join(p.rstrip("012").lower() for p, _ in word_phones)
    target_str = "-".join(p.rstrip("012").lower() for p, _ in word_phones)
    heard_str = "-".join(id_to_phone.get(i, "?") for i in heard_ids)
    print(f"{label:<8}{target_str:<15}{str(confs)+' avg='+str(avg_conf):<20}{heard_str:<15}")
