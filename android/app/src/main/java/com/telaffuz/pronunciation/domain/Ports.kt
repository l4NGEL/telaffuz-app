package com.telaffuz.pronunciation.domain

import com.telaffuz.pronunciation.domain.model.ScoreResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain katmanının dış dünyaya açtığı arayüzler. Domain hiçbir Android sınıfına
 * (Context, AudioRecord, ONNX Runtime) bağımlı değil; gerçek uygulamaları `data`
 * paketinde, testlerdeki sahteleri `src/test` altında.
 */

/** Kelime -> ham ARPABET fonem dizisi ("AE1", "CH", ...). Sözlükte yoksa null. */
fun interface PhonemeDictionary {
    fun phonesFor(word: String): List<String>?
}

/** 16 kHz mono ses örneklerini alır, kare başına ham logit matrisini ([T][V]) döner. */
fun interface AcousticModel {
    fun logits(samples: FloatArray): Array<FloatArray>
}

/** Mikrofon kaydı. `stop()` kaydı bitirip örnekleri [-1, 1] aralığında döner. */
interface AudioRecorder {
    /** 0f (sessizlik) ile 1f (yüksek ses) arası anlık seviye — konuşurken animasyon için. */
    val level: StateFlow<Float>
    fun start()
    fun stop(): FloatArray
}

/** ViewModel'in gördüğü tek skorlama giriş noktası. */
fun interface PronunciationScorer {
    fun score(text: String, samples: FloatArray): ScoreResult
}
