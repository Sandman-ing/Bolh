package eu.bolh.app.domain.templates

import androidx.annotation.StringRes
import eu.bolh.app.R
import eu.bolh.app.domain.model.CategoryKind

/**
 * Готовый набор категорий (§6.4).
 *
 * Имя системной категории хранится ключом, а не текстом: иначе при смене языка
 * приложения половина экрана расходов осталась бы на прежнем. Переименованная
 * пользователем категория ключ теряет и живёт как обычный текст.
 *
 * Цвета — оттенки синего (§6.8). Красный не раздаётся по списку: он значит
 * «категория выросла аномально» и появляется только по расчёту.
 */
data class SystemCategory(
    val key: String,
    @param:StringRes val nameRes: Int,
    val icon: String,
    val colorArgb: Int,
    val kind: CategoryKind,
)

object SystemCategories {

    val all: List<SystemCategory> = listOf(
        SystemCategory("groceries", R.string.cat_groceries, "shopping_cart", 0xFF85B7EB.toInt(), CategoryKind.Expense),
        SystemCategory("cafe", R.string.cat_cafe, "restaurant", 0xFF6FA8E0.toInt(), CategoryKind.Expense),
        SystemCategory("home", R.string.cat_home, "home", 0xFF5A9AD6.toInt(), CategoryKind.Expense),
        SystemCategory("transport", R.string.cat_transport, "directions_car", 0xFF4A8DCC.toInt(), CategoryKind.Expense),
        SystemCategory("health", R.string.cat_health, "local_hospital", 0xFF3E80C2.toInt(), CategoryKind.Expense),
        SystemCategory("clothes", R.string.cat_clothes, "checkroom", 0xFF3673B4.toInt(), CategoryKind.Expense),
        SystemCategory("fun", R.string.cat_fun, "sports_esports", 0xFF2F67A6.toInt(), CategoryKind.Expense),
        SystemCategory("bills", R.string.cat_bills, "receipt_long", 0xFF2A5C97.toInt(), CategoryKind.Expense),
        SystemCategory("travel", R.string.cat_travel, "flight", 0xFF7FA9CE.toInt(), CategoryKind.Expense),
        SystemCategory("pets", R.string.cat_pets, "pets", 0xFF6D97BC.toInt(), CategoryKind.Expense),
        SystemCategory("education", R.string.cat_education, "school", 0xFF5C86AA.toInt(), CategoryKind.Expense),
        SystemCategory("other", R.string.cat_other, "payments", 0xFF4C7599.toInt(), CategoryKind.Expense),

        SystemCategory("salary", R.string.cat_salary, "work", 0xFF97C459.toInt(), CategoryKind.Income),
        SystemCategory("gift", R.string.cat_gift, "celebration", 0xFF89B551.toInt(), CategoryKind.Income),
        SystemCategory("refund", R.string.cat_refund, "payments", 0xFF7BA649.toInt(), CategoryKind.Income),
        SystemCategory("other_income", R.string.cat_other_income, "savings", 0xFF6D9741.toInt(), CategoryKind.Income),
    )

    private val byKey: Map<String, SystemCategory> = all.associateBy { it.key }

    /** Обязательные траты: их убирает переключатель «без обязательных» (§6.8). */
    val obligatoryKeys: Set<String> = setOf("home", "bills")

    fun nameResFor(key: String?): Int? = key?.let { byKey[it]?.nameRes }

    fun find(key: String): SystemCategory? = byKey[key]
}
