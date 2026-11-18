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
)

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
        )
    }
}

/** Kontrakten: skjul når slutt (ellers start) er passert og raden ikke venter på avkrysning. */
fun synlig(r: Rad, naa: OffsetDateTime): Boolean {
    val grense = r.slutt ?: r.start ?: return true
    return !(grense.isBefore(naa) && r.ferdig != false)
}

/** Hvilken widget-klasse en Glance-id hører til (liste eller måned). */
fun erMaanedWidget(c: Context, appWidgetId: Int): Boolean =
    AppWidgetManager.getInstance(c).getAppWidgetInfo(appWidgetId)?.provider?.className ==
        MaanedWidgetMottaker::class.java.name

fun widgetFor(c: Context, id: GlanceId): GlanceAppWidget =
    if (erMaanedWidget(c, GlanceAppWidgetManager(c).getAppWidgetId(id))) MaanedWidget() else ListeWidget()
