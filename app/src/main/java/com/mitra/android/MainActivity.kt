package com.mitra.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitra.android.ui.MitraAppShell
import com.mitra.android.ui.MitraViewModel
import com.mitra.android.ui.theme.MitraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MitraViewModel = hiltViewModel()
            val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            MitraTheme(
                darkTheme = settings.darkThemeEnabled,
                dynamicColor = settings.dynamicColorEnabled,
                accentTheme = settings.accentTheme
            ) {
                MitraAppShell(
                    viewModel = viewModel,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }
}
