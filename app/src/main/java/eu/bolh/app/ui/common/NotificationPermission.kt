package eu.bolh.app.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.notifications.BolhNotifications

/**
 * Просит разрешение на уведомления, если напоминания уже есть, а разрешения нет.
 *
 * Обычно разрешение спрашивается в редакторе, когда человек ставит время. Но
 * напоминания появляются и без редактора: из резервной копии на новом
 * телефоне или после переезда со старого Android, где разрешения не было.
 * Без этой проверки такие напоминания молча не приходили бы. Если человек
 * отказал дважды, система сама перестаёт показывать запрос.
 *
 * [trigger] — повод проверить снова, например число восстановленных записей.
 */
@Composable
fun AskNotificationsForReminders(trigger: Any?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(trigger) {
        if (trigger == null || askedInProcess || BolhNotifications.granted(context)) return@LaunchedEffect
        val app = context.applicationContext as? BolhApplication ?: return@LaunchedEffect
        if (app.hasReminders()) {
            askedInProcess = true
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Один запрос за жизнь процесса: экран пересоздаётся при смене языка, и без
 * этой отметки вопрос всплывал бы дважды подряд.
 */
private var askedInProcess = false
