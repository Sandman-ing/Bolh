package eu.bolh.app.data.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.bolh.app.BolhApplication
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.ScheduleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Срабатывание будильника: показать напоминание и поставить следующее.
 *
 * Если день уже закрыт, напоминание не приходит: напоминать о сделанном —
 * это шум, после которого уведомления выключают целиком.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        if (intent.action == ACTION_DISMISS) {
            BolhNotifications.cancel(context, id)
            return
        }
        val app = context.applicationContext as? BolhApplication ?: return
        if (!firstDelivery(context, intent)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_HABIT -> remindHabit(app, id, intent.getBooleanExtra(EXTRA_EVENING, false))
                    ACTION_PAYMENT -> remindPayment(app, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * У напоминания без точного времени два будильника на один момент.
     * Сработавший вторым видит, что этот момент уже обработан, и молчит.
     * Получатель вызывается по очереди, поэтому проверка без гонок.
     */
    // Запись синхронная намеренно: следующий будильник может прийти сразу,
    // и отметка должна лежать на диске раньше, чем он её спросит.
    @SuppressLint("ApplySharedPref")
    private fun firstDelivery(context: Context, intent: Intent): Boolean {
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val address = intent.data?.toString() ?: return true
        if (at == 0L) return true
        val key = address.removeSuffix(BACKUP_SUFFIX)
        val prefs = context.getSharedPreferences(PREFS_DELIVERED, Context.MODE_PRIVATE)
        if (prefs.getLong(key, 0L) == at) return false
        prefs.edit().putLong(key, at).commit()
        return true
    }

    private suspend fun remindHabit(app: BolhApplication, habitId: String, evening: Boolean) {
        val repository = app.container.habitRepository
        val habit = repository.findHabit(habitId) ?: return
        val today = repository.today()

        val planned = habit.schedule.type != ScheduleType.Weekdays || habit.schedule.plannedOn(today)
        val started = !today.isBefore(habit.startDate)
        val paused = repository.pausesFor(habitId).any { it.covers(today) }
        val marks = if (habit.kind == HabitKind.Good) repository.doneMarksOn(habitId, today) else 0
        val done = habit.kind == HabitKind.Good && marks >= habit.timesPerDay

        if (planned && started && !paused && !done) {
            BolhNotifications.showHabitReminder(app, habit, evening, doneToday = marks)
        }
        // Будильник системы одноразовый: следующий ставится здесь же.
        app.container.reminders.scheduleHabit(habit)
    }

    private suspend fun remindPayment(app: BolhApplication, paymentId: String) {
        val payment = app.container.financeRepository.findRecurring(paymentId) ?: return
        if (payment.isActive) BolhNotifications.showPaymentReminder(app, payment)
        app.container.reminders.schedulePayment(payment)
    }

    companion object {
        const val ACTION_HABIT = "eu.bolh.app.action.REMIND_HABIT"
        const val ACTION_PAYMENT = "eu.bolh.app.action.REMIND_PAYMENT"
        const val ACTION_DISMISS = "eu.bolh.app.action.DISMISS_REMINDER"
        const val EXTRA_ID = "id"
        const val EXTRA_EVENING = "evening"
        const val EXTRA_AT = "at"

        /** Окончание адреса у страхующего будильника. */
        const val BACKUP_SUFFIX = "/window"

        private const val PREFS_DELIVERED = "reminders_delivered"

        /** Кнопка «Выключить» у напоминания-будильника. */
        fun dismissIntent(context: Context, id: String): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_DISMISS)
                .setData(Uri.parse("bolh://reminder/dismiss/$id"))
                .putExtra(EXTRA_ID, id)
    }
}

/**
 * Будильники системы не переживают перезагрузку, а после смены времени
 * или обновления приложения их стоит поставить заново.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Только системные события из манифеста: чужое намерение без действия
        // не должно дёргать перестановку всех будильников.
        if (intent.action !in ACTIONS) return
        val app = context.applicationContext as? BolhApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.rescheduleReminders()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // Константа появилась в Android 12; строка работает и раньше.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
