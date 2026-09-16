package eu.bolh.app.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.bolh.app.MainActivity
import eu.bolh.app.R
import eu.bolh.app.data.prefs.LanguagePrefs
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.ui.common.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Уведомления — единственный способ приложения заговорить первым, поэтому правило
 * жёсткое: только напоминание о запланированном действии. Никаких «ты пропустил,
 * вернись» и никаких неприятных выводов в шторке (§3.4).
 */
object BolhNotifications {

    /** Обычные напоминания: всплывают поверх экрана, но звучат один раз. */
    const val CHANNEL_REMINDERS = "reminders"

    /** Напоминания-будильники: звук будильника, пока уведомление не откроют. */
    const val CHANNEL_ALARMS = "reminders_alarm"

    const val CHANNEL_PAYMENTS = "payments"

    /** Канал прежних версий: тихий, из-за него напоминания легко пропускали. */
    private const val LEGACY_CHANNEL = "habits"

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

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val text = LanguagePrefs.localized(context)

        if (manager.getNotificationChannel(LEGACY_CHANNEL) != null) {
            manager.deleteNotificationChannel(LEGACY_CHANNEL)
        }
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    text.getString(R.string.notif_channel_habits),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = text.getString(R.string.notif_channel_habits_desc)
                    enableVibration(true)
                    setShowBadge(false)
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ALARMS) == null) {
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALARMS,
                    text.getString(R.string.notif_channel_alarms),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = text.getString(R.string.notif_channel_alarms_desc)
                    setSound(
                        sound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 700, 500, 700)
                    setShowBadge(false)
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_PAYMENTS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PAYMENTS,
                    text.getString(R.string.notif_channel_payments),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = text.getString(R.string.notif_channel_payments_desc)
                    setShowBadge(false)
                },
            )
        }
    }

    /**
     * Напоминание о привычке.
     *
     * Вечернее предлагает минимум, а не норму (§5.3): «одна страница, и день
     * засчитан». Кнопки отмечают день прямо из шторки: ежедневное действие
     * должно быть самым быстрым в приложении.
     */
    fun showHabitReminder(context: Context, habit: Habit, evening: Boolean, doneToday: Int = 0) {
        ensureChannels(context)
        if (!allowed(context)) return
        val text = LanguagePrefs.localized(context)

        val offerMinimum = evening && habit.mode == HabitMode.Minimum && !habit.minimumLabel.isNullOrBlank()
        val minutes = habit.estimatedMinutes?.takeIf { it > 0 }
        val body = when {
            habit.kind == HabitKind.Bad -> text.getString(R.string.notif_bad_text)
            offerMinimum -> text.getString(R.string.notif_evening_text, habit.minimumLabel)
            // Привычка на несколько раз в день: напоминание говорит, сколько
            // уже набрано, иначе второе и третье выглядят как повтор первого.
            habit.timesPerDay > 1 -> text.getString(R.string.notif_times_text, doneToday, habit.timesPerDay)
            minutes != null -> text.getString(R.string.notif_minutes_text, minutes)
            else -> text.getString(R.string.notif_default_text)
        }

        val builder = NotificationCompat.Builder(
            context,
            if (habit.reminderAlarm) CHANNEL_ALARMS else CHANNEL_REMINDERS,
        )
            .setSmallIcon(R.drawable.ic_tab_habits)
            .setContentTitle(habit.name)
            .setContentText(body)
            .setContentIntent(openApp(context, habit.id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (habit.reminderAlarm) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_REMINDER
                },
            )

        if (habit.kind == HabitKind.Good) {
            if (habit.mode == HabitMode.Minimum) {
                builder.addAction(0, text.getString(R.string.notif_action_minimum), mark(context, habit.id, EntryLevel.Minimum))
                builder.addAction(0, text.getString(R.string.notif_action_norm), mark(context, habit.id, EntryLevel.Norm))
            } else {
                builder.addAction(0, text.getString(R.string.notif_action_done), mark(context, habit.id, EntryLevel.Norm))
            }
        }
        if (habit.reminderAlarm) {
            builder.addAction(
                0,
                text.getString(R.string.notif_action_dismiss),
                PendingIntent.getBroadcast(
                    context,
                    0,
                    ReminderReceiver.dismissIntent(context, habit.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        val notification = builder.build()
        // Будильник повторяет звук, пока уведомление не откроют или не смахнут.
        if (habit.reminderAlarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notify(context, habit.id.hashCode(), notification)
    }

    /**
     * Регулярный платёж: напоминание накануне и кнопка «Записать» (§6.5).
     * Ничего не появляется в истории без ведома человека.
     */
    fun showPaymentReminder(context: Context, payment: RecurringPayment) {
        ensureChannels(context)
        if (!allowed(context)) return
        val text = LanguagePrefs.localized(context)

        val record = PendingIntent.getBroadcast(
            context,
            payment.id.hashCode() + 1,
            RecurringRecordReceiver.intent(context, payment.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Напоминание приходит и за несколько дней, и в сам день списания:
        // «завтра» в тексте было правдой только один раз из трёх.
        val locale = LanguagePrefs.locale(context)
        val amount = Money.format(payment.amountMinor, payment.currencyCode, locale)
        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), payment.nextDate)
        val body = when {
            daysLeft <= 0L -> text.getString(R.string.notif_recurring_today, amount)
            daysLeft == 1L -> text.getString(R.string.notif_recurring_text, amount)
            else -> text.getString(
                R.string.notif_recurring_on,
                amount,
                payment.nextDate.format(
                    DateTimeFormatter.ofPattern(text.getString(R.string.date_day_month), locale),
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_tab_finance)
            .setContentTitle(payment.name)
            .setContentText(body)
            .setContentIntent(openApp(context, payment.id))
            .setAutoCancel(true)
            .addAction(0, text.getString(R.string.finance_record), record)
            .build()
        notify(context, payment.id.hashCode(), notification)
    }

    fun cancel(context: Context, id: String) {
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun mark(context: Context, habitId: String, level: EntryLevel): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (habitId + level.name).hashCode(),
            HabitMarkReceiver.intent(context, habitId, level),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context, id: String): PendingIntent = PendingIntent.getActivity(
        context,
        id.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (ignored: SecurityException) {
            // Разрешение отозвали между проверкой и показом — тогда просто молчим.
        }
    }
}
