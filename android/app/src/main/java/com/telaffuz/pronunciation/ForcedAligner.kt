package com.telaffuz.pronunciation

import kotlin.math.exp

data class PhoneSpan(val phoneId: Int, val startFrame: Int, val endFrame: Int, val confidence: Int)

/**
 * torchaudio.functional.forced_align + merge_tokens'ın Kotlin karşılığı:
 * verilen log-softmax çıktısını (logProbs) hedef fonem id dizisine (targets) zorla
 * hizalayan bir Viterbi (max-plus) dynamic-programming algoritması.
 */
object ForcedAligner {

    private const val NEG_INF = Float.NEGATIVE_INFINITY

    fun align(logProbs: Array<FloatArray>, targets: IntArray, blank: Int = 0): List<PhoneSpan> {
        val t = logProbs.size
        val n = targets.size
        if (t == 0 || n == 0) return emptyList()

        val l = 2 * n + 1
        val extended = IntArray(l)
        for (i in 0 until n) {
            extended[2 * i] = blank
            extended[2 * i + 1] = targets[i]
        }
        extended[l - 1] = blank

        val alpha = Array(t) { FloatArray(l) { NEG_INF } }
        val backptr = Array(t) { IntArray(l) }

        alpha[0][0] = logProbs[0][blank]
        if (l > 1) alpha[0][1] = logProbs[0][extended[1]]

        for (frame in 1 until t) {
            val prev = alpha[frame - 1]
            val cur = alpha[frame]
            val bp = backptr[frame]
            for (s in 0 until l) {
                var best = prev[s]
                var bestFrom = 0
                if (s - 1 >= 0 && prev[s - 1] > best) {
                    best = prev[s - 1]
                    bestFrom = 1
                }
                if (s - 2 >= 0 && extended[s] != blank && extended[s] != extended[s - 2]) {
                    if (prev[s - 2] > best) {
                        best = prev[s - 2]
                        bestFrom = 2
                    }
                }
                bp[s] = bestFrom
                cur[s] = if (best == NEG_INF) NEG_INF else best + logProbs[frame][extended[s]]
            }
        }

        var bestS = l - 1
        if (l > 1 && alpha[t - 1][l - 2] > alpha[t - 1][l - 1]) {
            bestS = l - 2
        }

        val path = IntArray(t)
        var s = bestS
        for (frame in t - 1 downTo 0) {
            path[frame] = s
            if (frame == 0) break
            s -= backptr[frame][s]
        }

        val targetIdxPerFrame = IntArray(t) { frame ->
            val pos = path[frame]
            if (pos % 2 == 1) pos / 2 else -1
        }

        val spans = mutableListOf<PhoneSpan>()
        var i = 0
        while (i < t) {
            val idx = targetIdxPerFrame[i]
            if (idx == -1) {
                i++
                continue
            }
            var j = i
            var sumLogProb = 0f
            var count = 0
            while (j < t && targetIdxPerFrame[j] == idx) {
                sumLogProb += logProbs[j][targets[idx]]
                count++
                j++
            }
            val avgLogProb = sumLogProb / count
            val confidence = (100 * exp(avgLogProb.toDouble())).toInt().coerceIn(0, 100)
            spans.add(PhoneSpan(targets[idx], i, j, confidence))
            i = j
        }
        return spans
    }
}
