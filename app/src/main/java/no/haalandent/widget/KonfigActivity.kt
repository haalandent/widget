package no.haalandent.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Velg modul, type og ev. parametre for én widget. */
class KonfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var valgt by mutableStateOf<Pair<Modul, JSONObject>?>(null)
    private val paramValg = mutableStateMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent { Tema { Skjerm() } }
    }

    private fun typerFor(m: Modul): List<JSONObject> =
        (0 until m.typer.length()).map { m.typer.getJSONObject(it) }
            .filter { it.optString("form") == "liste" }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Skjerm() {
        val moduler = Lager.moduler(this)
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Velg visning", style = MaterialTheme.typography.headlineSmall)
            if (moduler.isEmpty()) {
                Text("Ingen moduler lagt til. Åpne appen og skann QR fra modulens Mer-side først.")
                Button(onClick = {
                    startActivity(Intent(this@KonfigActivity, MainActivity::class.java))
                    finish()
                }) { Text("Åpne appen") }
                return@Column
            }
            moduler.forEach { m ->
                Text(m.tittel, style = MaterialTheme.typography.titleMedium)
                typerFor(m).forEach { t ->
                    val erValgt = valgt?.first?.id == m.id && valgt?.second?.optString("id") == t.optString("id")
                    FilterChip(
                        selected = erValgt,
                        onClick = { valgt = m to t; standardParam(t) },
                        label = { Text(t.optString("tittel", t.optString("id"))) },
                    )
                }
            }
            valgt?.second?.optJSONArray("param")?.let { param ->
                for (i in 0 until param.length()) {
                    val p = param.getJSONObject(i)
                    val navn = p.getString("navn")
                    Text(p.optString("tittel", navn), style = MaterialTheme.typography.titleSmall)
                    val valg = p.optJSONArray("valg")
                    if (valg != null) for (j in 0 until valg.length()) {
                        val v = valg.getJSONObject(j)
                        val verdi = v.getString("verdi")
                        FilterChip(
                            selected = paramValg[navn] == verdi,
                            onClick = { paramValg[navn] = verdi },
                            label = { Text(v.optString("tekst", verdi)) },
                        )
                    }
                }
            }
            Button(onClick = { lagre() }, enabled = valgt != null, modifier = Modifier.fillMaxWidth()) {
                Text("Lagre")
            }
            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Avbryt") }
        }
    }

    private fun standardParam(t: JSONObject) {
        paramValg.clear()
        val param = t.optJSONArray("param") ?: return
        for (i in 0 until param.length()) {
            val p = param.getJSONObject(i)
            val forste = p.optJSONArray("valg")?.optJSONObject(0)?.optString("verdi") ?: continue
            paramValg[p.getString("navn")] = forste
        }
    }

    private fun lagre() {
        val (m, t) = valgt ?: return
        val query = paramValg.entries.joinToString("&") { (k, v) -> "${Uri.encode(k)}=${Uri.encode(v)}" }
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@KonfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@KonfigActivity, glanceId) {
                it[N.modul] = m.id
                it[N.type] = t.getString("id")
                it[N.param] = query
                it.remove(N.data)
                it.remove(N.feil)
            }
            ListeWidget().update(this@KonfigActivity, glanceId)
            Oppdatering.startPeriodisk(this@KonfigActivity)
            Oppdatering.hentNaa(this@KonfigActivity)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
