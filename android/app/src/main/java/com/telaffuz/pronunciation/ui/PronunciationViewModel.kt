package com.telaffuz.pronunciation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.telaffuz.pronunciation.domain.AudioRecorder
import com.telaffuz.pronunciation.domain.PronunciationScorer
import com.telaffuz.pronunciation.domain.SentenceProvider
import com.telaffuz.pronunciation.ui.PronunciationUiState.Phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PronunciationViewModel(
    private val recorder: AudioRecorder,
    private val scorer: PronunciationScorer,
    private val sentences: SentenceProvider,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val stopTailMillis: Long = STOP_TAIL_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(PronunciationUiState(targetText = sentences.next(exclude = null)))
    val state: StateFlow<PronunciationUiState> = _state.asStateFlow()

    val audioLevel: StateFlow<Float> = recorder.level

    fun onTextChange(text: String) {
        _state.update { it.copy(targetText = text) }
    }

    fun onShuffle() {
        if (_state.value.isRecording || _state.value.isBusy) return
        _state.update {
            it.copy(
                targetText = sentences.next(exclude = it.targetText),
                phase = Phase.Idle,
                result = null,
                errorMessage = null,
            )
        }
    }

    /** Kayıt düğmesi: boştayken başlatır, kayıttayken bitirip skorlar, işlem sürerken yok sayılır. */
    fun onRecordToggle() {
        val current = _state.value
        when {
            current.isBusy -> return
            current.isRecording -> finishAndScore()
            else -> startRecording()
        }
    }

    private fun startRecording() {
        try {
            recorder.start()
        } catch (e: Exception) {
            showError(e)
            return
        }
        _state.update { it.copy(phase = Phase.Recording, result = null, errorMessage = null) }
    }

    private fun finishAndScore() {
        _state.update { it.copy(phase = Phase.Finishing) }
        viewModelScope.launch {
            // Son kelimenin sonu kırpılmasın diye "Durdur"a basıldığı anda değil,
            // kısa bir tampon süre sonra gerçekten durduruyoruz (kayıt arka planda devam ediyor).
            delay(stopTailMillis)
            try {
                val text = _state.value.targetText
                val scored = withContext(workDispatcher) {
                    val samples = recorder.stop()
                    _state.update { it.copy(phase = Phase.Processing) }
                    scorer.score(text, samples)
                }
                _state.update {
                    it.copy(
                        phase = Phase.Done,
                        result = scored,
                        targetText = sentences.next(exclude = text),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun showError(e: Exception) {
        _state.update {
            it.copy(phase = Phase.Error, errorMessage = e.message ?: "Bilinmeyen hata")
        }
    }

    companion object {
        const val STOP_TAIL_MILLIS = 600L

        fun factory(
            recorder: () -> AudioRecorder,
            scorer: () -> PronunciationScorer,
            sentences: () -> SentenceProvider,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PronunciationViewModel(recorder(), scorer(), sentences()) as T
        }
    }
}
