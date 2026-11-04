-processkotlinnullchecks remove_message

# Задание WorkManager прежних версий. Из кода на него никто не ссылается,
# но WorkManager находит его по имени класса из своей базы, когда снимает
# старые задания, — поэтому R8 не должен его вырезать.
-keep class eu.bolh.app.data.notifications.RecurringReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
