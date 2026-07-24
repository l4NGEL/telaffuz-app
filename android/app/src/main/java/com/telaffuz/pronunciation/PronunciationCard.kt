package com.telaffuz.pronunciation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PronunciationCard(result: ScoreResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    result.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1F2937),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val badgeConf = result.humanTotal * 10
                val badgeEmoji = when {
                    badgeConf >= 80 -> "🌟"
                    badgeConf >= 50 -> "🙂"
                    else -> "💪"
                }
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .background(confBg(badgeConf), RoundedCornerShape(999.dp))
                        .border(1.dp, confColor(badgeConf).copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(badgeEmoji, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%$badgeConf",
                        color = confColor(badgeConf),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                result.words.forEach { word ->
                    WordColumn(word, modifier = Modifier.width(96.dp).fillMaxHeight())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordColumn(word: WordResult, modifier: Modifier = Modifier) {
    val units = simplifyPhonesWithConf(word.phones)
    val scoreablePhones = word.phones.filterNot { it.lowSignal }
    val avgConf = if (scoreablePhones.isNotEmpty()) {
        scoreablePhones.sumOf { it.conf } / scoreablePhones.size
    } else 0

    Column(
        modifier = modifier
            .background(Color(0xFFFAFAFC), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFEDEBF7), RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(confColor(avgConf), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        )

        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        Text(
            word.word.lowercase(),
            fontSize = 9.sp,
            color = Color(0xFF9CA3AF),
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(2.dp))

        val annotated = buildAnnotatedString {
            units.forEach { unit ->
                withStyle(SpanStyle(color = confColor(unit.conf))) {
                    append(ARPA_TO_TR[unit.phone] ?: unit.phone)
                }
            }
        }
        Text(annotated, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

        Spacer(Modifier.height(4.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            word.phones.forEach { p ->
                Box(
                    modifier = Modifier
                        .background(confBg(p.conf), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(p.phone, fontSize = 9.sp, color = confColor(p.conf), fontWeight = FontWeight.Medium)
                }
            }
        }

        Text("ort %$avgConf", fontSize = 9.sp, color = Color(0xFFB0AEC2))

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.HorizontalDivider(color = Color(0xFFEDEBF7), thickness = 1.dp)
        Spacer(Modifier.height(6.dp))

        Text("hedef", fontSize = 8.sp, color = Color(0xFFB0AEC2))
        Text(
            phonesToTrPlain(word.phones.map { it.phone }),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151)
        )

        Spacer(Modifier.height(4.dp))
        Text("sen", fontSize = 8.sp, color = Color(0xFFB0AEC2))
        if (word.phones.all { it.heard.isEmpty() }) {
            Text("belirsiz", fontSize = 12.sp, color = Color(0xFFB0AEC2))
        } else {
            val heardAnnotated = buildAnnotatedString {
                word.phones.forEach { p ->
                    if (p.heard.isEmpty()) {
                        withStyle(SpanStyle(color = Color(0xFFD1D5DB))) {
                            append("·")
                        }
                    } else {
                        val letter = ARPA_TO_TR[p.heard] ?: p.heard
                        val isDifferent = p.heard != p.phone
                        withStyle(
                            SpanStyle(
                                color = if (isDifferent) BrandRed else BrandPurple,
                                fontWeight = if (isDifferent) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        ) {
                            append(letter)
                        }
                    }
                }
            }
            Text(heardAnnotated, fontSize = 13.sp)
        }
        }
    }
}
