package com.gonzalo.ptah

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import androidx.compose.runtime.mutableIntStateOf
import com.gonzalo.ptah.EspejoCuenta.Tipo
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Tus diseños, en tu cuenta: `ajustes/ptah_disenos`, **un campo por diseño** (su id, `d` y 10 cifras hexadecimales) con el diseño en JSON
 * ([Diseno.aJson]). De cada diseño gana el último cambio, como en todo lo demás de la familia; borrar uno es dejar su campo en `""`.
 * Las preferencias de aquí son la copia para leerlos al momento y sin red ([EspejoCuenta]). Las fotos no van: se quedan en el móvil ([Fotos]).
 */
object Disenos {
    val GRUPO = EspejoCuenta.Grupo("ptah_disenos", "ptah_disenos", { k -> if (k.startsWith("d") && k.length in 2..32) Tipo.TEXTO else null },
        alLlegar = { c -> Widgets.pedir(c) })

    /** Sube con cada cambio de aquí (las pantallas lo leen para volver a pintarse). */
    private val ver = mutableIntStateOf(0)
    /** En las pruebas de pantallas: los diseños, fijos. */
    @Volatile var fijos: List<Diseno>? = null

    private fun p(ctx: Context) = EspejoCuenta.prefs(ctx, GRUPO)

    fun todos(ctx: Context): List<Diseno> {
        ver.intValue; EspejoCuenta.version
        fijos?.let { return it }
        return EspejoCuenta.base(ctx, GRUPO).all.mapNotNull { (k, v) -> if (k.startsWith("d")) Diseno.deJson(v as? String) else null }
            .sortedWith(compareByDescending<Diseno> { it.creado }.thenBy { it.nombre })
    }

    fun de(ctx: Context, id: String): Diseno? = fijos?.firstOrNull { it.id == id } ?: Diseno.deJson(EspejoCuenta.base(ctx, GRUPO).getString(id, null))

    /** Lo guarda (aquí y en tu cuenta) y repinta los widgets que lo llevan. */
    fun guarda(ctx: Context, d: Diseno) {
        val n = d.copy(cambiado = System.currentTimeMillis(), creado = d.creado.takeIf { it > 0 } ?: System.currentTimeMillis())
        if (fijos != null) { fijos = fijos!!.filter { it.id != n.id } + n; ver.intValue++; return }
        p(ctx).edit().putString(n.id, n.aJson()).apply()
        ver.intValue++
        Widgets.pedir(ctx)
    }

    /** Lo borra (aquí, en la cuenta y, si tenía, su foto de este móvil). Los widgets que lo llevaban lo dicen y dejan elegir otro. */
    fun borra(ctx: Context, id: String) {
        if (fijos != null) { fijos = fijos!!.filter { it.id != id }; ver.intValue++; return }
        p(ctx).edit().remove(id).apply()
        Fotos.borra(ctx, id)
        ver.intValue++
        Widgets.pedir(ctx)
    }

    /** Una copia (con otro id y «(copia)»). */
    fun duplica(ctx: Context, d: Diseno): Diseno = d.copy(id = Diseno.nuevoId(), nombre = d.nombre + " (copia)", creado = System.currentTimeMillis()).also { guarda(ctx, it) }
}

/** Los colores que has usado hace poco (para el selector de color): tuyos, en `ajustes/ptah_general` (`recientes`, con comas). */
object Recientes {
    val GRUPO = EspejoCuenta.Grupo("ptah_general", "ptah_general", { k -> if (k == "recientes") Tipo.TEXTO else null })
    private val ver = mutableIntStateOf(0)
    fun lista(ctx: Context): List<String> { ver.intValue; EspejoCuenta.version; return EspejoCuenta.prefs(ctx, GRUPO).getString("recientes", "").orEmpty().split(',').filter { Col.argb(it) != null } }
    fun pon(ctx: Context, hex: String) {
        if (Col.argb(hex) == null) return
        val l = (listOf(hex.uppercase()) + lista(ctx)).distinct().take(12)
        EspejoCuenta.prefs(ctx, GRUPO).edit().putString("recientes", l.joinToString(",")).apply()
        ver.intValue++
    }
}

/** Lo de este móvil para dibujar: la hora, el tiempo guardado, la batería, los iconos de las apps y las fotos. */
class DatosAndroid(private val ctx: Context) : Datos {
    override fun ahora(): ZonedDateTime = ZonedDateTime.now()
    override fun tiempo(d: Diseno): Prevision? = sitioDe(ctx, d)?.let { (la, lo) -> CacheTiempo.guardada(ctx, la, lo) }
    override fun bateria(): Bateria? = runCatching {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (nivel < 0) null else Bateria((nivel * 100 / escala.coerceAtLeast(1)).coerceIn(0, 100), st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
    }.getOrNull()

    override fun iconoApp(paquete: String): Bitmap? = iconos.getOrPut(paquete) {
        runCatching {
            val dr = ctx.packageManager.getApplicationIcon(paquete)
            val b = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, 144, 144); dr.draw(Canvas(b)); b
        }.getOrNull() ?: return null
    }

    override fun nombreApp(paquete: String): String? = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrNull()
    override fun foto(id: String): Bitmap? = Fotos.carga(ctx, id)

    companion object { private val iconos = ConcurrentHashMap<String, Bitmap>() }
}
