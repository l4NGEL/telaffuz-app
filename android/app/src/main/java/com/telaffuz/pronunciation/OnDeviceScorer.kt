package com.telaffuz.pronunciation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.ln

class UnknownWordException(val words: List<String>) :
    Exception("Sözlükte olmayan kelime(ler): ${words.joinToString(", ")}. Başka bir kelime/cümle deneyin.")

object OnDeviceScorer {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null

    @Synchronized
    private fun getSession(context: Context): OrtSession {
        session?.let { return it }
        val ortEnv = OrtEnvironment.getEnvironment()
        env = ortEnv
        val modelFile = ensureModelFile(context)
        val sess = ortEnv.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        session = sess
        return sess
    }

    /**
     * 120MB'lık modeli tek seferde ByteArray'e okumak Android'in Java heap limitini
     * aşıp OutOfMemoryError atıyor. Bunun yerine dosyayı (küçük bir tampon ile,
     * belleğe tek parça yüklemeden) internal storage'a bir kez kopyalayıp ONNX
     * Runtime'ın dosya yolundan (memory-map ile) yüklemesini sağlıyoruz.
     */
    private fun ensureModelFile(context: Context): File {
        val outFile = File(context.filesDir, "phoneme_model.onnx")
        val assetSize = context.assets.open("phoneme_model.onnx").use { it.available().toLong() }
        if (outFile.exists() && outFile.length() == assetSize) {
            return outFile
        }
        context.assets.open("phoneme_model.onnx").use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1 shl 20)
            }
        }
        return outFile
    }

    private data class WordPhones(val word: String, val phoneIndices: List<Int>)

    fun score(context: Context, text: String, wavFile: File): ScoreResult {
        val cmu = CmuDict.load(context)

        val words = mutableListOf<WordPhones>()
        val targetIds = mutableListOf<Int>()
        val unknownWords = mutableListOf<String>()

        for (rawWord in text.trim().split(Regex("\\s+"))) {
            if (rawWord.isEmpty()) continue
            val cleaned = rawWord.lowercase().filter { it.isLetter() || it == '\'' }
            if (cleaned.isEmpty()) continue
            val phones = cmu[cleaned]
            if (phones == null) {
                unknownWords.add(rawWord)
                continue
            }
            val idxs = mutableListOf<Int>()
            for (ph in phones) {
                val tid = Vocab.arpabetToId(ph) ?: continue
                targetIds.add(tid)
                idxs.add(targetIds.size - 1)
            }
            words.add(WordPhones(rawWord, idxs))
        }

        if (unknownWords.isNotEmpty()) {
            throw UnknownWordException(unknownWords)
        }
        if (targetIds.isEmpty()) {
            throw IllegalArgumentException("Skorlanacak fonem bulunamadı")
        }

        val rawSamples = WavUtils.readPcm16Wav(wavFile)
        val paddedSamples = WavUtils.padAudio(rawSamples, 16000, 0.4f)

        val sess = getSession(context)
        val ortEnv = env!!

        val inputShape = longArrayOf(1, paddedSamples.size.toLong())
        val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(paddedSamples), inputShape)

        val logits: Array<FloatArray>
        inputTensor.use { tensor ->
            sess.run(mapOf("input_values" to tensor)).use { output ->
                @Suppress("UNCHECKED_CAST")
                val logitsTensor = output.get("logits").get() as OnnxTensor
                val raw = logitsTensor.value as Array<Array<FloatArray>> // [1][T][V]
                logits = raw[0]
            }
        }

        val logProbs = Array(logits.size) { t -> logSoftmax(logits[t]) }
        val targetArray = targetIds.toIntArray()
        val spans = ForcedAligner.align(logProbs, targetArray, blank = Vocab.BLANK_ID)

        val confsByIdx = IntArray(targetArray.size)
        val spansByIdx = arrayOfNulls<PhoneSpan>(targetArray.size)
        for ((i, span) in spans.withIndex()) {
            if (i < confsByIdx.size) {
                confsByIdx[i] = span.confidence
                spansByIdx[i] = span
            }
        }

        // "Sen böyle okudun": her hedef fonem için, TÜM 40 fonem arasında değil,
        // sadece o fonemle akustik/fonetik olarak karışabilecek küçük bir aday
        // grubu (Vocab.confusionSetFor) içinde en olası olanı buluyoruz. Serbest
        // (tüm-vokabüler) çözümleme "crown" gibi kelimelerde anlamsız sonuçlar
        // üretiyordu (validate_confusion.py ile doğrulandı); bu kısıtlı yaklaşım
        // hem gerçek karışıklıkları yakalıyor hem halüsinasyonu engelliyor.
        //
        // Bazı fonemler (patlamalı ünsüzler ama aynı zamanda diftonglar/kısa ünlüler de
        // olabilir — gerçek kayıtlarda hem "like"nin k'si hem "crown"un aw'si aynı imzayı
        // gösterdi) akıcı konuşmada tek bir frame'e sıkışabiliyor; o tek frame'de TÜM
        // adaylar (hedef dahil) için olasılık neredeyse sıfırsa, bu bir telaffuz hatası
        // değil, o anda ölçülebilir bir akustik sinyal olmamasıdır — fonem türünden
        // bağımsız olarak puana dahil etmiyoruz.
        val wordResults = words.map { wp ->
            val phones = wp.phoneIndices.map { idx ->
                val targetPhoneId = targetArray[idx]
                val targetPhone = Vocab.idToPhone[targetPhoneId] ?: "?"
                val conf = if (idx < confsByIdx.size) confsByIdx[idx] else 0
                val span = spansByIdx.getOrNull(idx)

                // Güvenli bir tespit yoksa hedefi tekrar edip "eşleşti" izlenimi vermiyoruz —
                // "sen" boş kalır (bkz. PronunciationCard: heard.isEmpty() -> "belirsiz" gösterimi).
                var heardPhone = ""
                var heardConf = 0
                var lowSignal = false
                if (span != null && span.endFrame > span.startFrame) {
                    var bestPhone = targetPhone
                    var bestAvg = -1.0
                    for (candidate in Vocab.confusionSetFor(targetPhone)) {
                        val candidateId = Vocab.phoneToId[candidate] ?: continue
                        var sum = 0.0
                        for (t in span.startFrame until span.endFrame) {
                            sum += exp(logProbs[t][candidateId].toDouble())
                        }
                        val avg = sum / (span.endFrame - span.startFrame)
                        if (avg > bestAvg) {
                            bestAvg = avg
                            bestPhone = candidate
                        }
                    }
                    // Üç kademeli değerlendirme:
                    // <%2: gerçek sinyal yok, gürültü — puana dahil etmiyoruz (lowSignal), "sen" boş.
                    // %2-%50: zayıf/belirsiz sinyal — "farklı" diye damgalamak için yeterince
                    //   güvenilir değil (tamamen sessiz kayıtlarda bile rastgele bir aday bu
                    //   aralığa girebiliyor, gerçek kayıtlarla doğrulandı) — "sen" boş kalır,
                    //   ama hedefin kendi güveni puana dahil edilmeye devam eder.
                    // >=%50: gerçekten güvenilir bir tespit, farklıysa kırmızı gösteriyoruz.
                    if (bestAvg >= 0.50) {
                        heardPhone = bestPhone
                        heardConf = (bestAvg * 100).toInt().coerceIn(0, 100)
                    } else if (bestAvg < 0.02) {
                        lowSignal = true
                    }
                }

                // Cümlenin en baştaki ve en sondaki sesi, modelin öncesinde/sonrasında
                // bağlam olmaması yüzünden sistematik olarak düşük güvenle ölçülüyor
                // (gerçek kayıtlarla defalarca doğrulandı: aynı cümle farklı denemelerde
                // bile ilk/son sesi hep düşük çıkıyor). Yüksek çıkarsa yine sayılır,
                // ama düşükse (telaffuz hatası değil, kenar etkisi olduğu için) puana
                // dahil etmiyoruz.
                val isUtteranceEdge = idx == 0 || idx == targetArray.size - 1
                if (isUtteranceEdge && conf < 40) {
                    lowSignal = true
                }

                PhoneResult(targetPhone, conf, heardPhone, heardConf, lowSignal)
            }
            WordResult(wp.word, phones)
        }

        val allConfs = wordResults.flatMap { it.phones }.filterNot { it.lowSignal }.map { it.conf }
        val overall10 = if (allConfs.isNotEmpty()) {
            Math.round(allConfs.average() / 10.0).toInt()
        } else 0

        return ScoreResult(text, overall10, wordResults)
    }

    private fun logSoftmax(logits: FloatArray): FloatArray {
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > maxVal) maxVal = v
        var sumExp = 0.0
        for (v in logits) sumExp += exp((v - maxVal).toDouble())
        val logSumExp = maxVal + ln(sumExp).toFloat()
        return FloatArray(logits.size) { i -> logits[i] - logSumExp }
    }
}
