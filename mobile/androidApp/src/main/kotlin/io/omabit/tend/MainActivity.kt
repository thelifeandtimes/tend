@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.omabit.tend

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

class TendApplication : Application() {
    val repository by lazy {
        val http =
            HttpClient(OkHttp) {
                followRedirects = false
                engine {
                    config {
                        followRedirects(false)
                        followSslRedirects(false)
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(90, TimeUnit.SECONDS)
                        writeTimeout(20, TimeUnit.SECONDS)
                    }
                }
            }
        TendRepository(Eyre(http, BuildConfig.DEBUG), AndroidCredentials(this), AndroidCache(this))
    }
}

class MainActivity : ComponentActivity() {
    private val repository
        get() = (application as TendApplication).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val client by repository.state.collectAsState()
            val lightBars =
                luminance(client.data.appearance.palette.colors.getValue("background")) > .179
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
            CompositionLocalProvider(
                LocalSheetSystemBars provides
                    { light ->
                        val view = LocalView.current
                        // Material 3 initializes these flags when the dialog is created,
                        // but does not update them when an already-open sheet changes theme.
                        SideEffect {
                            val owner =
                                generateSequence(view as android.view.View?) {
                                        it.parent as? android.view.View
                                    }
                                    .filterIsInstance<DialogWindowProvider>()
                                    .firstOrNull()
                            owner?.window?.let { sheetWindow ->
                                WindowCompat.getInsetsController(sheetWindow, view).apply {
                                    isAppearanceLightStatusBars = light
                                    isAppearanceLightNavigationBars = light
                                }
                            }
                        }
                    },
                LocalSheetAppearance provides
                    { light ->
                        ModalBottomSheetProperties(
                            isAppearanceLightStatusBars = light,
                            isAppearanceLightNavigationBars = light,
                        )
                    },
            ) {
                TendApp(repository)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        repository.setForeground(true)
    }

    override fun onStop() {
        repository.setForeground(false)
        super.onStop()
    }
}
