package eu.bolh.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Переходы в настройки телефона: уведомления, точное время, батарея.
 *
 * Это разрешения человека, а не приложения, поэтому они живут там, а не у нас.
 * Если нужный экран у производителя не открывается, пробуем общий.
 */
object SystemSettings {

    fun openNotifications(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!start(context, intent)) openAppDetails(context)
    }

    /** «Будильники и напоминания»: без этого разрешения время напоминаний неточное. */
    fun openExactAlarms(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        )
        if (!start(context, intent)) openAppDetails(context)
    }

    /** Экономия батареи: на части телефонов именно она глушит напоминания. */
    fun openBatteryOptimization(context: Context) {
        if (!start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            openAppDetails(context)
        }
    }

    private fun openAppDetails(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
