package eu.bolh.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.bolh.app.data.db.BolhDatabase
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.data.prefs.LanguagePrefs
import eu.bolh.app.data.prefs.ThemePrefs
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Файл не похож на копию Bolh: не тот формат или повреждён. */
class NotBolhBackupException : Exception("not a Bolh backup")

/**
 * Итог восстановления.
 *
 * [language] — язык, сохранённый в копии: если он отличается от текущего,
 * экран нужно пересоздать, чтобы надписи сменились сразу.
 */
data class RestoreResult(val rows: Int, val language: String?)

/**
 * Резервная копия и перенос на новый телефон (§8.1).
 *
 * Данные лежат только на устройстве, значит потеря телефона равна потере всего.
 * Копия — один файл со всеми таблицами и настройками. Снимок снимается прямо
 * с таблиц, а не с моделей: так копия остаётся полной при любом изменении
 * схемы, и новое поле не требует переписывать сериализацию.
 */
class BackupService(
    private val database: BolhDatabase,
    private val context: Context,
) {

    suspend fun exportJson(output: OutputStream): Int = withContext(Dispatchers.IO) {
        var rows = 0
        val db = database.openHelper.readableDatabase
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name(FIELD_FORMAT).value(FORMAT)
            writer.name(FIELD_VERSION).value(VERSION.toLong())
            writer.name(FIELD_SCHEMA).value(db.version.toLong())
            writer.name(FIELD_EXPORTED_AT).value(Instant.now().toString())
            writer.name(FIELD_PREFS).beginObject()
            writer.name(PREF_CURRENCY).value(CurrencyPrefs.get(context))
            writer.name(PREF_LANGUAGE).value(LanguagePrefs.get(context))
            writer.name(PREF_THEME).value(ThemePrefs.get(context))
            writer.endObject()
            writer.name(FIELD_TABLES).beginObject()
            TABLES.forEach { table ->
                writer.name(table).beginArray()
                rows += writeTable(db, table, writer)
                writer.endArray()
            }
            writer.endObject()
            writer.endObject()
            writer.flush()
        }
        rows
    }

    /**
     * Операции таблицей для чужих программ, поэтому в ней читаемые значения,
     * а не внутренние идентификаторы.
     */
    suspend fun exportTransactionsCsv(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        var rows = 0
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.write("date,type,amount,currency,category,account,note\n")
            // Операции удалённого счёта не выгружаются, как не видны и в самом
            // приложении; перевод остаётся, пока жив хоть один из его счетов.
            db.query(
                """
                SELECT t.date, t.type, t.amountMinor, t.currencyCode,
                       COALESCE(c.nameKey, c.name, '') AS category,
                       COALESCE(a.name, '') AS account,
                       COALESCE(t.note, '') AS note
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.categoryId
                LEFT JOIN accounts a ON a.id = t.accountId
                WHERE t.deletedAt IS NULL
                  AND (t.accountId IN (SELECT id FROM accounts WHERE deletedAt IS NULL)
                       OR t.toAccountId IN (SELECT id FROM accounts WHERE deletedAt IS NULL))
                ORDER BY t.date
                """.trimIndent(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val code = cursor.getString(3)
                    // Знаков после запятой у валюты свои: у иены их нет, и деление
                    // на сто уменьшало бы такие суммы в сто раз.
                    val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }
                        .getOrNull()
                        ?.coerceAtLeast(0)
                        ?: 2
                    val amount = java.math.BigDecimal.valueOf(cursor.getLong(2), digits)
                    writer.write(
                        listOf(
                            cursor.getString(0),
                            cursor.getString(1),
                            amount.toPlainString(),
                            code,
                            cursor.getString(4),
                            cursor.getString(5),
                            cursor.getString(6),
                        ).joinToString(",") { escapeCsv(it) },
                    )
                    writer.write("\n")
                    rows++
                }
            }
            writer.flush()
        }
        rows
    }

    /**
     * Восстановление из копии.
     *
     * Данные телефона заменяются целиком. Копия — это снимок, и смешивать его
     * с записями нового телефона нельзя: готовые категории появляются там при
     * первом заходе в финансы, и после слияния каждая была бы в двух экземплярах.
     *
     * Файл сначала читается и проверяется целиком. Таблицы трогаются, только
     * если это действительно копия Bolh, и всё делается одной транзакцией:
     * сбой посередине оставляет данные такими, какими они были.
     */
    suspend fun importJson(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val snapshot = readSnapshot(input)
        val db = database.openHelper.writableDatabase
        var restored = 0

        db.beginTransaction()
        try {
            TABLES.asReversed().forEach { db.delete(it, null, null) }
            TABLES.forEach { table ->
                val known = columnsOf(db, table)
                snapshot.tables[table].orEmpty().forEach { row ->
                    val values = ContentValues()
                    row.forEach { (column, value) ->
                        // Колонки, которых в этой версии нет, пропускаются:
                        // копия с более нового телефона не должна ронять импорт.
                        if (column in known) putValue(values, column, value)
                    }
                    fillDefaults(table, values)
                    if (values.size() > 0) {
                        db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                        restored++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        snapshot.currency?.let { CurrencyPrefs.set(context, it) }
        snapshot.theme?.let { ThemePrefs.set(context, it) }
        snapshot.language?.let { LanguagePrefs.set(context, it) }
        // Экраны слушают таблицы через Room; запись шла мимо него, поэтому
        // ему нужно явно сказать, что всё поменялось.
        database.invalidationTracker.refreshAsync()
        RestoreResult(rows = restored, language = snapshot.language)
    }

    private class Snapshot(
        val tables: Map<String, List<Map<String, Any?>>>,
        val currency: String?,
        val language: String?,
        val theme: String?,
    )

    private fun readSnapshot(input: InputStream): Snapshot {
        var format: String? = null
        val tables = HashMap<String, List<Map<String, Any?>>>()
        val prefs = HashMap<String, String>()
        var sawTables = false

        try {
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        FIELD_FORMAT -> format = if (reader.peek() == JsonToken.STRING) {
                            reader.nextString()
                        } else {
                            reader.skipValue().let { null }
                        }

                        FIELD_PREFS -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val key = reader.nextName()
                                if (reader.peek() == JsonToken.STRING) {
                                    prefs[key] = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }

                        FIELD_TABLES -> {
                            sawTables = true
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val table = reader.nextName()
                                if (table in TABLES) tables[table] = readRows(reader) else reader.skipValue()
                            }
                            reader.endObject()
                        }

                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (broken: Exception) {
            throw NotBolhBackupException()
        }

        if (format != FORMAT || !sawTables) throw NotBolhBackupException()
        return Snapshot(
            tables = tables,
            currency = prefs[PREF_CURRENCY]?.takeIf { it.length == 3 },
            language = prefs[PREF_LANGUAGE]?.takeIf { code ->
                code == LanguagePrefs.SYSTEM || LanguagePrefs.languages.any { it.first == code }
            },
            theme = prefs[PREF_THEME]?.takeIf { it.isNotBlank() },
        )
    }

    private fun readRows(reader: JsonReader): List<Map<String, Any?>> {
        val rows = ArrayList<Map<String, Any?>>()
        reader.beginArray()
        while (reader.hasNext()) {
            val row = LinkedHashMap<String, Any?>()
            reader.beginObject()
            while (reader.hasNext()) {
                val column = reader.nextName()
                row[column] = when (reader.peek()) {
                    JsonToken.NULL -> reader.nextNull().let { null }
                    JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1L else 0L
                    JsonToken.NUMBER -> {
                        val raw = reader.nextString()
                        raw.toLongOrNull() ?: raw.toDouble()
                    }

                    JsonToken.STRING -> reader.nextString()
                    else -> reader.skipValue().let { null }
                }
            }
            reader.endObject()
            rows += row
        }
        reader.endArray()
        return rows
    }

    private fun writeTable(db: SupportSQLiteDatabase, table: String, writer: JsonWriter): Int {
        var rows = 0
        db.query("SELECT * FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                writer.beginObject()
                for (index in 0 until cursor.columnCount) {
                    writer.name(cursor.getColumnName(index))
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                        Cursor.FIELD_TYPE_INTEGER -> writer.value(cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> writer.value(cursor.getDouble(index))
                        else -> writer.value(cursor.getString(index))
                    }
                }
                writer.endObject()
                rows++
            }
        }
        return rows
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = HashSet<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns += cursor.getString(name)
        }
        return columns
    }

    private fun putValue(values: ContentValues, column: String, value: Any?) {
        when (value) {
            null -> values.putNull(column)
            is Long -> values.put(column, value)
            is Double -> values.put(column, value)
            else -> values.put(column, value.toString())
        }
    }

    /**
     * Поля, которых не было в старых копиях. Без значения по умолчанию
     * вставка в новую базу упала бы на ограничении NOT NULL.
     */
    private fun fillDefaults(table: String, values: ContentValues) {
        if (table != "habits") return
        if (!values.containsKey("timesPerDay")) values.put("timesPerDay", 1L)
        if (!values.containsKey("extraReminders")) values.put("extraReminders", "")
        if (!values.containsKey("reminderAlarm")) values.put("reminderAlarm", 0L)
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    companion object {
        const val FORMAT = "bolh-backup"
        const val VERSION = 2

        private const val FIELD_FORMAT = "format"
        private const val FIELD_VERSION = "version"
        private const val FIELD_SCHEMA = "schema"
        private const val FIELD_EXPORTED_AT = "exportedAt"
        private const val FIELD_PREFS = "prefs"
        private const val FIELD_TABLES = "tables"

        private const val PREF_CURRENCY = "currency"
        private const val PREF_LANGUAGE = "language"
        private const val PREF_THEME = "theme"

        /** Порядок важен: сначала то, на что ссылаются, потом ссылающееся. */
        private val TABLES = listOf(
            "accounts",
            "categories",
            "goals",
            "goal_cost_items",
            "stages",
            "tasks",
            "deadline_records",
            "habits",
            "habit_entries",
            "habit_pauses",
            "savings_jars",
            "jar_contributions",
            "transactions",
            "recurring_payments",
            "currency_rates",
        )

        /** Имя файла копии: дата видна сразу, в списке файлов их легко различить. */
        fun fileName(date: java.time.LocalDate): String =
            String.format(Locale.ROOT, "bolh-backup-%s.json", date)
    }
}
