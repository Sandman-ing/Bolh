package eu.bolh.app

import androidx.room.withTransaction
import android.app.Application
import android.content.Context
import androidx.work.WorkManager
import eu.bolh.app.data.backup.BackupService
import eu.bolh.app.data.backup.PdfReport
import eu.bolh.app.data.db.BolhDatabase
import eu.bolh.app.data.notifications.BolhNotifications
import eu.bolh.app.data.notifications.ReminderAlarms
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.data.prefs.SettingsStore
import eu.bolh.app.data.rates.RatesClient
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.ui.theme.ThemeMode
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сборка зависимостей вручную, без библиотеки внедрения.
 *
 * Причина простая: единственный обработчик аннотаций в проекте — Room, и держать
 * его в одиночестве дешевле, чем согласовывать версии Kotlin, KSP и генератора
 * графа при каждом обновлении. Контейнер помещается на один экран.
 */
class BolhContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {

    val database: BolhDatabase = BolhDatabase.get(context)

    val habitRepository: HabitRepository = HabitRepository(database.habitDao(), clock)

    val goalRepository: GoalRepository = GoalRepository(database.goalDao(), clock)

    val financeRepository: FinanceRepository = FinanceRepository(
        dao = database.financeDao(),
        clock = clock,
        initialCurrency = CurrencyPrefs.get(context),
        ratesClient = RatesClient(),
        inTransaction = { block -> database.withTransaction { block() } },
    )

    val settings: SettingsStore = SettingsStore(context.applicationContext)

    val backupService: BackupService = BackupService(database, context.applicationContext)

    val pdfReport: PdfReport = PdfReport(
        context = context.applicationContext,
        habits = habitRepository,
        goals = goalRepository,
        finance = financeRepository,
    )

    /** Будильники системы для напоминаний о привычках и платежах. */
    val reminders: ReminderAlarms = ReminderAlarms(context.applicationContext, clock)
}

class BolhApplication : Application() {

    lateinit var container: BolhContainer
        private set

    /** Живёт столько же, сколько процесс: следит за напоминаниями всё это время. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = BolhContainer(this)
        ThemeMode.load(this)
        BolhNotifications.ensureChannels(this)
        syncReminders()
        dropLegacyWork()
        // Курсы раз в полсуток; без сети молча остаются последние известные.
        scope.launch { container.financeRepository.refreshRatesIfStale() }
    }

    /**
     * Все напоминания заново: после перезагрузки, смены времени, выдачи
     * разрешения на точное время или восстановления данных из копии.
     */
    suspend fun rescheduleReminders() {
        container.habitRepository.allHabits().forEach { container.reminders.scheduleHabit(it) }
        container.financeRepository.allRecurring().forEach { container.reminders.schedulePayment(it) }
    }

    /** Есть ли что напоминать: привычки со временем или регулярные платежи. */
    suspend fun hasReminders(): Boolean = withContext(Dispatchers.IO) {
        container.habitRepository.allHabits().any { it.reminderTimes.isNotEmpty() } ||
            container.financeRepository.allRecurring().any { it.isActive }
    }

    /**
     * Напоминания следуют за данными, а не за моментом сохранения.
     *
     * Стоило записать регулярный платёж по уведомлению, и дата списания
     * уезжала на следующий месяц: напоминание должно уехать вместе с ней.
     * То же после восстановления из копии. Здесь оба списка слушаются
     * целиком, и любая правка данных сразу доходит до будильников (§5.6, §6.5).
     */
    private fun syncReminders() {
        scope.launch {
            var known = emptyMap<String, RecurringPayment>()
            container.financeRepository.observeRecurring().collect { payments ->
                val byId = payments.associateBy { it.id }
                // Перезаводим только изменившееся: ставить заново всё на
                // каждое изменение таблицы — лишняя работа.
                byId.values
                    .filter { known[it.id] != it }
                    .forEach { container.reminders.schedulePayment(it) }
                known.keys
                    .filterNot { it in byId }
                    .forEach { container.reminders.cancelPayment(it) }
                known = byId
            }
        }

        scope.launch {
            var known = emptyMap<String, Habit>()
            container.habitRepository.observeAllHabits().collect { habits ->
                val byId = habits.associateBy { it.id }
                byId.values
                    .filter { known[it.id] != it }
                    .forEach { container.reminders.scheduleHabit(it) }
                known.keys
                    .filterNot { it in byId }
                    .forEach {
                        container.reminders.cancelHabit(it)
                        BolhNotifications.cancel(this@BolhApplication, it)
                    }
                known = byId
            }
        }
    }

    /**
     * Прежние версии ставили напоминания заданиями WorkManager. Теперь это
     * будильники, и старые задания снимаются один раз, чтобы не прийти дважды.
     */
    private fun dropLegacyWork() {
        val prefs = getSharedPreferences(PREFS_MIGRATIONS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_WORK, false)) return
        scope.launch {
            runCatching {
                WorkManager.getInstance(this@BolhApplication).apply {
                    cancelAllWorkByTag(LEGACY_HABIT_TAG)
                    cancelAllWorkByTag(LEGACY_PAYMENT_TAG)
                }
                prefs.edit().putBoolean(KEY_LEGACY_WORK, true).apply()
            }
        }
    }

    private companion object {
        const val PREFS_MIGRATIONS = "migrations"
        const val KEY_LEGACY_WORK = "legacy_work_dropped"
        const val LEGACY_HABIT_TAG = "habit-reminder"
        const val LEGACY_PAYMENT_TAG = "recurring-reminder"
    }
}
