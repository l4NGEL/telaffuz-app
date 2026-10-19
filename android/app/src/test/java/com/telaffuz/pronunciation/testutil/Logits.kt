package com.telaffuz.pronunciation.testutil

import com.telaffuz.pronunciation.domain.Vocab

/**
 * Her kare için tek bir fonemin baskın olduğu sahte logit matrisi üretir.
 * Baskın logit 10, diğerleri 0 -> softmax sonrası baskın fonemin olasılığı ≈ %99.8.
 * "eh+ae" yazımı eh'yi baskın yapar, ae'ye de küçük bir ikincil logit (3) verir: gerçek
 * modellerdeki gibi hedefi diğer yanlış adaylardan biraz daha olası kılarak Viterbi'de
 * eşit skorlu alternatif hizalamaları ortadan kaldırır.
 */
fun peakedLogits(vararg phones: String): Array<FloatArray> =
    Array(phones.size) { t ->
        val parts = phones[t].split("+")
        FloatArray(Vocab.VOCAB_SIZE).also { row ->
            row[idOrBlank(parts[0])] = 10f
            parts.drop(1).forEach { row[idOrBlank(it)] = 3f }
        }
    }

private fun idOrBlank(phone: String): Int =
    if (phone == "_") Vocab.BLANK_ID else requireNotNull(Vocab.phoneToId[phone]) { "Bilinmeyen fonem: $phone" }

/** peakedLogits'in log-softmax'lanmış hali (ForcedAligner doğrudan bunu bekliyor). */
fun peakedLogProbs(vararg phones: String): Array<FloatArray> =
    peakedLogits(*phones).map { row ->
        val max = row.max()
        val logSum = max + kotlin.math.ln(row.sumOf { kotlin.math.exp((it - max).toDouble()) }).toFloat()
        FloatArray(row.size) { row[it] - logSum }
    }.toTypedArray()

fun id(phone: String): Int = requireNotNull(Vocab.phoneToId[phone])
