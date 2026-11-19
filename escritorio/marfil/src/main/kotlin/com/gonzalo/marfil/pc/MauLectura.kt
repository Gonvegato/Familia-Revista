package com.gonzalo.marfil.pc

import com.gonzalo.mau.Frases
import com.gonzalo.mau.ui.Mau

/**
 * Lo que Mau dice mientras lees (una por libro abierto): que se acaba el capítulo, que llevas mucho rato leyendo seguido, que es
 * tarde. Aquí solo se decide cuándo (puro, se prueba); el lector se lo cuenta a Mau con [MauLector]. Lo mismo que en Seshat del móvil.
 */
class MauLectura {
    private var capAntes: Int? = null
    private var quedanAntes = -1
    private val avisados = HashSet<Int>()

    /**
     * Has llegado a una página del capítulo [cap] y te quedan [quedan] por delante (0: la última a la vista). Devuelve cuántas
     * decir, o null: solo leyendo hacia delante dentro del mismo capítulo (abrir el libro, saltar con el índice o volver atrás no
     * avisan), al quedar 3 o menos, y una vez por capítulo.
     */
    fun pagina(cap: Int, quedan: Int): Int? {
        val avanzas = cap == capAntes && quedan in 0 until quedanAntes
        capAntes = cap; quedanAntes = quedan
        if (!avanzas || quedan !in 1..QUEDAN || cap in avisados) return null
        avisados += cap
        return quedan
    }

    private var rachaAntes = 0
    private var hito = 0

    /**
     * Llevas [min] minutos leyendo seguidos (los cuenta [Lectura]: 3 minutos sin tocar nada o cerrar el libro los vuelven a cero).
     * Devuelve el hito que acabas de pasar (30, 60 o 120 min), una vez por racha; si la racha vuelve a empezar, se puede volver a decir.
     */
    fun racha(min: Int): Int? {
        if (min < rachaAntes) hito = 0
        rachaAntes = min
        val h = HITOS.lastOrNull { min >= it } ?: return null
        if (h <= hito) return null
        hito = h
        return h
    }

    companion object {
        /** Cuántas páginas antes del final del capítulo avisa. */
        const val QUEDAN = 3
        /** Los minutos seguidos leyendo que se dicen. */
        val HITOS = listOf(30, 60, 120)
        /** De noche: de las 00:00 a las 05:59. */
        fun deNoche(hora: Int) = hora in 0..5
    }
}

/** Lo que el lector le dice a Mau (desde cualquier hilo: Mau lo pasa al de la pantalla). */
object MauLector {
    fun quedan(n: Int) = Mau.di(Frases.quedanPaginas(n))
    fun llevas(min: Int) = Mau.di(Frases.llevasLeyendo(min))
    fun libroAcabado(titulo: String?) = Mau.celebra(Frases.libroAcabado(titulo))

    /** Es tarde y sigues leyendo: una vez por noche. */
    fun tarde() = unaVezHoy("tardeLeyendo") { Mau.di(Mau.cerebro.elige(Frases.tardeLeyendo)) }

    /** El objetivo de lectura de hoy, cumplido: una vez al día. */
    fun objetivo() = unaVezHoy("objetivoLectura") { Mau.celebra(Frases.objetivoLectura) }

    /** [f], la primera vez de hoy que se pide [clave] (en el hilo de la pantalla, si Mau está en marcha). */
    private fun unaVezHoy(clave: String, f: () -> Unit) {
        if (!Mau.iniciado) return
        Mau.casa.enPrincipal { if (Mau.cerebro.primeraVezHoy(clave, Mau.ahora())) f() }
    }
}
