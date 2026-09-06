package eu.bolh.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.TypeConverters
import eu.bolh.app.data.db.dao.FinanceDao
import eu.bolh.app.data.db.dao.GoalDao
import eu.bolh.app.data.db.dao.HabitDao
import eu.bolh.app.data.db.entity.AccountEntity
import eu.bolh.app.data.db.entity.CategoryEntity
import eu.bolh.app.data.db.entity.CurrencyRateEntity
import eu.bolh.app.data.db.entity.DeadlineRecordEntity
import eu.bolh.app.data.db.entity.GoalEntity
import eu.bolh.app.data.db.entity.HabitEntity
import eu.bolh.app.data.db.entity.HabitEntryEntity
import eu.bolh.app.data.db.entity.HabitPauseEntity
import eu.bolh.app.data.db.entity.JarContributionEntity
import eu.bolh.app.data.db.entity.RecurringPaymentEntity
import eu.bolh.app.data.db.entity.SavingsJarEntity
import eu.bolh.app.data.db.entity.StageEntity
import eu.bolh.app.data.db.entity.TaskEntity
import eu.bolh.app.data.db.entity.TransactionEntity

/**
 * Данные лежат только на устройстве: ни аккаунтов, ни серверов (§8.1).
 * Следствие — потеря телефона означает потерю всего, поэтому экспорт
 * и резервная копия входят в первую версию, а не откладываются.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringPaymentEntity::class,
        SavingsJarEntity::class,
        JarContributionEntity::class,
        CurrencyRateEntity::class,
        GoalEntity::class,
        StageEntity::class,
        TaskEntity::class,
        DeadlineRecordEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        HabitPauseEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BolhDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao

    abstract fun goalDao(): GoalDao

    abstract fun financeDao(): FinanceDao

    companion object {
        private const val NAME = "bolh.db"

        /**
         * Месячный лимит по категории (§6.7).
         *
         * Именно миграция, а не пересоздание базы: у людей уже есть записанные
         * операции, и терять их ради одной колонки недопустимо.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE categories ADD COLUMN plannedMinor INTEGER")
            }
        }

        @Volatile
        private var instance: BolhDatabase? = null

        fun get(context: Context): BolhDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): BolhDatabase =
            Room.databaseBuilder(context.applicationContext, BolhDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
