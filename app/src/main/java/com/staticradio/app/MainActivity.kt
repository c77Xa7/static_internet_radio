package com.staticradio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.staticradio.app.data.settings.AccentColor
import com.staticradio.app.data.settings.ThemeMode
import com.staticradio.app.ui.nav.StaticApp
import com.staticradio.app.ui.theme.StaticTheme
import com.staticradio.app.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way — playback works without it, just no visible notification */ }

    // Holds a URL shared in from SoundCloud/Mixcloud's own share sheet (see
    // extractSharedUrl below). A plain Compose State so onNewIntent's update
    // (app already running, singleTop launch mode) triggers recomposition.
    private val sharedMixUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        sharedMixUrl.value = extractSharedUrl(intent)

        val app = application as StaticRadioApp
        val stationDao = app.database.stationDao()
        setContent {
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val accentColor by app.settingsRepository.accentColor.collectAsState(initial = AccentColor.REBAR)
            val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())

            StaticTheme(themeMode = themeMode, accentColor = accentColor) {
                val statusBarColor = MaterialTheme.colorScheme.background.toArgb()
                val view = LocalView.current
                SideEffect {
                    window.statusBarColor = statusBarColor
                    window.navigationBarColor = statusBarColor
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    // Dark theme -> light (white) status bar icons/text. Light theme -> dark icons.
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
                StaticApp(stationDao = stationDao, sharedMixUrl = sharedMixUrl.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedMixUrl.value = extractSharedUrl(intent)
    }
}

/** SoundCloud/Mixcloud's share sheet sends ACTION_SEND text/plain, usually just the link or "title + link". */
private fun extractSharedUrl(intent: Intent): String? {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    return Regex("""https?://\S+""").find(text)?.value
}
