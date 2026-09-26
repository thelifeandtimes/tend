package io.omabit.tend

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent { TendApp(repository) }
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
