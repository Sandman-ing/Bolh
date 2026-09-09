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

    override fun onCreate() {
        super.onCreate()
        container = BolhContainer(this)
        BolhNotifications.ensureChannel(this)
    }
}
