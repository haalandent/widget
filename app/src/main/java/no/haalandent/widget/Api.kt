package no.haalandent.widget

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ApiFeil(val status: Int, melding: String) : Exception(melding)

/** Klient for widget-kontrakt v1 (kjoreregler.md avsnitt 9). */
object Api {
    private const val STI = "/api/widget/v1"

    /** Paring: engangskode -> token. Returnerer modulens id i lageret. */
    suspend fun par(c: Context, url: String, kode: String): String {
        if (url == DEMO_URL) {
            return Lager.leggTil(c, DEMO_URL, "Demo", null, Demo.typer())
        }
        val base = url.trimEnd('/')
        require(base.startsWith("https://")) { "Bare https-adresser støttes" }
        val svar = kall("POST", "$base$STI/par", null,
            JSONObject().put("kode", kode).put("enhet", "${Build.MANUFACTURER} ${Build.MODEL}"))
        val token = svar.getString("token")
        val rot = kall("GET", "$base$STI", token, null)
        return Lager.leggTil(c, base, rot.optString("tittel", svar.optString("tittel", base)),
            token, rot.optJSONArray("typer") ?: JSONArray())
    }

    /** Henter typer på nytt (f.eks. etter at modulen har fått nye). */
    suspend fun oppdaterTyper(c: Context, m: Modul) {
        if (m.erDemo) return
        val rot = kall("GET", "${m.url}$STI", tokenFor(c, m), null)
        Lager.leggTil(c, m.url, rot.optString("tittel", m.tittel), Lager.token(m),
            rot.optJSONArray("typer") ?: JSONArray())
    }

    suspend fun hent(c: Context, m: Modul, type: String, param: String): JSONObject {
        if (m.erDemo) return Demo.hent(c, type)
        val q = if (param.isBlank()) "" else "?$param"
        return kall("GET", "${m.url}$STI/$type$q", tokenFor(c, m), null, c, m)
    }

    /** `sti` fra form=maned sitt `dag`-felt, med {dato} byttet ut. */
    suspend fun hentSti(c: Context, m: Modul, sti: String): JSONObject {
        if (m.erDemo) return Demo.hent(c, "min-dag")
        return kall("GET", m.url + sti, tokenFor(c, m), null, c, m)
    }

    suspend fun handling(c: Context, m: Modul, id: String): JSONObject {
        if (m.erDemo) return Demo.handling(c, id)
        return kall("POST", "${m.url}$STI/handling", tokenFor(c, m), JSONObject().put("id", id), c, m)
    }

    private fun tokenFor(c: Context, m: Modul): String =
        Lager.token(m) ?: throw ApiFeil(401, "Tilgangen er trukket. Legg til modulen på nytt.")

    private suspend fun kall(
        metode: String, adresse: String, token: String?, body: JSONObject?,
        c: Context? = null, m: Modul? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val con = URL(adresse).openConnection() as HttpURLConnection
        try {
            con.requestMethod = metode
            con.connectTimeout = 10_000
            con.readTimeout = 15_000
            con.setRequestProperty("Accept", "application/json")
            if (token != null) con.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                con.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = con.responseCode
            val tekst = (if (status in 200..299) con.inputStream else con.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (status !in 200..299) {
                val melding = runCatching { JSONObject(tekst).optString("melding") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "Feil $status"
                if (status == 401 && c != null && m != null) Lager.slettToken(c, m.id)
                throw ApiFeil(status, melding)
            }
            JSONObject(tekst)
        } finally {
            con.disconnect()
        }
    }
}

/** Innebygd demo-modul med oppdiktede data, for test uten server. */
object Demo {
    fun typer(): JSONArray = JSONArray()
        .put(JSONObject().put("id", "min-dag").put("tittel", "Min dag").put("form", "liste").put("param", JSONArray()))
        .put(JSONObject().put("id", "familie").put("tittel", "Familiens dag").put("form", "liste").put("param", JSONArray()))
        .put(JSONObject().put("id", "maned").put("tittel", "Månedskalender").put("form", "maned").put("param", JSONArray()))

    fun hent(c: Context, type: String): JSONObject {
        if (type == "maned") return maned()
        val sone = ZoneId.systemDefault()
        val i_dag = LocalDate.now(sone)
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        fun t(h: Int, m: Int) = ZonedDateTime.of(i_dag, LocalTime.of(h, m), sone).format(fmt)
        val naa = ZonedDateTime.now(sone)
        val snart = naa.plusMinutes(30).withSecond(0).withNano(0)
        val ferdig = Lager.demoFerdig(c)

        fun rad(id: String, start: String, slutt: String?, tekst: String, ikon: String?,
                farge: String, under: String? = null, kryss: Boolean = false, heldag: Boolean = false) =
            JSONObject().put("id", id).put("start", start).put("slutt", slutt ?: JSONObject.NULL)
                .put("heldag", heldag).put("ikon", ikon ?: JSONObject.NULL).put("tekst", tekst)
                .put("undertekst", under ?: JSONObject.NULL).put("farge", farge)
                .put("ferdig", if (kryss) id in ferdig else JSONObject.NULL)
                .put("url", JSONObject.NULL)
                .put("handling", if (kryss) JSONObject().put("id", id)
                    .put("tittel", if (id in ferdig) "Angre" else "Ferdig") else JSONObject.NULL)

        val rader = JSONArray()
        rader.put(rad("d1", t(0, 0), t(23, 59), "Planleggingsdag", "🏫", "#8E7CC3", heldag = true))
        rader.put(rad("d2", t(7, 0), t(7, 30), "Frokost", "🥣", "#4A90D9", kryss = true))
        rader.put(rad("d3", snart.format(fmt), snart.plusMinutes(15).format(fmt), "Hente i barnehagen", "🚗", "#E67E22", "Ola", kryss = true))
        rader.put(rad("d4", t(17, 0), t(17, 45), "Middag: taco", "🌮", "#27AE60"))
        rader.put(rad("d5", t(19, 0), t(20, 0), "Fotball", "⚽", "#C0392B", "Bane 2"))
        if (type == "familie") {
            rader.put(rad("d6", t(8, 0), t(15, 0), "Skole", "🎒", "#16A085", "Kari"))
        }
        val sortert = (0 until rader.length()).map { rader.getJSONObject(it) }
            .sortedWith(compareBy({ !it.optBoolean("heldag") }, { it.getString("start") }))
        return JSONObject()
            .put("tittel", if (type == "familie") "Familiens dag (demo)" else "Min dag (demo)")
            .put("url", JSONObject.NULL)
            .put("oppdatert", naa.format(fmt))
            .put("gyldig_til", naa.plusHours(1).format(fmt))
            .put("rader", JSONArray(sortert))
    }

    private fun maned(): JSONObject {
        val idag = LocalDate.now()
        val farger = listOf("#4A90D9", "#E67E22", "#27AE60", "#C0392B")
        val dager = JSONObject()
        for (d in 1..idag.lengthOfMonth()) {
            val n = (d * 7) % 5 // 0–4 prikker, fast mønster
            if (n == 0) continue
            val a = JSONArray()
            for (i in 0 until n.coerceAtMost(4)) a.put(JSONObject().put("farge", farger[i]))
            dager.put(idag.withDayOfMonth(d).toString(), a)
        }
        return JSONObject()
            .put("tittel", "Kalender (demo)").put("url", JSONObject.NULL)
            .put("maned", idag.toString().substring(0, 7))
            .put("oppdatert", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("gyldig_til", ZonedDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("dager", dager)
            .put("dag", "/api/widget/v1/dag?dato={dato}")
    }

    fun handling(c: Context, id: String): JSONObject {
        val s = Lager.demoFerdig(c).toMutableSet()
        val ny = s.add(id)
        if (!ny) s.remove(id)
        Lager.settDemoFerdig(c, s)
        return JSONObject().put("melding", if (ny) "Krysset av" else "Angret").put("oppdater", true)
    }
}
