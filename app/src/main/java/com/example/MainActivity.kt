package com.example

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.BrazilLocaleConfig
import com.example.data.SecureBrokerSessionManager
import com.example.firebase.configureAppCheck
import com.example.service.RobotBackgroundService
import com.example.ui.TrendLensScreen
import com.example.ui.theme.TrendLensTheme

class MainActivity : ComponentActivity() {
    private var pendingSharedImageUri by mutableStateOf<Uri?>(null)
    private var isInPipMode by mutableStateOf(false)
    private var keepRunningInBackgroundActive: Boolean = true

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(BrazilLocaleConfig.wrapContextInBrazilLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrazilLocaleConfig.applyBrazilProcessDefaults()
        super.onCreate(savedInstanceState)
        runCatching { configureAppCheck(this, intent) }
        runCatching {
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            SecureBrokerSessionManager.restoreToWebViewIfMissing(applicationContext)
        }
        handleIncomingShareIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyFullScreenMode()
        updatePipAutoEnterParams(keepRunningInBackgroundActive)
        setContent {
            TrendLensTheme {
                TrendLensScreen(
                    sharedImageUri = pendingSharedImageUri,
                    onSharedImageConsumed = { pendingSharedImageUri = null },
                    isInPipMode = isInPipMode,
                    onKeepBackgroundRunningChanged = { active ->
                        keepRunningInBackgroundActive = active
                        updatePipAutoEnterParams(active)
                    },
                    onMinimizeToBackground = { minimizeToBackgroundOrPip() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")) {
            runCatching { configureAppCheck(this, intent) }
        }
        handleIncomingShareIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (keepRunningInBackgroundActive && !isInPipMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(buildPipParams(autoEnter = true))
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            applyFullScreenMode()
        }
    }

    private fun minimizeToBackgroundOrPip() {
        if (keepRunningInBackgroundActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPipMode) {
            val enteredPip = runCatching {
                enterPictureInPictureMode(buildPipParams(autoEnter = true))
            }.getOrDefault(false)
            if (enteredPip) return
        }
        moveTaskToBack(true)
    }

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipAutoEnterParams(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            setPictureInPictureParams(buildPipParams(autoEnter = enabled))
        }
    }

    @Suppress("DEPRECATION")
    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) {
            pendingSharedImageUri = uri
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            SecureBrokerSessionManager.restoreToWebViewIfMissing(applicationContext)
        }
    }

    override fun onPause() {
        runCatching {
            android.webkit.CookieManager.getInstance().flush()
            SecureBrokerSessionManager.captureAndPersistFromWebView(applicationContext, synchronous = true)
        }
        super.onPause()
    }

    override fun onStop() {
        runCatching {
            android.webkit.CookieManager.getInstance().flush()
            SecureBrokerSessionManager.captureAndPersistFromWebView(applicationContext, synchronous = true)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            RobotBackgroundService.stop(applicationContext)
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isInPipMode) {
            applyFullScreenMode()
        }
    }

    private fun applyFullScreenMode() {
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
