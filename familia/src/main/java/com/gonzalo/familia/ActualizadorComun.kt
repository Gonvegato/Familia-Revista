package com.gonzalo.familia

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.File

/**
 * Lo común del actualizador de las apps de la familia en Android (versión, validación de firma, instalación silenciosa y
 * descarga dual desde Google Drive o kDrive).
 */
object ActualizadorComun {
    fun mayor(a: List<Int>, b: List<Int>): Boolean {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** La versión más nueva de los archivos remotos (Google Drive o kDrive). */
    fun ultimaDual(archivos: List<NubeFamilia.VersionRemota>, versionDe: (String) -> List<Int>?): Pair<NubeFamilia.VersionRemota, List<Int>>? =
        archivos.mapNotNull { a -> versionDe(a.nombre)?.let { a to it } }
            .fold(null as Pair<NubeFamilia.VersionRemota, List<Int>>?) { m, x -> if (m == null || mayor(x.second, m.second)) x else m }

    fun hayInternet(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val c = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @Suppress("DEPRECATION")
    fun validaApk(ctx: Context, f: File): Long? {
        val pm = ctx.packageManager
        val nuevo = pm.getPackageArchiveInfo(f.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        val actual = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (nuevo.packageName != ctx.packageName || nuevo.longVersionCode <= actual.longVersionCode) return null
        val a = actual.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val n = nuevo.signingInfo?.let { it.signingCertificateHistory ?: it.apkContentsSigners }?.map { it.toCharsString() }?.toSet().orEmpty()
        return if (n.isEmpty() || (a.isNotEmpty() && n.containsAll(a))) nuevo.longVersionCode else null
    }

    fun puedeSilenciosa(ctx: Context, sdk: Int = Build.VERSION.SDK_INT, target: Int = ctx.applicationInfo.targetSdkVersion): Boolean {
        if (sdk < 31 || !ctx.packageManager.canRequestPackageInstalls()) return false
        val minimo = when { sdk >= 37 -> 35; sdk >= 36 -> 34; sdk >= 35 -> 33; sdk >= 34 -> 31; sdk >= 33 -> 30; else -> 29 }
        return target >= minimo
    }

    fun esperaParaInstalar(ctx: Context, ocupada: Int, dejoDeVerseEn: Long, conAlgoAbiertoMs: Long = 15 * 60_000L): Long {
        val pantalla = ctx.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
        val bloqueada = ctx.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!pantalla || bloqueada) return 30 * 60_000L
        if (ocupada > 0) {
            val fuera = if (dejoDeVerseEn == 0L) 0L else System.currentTimeMillis() - dejoDeVerseEn
            if (fuera < conAlgoAbiertoMs) return (conAlgoAbiertoMs - fuera).coerceAtLeast(60_000L)
        }
        return 0L
    }

    fun instalaApk(ctx: Context, f: File, silenciosa: Boolean, receiverClass: Class<*>) {
        val pi = ctx.packageManager.packageInstaller
        val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        p.setAppPackageName(ctx.packageName)
        p.setSize(f.length())
        if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(
            if (silenciosa) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED else PackageInstaller.SessionParams.USER_ACTION_REQUIRED
        )
        val id = pi.createSession(p)
        pi.openSession(id).use { s ->
            f.inputStream().use { inp -> s.openWrite("app.apk", 0, f.length()).use { out -> inp.copyTo(out); s.fsync(out) } }
            val i = Intent(ctx, receiverClass).setPackage(ctx.packageName)
            s.commit(PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
        }
    }
}
