package fr.ivry.os.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Réponse HTTP non 2xx : porte le code pour un message d'erreur lisible côté UI. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

object Http {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8_000
                c.readTimeout = 12_000
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("User-Agent", "IvryOS/1.0 (Android TV)")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                val code = c.responseCode
                if (code !in 200..299) throw HttpStatusException(code)
                c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
}

/** Cache disque minimal : première ligne = horodatage, puis la réponse brute. */
class DiskCache(private val dir: File) {
    init { dir.mkdirs() }

    fun write(key: String, text: String) {
        runCatching { File(dir, "$key.json").writeText("${System.currentTimeMillis()}\n$text") }
    }

    fun read(key: String): Pair<Long, String>? = runCatching {
        val f = File(dir, "$key.json")
        if (!f.exists()) return null
        val raw = f.readText()
        val i = raw.indexOf('\n')
        raw.substring(0, i).toLong() to raw.substring(i + 1)
    }.getOrNull()
}
