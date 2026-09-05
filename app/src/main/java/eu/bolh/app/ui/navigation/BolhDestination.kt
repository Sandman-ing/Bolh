package eu.bolh.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.bolh.app.R
import eu.bolh.app.ui.theme.Section

/**
 * Четыре вкладки нижней панели (§9.1). Главный экран при запуске — цели.
 * Активная вкладка окрашивается цветом своего раздела: переключаясь,
 * человек видит смену цвета и всегда знает, где находится.
 */
enum class BolhDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val section: Section,
) {
    Goals("goals", R.string.tab_goals, R.drawable.ic_tab_goals, Section.Goals),
    Habits("habits", R.string.tab_habits, R.drawable.ic_tab_habits, Section.Habits),
    Finance("finance", R.string.tab_finance, R.drawable.ic_tab_finance, Section.Finance),
    Stats("stats", R.string.tab_stats, R.drawable.ic_tab_stats, Section.Stats),
    ;

    companion object {
        val start = Goals

        fun fromRoute(route: String?): BolhDestination =
            entries.firstOrNull { it.route == route } ?: start
    }
}
