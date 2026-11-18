package no.haalandent.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Én rad i form=liste (kjoreregler.md avsnitt 9). */
data class Rad(
    val start: OffsetDateTime?, val slutt: OffsetDateTime?, val heldag: Boolean,
    val ikon: String?, val tekst: String, val undertekst: String?, val farge: Color?,
    val ferdig: Boolean?, val url: String?, val handling: String?,
    val steg: Steg? = null, val forfalt: Boolean = false,
    val bilde: String? = null,   // sti til profilbilde (hentes med token, se Bilder)
)

/** Gjeldende steg på et gjøremål; `ferdig`/`handling` på raden gjelder da dette steget. */
data class Steg(val tekst: String, val nr: Int, val antall: Int, val gjort: Boolean)

val varselFarge = Color(0xFFE5534B)
val datoKort: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M")

/** Optimistisk avkrysning: marker raden med denne handlingen som ferdig i lagret JSON. */
fun markerFerdig(data: String, handling: String): String = runCatching {
    val o = JSONObject(data)
    val a = o.optJSONArray("rader") ?: return data
    for (i in 0 until a.length()) {
        val r = a.getJSONObject(i)
        if (r.optJSONObject("handling")?.optString("id") == handling) {
            r.put("ferdig", true)
            r.optJSONObject("steg")?.put("gjort", true)
        }
    }
    o.toString()
}.getOrDefault(data)

fun JSONObject.str(n: String): String? = if (isNull(n)) null else optString(n).takeIf { it.isNotEmpty() }

fun tid(s: String?) = s?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

fun farge(s: String?): Color? = s?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

val klokke: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun rader(data: JSONObject): List<Rad> {
    val a = data.optJSONArray("rader") ?: return emptyList()
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Rad(
            start = tid(o.str("start")), slutt = tid(o.str("slutt")),
            heldag = o.optBoolean("heldag"), ikon = o.str("ikon"),
            tekst = o.optString("tekst"), undertekst = o.str("undertekst"),
            farge = farge(o.str("farge")),
            ferdig = if (!o.has("ferdig") || o.isNull("ferdig")) null else o.getBoolean("ferdig"),
            url = o.str("url"),
            handling = o.optJSONObject("handling")?.str("id"),
            steg = o.optJSONObject("steg")?.let { st ->
                Steg(st.optString("tekst"), st.optInt("nr"), st.optInt("antall"), st.optBoolean("gjort"))
            },
            forfalt = o.optBoolean("forfalt"),
            bilde = o.str("bilde"),
        )
    }
}

/** Kontrakten: skjul når slutt (ellers start) er passert og raden ikke venter på avkrysning. */
fun synlig(r: Rad, naa: OffsetDateTime): Boolean {
    val grense = r.slutt ?: r.start ?: return true
    return !(grense.isBefore(naa) && r.ferdig != false)
}

/** Hvilken form en widget har, ut fra mottakerklassen. */
fun formFor(c: Context, appWidgetId: Int): String =
    when (AppWidgetManager.getInstance(c).getAppWidgetInfo(appWidgetId)?.provider?.className) {
        MaanedWidgetMottaker::class.java.name -> "maned"
        KnappWidgetMottaker::class.java.name -> "knapper"
        else -> "liste"
    }

fun widgetFor(c: Context, id: GlanceId): GlanceAppWidget =
    when (formFor(c, GlanceAppWidgetManager(c).getAppWidgetId(id))) {
        "maned" -> MaanedWidget()
        "knapper" -> KnappWidget()
        else -> ListeWidget()
    }
