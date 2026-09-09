package eu.bolh.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import eu.bolh.app.data.prefs.LanguagePrefs
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.navigation.BolhApp

class MainActivity : ComponentActivity() {

    /**
     * Язык применяется здесь, до создания экрана. Так конфигурация Activity
     * сразу правильная, и всё остальное — строки, даты, суммы — берётся из неё
     * само, без подмен по дороге.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Пересоздание, а не запуск: recreate() сохраняет состояние, обычный
        // старт — нет. Затухание нужно только здесь, второй половиной перехода
        // при смене языка. На холодном старте оно было бы лишней задержкой.
        val recreated = savedInstanceState != null
        setContent {
            val appear = remember { Animatable(if (recreated) 0f else 1f) }
            LaunchedEffect(Unit) {
                if (recreated) appear.animateTo(1f, tween(Motion.CONTENT_MS))
            }
            Box(modifier = Modifier.fillMaxSize().alpha(appear.value)) {
                BolhApp()
            }
        }
    }
}
