package com.kometa.reader

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberLauncherForActivityResult
import com.kometa.reader.ui.KometaRoot
import com.kometa.reader.ui.theme.KometaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KometaTheme {
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                KometaRoot()
            }
        }
    }
}
