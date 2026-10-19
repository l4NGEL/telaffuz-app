package com.telaffuz.pronunciation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telaffuz.pronunciation.ui.PronunciationUiState.Phase
import com.telaffuz.pronunciation.ui.theme.BrandPurple
import com.telaffuz.pronunciation.ui.theme.BrandPurpleDark
import com.telaffuz.pronunciation.ui.theme.BrandPurpleLight
import com.telaffuz.pronunciation.ui.theme.BrandRed
import com.telaffuz.pronunciation.ui.theme.SurfaceCard

/** ViewModel'i ekrana bağlayan ince katman; asıl ekran durumsuz (stateless). */
@Composable
fun TelaffuzRoute(
    viewModel: PronunciationViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val level by viewModel.audioLevel.collectAsStateWithLifecycle()

    TelaffuzScreen(
        state = state,
        audioLevel = level,
        onTextChange = viewModel::onTextChange,
        onShuffle = viewModel::onShuffle,
        onRecordToggle = {
            if (hasPermission) viewModel.onRecordToggle() else onRequestPermission()
        },
    )
}

@Composable
fun TelaffuzScreen(
    state: PronunciationUiState,
    audioLevel: Float,
    onTextChange: (String) -> Unit,
    onShuffle: () -> Unit,
    onRecordToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandPurpleLight),
                contentAlignment = Alignment.Center
            ) {
                Text("🗣️", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Telaffuz Testi",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandPurpleDark
                )
                Text(
                    "Cihaz üzerinde, internetsiz",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = state.targetText,
                        onValueChange = onTextChange,
                        label = { Text("Okunacak metin") },
                        enabled = !state.isRecording && !state.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            focusedLabelColor = BrandPurple
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onShuffle,
                        enabled = !state.isRecording && !state.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BrandPurpleLight),
                        modifier = Modifier.size(56.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("🎲", fontSize = 20.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                val (statusBg, statusFg, statusIcon) = when (state.phase) {
                    Phase.Recording -> Triple(Color(0xFFFDEAEA), BrandRed, "🔴")
                    Phase.Finishing, Phase.Processing -> Triple(Color(0xFFFFF7E0), Color(0xFFB45309), "⏳")
                    Phase.Error -> Triple(Color(0xFFFDEAEA), BrandRed, "⚠️")
                    Phase.Done -> Triple(Color(0xFFE6F7EC), Color(0xFF15803D), "✅")
                    Phase.Idle -> Triple(BrandPurpleLight, BrandPurpleDark, "💤")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(statusBg)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusIcon, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(state.statusText, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = statusFg)
                    }
                    Spacer(Modifier.width(10.dp))
                    VoiceLevelIndicator(level = audioLevel, isActive = state.isRecording)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onRecordToggle,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRecording) BrandRed else BrandPurple
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        if (state.isRecording) "⏹  Kaydı Durdur" else "🎙️  Kaydı Başlat",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDEAEA))
            ) {
                Text(
                    state.errorMessage ?: "",
                    color = BrandRed,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }

        AnimatedVisibility(visible = state.result != null) {
            Column {
                Spacer(Modifier.height(16.dp))
                state.result?.let { PronunciationCard(it) }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VoiceLevelIndicator(level: Float, isActive: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 5
        for (i in 0 until barCount) {
            val variance = 0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin(i * 1.7f + level * 3f))
            val target = if (isActive) (level * variance).coerceIn(0.12f, 1f) else 0.12f
            val animatedFraction by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 150),
                label = "voiceBar$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((6 + animatedFraction * 18).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) BrandPurple else Color(0xFFD1D5DB))
            )
        }
    }
}
