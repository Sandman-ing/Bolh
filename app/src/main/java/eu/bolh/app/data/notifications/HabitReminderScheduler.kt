package eu.bolh.app.data.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.ScheduleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Напоминания намеренно неточные: WorkManager без разрешения на точные будильники.
 * Привычке «около девяти вечера» минута не важна, а разрешение на точные будильники
 * пугает пользователя и режется производителями телефонов.
 *
 * Каждая работа одноразовая и на исполнении назначает следующую: так расписание
 * переживает и смену времени, и перезагрузку, и правку самой привычки.
 */
class HabitReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /**
     * Первое обращение к WorkManager поднимает всю его подсистему, и на главном
     * потоке это заметная задержка — кнопка «Сохранить» отвечала с запозданием.
     * Планирование ничего не возвращает, поэтому уезжает в фон целиком.
     */
    suspend fun schedule(habit: Habit) = withContext(Dispatchers.IO) {
        cancel(habit.id)
        val minuteOfDay = habit.reminderMinuteOfDay ?: return@withContext

        enqueue(habit, minuteOfDay, evening = false)

        if (habit.reminderRepeatEvening) {
            val eveningMinute = EVENING_HOUR * 60
            // Если основное напоминание и так позднее, вечернего повтора не будет:
            // два уведомления подряд — это уже давление, а не помощь.
            if (minuteOfDay < eveningMinute) enqueue(habit, eveningMinute, evening = true)
        }
    }

    fun cancel(habitId: String) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(workName(habitId, evening = false))
        manager.cancelUniqueWork(workName(habitId, evening = true))
        BolhNotifications.cancel(context, habitId)
    }

    suspend fun rescheduleAll(habits: List<Habit>) {
        habits.forEach { schedule(it) }
    }

    private fun enqueue(habit: Habit, minuteOfDay: Int, evening: Boolean) {
        val delay = delayUntilNext(habit, minuteOfDay)
        val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(
                Data.Builder()
                    .putString(HabitReminderWorker.KEY_HABIT_ID, habit.id)
                    .putBoolean(HabitReminderWorker.KEY_EVENING, evening)
                    .build(),
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(habit.id, evening),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Ближайший день, когда привычка запланирована, начиная с сегодняшнего. */
    private fun delayUntilNext(habit: Habit, minuteOfDay: Int): Duration {
        val now = LocalDateTime.now(clock)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

        var date: LocalDate = now.toLocalDate()
        if (!now.toLocalTime().isBefore(time)) date = date.plusDays(1)

        // «N раз в неделю» не привязано к дням: напоминание приходит каждый день.
        if (habit.schedule.type == ScheduleType.Weekdays) {
            var guard = 0
            while (!habit.schedule.plannedOn(date) && guard < 8) {
                date = date.plusDays(1)
                guard++
            }
        }

        val target = LocalDateTime.of(date, time)
        val delay = Duration.between(now, target)
        return if (delay.isNegative) Duration.ofMinutes(1) else delay
    }

    private fun workName(habitId: String, evening: Boolean): String =
        if (evening) "habit-evening-$habitId" else "habit-reminder-$habitId"

    companion object {
        const val TAG = "habit-reminder"
        private const val EVENING_HOUR = 21
    }
}
