package com.gynda.fridaystm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gynda.fridaystm.ui.FridayStmApp
import com.gynda.fridaystm.ui.theme.FridaySTMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FridaySTMTheme {
                FridayStmApp()
            }
        }
    }
}
