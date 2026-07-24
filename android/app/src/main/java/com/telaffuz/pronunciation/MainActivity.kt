package com.telaffuz.pronunciation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            TelaffuzTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundSoft) {
                    TelaffuzScreen(
                        hasPermission = hasPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TelaffuzScreen(hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var targetText by remember { mutableStateOf(randomPracticeSentence()) }
    var status by remember { mutableStateOf("Hazır — tamamen çevrimdışı çalışır") }
    var isRecording by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScoreResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val recorder = remember { WavRecorder() }

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
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text("Okunacak metin") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            focusedLabelColor = BrandPurple
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            targetText = randomPracticeSentence(exclude = targetText)
                            result = null
                            errorMsg = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandPurpleLight),
                        modifier = Modifier.size(56.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("🎲", fontSize = 20.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                val (statusBg, statusFg, statusIcon) = when {
                    isRecording -> Triple(Color(0xFFFDEAEA), BrandRed, "🔴")
                    isBusy -> Triple(Color(0xFFFFF7E0), Color(0xFFB45309), "⏳")
                    errorMsg != null -> Triple(Color(0xFFFDEAEA), BrandRed, "⚠️")
                    result != null -> Triple(Color(0xFFE6F7EC), Color(0xFF15803D), "✅")
                    else -> Triple(BrandPurpleLight, BrandPurpleDark, "💤")
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
                        Text(status, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = statusFg)
                    }
                    Spacer(Modifier.width(10.dp))
                    VoiceLevelIndicator(level = recorder.audioLevel, isActive = isRecording)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!hasPermission) {
                            onRequestPermission()
                            return@Button
                        }
                        if (!isRecording) {
                            val file = File(context.cacheDir, "kayit_${System.currentTimeMillis()}.wav")
                            recorder.start(file)
                            isRecording = true
                            status = "Kayıt yapılıyor..."
                            errorMsg = null
                            result = null
                        } else {
                            // Son kelimenin sonu kırpılmasın diye "Durdur"a basıldığı anda değil,
                            // kısa bir tampon süre sonra gerçekten durduruyoruz (kayıt arka planda devam ediyor).
                            isRecording = false
                            isBusy = true
                            status = "Bitiriyor..."
                            scope.launch {
                                delay(600)
                                recorder.stop()
                                status = "Cihaz üzerinde işleniyor..."
                                val wavFile = recorder.lastFile
                                if (wavFile != null) {
                                    try {
                                        val scored = withContext(Dispatchers.Default) {
                                            OnDeviceScorer.score(context, targetText, wavFile)
                                        }
                                        result = scored
                                        status = "Tamamlandı"
                                        targetText = randomPracticeSentence(exclude = targetText)
                                    } catch (e: Exception) {
                                        errorMsg = e.message ?: "Bilinmeyen hata"
                                        status = "Hata"
                                    } finally {
                                        isBusy = false
                                    }
                                } else {
                                    isBusy = false
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) BrandRed else BrandPurple
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        if (isRecording) "⏹  Kaydı Durdur" else "🎙️  Kaydı Başlat",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(visible = errorMsg != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDEAEA))
            ) {
                Text(
                    errorMsg ?: "",
                    color = BrandRed,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }

        AnimatedVisibility(visible = result != null) {
            Column {
                Spacer(Modifier.height(16.dp))
                result?.let { PronunciationCard(it) }
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
            val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                targetValue = target,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
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
