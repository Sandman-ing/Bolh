package eu.bolh.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.bolh.app.BolhApplication
import eu.bolh.app.domain.model.EntryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Отметка дня прямо из шторки уведомления. Ежедневное действие должно быть самым
 * быстрым в приложении (§3.2) — а самое быстрое, что бывает, это не открывать его.
 */
class HabitMarkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val level = runCatching {
            EntryLevel.valueOf(intent.getStringExtra(EXTRA_LEVEL) ?: EntryLevel.Norm.name)
        }.getOrDefault(EntryLevel.Norm)

        val app = context.applicationContext as? BolhApplication ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = app.container.habitRepository
                repository.markDay(habitId, repository.today(), level)
                BolhNotifications.cancel(context, habitId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_MARK = "eu.bolh.app.action.MARK_HABIT"
        private const val EXTRA_HABIT_ID = "habitId"
        private const val EXTRA_LEVEL = "level"

        fun intent(context: Context, habitId: String, level: EntryLevel): Intent =
            Intent(context, HabitMarkReceiver::class.java).apply {
                action = ACTION_MARK
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_LEVEL, level.name)
            }
    }
}
