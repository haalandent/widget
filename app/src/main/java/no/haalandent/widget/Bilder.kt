package no.haalandent.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Profilbilder (rad.bilde = sti med ?v=versjon). Hentes med widget-tokenet ved datahenting,
 * lagres runde og små i cacheDir/bilder/, og leses derfra når widgeten tegnes.
 * Ny versjon = ny sti = ny fil.
 */
object Bilder {
    private const val PIKSLER = 72

    private fun fil(c: Context, m: Modul, sti: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest("${m.id}|$sti".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(c.cacheDir, "bilder").apply { mkdirs() }, "$h.png")
    }

    /** Ferdig bilde fra cache, eller null (da vises fargestripen). */
    fun lokal(c: Context, m: Modul?, sti: String?): Bitmap? {
        if (m == null || sti == null) return null
        val f = fil(c, m, sti)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** Henter bildene radene i `data` peker på og som mangler i cache. Feil ignoreres. */
    suspend fun forhandsHent(c: Context, m: Modul, data: JSONObject) = withContext(Dispatchers.IO) {
        if (m.erDemo) return@withContext
        val token = Lager.token(m) ?: return@withContext
        val a = data.optJSONArray("rader") ?: return@withContext
        val stier = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.str("bilde") }.toSet()
        for (sti in stier) {
            val f = fil(c, m, sti)
            if (f.exists()) continue
            runCatching {
                val con = URL(m.url + sti).openConnection() as HttpURLConnection
                try {
                    con.connectTimeout = 10_000
                    con.readTimeout = 15_000
                    con.setRequestProperty("Authorization", "Bearer $token")
                    if (con.responseCode != 200) return@runCatching
                    val raa = con.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching
                    val rund = rundt(raa)
                    f.outputStream().use { rund.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    con.disconnect()
                }
            }
        }
    }

    private fun rundt(b: Bitmap): Bitmap {
        val side = minOf(b.width, b.height)
        val kvadrat = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        val liten = Bitmap.createScaledBitmap(kvadrat, PIKSLER, PIKSLER, true)
        val ut = Bitmap.createBitmap(PIKSLER, PIKSLER, Bitmap.Config.ARGB_8888)
        val maling = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(liten, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(ut).drawCircle(PIKSLER / 2f, PIKSLER / 2f, PIKSLER / 2f, maling)
        return ut
    }
}
