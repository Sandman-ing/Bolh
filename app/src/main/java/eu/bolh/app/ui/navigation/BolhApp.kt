package eu.bolh.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.bolh.app.R
import eu.bolh.app.ui.finance.AccountDetailScreen
import eu.bolh.app.ui.finance.AccountDetailViewModel
import eu.bolh.app.ui.finance.AccountEditorScreen
import eu.bolh.app.ui.finance.CategoriesScreen
import eu.bolh.app.ui.finance.FinanceScreen
import eu.bolh.app.ui.finance.RecurringEditorScreen
import eu.bolh.app.ui.finance.RecurringEditorViewModel
import eu.bolh.app.ui.finance.TransactionSheet
import eu.bolh.app.ui.finance.TransactionsScreen
import eu.bolh.app.ui.goals.GoalDetailScreen
import eu.bolh.app.ui.goals.GoalDetailViewModel
import eu.bolh.app.ui.goals.GoalEditorScreen
import eu.bolh.app.ui.goals.GoalEditorViewModel
import eu.bolh.app.ui.goals.GoalsScreen
import eu.bolh.app.ui.habits.HabitEditorScreen
import eu.bolh.app.ui.habits.HabitEditorViewModel
import eu.bolh.app.ui.habits.HabitsScreen
import eu.bolh.app.ui.settings.SettingsScreen
import eu.bolh.app.ui.stats.StatsScreen
import eu.bolh.app.data.prefs.ThemePrefs
import eu.bolh.app.ui.theme.ThemeMode
import eu.bolh.app.ui.theme.BolhTheme
import eu.bolh.app.ui.theme.LocalDarkTheme
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Section

/** Длительности перехода: сдвиг чуть длиннее проявления, иначе движение рвётся. */
private const val SCREEN_MOVE_MS = 260
private const val SCREEN_FADE_MS = 180

object Routes {
    const val HABIT_NEW = "habits/new"
    const val HABIT_EDIT = "habits/edit/{habitId}"
    const val GOAL_NEW = "goals/new"
    const val GOAL_EDIT = "goals/edit/{goalId}"
    const val GOAL_DETAIL = "goals/detail/{goalId}"
    const val ACCOUNT_NEW = "finance/account/new"
    const val SETTINGS = "settings"
    const val RECURRING_NEW = "finance/recurring/new"
    const val RECURRING_EDIT = "finance/recurring/edit/{paymentId}"
    const val TRANSACTIONS = "finance/transactions"
    const val CATEGORIES = "finance/categories"
    const val ACCOUNT_DETAIL = "finance/account/detail/{accountId}"

    fun accountDetail(id: String): String = "finance/account/detail/$id"

    fun recurringEdit(id: String): String = "finance/recurring/edit/$id"

    fun habitEdit(id: String): String = "habits/edit/$id"

    fun goalEdit(id: String): String = "goals/edit/$id"

    fun goalDetail(id: String): String = "goals/detail/$id"
}

@Composable
fun BolhApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tab = BolhDestination.entries.firstOrNull { it.route == route }
    // Цвет берётся по разделу, а не по вкладке: редактор привычки принадлежит
    // привычкам, даже когда это отдельный экран поверх (§9.3).
    val section = sectionFor(route, tab)
    val onTab = tab != null
    var showTransactionSheet by remember { mutableStateOf(false) }

    // Тема читается синхронно и наблюдается: смена в настройках должна
    // доходить до экрана сразу, без пересоздания Activity.
    val context = LocalContext.current
    val themeMode by ThemeMode.current.collectAsStateWithLifecycle()
    val dark = ThemePrefs.isDark(themeMode, isSystemInDarkTheme())

    // Значки системной строки — часов, батареи, жестов — рисует система, и
    // про нашу тему она не знает. На светлой теме белые значки на белом фоне
    // просто исчезали, поэтому их вид переключается вместе с темой (§9.4).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    BolhTheme(section = section, dark = dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (onTab) {
                    BolhBottomBar(
                        current = tab ?: BolhDestination.start,
                        onSelect = { navController.switchTab(it) },
                    )
                }
            },
            floatingActionButton = {
                val create: (() -> Unit)? = when (tab) {
                    BolhDestination.Habits -> ({ navController.navigate(Routes.HABIT_NEW) })
                    BolhDestination.Goals -> ({ navController.navigate(Routes.GOAL_NEW) })
                    // Запись траты — шторка, а не экран: это самое частое действие (§6.9).
                    BolhDestination.Finance -> ({ showTransactionSheet = true })
                    else -> null
                }
                if (create != null) {
                    FloatingActionButton(
                        onClick = create,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.action_create),
                        )
                    }
                }
            },
        ) { innerPadding ->
            // Экран приезжает сбоку и уходит назад тем же путём. Стандартное
            // наложение двух экранов друг на друга читается как сбой отрисовки,
            // а короткий сдвиг сам объясняет, куда человек попал.
            NavHost(
                navController = navController,
                startDestination = BolhDestination.start.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideInHorizontally(tween(SCREEN_MOVE_MS)) { it / 6 } +
                        fadeIn(tween(SCREEN_FADE_MS))
                },
                exitTransition = {
                    slideOutHorizontally(tween(SCREEN_MOVE_MS)) { -it / 8 } +
                        fadeOut(tween(SCREEN_FADE_MS))
                },
                popEnterTransition = {
                    slideInHorizontally(tween(SCREEN_MOVE_MS)) { -it / 8 } +
                        fadeIn(tween(SCREEN_FADE_MS))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(SCREEN_MOVE_MS)) { it / 6 } +
                        fadeOut(tween(SCREEN_FADE_MS))
                },
            ) {
                composable(BolhDestination.Goals.route) {
                    GoalsScreen(
                        onCreate = { navController.navigate(Routes.GOAL_NEW) },
                        onOpenGoal = { id -> navController.navigate(Routes.goalDetail(id)) },
                    )
                }

                composable(BolhDestination.Habits.route) {
                    HabitsScreen(
                        onCreate = { navController.navigate(Routes.HABIT_NEW) },
                        onOpenHabit = { id -> navController.navigate(Routes.habitEdit(id)) },
                    )
                }

                composable(BolhDestination.Finance.route) {
                    FinanceScreen(
                        onAddAccount = { navController.navigate(Routes.ACCOUNT_NEW) },
                        onAddTransaction = { showTransactionSheet = true },
                        onAddRecurring = { navController.navigate(Routes.RECURRING_NEW) },
                        onOpenTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                        onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                        onOpenAccount = { id -> navController.navigate(Routes.accountDetail(id)) },
                        onOpenRecurring = { id ->
                            navController.navigate(Routes.recurringEdit(id))
                        },
                    )
                }

                composable(BolhDestination.Stats.route) {
                    StatsScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.HABIT_NEW) {
                    HabitEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(
                    route = Routes.HABIT_EDIT,
                    arguments = listOf(
                        navArgument(HabitEditorViewModel.ARG_HABIT_ID) { type = NavType.StringType },
                    ),
                ) {
                    HabitEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.ACCOUNT_NEW) {
                    AccountEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(
                    route = Routes.ACCOUNT_DETAIL,
                    arguments = listOf(
                        navArgument(AccountDetailViewModel.ARG_ACCOUNT_ID) {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    AccountDetailScreen(
                        onBack = { navController.popBackStack() },
                        onAddAccount = { navController.navigate(Routes.ACCOUNT_NEW) },
                    )
                }

                composable(Routes.CATEGORIES) {
                    CategoriesScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.TRANSACTIONS) {
                    TransactionsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.RECURRING_NEW) {
                    RecurringEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(
                    route = Routes.RECURRING_EDIT,
                    arguments = listOf(
                        navArgument(RecurringEditorViewModel.ARG_PAYMENT_ID) {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    RecurringEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.GOAL_NEW) {
                    GoalEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(
                    route = Routes.GOAL_EDIT,
                    arguments = listOf(
                        navArgument(GoalEditorViewModel.ARG_GOAL_ID) { type = NavType.StringType },
                    ),
                ) {
                    GoalEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(
                    route = Routes.GOAL_DETAIL,
                    arguments = listOf(
                        navArgument(GoalDetailViewModel.ARG_GOAL_ID) { type = NavType.StringType },
                    ),
                ) {
                    GoalDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.goalEdit(id)) },
                    )
                }
            }

            if (showTransactionSheet) {
                TransactionSheet(
                    onDismiss = { showTransactionSheet = false },
                    onAddAccount = {
                        showTransactionSheet = false
                        navController.navigate(Routes.ACCOUNT_NEW)
                    },
                )
            }
        }
    }
}

/**
 * Раздел по маршруту. Совпадения с вкладкой мало: вложенные экраны должны
 * держать цвет своего блока, иначе цвет перестаёт быть языком и становится
 * украшением.
 */
private fun sectionFor(route: String?, tab: BolhDestination?): Section = when {
    tab != null -> tab.section
    route == null -> BolhDestination.start.section
    route.startsWith("habits") -> Section.Habits
    route.startsWith("goals") -> Section.Goals
    route.startsWith("finance") -> Section.Finance
    else -> Section.Stats
}

@Composable
private fun BolhBottomBar(
    current: BolhDestination,
    onSelect: (BolhDestination) -> Unit,
) {
    val status = LocalStatusColors.current
    val dark = LocalDarkTheme.current
    val entries = remember { BolhDestination.entries }

    NavigationBar(containerColor = status.surfaceRaised, tonalElevation = 0.dp) {
        entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = {
                    // Кегль меньше на ступень: «Статистика» — самое длинное
                    // слово из четырёх, и в labelMedium оно доезжало до самого
                    // края экрана. В labelSmall помещается целиком.
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    // Акцент раздела берётся под текущую тему: светлый оттенок
                    // на белом выцветает и перестаёт означать «вы здесь».
                    selectedIconColor = destination.section.accentOn(dark),
                    selectedTextColor = destination.section.accentOn(dark),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = status.textTertiary,
                    unselectedTextColor = status.textTertiary,
                ),
            )
        }
    }
}

/**
 * Переключение вкладок не копит историю: возврат назад со второй вкладки
 * ведёт на первую, а не в глубину предыдущих переходов.
 */
private fun NavHostController.switchTab(destination: BolhDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
