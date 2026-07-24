package com.telaffuz.pronunciation

data class PhoneResult(
    val phone: String,
    val conf: Int,
    val heard: String = "",
    val heardConf: Int = 0,
    val lowSignal: Boolean = false
)
data class WordResult(val word: String, val phones: List<PhoneResult>)
data class ScoreResult(val text: String, val humanTotal: Int, val words: List<WordResult>)
