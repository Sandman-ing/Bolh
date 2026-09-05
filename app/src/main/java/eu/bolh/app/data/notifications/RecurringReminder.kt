package eu.bolh.app.data.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.bolh.app.BolhApplication
import eu.bolh.app.MainActivity
import eu.bolh.app.R
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.ui.common.Money
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Уведомление о регулярном платеже приходит за день до списания, и в нём кнопка
 * «записать» (§6.5). Ничего не появляется в истории без ведома человека —
 * поэтому операция создаётся только по нажатию, а не по расписанию.
 */
class RecurringReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun schedule(payment: RecurringPayment) {
        cancel(payment.id)
        if (!payment.isActive) return

        val fireDate = payment.nextDate.minusDays(payment.notifyDaysBefore.toLong())
        val target = LocalDateTime.of(fireDate, LocalTime.of(NOTIFY_HOUR, 0))
        val delay = Duration.between(LocalDateTime.now(clock), target)

        val request = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
            .setInitialDelay(if (delay.isNegative) Duration.ofMinutes(1) else delay)
            .setInputData(
                Data.Builder().putString(RecurringReminderWorker.KEY_PAYMENT_ID, payment.id).build(),
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(payment.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(paymentId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(paymentId))
        NotificationManagerCompat.from(context).cancel(paymentId.hashCode())
    }

    private fun workName(paymentId: String) = "recurring-$paymentId"

    companion object {
        const val TAG = "recurring-reminder"
        private const val NOTIFY_HOUR = 11
    }
}

class RecurringReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val paymentId = inputData.getString(KEY_PAYMENT_ID) ?: return Result.failure()
        val app = applicationContext as? BolhApplication ?: return Result.retry()

        val payment = app.container.financeRepository.findRecurring(paymentId)
            ?: return Result.success()

        BolhNotifications.ensureChannel(applicationContext)
        if (BolhNotifications.allowed(applicationContext)) {
            val open = PendingIntent.getActivity(
                applicationContext,
                paymentId.hashCode(),
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val record = PendingIntent.getBroadcast(
                applicationContext,
                paymentId.hashCode() + 1,
                RecurringRecordReceiver.intent(applicationContext, paymentId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(
                applicationContext,
                BolhNotifications.CHANNEL_HABITS,
            )
                .setSmallIcon(R.drawable.ic_tab_finance)
                .setContentTitle(payment.name)
                .setContentText(
                    applicationContext.getString(
                        R.string.notif_recurring_text,
                        Money.format(payment.amountMinor, payment.currencyCode),
                    ),
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, applicationContext.getString(R.string.finance_record), record)
                .build()

            try {
                NotificationManagerCompat.from(applicationContext)
                    .notify(paymentId.hashCode(), notification)
            } catch (ignored: SecurityException) {
                // Разрешение отозвали между проверкой и показом — тогда просто молчим.
            }
        }

        // Даже если человек не нажмёт «записать», следующее напоминание должно прийти.
        app.container.recurringScheduler.schedule(payment)
        return Result.success()
    }

    companion object {
        const val KEY_PAYMENT_ID = "paymentId"
    }
}

class RecurringRecordReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID) ?: return
        val app = context.applicationContext as? BolhApplication ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.financeRepository.recordRecurring(paymentId)
                NotificationManagerCompat.from(context).cancel(paymentId.hashCode())
                app.container.financeRepository.findRecurring(paymentId)
                    ?.let { app.container.recurringScheduler.schedule(it) }
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
