package com.gonzalo.ammit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Una app que se puede abrir (una entrada del cajón de apps). */
class AppDeInicio(val componente: ComponentName, val nombre: String, val icono: ImageBitmap?) {
    val clave: String get() = componente.flattenToShortString()
    val paquete: String get() = componente.packageName
    val deLaFamilia: Boolean get() = Ammit.pasa(paquete)
}

object Apps {
    /** Todas las apps que se pueden abrir, colocadas como las dejaste ([Ammit.orden]). */
    fun todas(c: Context, tamIcono: Int = 144): List<AppDeInicio> {
        val pm = c.packageManager
        val l = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { r ->
            val ai = r.activityInfo
            AppDeInicio(ComponentName(ai.packageName, ai.name), r.loadLabel(pm).toString(),
                runCatching { bitmap(r.loadIcon(pm), tamIcono).asImageBitmap() }.getOrNull())
        }.distinctBy { it.clave }
        // La familia, primero la primera vez; luego, como las coloques
        val orden = Ammit.orden(c).ifEmpty { Ammit.FAMILIA.mapNotNull { f -> l.firstOrNull { it.paquete == f.paquete }?.clave } }
        return Ammit.ordena(l, { it.clave }, { it.nombre }, orden)
    }

    fun abrir(c: Context, a: AppDeInicio) {
        if (Ammit.encendida && !a.deLaFamilia) { JuicioActivity.abrir(c, a.nombre); return }
        runCatching {
            c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.componente)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
    }

    private fun bitmap(d: Drawable, tam: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == tam) return d.bitmap
        val b = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, tam, tam); d.draw(Canvas(b))
        return b
    }
}
