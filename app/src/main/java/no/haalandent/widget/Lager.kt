package no.haalandent.widget

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** En modul appen er paret med. url = "demo" for den innebygde demo-modulen. */
data class Modul(
    val id: String,
    val url: String,
    val tittel: String,
    val tokenKryptert: String?,
    val typer: JSONArray,
    val ny: JSONObject? = null,   // {tittel, url med {dato}} fra roten, valgfri
) {
    val erDemo get() = url == DEMO_URL

    fun tilJson() = JSONObject()
        .put("id", id).put("url", url).put("tittel", tittel)
        .put("token", tokenKryptert ?: JSONObject.NULL).put("typer", typer)
        .put("ny", ny ?: JSONObject.NULL)

    companion object {
        fun fraJson(o: JSONObject) = Modul(
            id = o.getString("id"),
            url = o.getString("url"),
            tittel = o.optString("tittel", o.getString("url")),
            tokenKryptert = o.optString("token").takeIf { it.isNotEmpty() && it != "null" },
            typer = o.optJSONArray("typer") ?: JSONArray(),
            ny = o.optJSONObject("ny"),
        )
    }
}

const val DEMO_URL = "demo"

/** Modullista i vanlige SharedPreferences; tokenet kryptert med nøkkel i Android Keystore. */
object Lager {
    private const val FIL = "lager"
    private const val NOKKEL_ALIAS = "widget-token"

    private fun prefs(c: Context) = c.getSharedPreferences(FIL, Context.MODE_PRIVATE)

    fun moduler(c: Context): List<Modul> {
        val a = JSONArray(prefs(c).getString("moduler", "[]"))
        return (0 until a.length()).map { Modul.fraJson(a.getJSONObject(it)) }
    }

    fun modul(c: Context, id: String?): Modul? = moduler(c).firstOrNull { it.id == id }

    private fun lagre(c: Context, liste: List<Modul>) {
        val a = JSONArray()
        liste.forEach { a.put(it.tilJson()) }
        prefs(c).edit().putString("moduler", a.toString()).apply()
    }

    /** Legger til eller erstatter (samme url). Returnerer modulens id. */
    fun leggTil(c: Context, url: String, tittel: String, token: String?, typer: JSONArray,
                ny: JSONObject? = null): String {
        val liste = moduler(c).toMutableList()
        val gammel = liste.firstOrNull { it.url == url }
        val id = gammel?.id ?: UUID.randomUUID().toString()
        liste.removeAll { it.url == url }
        liste.add(Modul(id, url, tittel, token?.let { krypter(it) }, typer, ny))
        lagre(c, liste)
        return id
    }

    fun fjern(c: Context, id: String) = lagre(c, moduler(c).filterNot { it.id == id })

    /** Ved 401: tokenet er trukket. Modulen beholdes så widgetene kan vise beskjed. */
    fun slettToken(c: Context, id: String) =
        lagre(c, moduler(c).map { if (it.id == id) it.copy(tokenKryptert = null) else it })

    fun token(m: Modul): String? = m.tokenKryptert?.let { runCatching { dekrypter(it) }.getOrNull() }

    // Demo-modulens avkryssinger
    fun demoFerdig(c: Context): Set<String> = prefs(c).getStringSet("demo_ferdig", emptySet())!!
    fun settDemoFerdig(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("demo_ferdig", s).apply()

    // --- Keystore ---
    private fun nokkel(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(NOKKEL_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                NOKKEL_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun krypter(tekst: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, nokkel())
        val iv = cipher.iv
        val ct = cipher.doFinal(tekst.toByteArray())
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun dekrypter(lagret: String): String {
        val alt = Base64.decode(lagret, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, nokkel(), GCMParameterSpec(128, alt, 0, 12))
        return String(cipher.doFinal(alt, 12, alt.size - 12))
    }
}
