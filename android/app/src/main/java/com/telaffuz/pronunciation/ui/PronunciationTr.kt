package com.telaffuz.pronunciation.ui

import androidx.compose.ui.graphics.Color
import com.telaffuz.pronunciation.domain.model.PhoneResult

val ARPA_TO_TR: Map<String, String> = mapOf(
    "aa" to "a", "ae" to "e", "ah" to "a", "ao" to "o", "aw" to "au", "ay" to "ay",
    "eh" to "e", "er" to "ır", "ey" to "ey", "ih" to "i", "iy" to "i", "ow" to "o",
    "oy" to "oy", "uh" to "u", "uw" to "u",
    "b" to "b", "ch" to "ç", "d" to "d", "dh" to "d", "f" to "f", "g" to "g",
    "hh" to "h", "jh" to "c", "k" to "k", "l" to "l", "m" to "m", "n" to "n",
    "ng" to "ng", "p" to "p", "r" to "r", "s" to "s", "sh" to "ş", "t" to "t",
    "th" to "s", "v" to "v", "w" to "v", "y" to "y", "z" to "z", "zh" to "j",
)

val CLUSTER_RULES: List<Pair<List<String>, List<String>>> = listOf(
    listOf("ah", "w", "ah") to listOf("uw"),
    listOf("ah", "w") to listOf("uw"),
    listOf("w", "ah") to listOf("uw"),
    listOf("ah", "y", "ah") to listOf("iy"),
    listOf("ah", "y") to listOf("iy"),
    listOf("y", "ah") to listOf("iy"),
)

data class TrUnit(val phone: String, val conf: Int, val lowSignal: Boolean = false)

fun simplifyPhonesWithConf(phones: List<PhoneResult>): List<TrUnit> {
    val units = mutableListOf<TrUnit>()
    var i = 0
    while (i < phones.size) {
        var matched = false
        for ((pattern, repl) in CLUSTER_RULES) {
            val n = pattern.size
            if (i + n <= phones.size && phones.subList(i, i + n).map { it.phone } == pattern) {
                val avgConf = phones.subList(i, i + n).sumOf { it.conf } / n
                units.add(TrUnit(repl[0], avgConf))
                i += n
                matched = true
                break
            }
        }
        if (!matched) {
            units.add(TrUnit(phones[i].phone, phones[i].conf, phones[i].lowSignal))
            i += 1
        }
    }
    return units
}

fun simplifyPhonesPlain(phones: List<String>): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < phones.size) {
        var matched = false
        for ((pattern, repl) in CLUSTER_RULES) {
            val n = pattern.size
            if (i + n <= phones.size && phones.subList(i, i + n) == pattern) {
                out.add(repl[0])
                i += n
                matched = true
                break
            }
        }
        if (!matched) {
            out.add(phones[i])
            i += 1
        }
    }
    return out
}

fun phonesToTrPlain(phones: List<String>): String =
    simplifyPhonesPlain(phones).joinToString("") { ARPA_TO_TR[it] ?: it }

fun confColor(conf: Int): Color = when {
    conf >= 70 -> Color(0xFF15803D)
    conf >= 40 -> Color(0xFFB45309)
    else -> Color(0xFFB91C1C)
}

fun confBg(conf: Int): Color = when {
    conf >= 70 -> Color(0xFFE6F7EC)
    conf >= 40 -> Color(0xFFFFF7E0)
    else -> Color(0xFFFDEAEA)
}
