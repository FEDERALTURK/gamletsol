package com.federalturk.gamletsol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.federalturk.gamletsol.ui.CaptionScreen
import com.federalturk.gamletsol.ui.GamletsolTheme

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

        setContent {
            GamletsolTheme {
                CaptionScreen(
                    viewModel = viewModel,
                    onRequestCameraPermission = {
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    },
                )
            }
        }
    }
}
