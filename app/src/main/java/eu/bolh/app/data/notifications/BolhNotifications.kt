package eu.bolh.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.bolh.app.MainActivity
import eu.bolh.app.R
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.HabitMode

/**
 * Уведомления — единственный способ приложения заговорить первым, поэтому правило
 * жёсткое: только напоминание о запланированном действии. Никаких «ты пропустил,
 * вернись» и никаких неприятных выводов в шторке (§3.4).
 */
object BolhNotifications {

    const val CHANNEL_HABITS = "habits"

    /**
     * На Android 13+ уведомления требуют разрешения, и человек мог его не дать.
     * Проверяем явно: молча ничего не показать честнее, чем поймать исключение.
     */
    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() && granted(context)

    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_HABITS) != null) return

        val channel = NotificationChannel(
            CHANNEL_HABITS,
            context.getString(R.string.notif_channel_habits),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_habits_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Вечернее напоминание предлагает минимум, а не норму (§5.3): «одна страница,
     * и день засчитан». Кнопка отмечает день прямо из шторки — ежедневное действие
     * должно быть самым быстрым в приложении.
     */
    fun showHabitReminder(
        context: Context,
        habitId: String,
        habitName: String,
        minimumLabel: String?,
        mode: HabitMode,
        evening: Boolean,
    ) {
        ensureChannel(context)
        if (!allowed(context)) return

        val offerMinimum = evening && mode == HabitMode.Minimum && !minimumLabel.isNullOrBlank()
        val text = if (offerMinimum) {
            context.getString(R.string.notif_evening_text, minimumLabel)
        } else {
            context.getString(R.string.notif_default_text)
        }

        val open = PendingIntent.getActivity(
            context,
            habitId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markLevel = if (mode == HabitMode.Minimum) EntryLevel.Minimum else EntryLevel.Norm
        val mark = PendingIntent.getBroadcast(
            context,
            habitId.hashCode() + 1,
            HabitMarkReceiver.intent(context, habitId, markLevel),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_tab_habits)
            .setContentTitle(habitName)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(
                    if (markLevel == EntryLevel.Minimum) {
                        R.string.notif_action_minimum
                    } else {
                        R.string.notif_action_done
                    },
                ),
                mark,
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(habitId.hashCode(), notification)
        } catch (ignored: SecurityException) {
            // Разрешение отозвали между проверкой и показом — тогда просто молчим.
        }
    }

    fun cancel(context: Context, habitId: String) {
        NotificationManagerCompat.from(context).cancel(habitId.hashCode())
    }
}
