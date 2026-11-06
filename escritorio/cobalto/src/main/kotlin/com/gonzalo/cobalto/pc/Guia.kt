package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.familia.pc.revista.*
import org.json.JSONObject

/** Un paso de la guía: qué es, cómo, el botón de acción y si se marca solo (auto) al detectar lo hecho. */
class Paso(val id: Int, val titulo: String, val auto: Boolean, val accion: String, val que: String, val como: List<String>, val hecho: String)

/** Lo que la guía necesita saber de una obra. */
class DatosGuia(val obra: JSONObject, val personajes: Int, val resumenes: Int, val dias: Int, val palabras: Int, val ahora: Long)

/** El estado de un paso: [estado] es «hecho», «saltado», «curso» (el reposo empezado) o «pendiente». */
class EstadoPaso(val id: Int, val estado: String, val aMano: Boolean, val dias: Int = 0)

/**
 * La guía para escribir una novela paso a paso: los pasos, sus textos y cómo se sabe lo que ya llevas hecho. Es la misma que la
 * del móvil (EscribirGuia.kt, que allí va junto a sus pantallas de Android) y la de Atrio (escribir-guia.js): los casos de
 * herramientas/casos/guia.json la prueban. En la obra se guarda gOn (1 = activada) y g1…g11 (1 = hecho a mano, 2 = saltado) y,
 * en el reposo, g7t (cuándo empezó).
 */
object GuiaNovela {
    const val DIA = 86_400_000L
    const val FUENTES = "Basada en guías de Grammarly, MasterClass y Escritor Flexible, el método Snowflake de Randy Ingermanson y las clases de Brandon Sanderson. Las cantidades y plazos son orientativos."
    val PASOS = listOf(
        Paso(1, "Una idea pequeña", true, "Escribir mi frase",
            "No hace falta tener la historia entera. Basta una frase: quién quiere qué y qué se lo impide.",
            listOf("Escríbela en una sola frase, corta y sin nombres propios.",
                "Si solo tienes una imagen o una escena, anótala igual: de ahí sale la frase.",
                "No busques la idea perfecta: se puede cambiar más adelante."),
            "Tienes tu frase."),
        Paso(2, "Tus personajes", true, "Crear un personaje",
            "Para cada protagonista: qué quiere, qué se lo impide y qué pasa si fracasa.",
            listOf("Empieza solo por quien lleva la historia.",
                "Rellena «Quiere», «Se lo impide» y «Si fracasa» en su ficha.",
                "Los demás personajes pueden esperar a que los necesites."),
            "Ya tienes al menos un personaje."),
        Paso(3, "Un esquema ligero", true, "Crear los tres actos",
            "Una lista corta de lo que pasa, no un guion detallado. Lo habitual son tres actos: planteamiento, nudo y desenlace.",
            listOf("Cobalto crea una nota «Esquema de la historia», fuera del manuscrito, con tres apartados: Planteamiento, Nudo y Desenlace.",
                "Escribe bajo cada uno una o dos líneas: qué pasa.",
                "Si planificar te agobia, haz un esquema mínimo y sigue: se puede ampliar mientras escribes."),
            "Tienes el esquema (al menos tres resúmenes)."),
        Paso(4, "Promesa, progreso y recompensa", false, "Anotar mis respuestas",
            "Una idea de las clases de Brandon Sanderson: el principio promete (tono, género, conflicto), el medio avanza y el final cumple lo prometido.",
            listOf("¿Qué le prometes al lector en las primeras páginas?",
                "¿Qué cambia en cada tramo para que el medio avance y no dé vueltas?",
                "¿Cómo cumple el final esas promesas?",
                "No es una plantilla rígida: es una brújula."),
            "Ya lo tienes pensado."),
        Paso(5, "Un hábito realista", true, "Poner mi meta diaria",
            "Una meta pequeña que cumplas todos los días pesa más que una grande que abandones.",
            listOf("Elige una cantidad que te parezca casi demasiado fácil (por ejemplo 300 palabras; es una sugerencia de Cobalto, no de las guías).",
                "Si puedes, escribe a la misma hora.",
                "Un sprint de 10 o 25 minutos ayuda a empezar."),
            "Ya has escrito en tres días distintos."),
        Paso(6, "El primer borrador", true, "Seguir escribiendo",
            "Escribir hasta el final sin corregir. Será imperfecto, y está bien: se arregla después.",
            listOf("No releas ni corrijas mientras escribes: apunta una nota y sigue.",
                "Si te atascas, salta a una escena que sí sepas escribir.",
                "El objetivo es llegar al final, no que quede bonito."),
            "Has llegado a la meta de palabras."),
        Paso(7, "Dejarlo reposar", false, "Empezar el reposo",
            "Aparta el borrador unas semanas antes de releerlo, para leerlo casi como un lector (consejo de Cobalto, sin una fuente concreta).",
            listOf("Pulsa «Empezar el reposo»: Cobalto cuenta los días.",
                "Mientras tanto no lo abras: lee, descansa o piensa en otra historia.",
                "Cuando quieras volver, márcalo como hecho."),
            "Ya has descansado del borrador."),
        Paso(8, "Revisar lo grande", false, "Ver el manuscrito",
            "Primera pasada: lo que afecta a la historia. La gramática se deja para la siguiente.",
            listOf("Agujeros de trama: ¿algo no tiene sentido o se olvida por el camino?",
                "Personajes: ¿cambian y quieren cosas claras?",
                "Ritmo: ¿hay tramos que se arrastran o que van demasiado deprisa?",
                "Marca cada escena como «Revisión» (y luego «Final») según la repasas."),
            "Has revisado la historia."),
        Paso(9, "Revisar lo pequeño", false, "Ver el manuscrito",
            "Segunda pasada: frases, gramática y repeticiones.",
            listOf("Lee en voz alta: lo que suena mal, suele fallar.",
                "Cambia las palabras repetidas; el editor te da sinónimos en el menú del botón derecho (PC) o en «⋯» (móvil).",
                "Mira la ortografía."),
            "Has revisado el texto."),
        Paso(10, "Lectores de prueba", false, "Exportar el manuscrito",
            "Que alguien lea tu novela y te diga qué funciona: lectores beta o un editor profesional.",
            listOf("Exporta el manuscrito en Word o PDF.",
                "Pide a dos o tres personas que lo lean y te digan dónde se aburrieron o se perdieron.",
                "Pregunta y escucha: no te defiendas."),
            "Ya te han leído."),
        Paso(11, "Corrección final y publicar", false, "Exportar el manuscrito",
            "Última lectura para las erratas y decidir si buscas editorial o publicas tú.",
            listOf("Corrige las erratas con calma.",
                "El Word «manuscrito» sigue el formato que suelen pedir las editoriales, pero cada una tiene sus normas: léelas antes de enviar.",
                "Decide: editorial o autopublicación."),
            "Tu novela está lista.")
    )

    private fun num(o: JSONObject, k: String): Double = o.opt(k)?.toString()?.toDoubleOrNull() ?: 0.0

    /** El esquema vive en una nota de la obra (no en el manuscrito): tres apartados con su etiqueta. */
    const val TITULO_ESQUEMA = "Esquema de la historia"
    const val PLANTILLA_ESQUEMA = "Planteamiento:\n\n\nNudo:\n\n\nDesenlace:\n"
    const val TITULO_PROMESAS = "Promesas de la historia"
    const val PLANTILLA_PROMESAS = "Promesa: ¿qué le prometo al lector en las primeras páginas (tono, género, conflicto)?\n\nProgreso: ¿qué cambia en cada tramo para que el medio avance?\n\nRecompensa: ¿cómo cumple el final lo que prometí?\n"
    private val ETIQUETA_ESQUEMA = Regex("^\\s*(Planteamiento|Nudo|Desenlace)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** Cuántos de los tres apartados tienen algo escrito debajo. */
    fun seccionesEsquema(texto: String): Int {
        val cuerpo = LinkedHashMap<String, String>()
        var actual: String? = null
        for (l in texto.split("\n")) {
            val m = ETIQUETA_ESQUEMA.matchEntire(l)
            if (m != null) { actual = m.groupValues[1].lowercase(); cuerpo[actual] = (cuerpo[actual] ?: "") + m.groupValues[2] }
            else if (actual != null) cuerpo[actual] = cuerpo[actual] + l
        }
        return cuerpo.values.count { it.isNotBlank() }
    }

    fun automatico(id: Int, d: DatosGuia): Boolean = when (id) {
        1 -> d.obra.optString("idea").trim().isNotEmpty()
        2 -> d.personajes >= 1
        3 -> d.resumenes >= 3
        5 -> d.dias >= 3
        6 -> num(d.obra, "meta") > 0 && d.palabras >= num(d.obra, "meta")
        else -> false
    }

    fun estados(d: DatosGuia): List<EstadoPaso> = PASOS.map { p ->
        val g = num(d.obra, "g${p.id}").toInt()
        when {
            g == 1 -> EstadoPaso(p.id, "hecho", true)
            g == 2 -> EstadoPaso(p.id, "saltado", true)
            automatico(p.id, d) -> EstadoPaso(p.id, "hecho", false)
            p.id == 7 && num(d.obra, "g7t") > 0 -> EstadoPaso(p.id, "curso", false, maxOf(0L, ((d.ahora - num(d.obra, "g7t").toLong()) / DIA)).toInt())
            else -> EstadoPaso(p.id, "pendiente", false)
        }
    }

    /** El primer paso que falta (el que toca ahora), o null si ya está todo. */
    fun siguiente(est: List<EstadoPaso>): Int? = est.firstOrNull { it.estado == "pendiente" || it.estado == "curso" }?.id
    fun hechos(est: List<EstadoPaso>): Int = est.count { it.estado == "hecho" }
    fun activa(obra: Doc): Boolean = obra.i("gOn") == 1
}

/** Lo que la guía mira de una obra (igual que en el móvil). */
fun datosGuia(e: Escritos, pid: String): DatosGuia {
    val o = e.get(pid)?.o ?: JSONObject()
    val res = e.capitulos(pid).count { it.s("resumen").isNotBlank() } + e.escenasDe(pid).count { it.s("resumen").isNotBlank() } + e.hitos(pid).size +
        e.notas(pid).filter { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA }.sumOf { GuiaNovela.seccionesEsquema(it.s("texto")) }
    return DatosGuia(o, e.fichas(pid).count { it.s("tipo") == "personaje" }, res, e.diasEscritos(pid), e.palabrasObra(pid), System.currentTimeMillis())
}

/** Lo que hace el botón de cada paso (en la obra): abrir un diálogo, crear una nota y abrirla, ir a un sitio… */
class AccionesGuia(val abre: AbreDoc, val irManuscrito: () -> Unit, val abrirEscena: (String) -> Unit, val exportar: () -> Unit, val frase: () -> Unit, val meta: () -> Unit)

fun haceGuia(id: Int, e: Escritos, obra: Doc, a: AccionesGuia) {
    val pid = obra.id
    when (id) {
        1 -> a.frase()
        2 -> a.abre.editar(Nuevos.ficha(e, pid, "personaje", listOf("Quiere", "Se lo impide", "Si fracasa", "Edad")).id, esNuevo = true)
        3 -> { val n = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA } ?: e.nuevo(Escritos.T_NOTA, pid) {
            put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_ESQUEMA); put("color", COLORES_ESCRITO[1]); put("texto", GuiaNovela.PLANTILLA_ESQUEMA) }; a.abre.editar(n.id) }
        4 -> { val n = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_PROMESAS } ?: e.nuevo(Escritos.T_NOTA, pid) {
            put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_PROMESAS); put("color", COLORES_ESCRITO[2]); put("texto", GuiaNovela.PLANTILLA_PROMESAS) }; a.abre.editar(n.id) }
        5 -> a.meta()
        6 -> { val s = e.escenasDe(pid).maxByOrNull { it.u }; if (s != null) a.abrirEscena(s.id) else a.irManuscrito() }
        7 -> e.edita(pid) { put("g7t", System.currentTimeMillis()) }
        8, 9 -> a.irManuscrito()
        10, 11 -> a.exportar()
    }
}

/** Los diálogos de la guía que no son una ficha ni una nota: la frase de la idea y la meta de cada día. */
@Composable
fun DialogosGuia(e: Escritos, obra: Doc, frase: Boolean, meta: Boolean, onCerrar: () -> Unit) {
    if (frase) {
        var t by remember { mutableStateOf(obra.s("idea")) }
        Dialogo("Tu historia en una frase", onCerrar, ancho = 640.dp, botones = {
            BotonSuave("Cancelar") { onCerrar() }
            BotonAcento("Guardar", alto = 44.dp, modifier = Modifier.width(120.dp)) { if (t.isNotBlank()) { e.edita(obra.id) { put("idea", t.trim()) }; onCerrar() } }
        }) {
            Text("Quién quiere qué, y qué se lo impide. Corta y sin nombres propios si puedes; no tiene que ser perfecta.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
            CampoRevista(t, { t = it }, "La frase", lineas = 3, estilo = TextStyle(fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 28.sp))
            Text("Ejemplo: Una aprendiz de hechicera sube a la torre donde nadie vuelve para salvar a su hermana, y descubre que lo que la espera arriba es ella misma.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
    if (meta) {
        var dia by remember { mutableStateOf(obra.i("diaria").takeIf { it > 0 }?.toString() ?: "300") }
        Dialogo("Tu meta de cada día", onCerrar, ancho = 560.dp, botones = {
            BotonSuave("Cancelar") { onCerrar() }
            BotonAcento("Guardar", alto = 44.dp, modifier = Modifier.width(120.dp)) { e.edita(obra.id) { put("diaria", dia.toIntOrNull() ?: 0) }; onCerrar() }
        }) {
            Text("Elige una cantidad que te parezca casi demasiado fácil. Mejor cumplirla todos los días que abandonar una grande.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
            FilaOpciones { listOf("150", "300", "500", "1000").forEach { x -> Opcion(x, dia == x) { dia = x } } }
            CampoRevista(dia, { dia = it.filter(Char::isDigit).take(6) }, "Palabras al día")
            Text("Para empezar, unas 300 (una sugerencia de Cobalto). Siempre puedes subirla.", color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
    }
}

/** El paso que toca ahora, arriba del índice del manuscrito (solo con la guía activada). */
@Composable
fun TarjetaGuia(e: Escritos, obra: Doc, hacer: (Int) -> Unit, verGuia: () -> Unit) {
    val ver = e.version
    if (!GuiaNovela.activa(obra)) return
    val est = remember(ver, obra.u) { GuiaNovela.estados(datosGuia(e, obra.id)) }
    val id = GuiaNovela.siguiente(est) ?: return
    val p = GuiaNovela.PASOS.first { it.id == id }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).border(1.dp, Acento).padding(12.dp)) {
        Text("GUÍA · PASO $id DE ${GuiaNovela.PASOS.size}", color = Acento, fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(p.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp)
        Text(p.que, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccionPeque(p.accion, acento = true) { hacer(id) }
            AccionPeque("Ver la guía") { verGuia() }
        }
    }
}

/** La pestaña Guía: los 11 pasos, con el que toca abierto. */
@Composable
fun GuiaObra(e: Escritos, obra: Doc, hacer: (Int) -> Unit) {
    val ver = e.version
    val pid = obra.id
    val d = remember(ver, obra.u) { datosGuia(e, pid) }
    val est = remember(d) { GuiaNovela.estados(d) }
    val sig = GuiaNovela.siguiente(est)
    val hechos = GuiaNovela.hechos(est)
    var abierto by remember(pid) { mutableIntStateOf(sig ?: 0) }
    LaunchedEffect(sig) { abierto = sig ?: 0 }
    val color = colorEscrito(obra.s("color"))
    Pagina(ancho = 980.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Antetitulo("La guía de la novela")
                Text("$hechos de ${GuiaNovela.PASOS.size} pasos", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (GuiaNovela.activa(obra)) "Activada: el paso que toca sale en el manuscrito" else "Desactivada: no sale nada en el manuscrito", color = Muted, fontFamily = Body, fontSize = 12.sp)
                EnlaceRevista(if (GuiaNovela.activa(obra)) "Desactivar" else "Activar", acento = !GuiaNovela.activa(obra)) { e.edita(pid) { put("gOn", if (GuiaNovela.activa(obra)) 0 else 1) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        Progreso(hechos.toFloat() / GuiaNovela.PASOS.size, color, 5.dp)
        if (obra.s("idea").isNotBlank()) Text("«${obra.s("idea")}»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 32.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        Spacer(Modifier.height(14.dp))
        GuiaNovela.PASOS.forEach { p ->
            val s = est[p.id - 1]
            val abre = abierto == p.id
            fun marca(x: Int) { abierto = if (p.id < GuiaNovela.PASOS.size) p.id + 1 else 0; e.edita(pid) { put("g${p.id}", x) } }
            Filete(1.dp, Line)
            Row(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { abierto = if (abre) 0 else p.id }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val hecho = s.estado == "hecho"
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (hecho) Charcoal else Color.Transparent).border(1.5.dp, if (p.id == sig) Acento else if (hecho) Charcoal else Muted, CircleShape), contentAlignment = Alignment.Center) {
                    Text(when (s.estado) { "hecho" -> "✓"; "saltado" -> "–"; "curso" -> "…"; else -> p.id.toString() }, color = if (hecho) Paper else if (p.id == sig) Acento else Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Text(p.titulo, Modifier.weight(1f), color = if (hecho) Muted else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Text(when { s.estado == "hecho" -> "HECHO"; s.estado == "saltado" -> "SALTADO"; s.estado == "curso" -> "REPOSO: ${s.dias} ${if (s.dias == 1) "DÍA" else "DÍAS"}"; p.id == sig -> "TOCA AHORA"; else -> "" },
                    color = if (p.id == sig || s.estado == "curso") Acento else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            if (abre) Column(Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.que, color = Charcoal, fontFamily = Display, fontSize = 18.sp, lineHeight = 26.sp)
                p.como.forEach { Row { Text("•", Modifier.width(18.dp), color = Acento, fontFamily = Display, fontSize = 16.sp); Text(it, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp) } }
                if (p.id == 6) {
                    val meta = obra.i("meta")
                    if (meta > 0) Progreso(d.palabras.toFloat() / meta, color, 4.dp, Modifier.padding(top = 4.dp))
                    Text("${Formato.cifra(d.palabras)}${if (meta > 0) " de ${Formato.cifra(meta)}" else ""} palabras", color = Muted, fontFamily = Body, fontSize = 12.sp)
                }
                if (p.id == 5) Text("Has escrito en ${d.dias} ${if (d.dias == 1) "día" else "días"} distintos (con 3 se da por hecho).", color = Muted, fontFamily = Body, fontSize = 12.sp)
                if (s.estado == "hecho") Text(p.hecho, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                if (s.estado == "curso") Text("Llevas ${s.dias} ${if (s.dias == 1) "día" else "días"} sin abrirlo (unas semanas suele ir bien).", color = Muted, fontFamily = Body, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (s.estado != "curso") BotonAcento(p.accion, alto = 40.dp) { hacer(p.id) }
                    if (s.estado == "curso") { BotonAcento("Ya he descansado", alto = 40.dp) { marca(1) }; BotonSuave("Empezar de nuevo") { e.edita(pid) { put("g7t", System.currentTimeMillis()) } } }
                    if (s.estado == "pendiente") BotonSuave(if (p.auto) "Marcar como hecho" else "Ya lo tengo") { marca(1) }
                    if (s.estado == "pendiente") EnlaceRevista("Saltar") { marca(2) }
                    if (s.aMano) EnlaceRevista("Quitar la marca") { e.edita(pid) { put("g${p.id}", 0) } }
                }
            }
        }
        Filete(1.dp, Line)
        Text(GuiaNovela.FUENTES, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 18.dp))
    }
}
