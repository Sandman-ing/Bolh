package eu.bolh.app.domain.model

import java.time.LocalDate

enum class TransactionType { Expense, Income, Transfer }

enum class CategoryKind { Expense, Income }

/** Тип счёта. Влияет только на значок и порядок, никакой особой логики за ним нет. */
enum class AccountType { Cash, Card, Bank, Savings }

data class Account(
    val id: String,
    val name: String,
    val icon: String,
    val currencyCode: String,
    val initialBalanceMinor: Long = 0L,
    val type: AccountType = AccountType.Card,
    val sortOrder: Int = 0,
)

data class Category(
    val id: String,
    val name: String,
    val nameKey: String? = null,
    val icon: String,
    val colorArgb: Int,
    val kind: CategoryKind,
    val isSystem: Boolean = false,
    val parentId: String? = null,
    val goalId: String? = null,
    val sortOrder: Int = 0,
)

data class Transaction(
    val id: String,
    val accountId: String,
    val toAccountId: String? = null,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val rateToBase: Double? = null,
    val date: LocalDate,
    val categoryId: String? = null,
    val note: String? = null,
    val goalId: String? = null,
    val recurringId: String? = null,
)

data class SavingsJar(
    val id: String,
    val goalId: String,
    val mode: JarMode,
    val accountId: String? = null,
    val targetMinor: Long,
    val currencyCode: String,
    val plannedMonthlyMinor: Long = 0L,
)

/** Периодичность регулярного платежа. Хранится строкой формата RRULE. */
enum class RecurrenceUnit { Day, Week, Month, Year }

data class Recurrence(val unit: RecurrenceUnit, val interval: Int = 1) {

    fun next(from: LocalDate): LocalDate = when (unit) {
        RecurrenceUnit.Day -> from.plusDays(interval.toLong())
        RecurrenceUnit.Week -> from.plusWeeks(interval.toLong())
        RecurrenceUnit.Month -> from.plusMonths(interval.toLong())
        RecurrenceUnit.Year -> from.plusYears(interval.toLong())
    }

    fun toRRule(): String {
        val freq = when (unit) {
            RecurrenceUnit.Day -> "DAILY"
            RecurrenceUnit.Week -> "WEEKLY"
            RecurrenceUnit.Month -> "MONTHLY"
            RecurrenceUnit.Year -> "YEARLY"
        }
        return "FREQ=$freq;INTERVAL=$interval"
    }

    companion object {
        /**
         * Разбор упрощённый: из всего RFC 5545 приложению нужны период и интервал.
         * Строка хранится в стандартном формате, чтобы позже расшириться без миграции.
         */
        fun parse(rrule: String): Recurrence {
            val parts = rrule.split(';').mapNotNull { part ->
                val pair = part.split('=')
                if (pair.size == 2) pair[0].uppercase() to pair[1].uppercase() else null
            }.toMap()

            val unit = when (parts["FREQ"]) {
                "DAILY" -> RecurrenceUnit.Day
                "WEEKLY" -> RecurrenceUnit.Week
                "YEARLY" -> RecurrenceUnit.Year
                else -> RecurrenceUnit.Month
            }
            val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            return Recurrence(unit, interval)
        }
    }
}

data class RecurringPayment(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: String,
    val categoryId: String? = null,
    val recurrence: Recurrence,
    val nextDate: LocalDate,
    val notifyDaysBefore: Int = 1,
    val isActive: Boolean = true,
)
