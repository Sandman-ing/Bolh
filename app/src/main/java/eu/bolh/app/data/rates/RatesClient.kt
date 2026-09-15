package eu.bolh.app.data.rates

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Курсы на дату: сколько единиц валюты дают за один евро. */
data class RatesSnapshot(val date: LocalDate, val perEur: Map<String, Double>)

/**
 * Откуда берутся курсы (§6.3).
 *
 * Открытый набор без ключа и без ограничения на число запросов, лицензия CC0,
 * раздаётся через CDN и обновляется раз в сутки. В нём есть драм и рубль —
 * у ЕЦБ их нет, из-за этого курсы и откладывались. Адресов два: если основной
 * недоступен, берётся запасной, который объявил сам проект.
 *
 * О человеке в сеть не уходит ничего: запрос одинаковый для всех.
 */
class RatesClient(
    private val urls: List<String> = listOf(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json",
        "https://latest.currency-api.pages.dev/v1/currencies/eur.json",
    ),
) {

    /** Свежие курсы или null, если ни один адрес не ответил: тогда живём на прежних. */
    suspend fun fetch(): RatesSnapshot? = withContext(Dispatchers.IO) {
        for (url in urls) {
            val snapshot = runCatching { load(url) }.getOrNull()
            if (snapshot != null) return@withContext snapshot
        }
        null
    }

    private fun load(address: String): RatesSnapshot? {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): RatesSnapshot {
        val root = JSONObject(body)
        val date = LocalDate.parse(root.getString("date"))
        val eur = root.getJSONObject("eur")
        // В наборе есть и криптовалюты с кодами вроде «1inch»; оставляем только
        // то, что система знает как валюту, — остальное выбрать всё равно нельзя.
        val known = Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
        val rates = HashMap<String, Double>()
        for (key in eur.keys()) {
            val code = key.uppercase()
            if (code.length != 3 || code !in known) continue
            val value = eur.optDouble(key)
            if (!value.isNaN() && value > 0.0) rates[code] = value
        }
        rates["EUR"] = 1.0
        return RatesSnapshot(date, rates)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
