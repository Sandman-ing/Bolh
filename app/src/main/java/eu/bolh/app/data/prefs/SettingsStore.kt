package eu.bolh.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import eu.bolh.app.domain.model.GoalSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Настройки приложения. По умолчанию всё выключено (§3.1): связи между блоками,
 * бюджеты и прочие надстройки включает сам человек, мастера первого запуска нет.
 */
class SettingsStore(private val context: Context) {

    val goalSort: Flow<GoalSort> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_GOAL_SORT]
            ?.let { runCatching { GoalSort.valueOf(it) }.getOrNull() }
            ?: GoalSort.Manual
    }

    val statPeriod: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STAT_PERIOD] ?: DEFAULT_STAT_PERIOD
    }

    val linksEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_LINKS_ENABLED] ?: false
    }

    suspend fun setGoalSort(sort: GoalSort) {
        context.settingsDataStore.edit { it[KEY_GOAL_SORT] = sort.name }
    }

    suspend fun setStatPeriod(period: String) {
        context.settingsDataStore.edit { it[KEY_STAT_PERIOD] = period }
    }

    suspend fun setLinksEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_LINKS_ENABLED] = enabled }
    }

    private companion object {
        /** Период статистики по умолчанию — месяц (§7). */
        const val DEFAULT_STAT_PERIOD = "month"

        val KEY_GOAL_SORT = stringPreferencesKey("goal_sort")
        val KEY_STAT_PERIOD = stringPreferencesKey("stat_period")
        val KEY_LINKS_ENABLED = booleanPreferencesKey("links_enabled")
    }
}
