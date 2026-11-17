package no.haalandent.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** Nøkler i hver widgets Glance-tilstand. */
object N {
    val modul = stringPreferencesKey("modul")
    val type = stringPreferencesKey("type")
    val param = stringPreferencesKey("param")
    val data = stringPreferencesKey("data")
    val feil = stringPreferencesKey("feil")
    val hentet = longPreferencesKey("hentet")
}

object Oppdatering {

    /** Henter data for én widget og tegner den på nytt. */
    suspend fun hentWidget(c: Context, id: GlanceId) {
        val p: Preferences = getAppWidgetState(c, PreferencesGlanceStateDefinition, id)
        val modul = Lager.modul(c, p[N.modul])
        val type = p[N.type]
        if (modul == null || type == null) {
            settFeil(c, id, if (p[N.modul] == null) null else "Modulen er fjernet. Sett opp widgeten på nytt.")
            return
        }
        try {
            val data = Api.hent(c, modul, type, p[N.param] ?: "")
            updateAppWidgetState(c, id) {
                it[N.data] = data.toString()
                it.remove(N.feil)
                it[N.hentet] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            settFeil(c, id, e.message ?: "Feil")
        }
        ListeWidget().update(c, id)
    }

    private suspend fun settFeil(c: Context, id: GlanceId, melding: String?) {
        updateAppWidgetState(c, id) {
            if (melding == null) {
                it.remove(N.feil)
            } else {
                it[N.feil] = melding
            }
        }
        ListeWidget().update(c, id)
    }

    suspend fun hentAlle(c: Context) {
        val ider = GlanceAppWidgetManager(c).getGlanceIds(ListeWidget::class.java)
        for (id in ider) hentWidget(c, id)
        planleggGyldigTil(c, ider)
    }

    /** Neste henting senest ved laveste gyldig_til (minst 1 min fram). */
    private suspend fun planleggGyldigTil(c: Context, ider: List<GlanceId>) {
        val naa = System.currentTimeMillis()
        val tider = ider.mapNotNull { id ->
            val p = getAppWidgetState<Preferences>(c, PreferencesGlanceStateDefinition, id)
            p[N.data]?.let { d ->
                runCatching {
                    OffsetDateTime.parse(JSONObject(d).getString("gyldig_til")).toInstant().toEpochMilli()
                }.getOrNull()
            }
        }
        val forste = tider.minOrNull() ?: return
        val forsinkelse = (forste - naa).coerceAtLeast(60_000L)
        WorkManager.getInstance(c).enqueueUniqueWork(
            "gyldig_til", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<Henter>()
                .setInitialDelay(forsinkelse, TimeUnit.MILLISECONDS)
                .setConstraints(nett())
                .build()
        )
    }

    private fun nett() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun startPeriodisk(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "periodisk", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Henter>(15, TimeUnit.MINUTES).setConstraints(nett()).build()
        )
    }

    fun hentNaa(c: Context) {
        WorkManager.getInstance(c).enqueueUniqueWork(
            "naa", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<Henter>().setConstraints(nett()).build()
        )
    }
}

class Henter(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Oppdatering.hentAlle(applicationContext)
        return Result.success()
    }
}
