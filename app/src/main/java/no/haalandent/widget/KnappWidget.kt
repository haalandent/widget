package no.haalandent.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.json.JSONObject

class KnappWidgetMottaker : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = KnappWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Oppdatering.startPeriodisk(context)
    }
}

// Alltid mørkt og dempet (brukes om natta)
private val bakgrunn = Color(0xFF111316)
private val knappFarge = Color(0xFF23272E)
private val hovedFarge = Color(0xFF2C3A52)
private val tekst = ColorProvider(Color(0xFFD8DCE2))
private val svak = ColorProvider(Color(0xFF8A929C))

private val knappKey = ActionParameters.Key<String>("knapp")
private const val VENTER_MAKS_MS = 30_000L

/** Form=knapper: status, inntil 4 linjer og 1–3 knapper som sender /handling uten å åpne noe. */
class KnappWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val oppsett = Intent(context, KonfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.parse("widget://oppsett/$appWidgetId"))
        provideContent { KnappInnhold(currentState(), oppsett) }
    }
}

/** Knapp-id som venter på svar, eller null (eldre enn 30 s regnes som avbrutt). */
private fun venter(p: Preferences): String? {
    val v = p[N.venter] ?: return null
    val id = v.substringBeforeLast("|")
    val t = v.substringAfterLast("|").toLongOrNull() ?: return null
    return if (System.currentTimeMillis() - t < VENTER_MAKS_MS) id else null
}

@Composable
private fun KnappInnhold(p: Preferences, oppsett: Intent) {
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(bakgrunn).padding(10.dp)
    ) {
        if (p[N.modul] == null) {
            Text("Trykk for å sette opp",
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(oppsett)),
                style = TextStyle(color = tekst, fontSize = 14.sp))
            return@Column
        }
        val data = p[N.data]?.let { runCatching { JSONObject(it) }.getOrNull() }
        val feil = p[N.feil]
        if (data == null) {
            Text(feil ?: "Henter …", style = TextStyle(color = svak, fontSize = 13.sp),
                modifier = GlanceModifier.fillMaxSize().clickable(
                    if (feil != null && feil.contains("på nytt")) actionStartActivity(oppsett)
                    else actionRunCallback<Oppfrisk>()))
            return@Column
        }
        val url = data.str("url")
        val aapne = if (url != null) GlanceModifier.clickable(visUrl(url)) else GlanceModifier

        // Status
        val status = data.optJSONObject("status")
        Row(modifier = aapne.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            status?.str("ikon")?.let {
                Text(it, style = TextStyle(fontSize = 26.sp))
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(status?.str("tekst") ?: data.optString("tittel"),
                    style = TextStyle(color = tekst, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1)
                status?.str("undertekst")?.let {
                    Text(it, style = TextStyle(color = svak, fontSize = 13.sp), maxLines = 1)
                }
            }
            Text(if (feil != null) "⚠ ↻" else "↻",
                modifier = GlanceModifier.padding(start = 6.dp).clickable(actionRunCallback<Oppfrisk>()),
                style = TextStyle(color = svak, fontSize = 15.sp))
        }

        // Linjer og melding
        val linjer = data.optJSONArray("linjer")
        if (linjer != null) for (i in 0 until minOf(4, linjer.length())) {
            linjer.optJSONObject(i)?.str("tekst")?.let {
                Text(it, style = TextStyle(color = svak, fontSize = 12.sp), maxLines = 1)
            }
        }
        val melding = feil ?: p[N.melding]
        if (melding != null) {
            Text(melding, style = TextStyle(color = tekst, fontSize = 12.sp), maxLines = 2)
        }

        Spacer(GlanceModifier.defaultWeight())

        // Knapper: hoved stor og alene, de andre på én rad
        val alle = data.optJSONArray("knapper")
        val knapper = if (alle == null) emptyList()
            else (0 until minOf(3, alle.length())).mapNotNull { alle.optJSONObject(it) }
        val hoved = knapper.firstOrNull { it.optBoolean("hoved") }
        val andre = knapper.filter { it !== hoved }
        val opptatt = venter(p)
        if (hoved != null) {
            Knapp(hoved, opptatt, stor = true, modifier = GlanceModifier.fillMaxWidth())
            if (andre.isNotEmpty()) Spacer(GlanceModifier.height(6.dp))
        }
        if (andre.isNotEmpty()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                andre.forEachIndexed { i, k ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    Knapp(k, opptatt, stor = hoved == null, modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun Knapp(k: JSONObject, opptatt: String?, stor: Boolean, modifier: GlanceModifier) {
    val id = k.optString("id")
    val denne = opptatt == id
    val etikett = if (denne) "Registrerer …"
        else listOfNotNull(k.str("ikon"), k.optString("tittel")).joinToString(" ")
    var m = modifier.height(if (stor) 56.dp else 44.dp).cornerRadius(12.dp)
        .background(if (stor) hovedFarge else knappFarge)
    if (opptatt == null) m = m.clickable(actionRunCallback<TrykkKnapp>(actionParametersOf(knappKey to id)))
    Box(modifier = m, contentAlignment = Alignment.Center) {
        Text(etikett, maxLines = 1, style = TextStyle(
            color = if (opptatt != null && !denne) svak else tekst,
            fontSize = if (stor) 17.sp else 14.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center))
    }
}

class TrykkKnapp : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val knapp = parameters[knappKey] ?: return
        val p = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, glanceId)
        if (venter(p) != null) return // et kall pågår allerede
        val modul = Lager.modul(context, p[N.modul]) ?: return
        // Bekreft straks
        updateAppWidgetState(context, glanceId) {
            it[N.venter] = "$knapp|${System.currentTimeMillis()}"
            it.remove(N.melding)
        }
        KnappWidget().update(context, glanceId)
        val melding = try {
            Api.handling(context, modul, knapp).optString("melding").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            updateAppWidgetState(context, glanceId) {
                it.remove(N.venter)
                it[N.feil] = e.message ?: "Feil"
            }
            KnappWidget().update(context, glanceId)
            return
        }
        updateAppWidgetState(context, glanceId) {
            it.remove(N.venter)
            if (melding != null) it[N.melding] = melding else it.remove(N.melding)
        }
        Oppdatering.hentWidget(context, glanceId, beholdMelding = true)
        Oppdatering.planlegg(context) // neste henting ved gyldig_til (f.eks. når «Angre» forsvinner)
    }
}
