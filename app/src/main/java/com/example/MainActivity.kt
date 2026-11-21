package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.SmartTourScreen
import com.example.ui.SmartTourViewModel
import com.example.ui.theme.MyApplicationTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Configuration.getInstance().apply {
      load(applicationContext, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
      userAgentValue = packageName
    }
    enableEdgeToEdge()
    setContent {
      val viewModel: SmartTourViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsState()
      val useDarkTheme = when (themeMode) {
          true -> true
          false -> false
          null -> isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = useDarkTheme) {
        SmartTourScreen(viewModel = viewModel)
      }
    }
  }
}

