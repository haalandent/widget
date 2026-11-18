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
import androidx.glance.GlanceTheme
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId

class ListeWidgetMottaker : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ListeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Oppdatering.startPeriodisk(context)
    }
}

private val handlingKey = ActionParameters.Key<String>("handling")

/** Generisk liste-widget for form=liste (Min dag, Familiens dag, ...). */
class ListeWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val oppsett = Intent(context, KonfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.parse("widget://oppsett/$appWidgetId"))
        provideContent {
            GlanceTheme {
                Innhold(currentState(), oppsett)
            }
        }
    }
}

fun visUrl(url: String) =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

@Composable
private fun Innhold(p: Preferences, oppsett: Intent) {
    val tekstFarge = GlanceTheme.colors.onSurface
    val svak = GlanceTheme.colors.onSurfaceVariant
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground).padding(10.dp)
    ) {
        if (p[N.modul] == null) {
            Text(
                "Trykk for å sette opp",
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(oppsett)),
                style = TextStyle(color = tekstFarge, fontSize = 14.sp),
            )
            return@Column
        }
        val data = p[N.data]?.let { runCatching { JSONObject(it) }.getOrNull() }
        val feil = p[N.feil]

        // Overskrift: tittel åpner webappen, ↻ henter på nytt
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val tittelUrl = data?.str("url")
            Text(
                data?.optString("tittel") ?: "Henter …",
                modifier = GlanceModifier.defaultWeight()
                    .let { m -> if (tittelUrl != null) m.clickable(visUrl(tittelUrl)) else m },
                style = TextStyle(color = tekstFarge, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                maxLines = 1,
            )
            Text(
                if (feil != null && data != null) "⚠ ↻" else "↻",
                modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<Oppfrisk>()),
                style = TextStyle(color = svak, fontSize = 16.sp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        if (data == null) {
            Text(feil ?: "Henter …", style = TextStyle(color = svak, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(
                    if (feil != null && feil.contains("på nytt")) actionStartActivity(oppsett)
                    else actionRunCallback<Oppfrisk>()))
            return@Column
        }

        val naa = OffsetDateTime.now(ZoneId.systemDefault())
        val liste = rader(data).filter { synlig(it, naa) }
        if (liste.isEmpty()) {
            Text("Ingen flere i dag", style = TextStyle(color = svak, fontSize = 13.sp))
            return@Column
        }
        LazyColumn {
            items(liste) { r ->
                RadVisning(r, tekstFarge, svak)
            }
        }
    }
}

@Composable
private fun RadVisning(r: Rad, tekstFarge: ColorProvider, svak: ColorProvider) {
    val ferdig = r.ferdig == true
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
            .let { m -> if (r.url != null) m.clickable(visUrl(r.url)) else m },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.width(4.dp).height(32.dp).cornerRadius(2.dp)
                .background(r.farge ?: Color.Gray)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        val lokal = r.start?.atZoneSameInstant(ZoneId.systemDefault())
        Text(
            when {
                r.heldag -> "Hele\ndagen"
                r.forfalt -> "⏰\n" + (lokal?.format(datoKort) ?: "")
                else -> lokal?.format(klokke) ?: ""
            },
            modifier = GlanceModifier.width(44.dp),
            style = TextStyle(color = if (r.forfalt) ColorProvider(varselFarge) else svak, fontSize = 12.sp),
            maxLines = 2,
        )
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                listOfNotNull(r.ikon, r.tekst).joinToString(" "),
                style = TextStyle(
                    color = if (ferdig) svak else tekstFarge, fontSize = 14.sp,
                    textDecoration = if (ferdig) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
            if (r.steg != null) {
                Text(
                    "▸ ${r.steg.tekst}" + if (r.steg.antall > 1) "  ${r.steg.nr}/${r.steg.antall}" else "",
                    style = TextStyle(color = tekstFarge, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            if (r.undertekst != null) {
                Text(r.undertekst, style = TextStyle(
                    color = if (r.forfalt) ColorProvider(varselFarge) else svak, fontSize = 12.sp), maxLines = 1)
            }
        }
        if (r.ferdig != null) {
            val boks = if (ferdig) "☑" else "☐"
            Text(
                boks,
                modifier = GlanceModifier.padding(start = 8.dp, end = 2.dp).let { m ->
                    if (r.handling != null)
                        m.clickable(actionRunCallback<KryssAv>(actionParametersOf(handlingKey to r.handling)))
                    else m
                },
                style = TextStyle(color = tekstFarge, fontSize = 22.sp),
            )
        }
    }
}

class Oppfrisk : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Oppdatering.hentWidget(context, glanceId)
    }
}

class KryssAv : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val handling = parameters[handlingKey] ?: return
        val p = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, glanceId)
        val modul = Lager.modul(context, p[N.modul]) ?: return
        // Vis avkrysset med en gang
        p[N.data]?.let { d ->
            updateAppWidgetState(context, glanceId) { it[N.data] = markerFerdig(d, handling) }
            widgetFor(context, glanceId).update(context, glanceId)
        }
        try {
            Api.handling(context, modul, handling)
        } catch (e: Exception) {
            updateAppWidgetState(context, glanceId) { it[N.feil] = e.message ?: "Feil" }
            Oppdatering.hentWidget(context, glanceId) // rull tilbake den optimistiske avkrysningen
            updateAppWidgetState(context, glanceId) { it[N.feil] = e.message ?: "Feil" }
            widgetFor(context, glanceId).update(context, glanceId)
            return
        }
        Oppdatering.hentWidget(context, glanceId)
        Oppdatering.hentNaa(context) // andre widgets kan vise samme rad
    }
}
