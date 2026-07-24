package com.telaffuz.pronunciation

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/** assets/cmudict.txt dosyasını ("word PH1 PH2 ..." satırları) belleğe yükleyip önbelleğe alır. */
object CmuDict {
    @Volatile
    private var dict: Map<String, List<String>>? = null

    @Synchronized
    fun load(context: Context): Map<String, List<String>> {
        dict?.let { return it }
        val map = HashMap<String, List<String>>()
        context.assets.open("cmudict.txt").use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.trim().split(" ")
                if (parts.size >= 2) {
                    map[parts[0]] = parts.subList(1, parts.size)
                }
            }
        }
        dict = map
        return map
    }
}
