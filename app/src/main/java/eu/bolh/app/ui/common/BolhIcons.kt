package eu.bolh.app.ui.common

import androidx.annotation.DrawableRes
import eu.bolh.app.R

/**
 * Значки хранятся в базе строковым ключом, а не номером ресурса: номера меняются
 * при каждой пересборке, и экспорт данных стал бы нечитаемым уже на второй версии.
 *
 * Набор — Material Symbols (Apache 2.0), приведённые к одному размеру и толщине.
 */
object BolhIcons {

    private val byKey: Map<String, Int> = mapOf(
        "arrow_back" to R.drawable.ic_arrow_back,
        "bedtime" to R.drawable.ic_bedtime,
        "brush" to R.drawable.ic_brush,
        "celebration" to R.drawable.ic_celebration,
        "check" to R.drawable.ic_check,
        "checkroom" to R.drawable.ic_checkroom,
        "chevron_left" to R.drawable.ic_chevron_left,
        "chevron_right" to R.drawable.ic_chevron_right,
        "cleaning_services" to R.drawable.ic_cleaning_services,
        "close" to R.drawable.ic_close,
        "code" to R.drawable.ic_code,
        "cookie" to R.drawable.ic_cookie,
        "credit_card" to R.drawable.ic_credit_card,
        "directions_car" to R.drawable.ic_directions_car,
        "directions_run" to R.drawable.ic_directions_run,
        "directions_walk" to R.drawable.ic_directions_walk,
        "download" to R.drawable.ic_download,
        "edit_note" to R.drawable.ic_edit_note,
        "fastfood" to R.drawable.ic_fastfood,
        "favorite" to R.drawable.ic_favorite,
        "fitness_center" to R.drawable.ic_fitness_center,
        "flag" to R.drawable.ic_flag,
        "flight" to R.drawable.ic_flight,
        "home" to R.drawable.ic_home,
        "local_bar" to R.drawable.ic_local_bar,
        "local_hospital" to R.drawable.ic_local_hospital,
        "medication" to R.drawable.ic_medication,
        "menu_book" to R.drawable.ic_menu_book,
        "music_note" to R.drawable.ic_music_note,
        "payments" to R.drawable.ic_payments,
        "pedal_bike" to R.drawable.ic_pedal_bike,
        "pets" to R.drawable.ic_pets,
        "phone_iphone" to R.drawable.ic_phone_iphone,
        "plus" to R.drawable.ic_plus,
        "public" to R.drawable.ic_public,
        "receipt_long" to R.drawable.ic_receipt_long,
        "restaurant" to R.drawable.ic_restaurant,
        "savings" to R.drawable.ic_savings,
        "school" to R.drawable.ic_school,
        "self_improvement" to R.drawable.ic_self_improvement,
        "settings" to R.drawable.ic_settings,
        "shopping_cart" to R.drawable.ic_shopping_cart,
        "smartphone" to R.drawable.ic_smartphone,
        "smoking_rooms" to R.drawable.ic_smoking_rooms,
        "sports_esports" to R.drawable.ic_sports_esports,
        "task" to R.drawable.ic_task,
        "translate" to R.drawable.ic_translate,
        "trending_up" to R.drawable.ic_trending_up,
        "upload_file" to R.drawable.ic_upload_file,
        "water_drop" to R.drawable.ic_water_drop,
        "wb_sunny" to R.drawable.ic_wb_sunny,
        "work" to R.drawable.ic_work,
    )

    /** Значок по ключу. Неизвестный ключ не роняет экран, а показывает флаг. */
    @DrawableRes
    fun resFor(key: String?): Int = byKey[key] ?: R.drawable.ic_flag

    fun has(key: String?): Boolean = key != null && key in byKey

    val habitGood: List<String> = listOf(
        "wb_sunny",
        "fitness_center",
        "directions_run",
        "menu_book",
        "water_drop",
        "translate",
        "self_improvement",
        "directions_walk",
        "medication",
        "bedtime",
        "edit_note",
        "cleaning_services",
        "music_note",
        "code",
        "brush",
        "pedal_bike",
    )

    val habitBad: List<String> = listOf(
        "smoking_rooms",
        "local_bar",
        "fastfood",
        "cookie",
        "smartphone",
    )

    val goal: List<String> = listOf(
        "flag",
        "home",
        "school",
        "savings",
        "favorite",
        "public",
        "trending_up",
        "celebration",
        "fitness_center",
        "translate",
        "work",
        "flight",
    )

    /** Значки счёта: карта, наличные, копилка — а не тележка и вилка. */
    val account: List<String> = listOf(
        "credit_card",
        "payments",
        "savings",
        "phone_iphone",
        "work",
        "home",
    )

    val finance: List<String> = listOf(
        "shopping_cart",
        "restaurant",
        "home",
        "directions_car",
        "local_hospital",
        "checkroom",
        "sports_esports",
        "receipt_long",
        "flight",
        "pets",
        "school",
        "work",
        "credit_card",
        "payments",
        "savings",
        "phone_iphone",
    )

}
