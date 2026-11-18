package no.haalandent.widget

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Oppdatering.startPeriodisk(this)
    }
}

class MainActivity : ComponentActivity() {
    private var moduler by mutableStateOf(listOf<Modul>())
    private var melding by mutableStateOf<String?>(null)
    private var arbeider by mutableStateOf(false)

    private val skann = registerForActivityResult(ScanContract()) { res ->
        res.contents?.let { behandle(Uri.parse(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduler = Lager.moduler(this)
        intent?.data?.let { behandle(it) }
        setContent { Tema { Skjerm() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { behandle(it) }
    }

    override fun onResume() {
        super.onResume()
        moduler = Lager.moduler(this)
        Oppdatering.hentNaa(this)
        lifecycleScope.launch {
            for (m in Lager.moduler(this@MainActivity)) runCatching { Api.oppdaterTyper(this@MainActivity, m) }
            moduler = Lager.moduler(this@MainActivity)
        }
    }

    /** haalandent-widget://legg-til?url=...&kode=wpk_... */
    private fun behandle(uri: Uri) {
        if (uri.scheme != "haalandent-widget" || uri.host != "legg-til") {
            melding = "Ukjent QR-kode."
            return
        }
        val url = uri.getQueryParameter("url")
        val kode = uri.getQueryParameter("kode")
        if (url.isNullOrBlank() || (url != DEMO_URL && kode.isNullOrBlank())) {
            melding = "QR-koden mangler url eller kode."
            return
        }
        arbeider = true
        melding = "Legger til …"
        lifecycleScope.launch {
            melding = try {
                Api.par(this@MainActivity, url, kode ?: "")
                moduler = Lager.moduler(this@MainActivity)
                Oppdatering.hentNaa(this@MainActivity)
                "Lagt til. Legg nå til en widget på hjemskjermen."
            } catch (e: ApiFeil) {
                if (e.status == 401) "Koden er brukt eller utløpt. Lag en ny QR." else "Feil: ${e.message}"
            } catch (e: Exception) {
                "Feil: ${e.message}"
            }
            arbeider = false
        }
    }

    @Composable
    private fun Skjerm() {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Haalandent widget", style = MaterialTheme.typography.headlineSmall)
            Text("Versjon ${packageManager.getPackageInfo(packageName, 0).versionName}",
                style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    skann.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Skann QR fra modulens Mer-side")
                            .setBeepEnabled(false).setOrientationLocked(false)
                    )
                },
                enabled = !arbeider,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Legg til modul (skann QR)") }

            melding?.let { Text(it) }

            Text("Moduler", style = MaterialTheme.typography.titleMedium)
            if (moduler.isEmpty()) Text("Ingen ennå.")
            moduler.forEach { m ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.tittel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    m.erDemo -> "Innebygd demo, oppdiktede data"
                                    m.tokenKryptert == null -> "${m.url} – tilgang trukket, skann ny QR"
                                    else -> m.url
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = {
                            Lager.fjern(this@MainActivity, m.id)
                            moduler = Lager.moduler(this@MainActivity)
                            Oppdatering.hentNaa(this@MainActivity)
                        }) { Text("Fjern") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Widget: hold fingeren på hjemskjermen → Widgets → Haalandent widget. " +
                    "Hold inne på widgeten → Innstillinger for å bytte modul eller visning.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { behandle(Uri.parse("haalandent-widget://legg-til?url=demo")) },
                enabled = !arbeider,
            ) { Text("Legg til demo-modul") }
        }
    }
}

@Composable
fun Tema(innhold: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), content = innhold)
    }
}
