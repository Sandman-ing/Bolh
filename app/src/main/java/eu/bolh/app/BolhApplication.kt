package eu.bolh.app

import android.app.Application
import android.content.Context
import eu.bolh.app.data.backup.BackupService
import eu.bolh.app.data.backup.PdfReport
import eu.bolh.app.data.db.BolhDatabase
import eu.bolh.app.data.notifications.BolhNotifications
import eu.bolh.app.data.notifications.HabitReminderScheduler
import eu.bolh.app.data.notifications.RecurringReminderScheduler
import eu.bolh.app.data.prefs.SettingsStore
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.RecurringPayment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import eu.bolh.app.ui.theme.ThemeMode
import java.time.Clock

/**
 * Сборка зависимостей вручную, без библиотеки внедрения.
 *
 * Причина простая: единственный обработчик аннотаций в проекте — Room, и держать
 * его в одиночестве дешевле, чем согласовывать версии Kotlin, KSP и генератора
 * графа при каждом обновлении. Контейнер помещается на один экран.
 */
class BolhContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {

    private val database: BolhDatabase = BolhDatabase.get(context)

    val habitRepository: HabitRepository = HabitRepository(database.habitDao(), clock)

    val goalRepository: GoalRepository = GoalRepository(database.goalDao(), clock)

    val financeRepository: FinanceRepository = FinanceRepository(
        dao = database.financeDao(),
        clock = clock,
        initialCurrency = CurrencyPrefs.get(context),
    )

    val settings: SettingsStore = SettingsStore(context.applicationContext)

    val backupService: BackupService = BackupService(database)

    val pdfReport: PdfReport = PdfReport(
        context = context.applicationContext,
        habits = habitRepository,
        goals = goalRepository,
        finance = financeRepository,
    )

    val reminderScheduler: HabitReminderScheduler = HabitReminderScheduler(context, clock)

    val recurringScheduler: RecurringReminderScheduler = RecurringReminderScheduler(context, clock)
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
        BolhNotifications.ensureChannel(this)
        syncReminders()
    }

    /**
     * Напоминания следуют за данными, а не за моментом сохранения.
     *
     * Раньше они заводились только в редакторе. Стоило записать регулярный
     * платёж по уведомлению — дата списания уезжала на следующий месяц, а
     * нового напоминания не появлялось: платёж молча переставал напоминать.
     * То же после восстановления из копии — привычки возвращались, будильники
     * нет. Здесь оба списка слушаются целиком, и любая правка данных сразу
     * доходит до планировщика (§5.6, §6.5).
     */
    private fun syncReminders() {
        scope.launch {
            var known = emptyMap<String, RecurringPayment>()
            container.financeRepository.observeRecurring().collect { payments ->
                val byId = payments.associateBy { it.id }
                // Перезаводим только изменившееся: перепланировать всё на
                // каждое изменение таблицы — лишняя работа WorkManager.
                byId.values
                    .filter { known[it.id] != it }
                    .forEach { container.recurringScheduler.schedule(it) }
                known.keys
                    .filterNot { it in byId }
                    .forEach { container.recurringScheduler.cancel(it) }
                known = byId
            }
        }

        scope.launch {
            var known = emptyMap<String, Habit>()
            container.habitRepository.observeAllHabits().collect { habits ->
                val byId = habits.associateBy { it.id }
                byId.values
                    .filter { known[it.id] != it }
                    .forEach { container.reminderScheduler.schedule(it) }
                known.keys
                    .filterNot { it in byId }
                    .forEach { container.reminderScheduler.cancel(it) }
                known = byId
            }
        }
    }
}
