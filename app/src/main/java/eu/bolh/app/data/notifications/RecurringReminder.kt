package eu.bolh.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.bolh.app.BolhApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * «Записать» из уведомления о платеже (§6.5): операция создаётся только по
 * нажатию, и тогда же платёж уезжает на следующий период.
 */
class RecurringRecordReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID) ?: return
        val app = context.applicationContext as? BolhApplication ?: return
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val finance = app.container.financeRepository
                finance.recordRecurring(paymentId)
                BolhNotifications.cancel(context, paymentId)
                finance.findRecurring(paymentId)?.let { app.container.reminders.schedulePayment(it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_RECORD = "eu.bolh.app.action.RECORD_RECURRING"
        private const val EXTRA_PAYMENT_ID = "paymentId"

        fun intent(context: Context, paymentId: String): Intent =
            Intent(context, RecurringRecordReceiver::class.java).apply {
                action = ACTION_RECORD
                putExtra(EXTRA_PAYMENT_ID, paymentId)
            }
    }
}

/**
 * Задание прежних версий. Напоминания о платежах теперь на будильниках
 * системы; класс остался, чтобы WorkManager мог спокойно снять старые задания.
 */
class RecurringReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}
