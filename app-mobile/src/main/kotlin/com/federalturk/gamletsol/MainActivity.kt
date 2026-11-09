package com.federalturk.gamletsol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.federalturk.gamletsol.ui.CaptionScreen
import com.federalturk.gamletsol.ui.GamletsolTheme
import com.federalturk.gamletsol.ui.OnboardingScreen

class MainActivity : ComponentActivity() {

    private val viewModel: CaptionViewModel by viewModels()

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onCameraPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onCameraPermissionResult(alreadyGranted)

        val onboardingPrefs = OnboardingPrefs(applicationContext)

        setContent {
            GamletsolTheme {
                // NOT (geri bildirim, 2026-11-09): kullanıcı ilk açılışta
                // menü/kurulum talimatları/bilgi istedi — bu bayrak, kamera
                // izni istenmeden ÖNCE bir tanıtım akışı gösterilmesini
                // sağlıyor. `remember` + `mutableStateOf`, Activity hayatta
                // olduğu sürece ekranlar arasında geçişi yönetir;
                // `onboardingPrefs` kalıcılığı sağlar (bkz. OnboardingPrefs).
                var showOnboarding by remember {
                    mutableStateOf(!onboardingPrefs.onboardingComplete)
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        onDone = {
                            onboardingPrefs.onboardingComplete = true
                            showOnboarding = false
                        },
                    )
                } else {
                    CaptionScreen(
                        viewModel = viewModel,
                        onRequestCameraPermission = {
                            requestCameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        onShowOnboardingAgain = { showOnboarding = true },
                    )
                }
            }
        }
    }
}
