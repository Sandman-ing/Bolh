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
import eu.bolh.app.data.db.entity.GoalCostItemEntity
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
        GoalCostItemEntity::class,
        StageEntity::class,
        TaskEntity::class,
        DeadlineRecordEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        HabitPauseEntity::class,
    ],
    version = 4,
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

        /**
         * Смета цели (§4.5): статьи расходов и привязка взноса к статье.
         *
         * Снова миграция, а не пересоздание: у людей уже есть цели, копилки и
         * история пополнений, и терять их ради двух новых полей недопустимо.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS goal_cost_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        goalId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        plannedMinor INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        FOREIGN KEY(goalId) REFERENCES goals(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_goal_cost_items_goalId " +
                        "ON goal_cost_items(goalId)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_goal_cost_items_deletedAt " +
                        "ON goal_cost_items(deletedAt)",
                )
                connection.execSQL(
                    "ALTER TABLE jar_contributions ADD COLUMN costItemId TEXT",
                )
            }
        }

        /**
         * Несколько отметок за день (§5.5): зубы утром и вечером — это два
         * раза, а не один. У всех прежних привычек по-прежнему один.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE habits ADD COLUMN timesPerDay INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        @Volatile
        private var instance: BolhDatabase? = null

        fun get(context: Context): BolhDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): BolhDatabase =
            Room.databaseBuilder(context.applicationContext, BolhDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
