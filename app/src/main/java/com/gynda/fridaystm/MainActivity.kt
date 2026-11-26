package com.gynda.fridaystm

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gynda.fridaystm.ui.FridayStmApp
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    /**
     * The app ships Indonesian resources only; pin the locale so framework and
     * AndroidX widgets (Material date picker, java.time formatters) stop falling
     * back to the device locale and rendering half the UI in English.
     */
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale("id", "ID")
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Daftarkan locale per-aplikasi ke sistem (API 33+) agar komponen
        // sistem (pemilih foto, dsb.) ikut memakai bahasa Indonesia.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getSystemService(LocaleManager::class.java)
            if (localeManager.applicationLocales.isEmpty) {
                localeManager.applicationLocales = LocaleList.forLanguageTags("id-ID")
            }
        }
        enableEdgeToEdge()
        setContent {
            FridaySTMTheme {
                FridayStmApp()
            }
        }
    }
}
