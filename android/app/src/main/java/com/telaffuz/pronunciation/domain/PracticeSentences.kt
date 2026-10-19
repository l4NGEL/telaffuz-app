package com.telaffuz.pronunciation.domain

import kotlin.random.Random

val PRACTICE_SENTENCES = listOf(
    "THE CRIME RATE IS HIGH",
    "I LIKE THIS BOOK",
    "LISA LOVES AUSTRALIA",
    "THE BALL IS IN THEIR COURT NOW",
    "WHAT A PERFECT ENDING TO THE DAY",
    "THIS LIFE IS THE BEST LIFE",
    "SHE SELLS SEASHELLS BY THE SEASHORE",
    "I NEED TO GO TO THE BANK",
    "IT WAS DIFFICULT TO GET THERE",
    "NOW THERE IS A SPECIAL OFFER",
    "WE LOOK FORWARD TO SEEING YOU",
    "THE WEATHER IS NICE TODAY",
    "CAN YOU HELP ME WITH THIS",
    "I WOULD LIKE A CUP OF COFFEE",
    "THIS IS ANOTHER WAY TO GO ABOUT IT",
    "SHE IS READING A GOOD BOOK",
    "THEY ARE PLAYING IN THE PARK",
    "HE WORKS VERY HARD EVERY DAY",
    "WHAT TIME IS THE MEETING TOMORROW",
    "FIRST PLACE IN THE DIVISION",
    "SO MUCH FOR A UNITED FRONT",
    "THOSE ARE NOT PART OF THE RULES",
    "WHAT IS THE BEST MONTH OF THE YEAR",
    "BUT THE STREETS WERE RELATIVELY CALM",
    "AFTER ALL THEY ONLY HAVE THEIR OWN PROPERTY",
    "I CAN UNDERSTAND HIS DESIRE TO GET ANOTHER OPINION",
    "CROWN",
    "APPLE",
    "COMPUTER",
    "BEAUTIFUL MORNING",
)

/** Sıradaki pratik cümlesini verir; bir öncekini (exclude) art arda tekrar etmez. */
fun interface SentenceProvider {
    fun next(exclude: String?): String
}

class RandomSentenceProvider(
    private val sentences: List<String> = PRACTICE_SENTENCES,
    private val random: Random = Random.Default,
) : SentenceProvider {

    init {
        require(sentences.isNotEmpty()) { "Cümle listesi boş olamaz" }
    }

    override fun next(exclude: String?): String {
        if (sentences.size <= 1) return sentences.first()
        var pick = sentences.random(random)
        while (pick == exclude) {
            pick = sentences.random(random)
        }
        return pick
    }
}
