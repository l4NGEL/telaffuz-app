import math
import os
import tempfile

import soundfile as sf
import torch
import torchaudio
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from g2p_en import G2p
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

PHONEME_MODEL = "mostafaashahin/wav2vec2-base-timit-phoneme-arpa-39"
PHONE_FOLD = {"ao": "aa"}

device = "cuda" if torch.cuda.is_available() else "cpu"
processor = Wav2Vec2Processor.from_pretrained(PHONEME_MODEL)
model = Wav2Vec2ForCTC.from_pretrained(PHONEME_MODEL).to(device).eval()
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


def process_single(text, audio_path):
    ref_lower = text.lower()
    raw_phones = g2p(ref_lower)
    words, current_word, target_ids = [], [], []
    for ph in raw_phones:
        if ph == " ":
            if current_word:
                words.append(current_word)
            current_word = []
        elif ph[0].isalpha():
            tid = arpabet_to_model_id(ph)
            if tid is None:
                continue
            target_ids.append(tid)
            current_word.append((ph, len(target_ids) - 1))
    if current_word:
        words.append(current_word)

    data, sr = sf.read(audio_path, dtype="float32", always_2d=True)
    wav = torch.from_numpy(data.mean(axis=1)).unsqueeze(0)
    if sr != 16000:
        wav = torchaudio.functional.resample(wav, sr, 16000)
    wav = pad_audio(wav, sr=16000, pad_seconds=0.4)

    inputs = processor(wav.squeeze().numpy(), sampling_rate=16000, return_tensors="pt")
    with torch.no_grad():
        logits = model(inputs.input_values.to(device)).logits
    log_probs = torch.log_softmax(logits, dim=-1).cpu()

    targets = torch.tensor([target_ids], dtype=torch.int32)
    aligned_tokens, scores = torchaudio.functional.forced_align(log_probs, targets, blank=0)
    token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0], blank=0)

    phone_confs = []
    for j, span in enumerate(token_spans):
        if j >= len(target_ids):
            break
        phone_confs.append(round(100 * math.exp(span.score)))

    word_list = []
    for word_phones in words:
        idxs = [idx for _, idx in word_phones]
        phones_out = [
            {"phone": word_phones[k][0].rstrip("012").lower(), "conf": phone_confs[idxs[k]]}
            for k in range(len(idxs))
            if idxs[k] < len(phone_confs)
        ]
        word_list.append({
            "word": "".join(p.rstrip("012") for p, _ in word_phones),
            "phones": phones_out,
        })

    all_confs = [p["conf"] for w in word_list for p in w["phones"]]
    overall10 = round((sum(all_confs) / len(all_confs)) / 10) if all_confs else 0

    return {"text": text, "human_total": overall10, "words": word_list}


app = FastAPI(title="Telaffuz Skorlama API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok", "device": device}


@app.post("/score")
async def score(text: str = Form(...), audio: UploadFile = File(...)):
    suffix = os.path.splitext(audio.filename or "audio.wav")[1] or ".wav"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(await audio.read())
        tmp_path = tmp.name
    try:
        result = process_single(text, tmp_path)
    finally:
        os.remove(tmp_path)
    return result
