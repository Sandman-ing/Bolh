package eu.bolh.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.bolh.app.domain.model.JarMode
import java.time.LocalDate

/** Все суммы хранятся в минорных единицах валюты: центы, а не дробные евро. */
@Entity(tableName = "accounts", indices = [Index("deletedAt")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val currencyCode: String,
    val initialBalanceMinor: Long = 0L,
    val type: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Категория трат или доходов.
 *
 * У системных категорий имя лежит ключом ресурса: иначе при смене языка
 * приложения половина экрана осталась бы на прежнем. Переименованная
 * пользователем категория ключ теряет и живёт как обычный текст.
 */
@Entity(
    tableName = "categories",
    indices = [Index("parentId"), Index("goalId"), Index("deletedAt")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String? = null,
    val icon: String,
    val colorArgb: Int,
    /** expense или income. */
    val kind: String,
    val isSystem: Boolean = false,
    val parentId: String? = null,
    /** Категория, помеченная как относящаяся к цели, — вход для цены цели в деньгах (§4.5). */
    val goalId: String? = null,
    /** Сколько человек собирается тратить здесь в месяц. Пусто — лимита нет (§6.7). */
    val plannedMinor: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Операция. Внешних ключей на счёт и категорию нет намеренно: удаление категории
 * не должно уносить с собой историю трат.
 *
 * Курс фиксируется на дату операции и больше никогда не меняется (§6.3).
 * Null в [rateToBase] означает «курс ещё не зафиксирован» — так бывает,
 * когда операция записана без сети; он досчитается при первом выходе в сеть.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
        Index("goalId"),
        Index("date"),
        Index("recurringId"),
        Index("deletedAt"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    /** Счёт-получатель перевода. У расхода и дохода пуст. */
    val toAccountId: String? = null,
    /** expense, income или transfer. */
    val type: String,
    val amountMinor: Long,
    val currencyCode: String,
    val rateToBase: Double? = null,
    val date: LocalDate,
    val categoryId: String? = null,
    val note: String? = null,
    val goalId: String? = null,
    val recurringId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Регулярный платёж. Уведомление приходит за день до списания, и в нём кнопка
 * «записать»: ничего не появляется в истории без ведома человека (§6.5).
 */
@Entity(
    tableName = "recurring_payments",
    indices = [Index("accountId"), Index("categoryId"), Index("nextDate"), Index("deletedAt")],
)
data class RecurringPaymentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: String,
    val categoryId: String? = null,
    /** Строка формата RRULE. Разбирается упрощённо: период и интервал. */
    val rrule: String,
    val nextDate: LocalDate,
    val notifyDaysBefore: Int = 1,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Копилка под цель (§6.6). Накопленное считается по истории пополнений. */
@Entity(
    tableName = "savings_jars",
    indices = [Index("goalId"), Index("accountId"), Index("deletedAt")],
)
data class SavingsJarEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val mode: JarMode = JarMode.Envelope,
    /** Счёт, на котором физически лежат деньги конверта. */
    val accountId: String? = null,
    val targetMinor: Long,
    val currencyCode: String,
    val plannedMonthlyMinor: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Пополнение копилки.
 *
 * Отдельная таблица нужна прогнозу: §4.3 требует прироста за 90 дней и минимум
 * трёх пополнений, а по одному полю «накоплено» ни того, ни другого не видно.
 */
@Entity(
    tableName = "jar_contributions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsJarEntity::class,
            parentColumns = ["id"],
            childColumns = ["jarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jarId", "date"), Index("transactionId"), Index("deletedAt")],
)
data class JarContributionEntity(
    @PrimaryKey val id: String,
    val jarId: String,
    val amountMinor: Long,
    val date: LocalDate,
    /** Операция, которой пополнение оформлено, если оно шло через счёт. */
    val transactionId: String? = null,
    /** Статья сметы, которую закрывает этот взнос. Пусто — взнос в общий котёл. */
    val costItemId: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Курс на дату. Источник — ежедневные данные ЕЦБ, кэшируются локально;
 * без сети используются последние известные (§6.3).
 */
@Entity(tableName = "currency_rates", primaryKeys = ["code", "date"])
data class CurrencyRateEntity(
    val code: String,
    val date: LocalDate,
    val rateToBase: Double,
    val fetchedAt: Long,
)
