package eu.bolh.app.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import eu.bolh.app.MainActivity
import eu.bolh.app.domain.analytics.ReminderTimes
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.RecurringPayment
import java.time.Clock
import java.time.LocalDateTime

/**
 * Напоминания на будильниках системы (§5.6).
 *
 * Раньше напоминания шли через WorkManager, а он запускает работу, когда
 * сочтёт нужным: на телефонах с экономией батареи напоминание на 9:00
 * приходило к обеду или не приходило вовсе. Будильник системы срабатывает
 * вовремя и будит телефон из сна.
 *
 * Для точного времени нужно разрешение «Будильники и напоминания». Без него
 * будильник всё равно ставится, но система может сдвинуть его на несколько
 * минут. Это лучше, чем промолчать.
 */
class ReminderAlarms(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val manager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /** Можно ли ставить точное время. До Android 12 разрешение не требовалось. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager?.canScheduleExactAlarms() == true

    /**
     * Ставит все напоминания привычки на ближайшие дни, где их ждут.
     *
     * Переставлять напоминания могут сразу двое: восстановление копии и
     * слежение за данными. Вперемешку снятие и постановка оставляли лишний
     * будильник, поэтому все четыре действия идут строго по очереди.
     */
    @Synchronized
    fun scheduleHabit(habit: Habit) {
        cancelHabit(habit.id)
        val now = LocalDateTime.now(clock)
        val times = habit.reminderTimes.take(MAX_TIMES)
        times.forEachIndexed { slot, minute ->
            set(
                intent = { backup, at -> habitIntent(habit.id, slot, evening = false, backup, at) },
                at = ReminderTimes.nextHabitFire(now, minute, habit.schedule, habit.startDate),
                alarm = habit.reminderAlarm,
            )
        }
        // Вечерний повтор нужен, только если все напоминания раньше вечера:
        // два сигнала подряд — это уже давление, а не помощь.
        val evening = EVENING_HOUR * 60
        if (habit.reminderRepeatEvening && times.isNotEmpty() && times.all { it < evening }) {
            set(
                intent = { backup, at -> habitIntent(habit.id, EVENING_SLOT, evening = true, backup, at) },
                at = ReminderTimes.nextHabitFire(now, evening, habit.schedule, habit.startDate),
                alarm = habit.reminderAlarm,
            )
        }
    }

    @Synchronized
    fun cancelHabit(habitId: String) {
        listOf(false, true).forEach { backup ->
            repeat(MAX_TIMES) { slot -> cancel(habitIntent(habitId, slot, evening = false, backup)) }
            cancel(habitIntent(habitId, EVENING_SLOT, evening = true, backup))
        }
    }

    @Synchronized
    fun schedulePayment(payment: RecurringPayment) {
        cancelPayment(payment.id)
        if (!payment.isActive) return
        val at = ReminderTimes.nextPaymentFire(
            now = LocalDateTime.now(clock),
            paymentDate = payment.nextDate,
            daysBefore = payment.notifyDaysBefore,
        ) ?: return
        set(
            intent = { backup, millis -> paymentIntent(payment.id, backup, millis) },
            at = at,
            alarm = false,
        )
    }

    @Synchronized
    fun cancelPayment(paymentId: String) {
        cancel(paymentIntent(paymentId, backup = false))
        cancel(paymentIntent(paymentId, backup = true))
    }

    private fun habitIntent(
        habitId: String,
        slot: Int,
        evening: Boolean,
        backup: Boolean,
        at: Long = 0L,
    ): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_HABIT)
            // Адрес различает будильники одной привычки: без него все её
            // времена сливались бы в один и срабатывал бы только последний.
            .setData(Uri.parse(address("bolh://reminder/habit/$habitId/$slot", backup)))
            .putExtra(ReminderReceiver.EXTRA_ID, habitId)
            .putExtra(ReminderReceiver.EXTRA_EVENING, evening)
            .putExtra(ReminderReceiver.EXTRA_AT, at)

    private fun paymentIntent(paymentId: String, backup: Boolean, at: Long = 0L): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_PAYMENT)
            .setData(Uri.parse(address("bolh://reminder/payment/$paymentId", backup)))
            .putExtra(ReminderReceiver.EXTRA_ID, paymentId)
            .putExtra(ReminderReceiver.EXTRA_AT, at)

    private fun address(base: String, backup: Boolean): String =
        if (backup) base + ReminderReceiver.BACKUP_SUFFIX else base

    private fun set(intent: (backup: Boolean, at: Long) -> Intent, at: LocalDateTime, alarm: Boolean) {
        val manager = manager ?: return
        val millis = at.atZone(clock.zone).toInstant().toEpochMilli()
        val operation = broadcast(intent(false, millis))
        val exact = canScheduleExact()
        try {
            when {
                // Будильник виден в строке состояния и не откладывается системой.
                alarm && exact -> manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(millis, openApp()),
                    operation,
                )

                exact -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)

                else -> setInexact(manager, millis, operation, broadcast(intent(true, millis)))
            }
        } catch (denied: SecurityException) {
            // Разрешение отозвали между проверкой и вызовом: ставим неточно, но ставим.
            setInexact(manager, millis, operation, broadcast(intent(true, millis)))
        }
    }

    /**
     * Без разрешения на точное время система вправе сдвинуть напоминание
     * на целый час. Поэтому будильников два: один с окном в десять минут,
     * которое система соблюдает, пока телефон не спит глубоко, и обычный,
     * который дойдёт и из сна. Какой сработает первым, тот и напомнит;
     * второй получатель узнает по времени, что всё уже сделано.
     */
    private fun setInexact(
        manager: AlarmManager,
        millis: Long,
        operation: PendingIntent,
        window: PendingIntent,
    ) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        try {
            manager.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MS, window)
        } catch (denied: SecurityException) {
            // Узкое окно — только подстраховка: без него остаётся обычный будильник.
        }
    }

    private fun broadcast(intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancel(intent: Intent) {
        val existing = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        manager?.cancel(existing)
        existing.cancel()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** Больше десяти напоминаний в день — это уже шум, а не помощь. */
        const val MAX_TIMES = 10

        private const val EVENING_SLOT = 99
        private const val EVENING_HOUR = 21

        /** Самое узкое окно, которое Android разрешает без точного времени. */
        private const val WINDOW_MS = 10 * 60 * 1000L
    }
}
