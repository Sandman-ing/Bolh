package eu.bolh.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.bolh.app.data.prefs.LanguagePrefs
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
        setContent { BolhApp() }
    }
}
