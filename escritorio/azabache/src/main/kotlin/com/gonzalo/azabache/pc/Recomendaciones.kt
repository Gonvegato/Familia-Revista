package com.gonzalo.azabache.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Las recomendaciones, como en el Azabache del móvil: desde la página de un juego o un libro le recomiendas algo a tu pareja (con unas
 * palabras, si quieres) y a ella le sale en su portada; lo que te recomienda tu pareja te sale en la tuya, con «A mi lista» (pasa a tus
 * pendientes) y «Descartar». Cada recomendación es un elemento de tu lista `recs` de Azabache (`azabache/recs~<id>`, con `o` = la
 * recomendación tal cual la escribe el móvil: id, kind, item, name, cover, creator, note, time). Lo que ya has añadido o descartado es de
 * este ordenador (en el móvil, de cada móvil), como `recsdone`.
 */

/** Una recomendación (la `Rec` del móvil): [kind] 0 juegos o 1 libros y [item] el número de la ficha en la colección de quien la hace. */
data class Rec(val id: Long, val kind: Int, val item: Long, val nombre: String, val portada: String, val creador: String, val nota: String, val hora: Long)

/** La recomendación como la escribe el móvil (`recToJson`: las mismas claves, en el mismo orden). */
fun recAMapa(r: Rec): Obj = linkedMapOf("id" to r.id, "kind" to r.kind.toLong(), "item" to r.item, "name" to r.nombre, "cover" to r.portada,
    "creator" to r.creador, "note" to r.nota, "time" to r.hora)

fun recDeMapa(o: Obj): Rec? {
    val id = o["id"].comoLong().takeIf { it != 0L && o.containsKey("id") } ?: return null
    return Rec(id, o["kind"].comoLong().toInt(), o["item"].comoLong(), o["name"].comoTexto(), o["cover"].comoTexto().takeIf { it.startsWith("http") }.orEmpty(),
        o["creator"].comoTexto(), o["note"].comoTexto(), o["time"].comoLong()).takeIf { it.nombre.isNotBlank() }
}

/** Lo que te recomienda tu pareja y aún no has añadido ni descartado (aquí), lo último primero. */
fun recsPendientes(deLaPareja: List<Rec>, hechas: Set<Long>): List<Rec> = deLaPareja.filter { it.id !in hechas }.sortedByDescending { it.hora }

/** ¿Ya tienes esto en esa categoría? (el mismo número o el mismo nombre, sin tildes ni mayúsculas). */
fun yaLoTienes(k: Kind, id: Long, nombre: String): Ficha? = Cuenta.fichas(k.id).firstOrNull { it.id == id || normName(it.nombre) == normName(nombre) }

/**
 * Añade algo a tus pendientes de una categoría (lo que te recomienda tu pareja). Como en el móvil: en «Pendiente», con el número de
 * su ficha (el de la ficha de tu pareja) y su portada de internet. Bloquea (la portada se baja).
 * Devuelve la ficha y si ya la tenías.
 */
fun anadePendiente(k: Kind, id: Long, nombre: String, creador: String = "", portada: String = "", paginas: Int = 0): Pair<Ficha?, Boolean> {
    yaLoTienes(k, id, nombre)?.let { return it to true }
    val o = linkedMapOf<String, Any?>(
        "id" to id, "name" to nombre.trim(), "cover" to (if (k.custom && portada.startsWith("http")) portada else ""), "hero" to "", "creator" to creador.trim(),
        "platform" to "", "pages" to paginas.toLong(), "start" to 0L, "end" to 0L, "updated" to System.currentTimeMillis(), "status" to 0L, "entries" to emptyList<Any?>())
    Cuenta.guarda(k.id, null, o, false)
    val f = Cuenta.ficha(k.id, id) ?: Cuenta.fichas(k.id).firstOrNull { normName(it.nombre) == normName(nombre) }
    if (f != null && !k.custom && portada.startsWith("http")) runCatching { Cuenta.ponPortada(f, Buscar.bytes(portada), portada) }
    return f to false
}

/** Recomendar a tu pareja (desde la página de un juego o un libro). */
@Composable
fun DialogoRecomendar(f: Ficha, k: Kind, pareja: String, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nota by remember { mutableStateOf("") }
    val antes = Cuenta.misRecs().firstOrNull { it.kind == f.kind && it.item == f.id }
    Dialogo("Recomendar a $pareja", onCerrar, ancho = 600.dp, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Recomendar", modifier = Modifier.width(170.dp), alto = 44.dp) {
            scope.launch {
                withContext(Dispatchers.IO) { Cuenta.recomienda(f, nota) }
                onCerrar(); nav.aviso("Recomendado a $pareja")
            }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniPortada(f, Modifier.width(54.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(f.nombre, color = Charcoal, fontFamily = Display, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(k.title, f.creador).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Cuerpo("Le saldrá en la portada de su Jonsu (en el móvil y en el ordenador), con lo que le escribas.", tam = 14)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(nota, { nota = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), placeholder = { Text("¿Por qué le va a gustar? (opcional)") })
        if (antes != null) Text("Ya se lo recomendaste el ${Formato.fecha(antes.hora)}.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * «Lucía te recomienda»: lo que te recomienda tu pareja y aún no has añadido ni descartado, con «A mi lista» y «Descartar». En la portada
 * y en la página de tu pareja.
 */
@Composable
fun TeRecomienda(uid: String, nombre: String, nav: Nav, maximo: Int = 6, conTitulo: Boolean = true, volver: Pantalla = Pantalla.Inicio) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    var hechas by remember { mutableStateOf(Local.recsHechas) }
    val recs = recsPendientes(Cuenta.recsDe(uid), hechas)
    if (recs.isEmpty()) return
    if (conTitulo) Seccion("$nombre te recomienda")
    recs.take(maximo).forEach { r ->
        val k = Cuenta.kind(r.kind)
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PortadaPequena("", r.portada, r.nombre, Modifier.width(48.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    FitTitle(r.nombre, 22.sp, 25.sp, color = Charcoal, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(k.title, r.creador, if (r.hora > 0) haceDias(diaDeMs(r.hora), LocalDate.now().toEpochDay()) else "").filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                        color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (r.nota.isNotBlank()) Row(Modifier.padding(top = 8.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Acento))
                Spacer(Modifier.width(12.dp))
                Text("«${r.nota}»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                EnlaceRevista("A mi lista →", acento = true) {
                    scope.launch {
                        val (f, ya) = withContext(Dispatchers.IO) { anadePendiente(k, r.item, r.nombre, r.creador, r.portada) }
                        Local.recHecha(r.id); hechas = Local.recsHechas
                        nav.aviso(if (ya) "Ya tenías ${r.nombre}" else "${r.nombre}, en tus pendientes de ${k.title.lowercase()}")
                        if (f != null && !ya) nav.ir(Pantalla.Detalle(k.id, f.id, volver))
                    }
                }
                EnlaceRevista("Descartar") { Local.recHecha(r.id); hechas = Local.recsHechas }
            }
        }
        Filete(1.dp, Line)
    }
}

/** El día (desde 1970) de una hora en milisegundos. */
fun diaDeMs(ms: Long): Long = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

/** «Le has recomendado»: lo tuyo, en la página de tu pareja. */
@Composable
fun LeHasRecomendado(nombre: String, nav: Nav) {
    val mias = Cuenta.misRecs()
    if (mias.isEmpty()) return
    Seccion("Le has recomendado")
    mias.take(8).forEach { r ->
        val k = Cuenta.kind(r.kind)
        val tuya = Cuenta.ficha(r.kind, r.item)
        Row(Modifier.fillMaxWidth().clickable(enabled = tuya != null) { tuya?.let { nav.ir(Pantalla.Detalle(k.id, it.id, Pantalla.Pareja)) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PortadaPequena(tuya?.portada.orEmpty(), r.portada.ifBlank { tuya?.portadaWeb.orEmpty() }, r.nombre, Modifier.width(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.nombre, color = Charcoal, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((listOf(k.title, Formato.fecha(r.hora)).joinToString(" · ") + if (r.nota.isNotBlank()) " · «${r.nota}»" else "").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Filete(1.dp, Line)
    }
}
