package com.telaffuz.pronunciation

import android.app.Application
import com.telaffuz.pronunciation.data.AndroidWavRecorder
import com.telaffuz.pronunciation.data.CmuDictionary
import com.telaffuz.pronunciation.data.OnnxAcousticModel
import com.telaffuz.pronunciation.domain.PronunciationScorer
import com.telaffuz.pronunciation.domain.RandomSentenceProvider
import com.telaffuz.pronunciation.domain.ScorePronunciationUseCase
import com.telaffuz.pronunciation.domain.SentenceProvider

/**
 * Elle kurulmuş bağımlılık grafiği. Tek ekranlı bir uygulama için Hilt/Koin'in
 * getireceği ek derleme yükü yerine burada tek noktada bağlanıyor; ViewModel
 * yalnızca domain arayüzlerini görüyor.
 */
class AppContainer(app: Application) {
    // Sözlük ve ONNX oturumu pahalı; uygulama ömrü boyunca tek örnek.
    val scorer: PronunciationScorer = ScorePronunciationUseCase(
        dictionary = CmuDictionary(app),
        model = OnnxAcousticModel(app),
    )
    val sentences: SentenceProvider = RandomSentenceProvider()
    private val cacheDir = app.cacheDir

    fun newRecorder() = AndroidWavRecorder(cacheDir)
}

class TelaffuzApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
