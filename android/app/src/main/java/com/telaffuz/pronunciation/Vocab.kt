package com.telaffuz.pronunciation

/** wav2vec2-base-timit-phoneme-arpa-39 modelinin fonem sözlüğü (id 0-39). */
object Vocab {
    val phoneToId: Map<String, Int> = mapOf(
        "k" to 2, "jh" to 3, "d" to 4, "g" to 5, "dh" to 6, "iy" to 7, "z" to 8, "l" to 9,
        "ih" to 10, "b" to 11, "ay" to 12, "r" to 13, "oy" to 14, "t" to 15, "zh" to 16,
        "m" to 17, "v" to 18, "p" to 19, "s" to 20, "sh" to 21, "ae" to 22, "ah" to 23,
        "aw" to 24, "w" to 25, "hh" to 26, "ow" to 27, "th" to 28, "er" to 29, "n" to 30,
        "y" to 31, "aa" to 32, "ch" to 33, "eh" to 34, "ey" to 35, "uh" to 36, "ng" to 37,
        "f" to 38, "uw" to 39
    )

    val idToPhone: Map<Int, String> = phoneToId.entries.associate { (k, v) -> v to k }

    private val phoneFold = mapOf("ao" to "aa")

    const val BLANK_ID = 0
    const val VOCAB_SIZE = 40

    /** "AE1", "CH", "AH0" gibi ham ARPABET kodunu model id'sine çevirir. */
    fun arpabetToId(rawPhone: String): Int? {
        var clean = rawPhone.trimEnd('0', '1', '2').lowercase()
        clean = phoneFold[clean] ?: clean
        return phoneToId[clean]
    }

    /**
     * Fonetik olarak birbirine karışabilecek fonem grupları (yer/yöntem/ses tonu
     * benzerliği). "Sen ne söyledin" tespiti bu grupla sınırlı tutulur — tüm 40
     * fonem arasında serbestçe arama yapmak anlamsız/rastgele sonuçlar üretiyordu
     * (validate_confusion.py ile doğrulandı); bu şekilde hem gerçek karışıklıkları
     * yakalıyor hem de alakasız halüsinasyonları engelliyor.
     */
    private val phoneGroups: List<Set<String>> = listOf(
        setOf("iy", "ih", "ey", "eh", "ae"),
        setOf("ah", "er", "aa"),
        setOf("uw", "uh", "ow"),
        setOf("ay", "aw", "oy"),
        setOf("b", "d", "g"),
        setOf("p", "t", "k"),
        setOf("v", "dh", "z", "zh"),
        setOf("f", "th", "s", "sh"),
        setOf("ch", "jh"),
        setOf("m", "n", "ng"),
        setOf("l", "r"),
        setOf("w", "y", "hh"),
    )

    fun confusionSetFor(phone: String): Set<String> =
        phoneGroups.firstOrNull { phone in it } ?: setOf(phone)

    /** Patlamalı ünsüzler (stop consonants): bir sonraki kelime ünsüzle başlıyorsa
     * doğal İngilizce'de bunların patlaması (release) genelde duyulmaz/yutulur
     * (ör. "like this" -> k'nin sonu belirsizleşir). Bu, telaffuz hatası değil,
     * doğal bağlaşık konuşmanın bir özelliği. */
    val stopConsonants: Set<String> = setOf("p", "t", "k", "b", "d", "g")

    private val vowels: Set<String> = setOf(
        "aa", "ae", "ah", "aw", "ay", "eh", "er", "ey", "ih", "iy", "ow", "oy", "uh", "uw"
    )

    fun isVowel(phone: String): Boolean = phone in vowels
}
