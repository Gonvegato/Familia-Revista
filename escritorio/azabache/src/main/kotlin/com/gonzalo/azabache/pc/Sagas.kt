package com.gonzalo.azabache.pc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una saga con lo suyo en orden (lo que ya no está en tu colección se salta). */
fun fichasDe(s: Saga): List<Ficha> { val todas = Cuenta.fichas(s.kind).associateBy { it.id }; return s.items.mapNotNull { todas[it] } }

/** «7 libros · 3 leídos · media 86». */
fun lineaDe(s: Saga, k: Kind): String {
    val l = fichasDe(s); val hechos = l.count { it.valorada }
    val media = l.filter { it.valorada && !it.sinValorar }.map { it.general }.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() }
    return listOfNotNull("${l.size} ${if (l.size == 1) k.unit else k.units}", if (hechos > 0) "$hechos ${k.doneWord.lowercase()}" else null, media?.let { "media $it" }).joinToString(" · ")
}

@Composable
fun PantSagas(nav: Nav) {
    LaFamilia.cambios
    var nueva by remember { mutableStateOf(false) }
    val sagas = Cuenta.sagas().sortedByDescending { it.updated }
    Pagina {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { TituloPagina("SAGAS") }
            BotonAcento("+ Nueva saga", alto = 44.dp, modifier = Modifier.padding(bottom = 12.dp).width(170.dp)) { nueva = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(8.dp))
        Entradilla("Tus sagas, en el orden en que se leen o se juegan.")
        if (sagas.isEmpty()) Vacio("Aún no tienes sagas", "Crea una aquí o desde la ficha de un libro o un juego.")
        Spacer(Modifier.height(16.dp))
        sagas.forEach { s ->
            val k = Cuenta.kind(s.kind)
            val l = fichasDe(s)
            Column(Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.UnaSaga(s.id)) }.padding(vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.name, Modifier.weight(1f), color = Charcoal, fontFamily = Grotesca, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(lineaDe(s, k), color = Muted, fontFamily = Body, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    l.take(9).forEach { f -> PortadaCosa(f.portada, f.portadaWeb, f.nombre, Modifier.width(78.dp)) }
                }
            }
            Filete(1.dp, Line)
        }
    }
    if (nueva) DialogoNuevaSaga(null, nav) { nueva = false }
}

@Composable
fun PantSaga(id: Long, nav: Nav) {
    LaFamilia.cambios
    val s = Cuenta.sagas().firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()
    var anadir by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    if (s == null) { Pagina { VolverRevista({ nav.ir(Pantalla.Sagas) }, "Sagas"); Vacio("Esta saga ya no está") }; return }
    val k = Cuenta.kind(s.kind)
    fun guarda(n: Saga) { scope.launch { withContext(Dispatchers.IO) { Cuenta.guardaSaga(n) } } }
    Pagina(ancho = 960.dp) {
        VolverRevista({ nav.ir(Pantalla.Sagas) }, "Sagas")
        Spacer(Modifier.height(12.dp))
        RevistaHeadline(s.name, "Saga · " + k.title)
        Text(lineaDe(s, k), color = Muted, fontFamily = Body, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnlaceRevista("+ Añadir ${k.unit}", acento = true) { anadir = true }
            EnlaceRevista("Borrar la saga") { borrar = true }
        }
        Spacer(Modifier.height(12.dp)); Filete(2.dp)
        val l = fichasDe(s)
        if (l.isEmpty()) Vacio("Vacía", "Añade lo que va en esta saga, en su orden.")
        l.forEachIndexed { i, f ->
            FilaFicha(f, k, "${i + 1}. " + estadoDe(f, k) + if (f.valorada && !f.sinValorar) " · ${f.general}" else "", derecha = {
                if (i > 0) EnlaceRevista("↑") { guarda(s.copy(items = s.items.toMutableList().also { m -> val j = m.indexOf(f.id); if (j > 0) { m.removeAt(j); m.add(j - 1, f.id) } })) }
                Spacer(Modifier.width(12.dp))
                if (i < l.size - 1) EnlaceRevista("↓") { guarda(s.copy(items = s.items.toMutableList().also { m -> val j = m.indexOf(f.id); if (j >= 0 && j < m.size - 1) { m.removeAt(j); m.add(j + 1, f.id) } })) }
                Spacer(Modifier.width(12.dp))
                EnlaceRevista("Quitar") { guarda(s.copy(items = s.items - f.id)) }
            }) { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.UnaSaga(s.id))) }
            Filete(1.dp, Line)
        }
    }
    if (anadir) DialogoElegirFicha(k, s.items.toSet(), { anadir = false }) { f -> guarda(s.copy(items = s.items + f.id)); anadir = false }
    if (borrar) Dialogo("¿Borrar la saga «${s.name}»?", { borrar = false }, botones = {
        BotonSuave("Cancelar") { borrar = false }
        BotonSuave("Borrar", peligro = true) { scope.launch { withContext(Dispatchers.IO) { Cuenta.borraSaga(s) }; nav.ir(Pantalla.Sagas) } }
    }) { Cuerpo("Se borra la saga; lo que había en ella sigue en tu colección.", tam = 14) }
}

/** Elegir una ficha de una categoría (para una saga). */
@Composable
fun DialogoElegirFicha(k: Kind, fuera: Set<Long>, onCerrar: () -> Unit, onElige: (Ficha) -> Unit) {
    var q by remember { mutableStateOf("") }
    val l = Cuenta.fichas(k.id).filter { it.id !in fuera && (q.isBlank() || normName(it.nombre).contains(normName(q)) || normName(it.creador).contains(normName(q))) }.sortedBy { normName(it.nombre) }
    Dialogo("Añadir ${k.unit}", onCerrar, ancho = 640.dp, botones = { BotonSuave("Cerrar") { onCerrar() } }) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar en ${k.title.lowercase()}") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 440.dp)) { items(l, key = { it.id }) { f -> FilaFicha(f, k, metaDe(f, k)) { onElige(f) } } }
    }
}

/** Crear una saga (desde la ficha, con ella dentro). */
@Composable
fun DialogoNuevaSaga(con: Pair<Kind, Ficha>?, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    val kinds = Cuenta.kinds()
    var kind by remember { mutableStateOf(con?.first?.id ?: BOOKS.id) }
    Dialogo("Nueva saga", onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Crear", modifier = Modifier.width(120.dp), alto = 44.dp) {
            if (nombre.isNotBlank()) scope.launch {
                val s = Saga(System.currentTimeMillis(), nombre.trim(), listOfNotNull(con?.second?.id), kind = kind)
                withContext(Dispatchers.IO) { Cuenta.guardaSaga(s) }
                onCerrar(); nav.ir(Pantalla.UnaSaga(s.id))
            }
        }
    }) {
        Campo("Nombre", nombre) { nombre = it }
        if (con == null) MagChoice(kinds.map { it.title }, kinds.indexOfFirst { it.id == kind }, "De") { kind = kinds[it].id }
    }
}

/** Desde una ficha: meterla en una de tus sagas (o en una nueva). */
@Composable
fun DialogoSaga(k: Kind, f: Ficha, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nueva by remember { mutableStateOf(false) }
    val sagas = Cuenta.sagas().filter { it.kind == k.id }
    if (nueva) { DialogoNuevaSaga(k to f, nav) { nueva = false; onCerrar() }; return }
    Dialogo("«${f.nombre}» en una saga", onCerrar, botones = {
        EnlaceRevista("+ Nueva saga", acento = true) { nueva = true }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cerrar") { onCerrar() }
    }) {
        if (sagas.isEmpty()) Text("No tienes sagas de ${k.title.lowercase()}.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
        sagas.forEach { s ->
            val dentro = f.id in s.items
            Row(Modifier.fillMaxWidth().clickable {
                scope.launch { withContext(Dispatchers.IO) { Cuenta.guardaSaga(if (dentro) s.copy(items = s.items - f.id) else s.copy(items = s.items + f.id)) } }
            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, Modifier.weight(1f), color = Charcoal, fontFamily = Grotesca, fontSize = 20.sp)
                Text(if (dentro) "Está ✓" else "Añadir", color = if (dentro) Acento else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Filete(1.dp, Line)
        }
    }
}
