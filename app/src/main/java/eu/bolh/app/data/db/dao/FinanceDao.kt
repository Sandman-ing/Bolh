package eu.bolh.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import eu.bolh.app.data.db.entity.AccountEntity
import eu.bolh.app.data.db.entity.CategoryEntity
import eu.bolh.app.data.db.entity.CurrencyRateEntity
import eu.bolh.app.data.db.entity.JarContributionEntity
import eu.bolh.app.data.db.entity.RecurringPaymentEntity
import eu.bolh.app.data.db.entity.SavingsJarEntity
import eu.bolh.app.data.db.entity.TransactionEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // --- Счета ------------------------------------------------------------

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id AND deletedAt IS NULL")
    suspend fun accountById(id: String): AccountEntity?

    @Upsert
    suspend fun upsertAccount(account: AccountEntity)

    @Query("UPDATE accounts SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteAccount(id: String, now: Long)

    // --- Категории --------------------------------------------------------

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun categoryById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query(
        "UPDATE categories SET colorArgb = :color, updatedAt = :now " +
            "WHERE nameKey = :key AND isSystem = 1 AND colorArgb != :color",
    )
    suspend fun updateSystemCategoryColor(key: String, color: Int, now: Long)

    @Query("UPDATE categories SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteCategory(id: String, now: Long)

    // --- Операции ---------------------------------------------------------

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY date DESC, createdAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL AND date BETWEEN :from AND :to
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeTransactionsBetween(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL AND date BETWEEN :from AND :to
        """,
    )
    suspend fun transactionsBetween(from: LocalDate, to: LocalDate): List<TransactionEntity>

    @Upsert
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun transactionById(id: String): TransactionEntity?

    @Query("UPDATE transactions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTransaction(id: String, now: Long)

    // --- Копилки ----------------------------------------------------------

    @Query("SELECT * FROM savings_jars WHERE deletedAt IS NULL")
    fun observeJars(): Flow<List<SavingsJarEntity>>

    @Query("SELECT * FROM savings_jars WHERE goalId = :goalId AND deletedAt IS NULL")
    suspend fun jarForGoal(goalId: String): SavingsJarEntity?

    @Upsert
    suspend fun upsertJar(jar: SavingsJarEntity)

    @Query("UPDATE savings_jars SET targetMinor = :target, updatedAt = :now WHERE id = :id")
    suspend fun setJarTarget(id: String, target: Long, now: Long)

    @Query("UPDATE savings_jars SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteJar(id: String, now: Long)

    @Query("SELECT * FROM jar_contributions WHERE deletedAt IS NULL ORDER BY date")
    fun observeContributions(): Flow<List<JarContributionEntity>>

    @Query("SELECT * FROM jar_contributions WHERE jarId = :jarId AND deletedAt IS NULL ORDER BY date")
    suspend fun contributionsForJar(jarId: String): List<JarContributionEntity>

    @Query(
        """
        SELECT * FROM jar_contributions
        WHERE jarId = :jarId AND deletedAt IS NULL
        ORDER BY date DESC, createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun lastContribution(jarId: String): JarContributionEntity?

    @Upsert
    suspend fun upsertContribution(contribution: JarContributionEntity)

    @Query("UPDATE jar_contributions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteContribution(id: String, now: Long)

    // --- Регулярные платежи -----------------------------------------------

    @Query("SELECT * FROM recurring_payments WHERE deletedAt IS NULL AND isActive = 1 ORDER BY nextDate")
    fun observeRecurring(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments WHERE deletedAt IS NULL")
    suspend fun allRecurring(): List<RecurringPaymentEntity>

    @Query("SELECT * FROM recurring_payments WHERE id = :id AND deletedAt IS NULL")
    suspend fun recurringById(id: String): RecurringPaymentEntity?

    @Upsert
    suspend fun upsertRecurring(payment: RecurringPaymentEntity)

    @Query("UPDATE recurring_payments SET nextDate = :nextDate, updatedAt = :now WHERE id = :id")
    suspend fun setRecurringNextDate(id: String, nextDate: LocalDate, now: Long)

    @Query("UPDATE recurring_payments SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteRecurring(id: String, now: Long)

    // --- Курсы валют ------------------------------------------------------

    @Upsert
    suspend fun upsertRates(rates: List<CurrencyRateEntity>)

    /** Самый свежий известный набор курсов: без сети живём на нём (§6.3). */
    @Query("SELECT * FROM currency_rates WHERE date = (SELECT MAX(date) FROM currency_rates)")
    fun observeLatestRates(): Flow<List<CurrencyRateEntity>>

    @Query("SELECT MAX(fetchedAt) FROM currency_rates")
    suspend fun lastRatesFetch(): Long?

    @Query("DELETE FROM currency_rates WHERE date < :before")
    suspend fun pruneRates(before: LocalDate)
}
