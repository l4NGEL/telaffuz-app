package com.telaffuz.pronunciation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.telaffuz.pronunciation.ui.PronunciationViewModel
import com.telaffuz.pronunciation.ui.TelaffuzRoute
import com.telaffuz.pronunciation.ui.theme.BackgroundSoft
import com.telaffuz.pronunciation.ui.theme.TelaffuzTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PronunciationViewModel by viewModels {
        val container = (application as TelaffuzApp).container
        PronunciationViewModel.factory(
            recorder = container::newRecorder,
            scorer = { container.scorer },
            sentences = { container.sentences },
        )
    }

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
                    TelaffuzRoute(
                        viewModel = viewModel,
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
