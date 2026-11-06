package com.gonzalo.cobalto

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Los widgets, dibujados en el ordenador con obras de ejemplo (build/outputs/widgets/). Sin -Proborazzi.test.record solo se comprueba que se dibujan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun siembra(vacia: Boolean = false) {
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        val e = Escritos.get(ctx)
        if (vacia) return
        Ejemplo.siembra(e)
    }

    private val tamanos = listOf(WTipo.HOY to (330f to 165f), WTipo.OBRA to (330f to 165f), WTipo.CITA to (330f to 200f), WTipo.MARCA to (330f to 80f), WTipo.MARCA_PAPEL to (330f to 165f))

    private fun foto(nombre: String, tipo: WTipo, w: Float, h: Float, noche: Boolean) {
        val pag = Widgets.componer(ctx, tipo, 1, w, h)
        val bm = pag.bitmap(ctx, w, h, noche, 2f)
        File("build/outputs/widgets").mkdirs()
        File("build/outputs/widgets/$nombre.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dibujaTodosSinFallar() {
        for (vacia in listOf(false, true)) {
            siembra(vacia)
            Skin.origenAtrio = false; Skin.invertido = false
            for ((t, s) in tamanos) for (noche in listOf(false, true)) for ((w, h) in listOf(s, 150f to 150f, 330f to 90f)) {
                val pag = Widgets.componer(ctx, t, 1, w, h)
                val bm = pag.bitmap(ctx, w, h, noche, 1f)
                assertTrue("$t sale con tamaño", bm.width > 0 && bm.height > 0)
            }
        }
    }

    @Test fun elWidgetDeLaObraAbreLaEscenaYElSprint() {
        siembra()
        val pag = Widgets.componer(ctx, WTipo.OBRA, 7, 330f, 165f)
        assertTrue(pag.zonas.any { it.desc.startsWith("Seguir escribiendo") })
        assertTrue(pag.zonas.any { it.desc.startsWith("Sprint en") })
        assertTrue("hay varias obras en marcha: se puede pasar a otra", pag.zonas.any { it.desc == "Otra obra" })
        // Con una obra fija, solo esa
        val fija = Escritos.get(ctx).obras().first { it.s("titulo") == "Cuentos del norte" }
        Widgets.ponFijo(ctx, 7, fija.id)
        assertEquals(fija.id, Widgets.fijoDe(ctx, 7)?.id)
        val p2 = Widgets.componer(ctx, WTipo.OBRA, 7, 330f, 165f)
        assertTrue(p2.zonas.any { it.desc == "Abrir Cuentos del norte" }); assertFalse(p2.zonas.any { it.desc == "Otra obra" })
        Widgets.ponFijo(ctx, 7, null); assertNull(Widgets.fijoDe(ctx, 7))
    }

    @Test fun dibujaLasVistasPrevias() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        Skin.origenAtrio = false; Skin.invertido = false
        for ((t, s) in tamanos) foto("w_prev_${t.name.lowercase().replace("_", "")}", t, s.first, s.second, false)
        for ((t, s) in tamanos) foto("noche_${t.name.lowercase()}", t, s.first, s.second, true)
        Skin.invertido = true
        for ((t, s) in tamanos) foto("inv_${t.name.lowercase()}", t, s.first, s.second, false)
        Skin.invertido = false
        siembra(vacia = true)
        foto("vacio_obra", WTipo.OBRA, 330f, 165f, false)
        foto("vacio_hoy", WTipo.HOY, 330f, 165f, false)
    }
}

/** Obras de ejemplo para las pruebas y las capturas: tres en marcha, una en pausa, una terminada y una archivada, con palabras de varios días. */
object Ejemplo {
    fun siembra(e: Escritos) {
        val hoy = Escritos.hoy()
        fun obra(titulo: String, autor: String, genero: String, color: String, meta: Int, diaria: Int, estado: EstadoObra, caps: List<Pair<String, List<Pair<String, String>>>>): Doc {
            val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", titulo); put("autor", autor); put("genero", genero); put("color", color); put("meta", meta); put("diaria", diaria); put("sinopsis", "Una sinopsis breve de $titulo.") }
            caps.forEachIndexed { i, (ct, escenas) ->
                val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", ct); if (i == 0 && caps.size > 1) put("hecho", 1) }
                escenas.forEach { (st, tx) -> e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", st); put("texto", tx) } }
            }
            if (estado != EstadoObra.ESCRIBIENDO) e.ponEstado(o.id, estado)
            return o
        }
        val p1 = "La ciudad despertaba despacio, como si cada ventana tuviera que decidir por su cuenta si merecía la pena abrirse aquella mañana."
        val p2 = "Nadie le había dicho a Elena que el silencio pudiera pesar tanto, ni que fuera posible echar de menos un ruido que nunca se había oído."
        val p3 = "Al fondo del pasillo, la puerta del taller seguía cerrada, y detrás de ella alguien afinaba un instrumento que no existía."
        val norte = obra("Cuentos del norte", "", "Relatos", "#2F5D8A", 30000, 300, EstadoObra.ESCRIBIENDO, listOf(
            "El faro" to listOf("La llegada" to "# La llegada\n\n$p3\n\nEl viento traía sal y una promesa que nadie supo cumplir."),
            "La casa del río" to listOf("Primera noche" to "Primera noche en la casa. Nada que contar todavía, salvo el río.")
        ))
        val ciudad = obra("La ciudad de cristal", "Gonzalo", "Novela", "#C8372D", 80000, 500, EstadoObra.ESCRIBIENDO, listOf(
            "Donde empieza todo" to listOf("El despertar" to "$p1\n\n$p2\n\nElena bajó las escaleras contando los peldaños, como siempre, y se equivocó, como nunca.", "La plaza" to "La plaza estaba vacía. *Vacía de verdad*, pensó, no como los domingos."),
            "Lo que no se dijo" to listOf("La carta" to "Querida nadie:\n\nHoy he vuelto a escribirte, y hoy tampoco voy a mandar esto."),
            "El taller" to listOf("Afinando" to "")
        ))
        obra("Lo que sabe el agua", "Gonzalo", "Novela corta", "#3F7A4A", 20000, 0, EstadoObra.ESCRIBIENDO, listOf("Uno" to listOf("Orilla" to "El agua sabe cosas que no cuenta a cualquiera.")))
        obra("Cartas a nadie", "Gonzalo", "Epistolar", "#7A4E8C", 0, 0, EstadoObra.PAUSA, listOf("Enero" to listOf("1" to "Primera carta.")))
        obra("El último verano", "Gonzalo", "Novela", "#B8861B", 60000, 0, EstadoObra.TERMINADA, listOf("Fin" to listOf("Final" to "Y así se acabó el verano, sin que nadie lo viera venir.")))
        obra("Borrador viejo", "", "", "#4A5560", 0, 0, EstadoObra.ARCHIVO, listOf("Uno" to listOf("Algo" to "Un borrador de otra época.")))
        // La última escena tocada: la de La ciudad de cristal (un momento después, para que no empate con las de antes en el mismo milisegundo)
        Thread.sleep(5)
        e.escribe(e.escenasDe(ciudad.id).first().id, "$p1\n\n$p2\n\nElena bajó las escaleras contando los peldaños, como siempre, y se equivocó, como nunca. Y siguió bajando.")
        // Palabras de varios días (en este dispositivo y en el PC)
        listOf(0 to 412, 1 to 530, 2 to 0, 3 to 760, 4 to 215, 5 to 480, 6 to 190, 7 to 620, 8 to 0, 9 to 330, 10 to 505, 11 to 150, 12 to 0, 13 to 410).forEach { (atras, n) ->
            if (n > 0) e.pon(JSONObject().put("id", "l_${ciudad.id}_${hoy - atras}").put("t", Escritos.T_LOG).put("pid", ciudad.id).put("dia", hoy - atras).put("dev", JSONObject().put(Cuenta.dispositivo.ifBlank { "movil" }, n * 2 / 3).put("pc", n / 3)))
        }
        e.pon(JSONObject().put("id", "l_${norte.id}_$hoy").put("t", Escritos.T_LOG).put("pid", norte.id).put("dia", hoy).put("dev", JSONObject().put(Cuenta.dispositivo.ifBlank { "movil" }, 96)))
        e.vacia()
    }

    /** Lo de dentro de «La ciudad de cristal»: fichas (con datos, alias y uniones), notas y la línea temporal. Devuelve la obra. */
    fun estudio(e: Escritos): Doc {
        val o = e.obras().first { it.s("titulo") == "La ciudad de cristal" }
        val pid = o.id
        fun campos(vararg kv: Pair<String, String>) = org.json.JSONArray().also { a -> kv.forEach { (k, v) -> a.put(JSONObject().put("k", k).put("v", v)) } }
        val elena = e.nuevo(Escritos.T_FICHA, pid) {
            put("tipo", "personaje"); put("nombre", "Elena"); put("alias", "Ele"); put("color", "#B0102F")
            put("resumen", "Cuenta los peldaños para no pensar en lo que dejó arriba.")
            put("campos", campos("Edad" to "34", "Aspecto" to "Pelo corto, gris antes de tiempo; una cicatriz en la ceja", "Quiere" to "Que la ciudad vuelva a hacer ruido", "Teme" to "El silencio del taller"))
            put("texto", "Elena creció en el piso de arriba del taller, entre el olor a barniz y las cuerdas a medio tensar. Su padre afinaba instrumentos para una orquesta que ya no existía, y ella aprendió a distinguir una nota falsa antes que a leer.\n\nCuando él desapareció, Elena cerró el taller con llave y no volvió a subir. Desde entonces cuenta los peldaños de todas las escaleras: es lo único que todavía puede medir.\n\n> Nadie afina para nadie.")
        }
        val clara = e.nuevo(Escritos.T_FICHA, pid) {
            put("tipo", "personaje"); put("nombre", "Clara"); put("color", "#3E8E9E")
            put("resumen", "La hermana que se quedó en la ciudad y nunca preguntó por qué.")
            put("campos", campos("Edad" to "29", "Oficio" to "Restauradora de vidrieras"))
        }
        val plaza = e.nuevo(Escritos.T_FICHA, pid) {
            put("tipo", "lugar"); put("nombre", "La plaza"); put("color", "#C9A227")
            put("resumen", "Vacía de verdad, no como los domingos.")
        }
        e.nuevo(Escritos.T_FICHA, pid) { put("tipo", "objeto"); put("nombre", "El diapasón"); put("color", "#6B6B6B"); put("resumen", "Lo único que Elena se llevó del taller.") }
        e.nuevo(Escritos.T_REL, pid) { put("a", elena.id); put("b", clara.id); put("titulo", "hermanas"); put("texto", "Se quieren sin decírselo y se reprochan todo lo demás.") }
        e.nuevo(Escritos.T_REL, pid) { put("a", elena.id); put("b", plaza.id); put("titulo", "la evita"); put("flecha", 1) }
        e.nuevo(Escritos.T_NOTA, pid) { put("tipo", "idea"); put("titulo", "El ruido que nunca se oyó"); put("texto", "¿Y si el instrumento que alguien afina detrás de la puerta es el que el padre no llegó a terminar?"); put("color", "#C9A227"); put("fija", 1) }
        e.nuevo(Escritos.T_NOTA, pid) { put("tipo", "investigacion"); put("titulo", "Cómo se afina un clavicordio"); put("texto", "Se afina por quintas, con un diapasón y mucha paciencia.\n\nLas cuerdas de latón suenan más brillantes que las de hierro; con la humedad, todo baja un poco."); put("color", "#4A6FA5") }
        e.nuevo(Escritos.T_NOTA, pid) { put("tipo", "plantilla"); put("titulo", "Ficha de escena"); put("texto", "Quién quiere qué:\nQué se lo impide:\nCómo acaba:"); put("color", "#5B8C5A") }
        val esc = e.escenasDe(pid)
        e.nuevo(Escritos.T_HITO, pid) { put("titulo", "El padre cierra el taller"); put("cuando", "Hace diez años"); put("orden", 1.0); put("nota", "Nadie sabe adónde fue.") }
        e.nuevo(Escritos.T_HITO, pid) { put("titulo", "Elena vuelve a la ciudad"); put("cuando", "Un martes de marzo"); put("orden", 2.0); put("eid", esc.first().id) }
        e.nuevo(Escritos.T_HITO, pid) { put("titulo", "La carta sin enviar"); put("cuando", "Esa misma noche"); put("orden", 3.0); put("eid", esc.getOrNull(2)?.id ?: esc.first().id) }
        // Un borrador de la primera escena (para comparar y mezclar)
        val primera = esc.first()
        e.nuevo(Escritos.T_BORRADOR, pid) {
            put("eid", primera.id); put("nombre", "Otra manera de empezar")
            put("texto", primera.s("texto").replace("La ciudad despertaba despacio", "La ciudad se despertó de golpe").replace("Y siguió bajando.", "Y no volvió a contar."))
        }
        e.vacia()
        return o
    }
}
