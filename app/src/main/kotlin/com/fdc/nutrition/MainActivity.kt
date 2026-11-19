package com.fdc.nutrition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fdc.nutrition.ui.AppViewModel
import com.fdc.nutrition.ui.navigation.FdcRoot
import com.fdc.nutrition.ui.theme.FdcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            FdcTheme(mode = state.preferences.theme, dynamicColor = state.preferences.dynamicColor) {
                FdcRoot(state)
            }
        }
    }
}
