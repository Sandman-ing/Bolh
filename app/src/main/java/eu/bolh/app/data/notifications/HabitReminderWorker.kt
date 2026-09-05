package eu.bolh.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.bolh.app.BolhApplication
import eu.bolh.app.domain.model.ScheduleType

/**
 * Показывает напоминание и тут же назначает следующее.
 *
 * Если день уже отмечен, уведомление не приходит: напоминать о сделанном —
 * это шум, после которого уведомления выключают целиком.
 */
class HabitReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getString(KEY_HABIT_ID) ?: return Result.failure()
        val evening = inputData.getBoolean(KEY_EVENING, false)

        val app = applicationContext as? BolhApplication ?: return Result.retry()
        val repository = app.container.habitRepository
        val habit = repository.findHabit(habitId) ?: return Result.success()

        val today = repository.today()
        val plannedToday = habit.schedule.type != ScheduleType.Weekdays ||
            habit.schedule.plannedOn(today)
        val alreadyMarked = repository.isMarked(habitId, today)

        if (plannedToday && !alreadyMarked) {
            BolhNotifications.showHabitReminder(
                context = applicationContext,
                habitId = habit.id,
                habitName = habit.name,
                minimumLabel = habit.minimumLabel,
                mode = habit.mode,
                evening = evening,
            )
        }

        // Следующее напоминание назначается здесь же: периодических работ с точным
        // временем суток WorkManager не даёт, а цепочка одноразовых не расползается.
        app.container.reminderScheduler.schedule(habit)
        return Result.success()
    }

    companion object {
        const val KEY_HABIT_ID = "habitId"
        const val KEY_EVENING = "evening"
    }
}
