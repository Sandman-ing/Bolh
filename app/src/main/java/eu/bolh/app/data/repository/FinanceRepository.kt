package eu.bolh.app.data.repository

import eu.bolh.app.data.db.dao.FinanceDao
import eu.bolh.app.data.db.entity.AccountEntity
import eu.bolh.app.data.db.entity.CategoryEntity
import eu.bolh.app.data.db.entity.JarContributionEntity
import eu.bolh.app.data.db.entity.RecurringPaymentEntity
import eu.bolh.app.data.db.entity.SavingsJarEntity
import eu.bolh.app.data.db.entity.TransactionEntity
import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.AccountType
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.CategoryKind
import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.JarMode
import eu.bolh.app.domain.model.Recurrence
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.domain.model.SavingsJar
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import eu.bolh.app.domain.templates.SystemCategories
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Деньги (§6). Первая версия — только ручной ввод: подключение банка требует
 * юрлица, договора и другого уровня требований к безопасности (§6.2).
 */
class FinanceRepository(
    private val dao: FinanceDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    initialCurrency: String = "EUR",
) {

    /**
     * Валюта приложения потоком.
     *
     * Смена валюты не должна дёргать экран пересозданием: суммы перерисуются
     * сами, как перерисовываются при любом другом изменении данных.
     */
    val currency = MutableStateFlow(initialCurrency)

    var baseCurrency: String
        get() = currency.value
        set(value) {
            currency.value = value
        }

    fun today(): LocalDate = LocalDate.now(clock)

    // --- Счета ------------------------------------------------------------

    fun observeAccounts(): Flow<List<Account>> =
        dao.observeAccounts().map { rows -> rows.map { it.toDomain() } }

    suspend fun createAccount(
        name: String,
        icon: String,
        type: AccountType,
        initialBalanceMinor: Long,
        currencyCode: String = baseCurrency,
    ): String {
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        dao.upsertAccount(
            AccountEntity(
                id = id,
                name = name.trim(),
                icon = icon,
                currencyCode = currencyCode,
                initialBalanceMinor = initialBalanceMinor,
                type = type.name,
                sortOrder = dao.accounts().size,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    /** Переименовать счёт и подвинуть начальный остаток. */
    suspend fun updateAccount(
        accountId: String,
        name: String,
        initialBalanceMinor: Long,
        icon: String? = null,
        type: AccountType? = null,
        currencyCode: String? = null,
    ) {
        val existing = dao.accountById(accountId) ?: return
        dao.upsertAccount(
            existing.copy(
                name = name,
                icon = icon ?: existing.icon,
                type = type?.name ?: existing.type,
                currencyCode = currencyCode ?: existing.currencyCode,
                initialBalanceMinor = initialBalanceMinor,
                updatedAt = clock.millis(),
            ),
        )
    }

    suspend fun deleteAccount(id: String) {
        dao.softDeleteAccount(id, clock.millis())
    }

    // --- Категории --------------------------------------------------------

    fun observeCategories(): Flow<List<Category>> =
        dao.observeCategories().map { rows -> rows.map { it.toDomain() } }

    /** Готовый набор заводится один раз, при первом заходе в финансы. */
    suspend fun ensureSystemCategories() {
        if (dao.categoryCount() > 0) return
        val now = clock.millis()
        dao.upsertCategories(
            SystemCategories.all.mapIndexed { index, category ->
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = category.key,
                    nameKey = category.key,
                    icon = category.icon,
                    colorArgb = category.colorArgb,
                    kind = category.kind.name,
                    isSystem = true,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }

    /**
     * Цвета готовых категорий подтягиваются при каждом заходе в финансы.
     *
     * Палитра — часть приложения, а не пользовательские данные: если она
     * поменялась, старые записи должны догнать её, иначе у человека,
     * поставившего приложение раньше, кольцо навсегда останется одноцветным.
     * Свои категории не трогаются — у них isSystem = 0.
     */
    suspend fun refreshSystemCategoryColors() {
        val now = clock.millis()
        SystemCategories.all.forEach { category ->
            dao.updateSystemCategoryColor(category.key, category.colorArgb, now)
        }
    }

    /**
     * Правка категории. Вид и признак «готовая» не меняются: перекрасить
     * готовую категорию можно, превратить расход в доход — нет, иначе
     * записанные операции задним числом сменят смысл.
     */
    suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        colorArgb: Int,
        plannedMinor: Long?,
    ) {
        val existing = dao.categoryById(id) ?: return
        dao.upsertCategory(
            existing.copy(
                name = name.trim().ifBlank { existing.name },
                icon = icon,
                colorArgb = colorArgb,
                plannedMinor = plannedMinor,
                // Своё название перебивает переводимое: иначе переименование
                // готовой категории не было бы видно.
                nameKey = if (name.isBlank()) existing.nameKey else null,
                updatedAt = clock.millis(),
            ),
        )
    }

    suspend fun deleteCategory(id: String) {
        dao.softDeleteCategory(id, clock.millis())
    }

    suspend fun createCategory(
        name: String,
        icon: String,
        colorArgb: Int,
        kind: CategoryKind,
        plannedMinor: Long? = null,
    ) {
        val now = clock.millis()
        dao.upsertCategory(
            CategoryEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                icon = icon,
                colorArgb = colorArgb,
                kind = kind.name,
                plannedMinor = plannedMinor,
                isSystem = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    // --- Операции ---------------------------------------------------------

    fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeTransactions().map { rows -> rows.map { it.toDomain() } }

    fun observeTransactionsBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>> =
        dao.observeTransactionsBetween(from, to).map { rows -> rows.map { it.toDomain() } }

    suspend fun transactionsBetween(from: LocalDate, to: LocalDate): List<Transaction> =
        dao.transactionsBetween(from, to).map { it.toDomain() }

    suspend fun record(transaction: Transaction) {
        val now = clock.millis()
        dao.upsertTransaction(transaction.toEntity(createdAt = now, updatedAt = now))
    }

    /**
     * Правка записанной операции.
     *
     * createdAt берётся у прежней записи: правка суммы не делает операцию
     * заведённой заново, иначе порядок в истории поехал бы.
     */
    suspend fun updateTransaction(transaction: Transaction) {
        val existing = dao.transactionById(transaction.id)
        dao.upsertTransaction(
            transaction.toEntity(
                createdAt = existing?.createdAt ?: clock.millis(),
                updatedAt = clock.millis(),
            ),
        )
    }

    suspend fun deleteTransaction(id: String) {
        dao.softDeleteTransaction(id, clock.millis())
    }

    // --- Копилки ----------------------------------------------------------

    fun observeJars(): Flow<List<SavingsJar>> =
        dao.observeJars().map { rows -> rows.map { it.toDomain() } }

    fun observeContributions(): Flow<Map<String, List<Contribution>>> =
        dao.observeContributions().map { rows ->
            rows.groupBy(
                keySelector = { it.jarId },
                valueTransform = {
                    Contribution(
                        date = it.date,
                        amountMinor = it.amountMinor,
                        costItemId = it.costItemId,
                    )
                },
            )
        }

    suspend fun createJar(
        goalId: String,
        targetMinor: Long,
        mode: JarMode = JarMode.Envelope,
        accountId: String? = null,
        plannedMonthlyMinor: Long = 0L,
    ): String {
        val now = clock.millis()
        val existing = dao.jarForGoal(goalId)
        val id = existing?.id ?: UUID.randomUUID().toString()
        dao.upsertJar(
            SavingsJarEntity(
                id = id,
                goalId = goalId,
                mode = mode,
                accountId = accountId,
                targetMinor = targetMinor,
                currencyCode = baseCurrency,
                plannedMonthlyMinor = plannedMonthlyMinor,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return id
    }

    /**
     * Пополнение копилки.
     *
     * [costItemId] говорит, какую статью сметы закрыл взнос. Оплаченное по
     * статье считается по этим записям, а не отдельным полем: поле разошлось
     * бы с историей при первой же отмене взноса (§4.5).
     */
    suspend fun addContribution(
        jarId: String,
        amountMinor: Long,
        date: LocalDate = today(),
        costItemId: String? = null,
    ) {
        val now = clock.millis()
        dao.upsertContribution(
            JarContributionEntity(
                id = UUID.randomUUID().toString(),
                jarId = jarId,
                amountMinor = amountMinor,
                date = date,
                costItemId = costItemId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Сумма цели меняется: жизнь дороже, чем казалась полгода назад. */
    suspend fun setJarTarget(jarId: String, targetMinor: Long) {
        if (targetMinor <= 0L) return
        dao.setJarTarget(jarId, targetMinor, clock.millis())
    }

    suspend fun deleteJar(jarId: String) {
        dao.softDeleteJar(jarId, clock.millis())
    }

    /** Промахнулся суммой — последнее пополнение снимается одним нажатием. */
    suspend fun undoLastContribution(jarId: String) {
        val last = dao.lastContribution(jarId) ?: return
        dao.softDeleteContribution(last.id, clock.millis())
    }

    suspend fun contributionsFor(jarId: String): List<Contribution> =
        dao.contributionsForJar(jarId).map {
            Contribution(it.date, it.amountMinor, it.costItemId)
        }

    // --- Регулярные платежи -----------------------------------------------

    fun observeRecurring(): Flow<List<RecurringPayment>> =
        dao.observeRecurring().map { rows -> rows.map { it.toDomain() } }

    suspend fun findRecurring(id: String): RecurringPayment? =
        dao.recurringById(id)?.toDomain()

    suspend fun allRecurring(): List<RecurringPayment> =
        dao.allRecurring().map { it.toDomain() }

    suspend fun createRecurring(payment: RecurringPayment) {
        val now = clock.millis()
        dao.upsertRecurring(payment.toEntity(createdAt = now, updatedAt = now))
    }

    /** Правка платежа: дата заведения остаётся прежней, меняется только запись. */
    suspend fun updateRecurring(payment: RecurringPayment) {
        val existing = dao.recurringById(payment.id) ?: return
        dao.upsertRecurring(
            payment.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()),
        )
    }

    /**
     * «Записать» из уведомления (§6.5): операция создаётся только по нажатию,
     * и тогда же платёж уезжает на следующий период. Ничего не появляется
     * в истории без ведома человека.
     */
    suspend fun recordRecurring(paymentId: String, date: LocalDate = today()) {
        val payment = dao.recurringById(paymentId) ?: return
        val now = clock.millis()
        dao.upsertTransaction(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = payment.accountId,
                type = TransactionType.Expense.name,
                amountMinor = payment.amountMinor,
                currencyCode = payment.currencyCode,
                rateToBase = if (payment.currencyCode == baseCurrency) 1.0 else null,
                date = date,
                categoryId = payment.categoryId,
                recurringId = payment.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val next = Recurrence.parse(payment.rrule).next(payment.nextDate)
        dao.setRecurringNextDate(payment.id, next, now)
    }

    suspend fun skipRecurring(paymentId: String) {
        val payment = dao.recurringById(paymentId) ?: return
        val next = Recurrence.parse(payment.rrule).next(payment.nextDate)
        dao.setRecurringNextDate(payment.id, next, clock.millis())
    }

    suspend fun deleteRecurring(id: String) {
        dao.softDeleteRecurring(id, clock.millis())
    }
}

// --- Преобразования -------------------------------------------------------

private fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    icon = icon,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.Card),
    sortOrder = sortOrder,
)

private fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    nameKey = nameKey,
    icon = icon,
    colorArgb = colorArgb,
    kind = runCatching { CategoryKind.valueOf(kind) }.getOrDefault(CategoryKind.Expense),
    isSystem = isSystem,
    parentId = parentId,
    goalId = goalId,
    plannedMinor = plannedMinor,
    sortOrder = sortOrder,
)

private fun TransactionEntity.toDomain() = Transaction(
    id = id,
    accountId = accountId,
    toAccountId = toAccountId,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.Expense),
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    rateToBase = rateToBase,
    date = date,
    categoryId = categoryId,
    note = note,
    goalId = goalId,
    recurringId = recurringId,
)

private fun Transaction.toEntity(createdAt: Long, updatedAt: Long) = TransactionEntity(
    id = id,
    accountId = accountId,
    toAccountId = toAccountId,
    type = type.name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    rateToBase = rateToBase,
    date = date,
    categoryId = categoryId,
    note = note,
    goalId = goalId,
    recurringId = recurringId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun SavingsJarEntity.toDomain() = SavingsJar(
    id = id,
    goalId = goalId,
    mode = mode,
    accountId = accountId,
    targetMinor = targetMinor,
    currencyCode = currencyCode,
    plannedMonthlyMinor = plannedMonthlyMinor,
)

private fun RecurringPaymentEntity.toDomain() = RecurringPayment(
    id = id,
    name = name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    accountId = accountId,
    categoryId = categoryId,
    recurrence = Recurrence.parse(rrule),
    nextDate = nextDate,
    notifyDaysBefore = notifyDaysBefore,
    isActive = isActive,
)

private fun RecurringPayment.toEntity(createdAt: Long, updatedAt: Long) = RecurringPaymentEntity(
    id = id,
    name = name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    accountId = accountId,
    categoryId = categoryId,
    rrule = recurrence.toRRule(),
    nextDate = nextDate,
    notifyDaysBefore = notifyDaysBefore,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
