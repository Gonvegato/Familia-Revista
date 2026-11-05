package com.gonzalo.familia

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File

/**
 * Entrar una vez: entras en Azabache (o en cualquiera de la familia) y las demás entran solas. La app que ya tiene la cuenta guarda
 * tu usuario y tu contraseña cifrados en el móvil (ver [DiscoCifrado]) y se los da, por un [AccesoProvider], solo a tus otras
 * apps de la familia **firmadas con la misma clave**: ninguna otra app del móvil puede pedírselos, y nunca van a la nube.
 */
object Acceso {
    /** La huella (SHA-256) del certificado de la familia: la firma común de las cuatro apps. */
    val CERTIFICADO: ByteArray = "1d3d3e1a631bbbfdff5e05d449a0fb52f968dae87c596a9bdd4a23fe08671463".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun disco(ctx: Context) = DiscoCifrado.de(ctx, File(ctx.filesDir, "familia_acceso"))

    fun guarda(ctx: Context, usuario: String, contrasena: String) {
        disco(ctx).escribeTexto("acceso.json", Json.escribe(mapOf("u" to usuario, "c" to contrasena)))
    }

    fun olvida(ctx: Context) = disco(ctx).borra("acceso.json")

    internal fun lee(ctx: Context): Pair<String, String>? {
        val m = disco(ctx).leeTexto("acceso.json")?.let { runCatching { Json.lee(it) }.getOrNull() }.comoMapa()
        val u = m["u"].comoTexto(); val c = m["c"].comoTexto()
        return if (u.isNotBlank() && c.isNotBlank()) u to c else null
    }

    /** ¿Lleva este paquete la firma de la familia? */
    fun esDeLaFamilia(ctx: Context, paquete: String): Boolean =
        paquete in LaFamilia.APPS && runCatching { ctx.packageManager.hasSigningCertificate(paquete, CERTIFICADO, PackageManager.CERT_INPUT_SHA256) }.getOrDefault(false)

    /**
     * Sin sesión aquí: se pregunta a las hermanas instaladas si alguna tiene la cuenta, y se entra con ella. Bloquea (red).
     * Devuelve true si ha entrado.
     */
    fun pideAHermanas(ctx: Context): Boolean {
        val fam = LaFamilia.familia
        if (fam.sesion.dentro) return true
        if (!esDeLaFamilia(ctx, ctx.packageName)) return false
        for (p in LaFamilia.APPS) {
            if (p == ctx.packageName || !esDeLaFamilia(ctx, p)) continue
            val par = runCatching {
                ctx.contentResolver.query(Uri.parse("content://$p.familia.acceso/sesion"), null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
                }
            }.getOrNull() ?: continue
            if (runCatching { LaFamilia.entrar(par.first, par.second) }.isSuccess) return true
        }
        return false
    }
}

/** Da la cuenta a las hermanas de la familia (y a nadie más). */
class AccesoProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val ctx = context ?: return null
        val quien = callingPackage ?: return null
        // Solo a una hermana con la firma de la familia, y solo si esta app también la lleva
        if (quien == ctx.packageName || !Acceso.esDeLaFamilia(ctx, quien) || !Acceso.esDeLaFamilia(ctx, ctx.packageName)) return null
        val par = Acceso.lee(ctx) ?: return null
        return MatrixCursor(arrayOf("usuario", "contrasena")).apply { addRow(arrayOf(par.first, par.second)) }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
