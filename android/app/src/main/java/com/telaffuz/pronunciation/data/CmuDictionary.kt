package com.telaffuz.pronunciation.data

import android.content.Context
import com.telaffuz.pronunciation.domain.PhonemeDictionary
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/** assets/cmudict.txt dosyasını ("word PH1 PH2 ..." satırları) ilk kullanımda belleğe yükler. */
class CmuDictionary(private val open: () -> InputStream) : PhonemeDictionary {

    constructor(context: Context) : this({ context.assets.open("cmudict.txt") })

    private val dict: Map<String, List<String>> by lazy { open().use(::parse) }

    override fun phonesFor(word: String): List<String>? = dict[word]

    companion object {
        fun parse(input: InputStream): Map<String, List<String>> {
            val map = HashMap<String, List<String>>()
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.trim().split(" ")
                if (parts.size >= 2) {
                    map[parts[0]] = parts.subList(1, parts.size)
                }
            }
            return map
        }
    }
}
