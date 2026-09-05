package eu.bolh.app.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.bolh.app.data.db.BolhDatabase
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Экспорт и восстановление (§8.1).
 *
 * Данные лежат только на устройстве, значит потеря телефона равна потере всего —
 * поэтому резервная копия входит в первую версию, а не откладывается.
 *
 * Снимок снимается прямо с таблиц, а не с моделей: так копия остаётся полной
 * при любом изменении схемы и не требует переписывать сериализацию под каждое
 * новое поле.
 */
class BackupService(private val database: BolhDatabase) {

    suspend fun exportJson(output: OutputStream): Int = withContext(Dispatchers.IO) {
        var rows = 0
        val db = database.openHelper.readableDatabase
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name(FIELD_FORMAT).value(FORMAT)
            writer.name(FIELD_VERSION).value(VERSION.toLong())
            writer.name(FIELD_EXPORTED_AT).value(Instant.now().toString())
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
     * CSV — для таблиц и чужих программ, поэтому в нём читаемые значения,
     * а не внутренние идентификаторы.
     */
    suspend fun exportTransactionsCsv(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        var rows = 0
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.write("date,type,amount,currency,category,account,note\n")
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
                ORDER BY t.date
                """.trimIndent(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val amount = cursor.getLong(2) / 100.0
                    writer.write(
                        listOf(
                            cursor.getString(0),
                            cursor.getString(1),
                            String.format(java.util.Locale.ROOT, "%.2f", amount),
                            cursor.getString(3),
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
     * Импорт из своего же формата. Строки перезаписываются по идентификатору:
     * восстановление поверх пустой базы даёт точную копию, поверх непустой —
     * объединение без дублей.
     */
    suspend fun importJson(input: InputStream): Int = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        var restored = 0
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    FIELD_TABLES -> {
                        db.beginTransaction()
                        try {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val table = reader.nextName()
                                if (table in TABLES) {
                                    restored += readTable(db, table, reader)
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }

                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        restored
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

    private fun readTable(db: SupportSQLiteDatabase, table: String, reader: JsonReader): Int {
        var rows = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val values = ContentValues()
            reader.beginObject()
            while (reader.hasNext()) {
                val column = reader.nextName()
                when (reader.peek()) {
                    JsonToken.NULL -> {
                        reader.nextNull()
                        values.putNull(column)
                    }

                    JsonToken.BOOLEAN -> values.put(column, if (reader.nextBoolean()) 1 else 0)

                    JsonToken.NUMBER -> {
                        val raw = reader.nextString()
                        val asLong = raw.toLongOrNull()
                        if (asLong != null) values.put(column, asLong) else values.put(column, raw.toDouble())
                    }

                    else -> values.put(column, reader.nextString())
                }
            }
            reader.endObject()
            if (values.size() > 0) {
                db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
                rows++
            }
        }
        reader.endArray()
        return rows
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    companion object {
        const val FORMAT = "bolh-backup"
        const val VERSION = 1

        private const val FIELD_FORMAT = "format"
        private const val FIELD_VERSION = "version"
        private const val FIELD_EXPORTED_AT = "exportedAt"
        private const val FIELD_TABLES = "tables"

        /** Порядок важен: сначала то, на что ссылаются, потом ссылающееся. */
        private val TABLES = listOf(
            "accounts",
            "categories",
            "goals",
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
    }
}
