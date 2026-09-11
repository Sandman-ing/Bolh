package eu.bolh.app.ui.theme

import android.content.Context
import eu.bolh.app.data.prefs.ThemePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Выбранная тема потоком.
 *
 * Тема живёт дольше экрана и меняется из настроек, а увидеть её должно всё
 * приложение сразу. Пересоздавать Activity ради этого не нужно: смена темы —
 * не смена языка, ресурсы остаются теми же.
 */
object ThemeMode {

    private val state = MutableStateFlow(ThemePrefs.DARK)

    val current: StateFlow<String> = state

    /** Читается один раз при старте, синхронно — до первой отрисовки. */
    fun load(context: Context) {
        state.value = ThemePrefs.get(context)
    }

    fun set(context: Context, mode: String) {
        ThemePrefs.set(context, mode)
        state.value = mode
    }
}
