package no.haalandent.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Popup fra månedskalenderen: én dags liste (form=liste fra `dag`-stien). */
class DagActivity : ComponentActivity() {
    companion object {
        const val MODUL = "modul"
        const val STI = "sti"
        const val DATO = "dato"
    }

    private var data by mutableStateOf<JSONObject?>(null)
    private var melding by mutableStateOf<String?>(null)
    private var arbeider by mutableStateOf(false)

    private val modul get() = Lager.modul(this, intent.getStringExtra(MODUL))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dato = intent.getStringExtra(DATO)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val overskrift = dato?.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale.forLanguageTag("nb")))
            ?.replaceFirstChar { it.uppercase() } ?: "Dag"
        hent()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Dialog(onDismissRequest = { finish() }) { Innhold(overskrift) }
            }
        }
    }

    private fun hent() {
        val m = modul ?: run { melding = "Modulen er fjernet."; return }
        val sti = intent.getStringExtra(STI) ?: return
        arbeider = true
        lifecycleScope.launch {
            try {
                data = Api.hentSti(this@DagActivity, m, sti)
                melding = null
            } catch (e: Exception) {
                melding = e.message ?: "Feil"
            }
            arbeider = false
        }
    }

    private fun kryss(handling: String) {
        val m = modul ?: return
        arbeider = true
        lifecycleScope.launch {
            try {
                Api.handling(this@DagActivity, m, handling)
                Oppdatering.hentNaa(this@DagActivity)
                hent()
            } catch (e: Exception) {
                melding = e.message ?: "Feil"
                arbeider = false
            }
        }
    }

    private fun aapne(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        finish()
    }

    @Composable
    private fun Innhold(overskrift: String) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(overskrift, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                melding?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val d = data
                if (d == null) {
                    if (melding == null) Text("Henter …")
                } else {
                    val liste = rader(d)
                    if (liste.isEmpty()) Text("Ingenting denne dagen.")
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(liste) { r -> RadLinje(r) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    val url = d?.str("url")
                        ?: liste_url(d)
                    if (url != null) TextButton(onClick = { aapne(url) }) { Text("Åpne i webappen") }
                    TextButton(onClick = { finish() }) { Text("Lukk") }
                }
            }
        }
    }

    /** Bruk første rads url hvis lista ikke har egen. */
    private fun liste_url(d: JSONObject?): String? = d?.let { rader(it).firstNotNullOfOrNull { r -> r.url } }

    @Composable
    private fun RadLinje(r: Rad) {
        val ferdig = r.ferdig == true
        Row(
            modifier = Modifier.fillMaxWidth()
                .let { m -> if (r.url != null) m.clickable { aapne(r.url) } else m }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(4.dp).height(32.dp)
                    .background(r.farge ?: Color.Gray, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (r.heldag) "Hele dagen"
                else r.start?.atZoneSameInstant(ZoneId.systemDefault())?.format(klokke) ?: "",
                modifier = Modifier.width(52.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    listOfNotNull(r.ikon, r.tekst).joinToString(" "),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (ferdig) TextDecoration.LineThrough else null,
                )
                r.undertekst?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (r.ferdig != null) {
                Checkbox(
                    checked = ferdig,
                    enabled = r.handling != null && !arbeider,
                    onCheckedChange = { r.handling?.let { kryss(it) } },
                )
            }
        }
    }
}
