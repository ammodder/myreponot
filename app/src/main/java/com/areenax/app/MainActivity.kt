package com.areenax.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.areenax.app.core.nav.parseDeepLink
import com.areenax.app.core.push.MyFirebaseMessagingService
import com.areenax.app.core.theme.AreenaxTheme
import com.areenax.app.core.ui.AppScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Single-Activity Compose host (manifest: singleTask, portrait, splash theme).
 *
 * Launch sequence mirrors web AppShell's hydration gate:
 *  1. core-splashscreen shows the brand splash (postSplashScreenTheme → Theme.Areenax).
 *  2. enableEdgeToEdge + status-bar icon contrast following the APP theme
 *     (the persisted areena-theme choice, not the system setting).
 *  3. AreenaxTheme(darkTheme = session.themeIsDark) → AppScaffold.
 *
 * "Hydrate-then-navigate": the navigator starts on `login` (store.ts initial
 * state); AppScaffold holds its splash until SessionManager hydration completes
 * and a restored token is validated via GET /me, then reveals the restored
 * screen or home. No screen renders before hydration — mirroring the web's
 * `hydrated` flag.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // Theme.Areenax.Splash → Theme.Areenax
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AreenaxApplication

        askNotificationPermission()
        handleNotificationIntent(intent, app)

        setContent {
            val darkTheme by app.session.themeIsDark.collectAsState()

            // Keep system-bar icon contrast in sync with the APP theme
            // (web adds/removes the `dark` class on <html> for the same effect).
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ),
                )
            }

            AreenaxTheme(darkTheme = darkTheme) {
                AppScaffold(env = app.env)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as AreenaxApplication
        handleNotificationIntent(intent, app)
    }

    private fun handleNotificationIntent(intent: Intent?, app: AreenaxApplication) {
        val link = intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_DEEP_LINK) ?: return
        val target = parseDeepLink(link) ?: return
        if (app.session.user.value != null) {
            app.navigator.navigate(target.screen, target.params)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                app.session.setPendingLink(link)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
