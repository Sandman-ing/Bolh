package eu.bolh.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Задание прежних версий. Напоминания о привычках теперь на будильниках
 * системы; класс остался, чтобы WorkManager мог спокойно снять старые задания.
 */
class HabitReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}
