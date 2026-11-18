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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MaanedWidgetMottaker : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MaanedWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Oppdatering.startPeriodisk(context)
    }
}

/** Månedskalender for form=maned. Trykk på en dag åpner DagActivity med dagens liste. */
class MaanedWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            GlanceTheme {
                MaanedInnhold(context, currentState(), appWidgetId)
            }
        }
    }
}

private val linjeFarge = androidx.glance.color.ColorProvider(
    day = Color(0xFFC9CED6), night = Color(0xFF3A3F47))
private val ukedager = listOf("M", "T", "O", "T", "F", "L", "S")
private val maanedNavn = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb"))

@Composable
private fun MaanedInnhold(c: Context, p: Preferences, appWidgetId: Int) {
    val tekstFarge = GlanceTheme.colors.onSurface
    val svak = GlanceTheme.colors.onSurfaceVariant
    val oppsett = Intent(c, KonfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setData(Uri.parse("widget://oppsett/$appWidgetId"))

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground).padding(8.dp)
    ) {
        if (p[N.modul] == null) {
            Text("Trykk for å sette opp",
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(oppsett)),
                style = TextStyle(color = tekstFarge, fontSize = 14.sp))
            return@Column
        }
        val data = p[N.data]?.let { runCatching { JSONObject(it) }.getOrNull() }
        val feil = p[N.feil]
        val idag = LocalDate.now(ZoneId.systemDefault())
        val maaned = data?.str("maned")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: YearMonth.from(idag)

        // Overskrift: måned (åpner webappen), ↻
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val url = data?.str("url")
            Text(
                maaned.format(maanedNavn).replaceFirstChar { it.uppercase() },
                modifier = GlanceModifier.defaultWeight()
                    .let { m -> if (url != null) m.clickable(visUrl(url)) else m },
                style = TextStyle(color = tekstFarge, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                maxLines = 1,
            )
            data?.str("tittel")?.let {
                Text(it, modifier = GlanceModifier.padding(start = 6.dp),
                    style = TextStyle(color = svak, fontSize = 11.sp), maxLines = 1)
            }
            Text(
                if (feil != null && data != null) "⚠ ↻" else "↻",
                modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<Oppfrisk>()),
                style = TextStyle(color = svak, fontSize = 16.sp),
            )
        }

        if (data == null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(feil ?: "Henter …", style = TextStyle(color = svak, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(
                    if (feil != null && feil.contains("på nytt")) actionStartActivity(oppsett)
                    else actionRunCallback<Oppfrisk>()))
            return@Column
        }

        val dager = data.optJSONObject("dager") ?: JSONObject()
        val dagSti = data.str("dag")
        val modulId = p[N.modul]!!

        // Ukedager
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
            ukedager.forEach { d ->
                Text(d, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = svak, fontSize = 11.sp, textAlign = TextAlign.Center))
            }
        }

        // Rutenett, mandag først
        val forste = maaned.atDay(1)
        val forskyvning = forste.dayOfWeek.value - 1
        val antall = maaned.lengthOfMonth()
        val uker = (forskyvning + antall + 6) / 7
        // Linjene er bakgrunnen som skinner gjennom 1 dp mellomrom mellom rutene
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(6.dp)
            .background(linjeFarge).padding(0.5.dp)) {
            for (u in 0 until uker) {
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (k in 0 until 7) {
                        val nr = u * 7 + k - forskyvning + 1
                        if (nr < 1 || nr > antall) {
                            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(0.5.dp)) {
                                Box(modifier = GlanceModifier.fillMaxSize()
                                    .background(GlanceTheme.colors.surfaceVariant)) {}
                            }
                        } else {
                            val dato = maaned.atDay(nr)
                            DagCelle(c, dato, dato == idag, dager, dagSti, modulId, appWidgetId,
                                tekstFarge, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DagCelle(
    c: Context, dato: LocalDate, idag: Boolean, dager: JSONObject, dagSti: String?,
    modulId: String, appWidgetId: Int,
    tekstFarge: androidx.glance.unit.ColorProvider, modifier: GlanceModifier,
) {
    val iso = dato.toString()
    val prikker = dager.optJSONArray(iso)
    val klikk = if (dagSti != null) {
        val intent = Intent(c, DagActivity::class.java)
            .putExtra(DagActivity.MODUL, modulId)
            .putExtra(DagActivity.STI, dagSti.replace("{dato}", iso))
            .putExtra(DagActivity.DATO, iso)
            // unik data per celle, ellers gjenbrukes samme PendingIntent
            .setData(Uri.parse("widget://dag/$appWidgetId/$iso"))
        GlanceModifier.clickable(actionStartActivity(intent))
    } else GlanceModifier

    Box(modifier = modifier.fillMaxHeight().padding(0.5.dp)) {
    Column(modifier = klikk.fillMaxSize().background(GlanceTheme.colors.surface).padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            dato.dayOfMonth.toString(),
            modifier = if (idag) GlanceModifier.width(22.dp).cornerRadius(11.dp)
                .background(GlanceTheme.colors.primary) else GlanceModifier.width(22.dp),
            style = TextStyle(
                color = if (idag) GlanceTheme.colors.onPrimary else tekstFarge,
                fontSize = 12.sp, textAlign = TextAlign.Center,
                fontWeight = if (idag) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        if (prikker != null && prikker.length() > 0) {
            // Inntil 4 store prikker i 2×2 (én per person)
            val farger = (0 until minOf(4, prikker.length())).map {
                farge(prikker.optJSONObject(it)?.str("farge")) ?: Color.Gray
            }
            Column(modifier = GlanceModifier.padding(top = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                farger.chunked(2).forEachIndexed { r, par ->
                    if (r > 0) Spacer(GlanceModifier.height(3.dp))
                    Row {
                        par.forEachIndexed { i, f ->
                            if (i > 0) Spacer(GlanceModifier.width(3.dp))
                            Box(modifier = GlanceModifier.size(11.dp).cornerRadius(6.dp).background(f)) {}
                        }
                    }
                }
            }
        }
    }
    }
}
