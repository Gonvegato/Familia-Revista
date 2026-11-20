package com.gonzalo.cobre

import android.content.Context
import com.gonzalo.familia.NubeFamilia
import java.io.File
import java.io.InputStream

/** Delega en [NubeFamilia] (Google Drive principal, kDrive como respaldo durante la transición). */
object KDrive {
    var api: String
        get() = NubeFamilia.api
        set(v) { NubeFamilia.api = v }

    fun token(ctx: Context) = NubeFamilia.token(ctx)
    fun drive(ctx: Context) = NubeFamilia.drive(ctx)
    fun gdriveOn(ctx: Context) = NubeFamilia.gdriveOn(ctx)
    fun kdriveOn(ctx: Context) = NubeFamilia.kdriveOn(ctx)
    fun on(ctx: Context) = NubeFamilia.on(ctx)
    fun nombreNube(ctx: Context) = NubeFamilia.nombreNube(ctx)

    fun save(ctx: Context, token: String, drive: String) = NubeFamilia.save(ctx, token, drive)
    fun confirma(ctx: Context) = NubeFamilia.confirma(ctx)
    fun descarta(ctx: Context) = NubeFamilia.descarta(ctx)
    fun forget(ctx: Context) = NubeFamilia.forget(ctx)
    fun migraALaCuenta(ctx: Context) = NubeFamilia.migraALaCuenta(ctx)
    suspend fun test(ctx: Context) = NubeFamilia.testKDrive(ctx)

    fun children(ctx: Context, dir: Long) = NubeFamilia.childrenKDrive(ctx, dir)
    fun home(ctx: Context) = NubeFamilia.homeKDrive(ctx)
    fun carpeta(ctx: Context, vararg ruta: String) = NubeFamilia.carpetaKDrive(ctx, *ruta)
    fun direccion(ctx: Context, file: Long) = NubeFamilia.direccionKDrive(ctx, file)
    fun cabeceras(ctx: Context) = NubeFamilia.cabecerasKDrive(ctx)

    fun upload(ctx: Context, path: String, name: String, size: Long, onProgress: (Long) -> Unit = {}, input: () -> InputStream) =
        NubeFamilia.uploadKDrive(ctx, path, name, size, onProgress, input)
    fun versiones(ctx: Context) = NubeFamilia.versionesKDrive(ctx)
    fun versionesDual(ctx: Context) = NubeFamilia.versionesDual(ctx)
    fun bajaVersion(ctx: Context, v: NubeFamilia.VersionRemota, dest: File) = NubeFamilia.bajaVersion(ctx, v, dest)
    fun download(ctx: Context, file: Long, dest: File) = NubeFamilia.downloadKDrive(ctx, file, dest)
    fun reach(ctx: Context) = NubeFamilia.reachKDrive(ctx)
    fun delete(ctx: Context, file: Long) = NubeFamilia.deleteKDrive(ctx, file)
}
