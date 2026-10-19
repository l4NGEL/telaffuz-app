package com.telaffuz.pronunciation.ui

import com.telaffuz.pronunciation.domain.model.ScoreResult

/** Ekranın tek doğruluk kaynağı. Compose sadece bunu çizer; mantık ViewModel'de. */
data class PronunciationUiState(
    val targetText: String,
    val phase: Phase = Phase.Idle,
    val result: ScoreResult? = null,
    val errorMessage: String? = null,
) {
    enum class Phase { Idle, Recording, Finishing, Processing, Done, Error }

    val isRecording: Boolean get() = phase == Phase.Recording
    val isBusy: Boolean get() = phase == Phase.Finishing || phase == Phase.Processing

    val statusText: String
        get() = when (phase) {
            Phase.Idle -> "Hazır — tamamen çevrimdışı çalışır"
            Phase.Recording -> "Kayıt yapılıyor..."
            Phase.Finishing -> "Bitiriyor..."
            Phase.Processing -> "Cihaz üzerinde işleniyor..."
            Phase.Done -> "Tamamlandı"
            Phase.Error -> "Hata"
        }
}
