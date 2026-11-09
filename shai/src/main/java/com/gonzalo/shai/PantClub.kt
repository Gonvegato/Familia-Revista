package com.gonzalo.shai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stadium
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un dato del club en dos columnas («Estadio · Benito Villamarín (60.721)»). */
@Composable
private fun DatoClub(nombre: String, valor: String) {
    if (valor.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.width(116.dp))
        Text(valor, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}

/** «es.wikipedia.org» de una dirección. */
fun sitio(url: String): String = url.substringAfter("://").substringBefore("/").removePrefix("www.")

/** Todo lo que se sabe del club (para la confirmación y para «Tu club»). */
@Composable
fun DatosClub(c: InfoClub, conFuentes: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(c.nombre, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            TituloTarjeta(c.nombre, tam = 21)
            listOf(c.apodo, listOf(c.ciudad, c.pais).filter { it.isNotBlank() }.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
                ?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp) }
        }
    }
    Spacer(Modifier.height(10.dp))
    DatoClub("Estadio", c.estadio + (if (c.aforo > 0) " (${Formato.cifra(c.aforo)})" else ""))
    DatoClub("Liga", listOf(c.liga, c.division).filter { it.isNotBlank() }.distinct().joinToString(" · "))
    DatoClub("Entrenador", c.entrenador)
    DatoClub("Presidente", c.presidente)
    DatoClub("Colores", c.colores)
    DatoClub("Rivales", c.rivales.joinToString(", "))
    DatoClub("Palmarés", c.palmares)
    DatoClub("Ahora", c.contexto)
    DatoClub("Afición", c.aficion)
    if (c.dudas.isNotBlank() && !c.deMentira) Text("Ojo: ${c.dudas}", color = Acento, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
    if (c.deMentira) Sello("Modo prueba: datos de ejemplo, no se ha buscado nada", Coral, Modifier.padding(top = 8.dp))
    if (conFuentes && c.fuentes.isNotEmpty()) {
        Rotulo("Fuentes", Modifier.padding(top = 12.dp), color = Muted)
        c.fuentes.take(5).forEach { (t, url) ->
            Text("${t.ifBlank { sitio(url) }} · ${sitio(url)}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
    if (!c.deMentira && c.buscado.isNotBlank()) Text("Búsqueda web del ${Fechas.bonita(c.buscado)}" + (if (c.busquedas > 0) " · ${c.busquedas} búsqueda${if (c.busquedas > 1) "s" else ""}" else "") +
        (if (c.coste > 0) " · ${Formato.dolares(c.coste)}" else ""), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

/**
 * «He identificado al club»: lo encontrado, con sus fuentes, y «Sí, es este» / «No, buscar otro» (con un campo para precisar). [extra]: un
 * botón más (al crear la carrera: «Empezar sin identificarlo»).
 */
@Composable
fun ConfirmaClub(c: InfoClub, buscando: Boolean, alSi: () -> Unit, alOtro: (String) -> Unit, textoSi: String = "Sí, es este", extra: (@Composable () -> Unit)? = null) {
    var otro by remember(c) { mutableStateOf(false) }
    var precisa by remember(c) { mutableStateOf("") }
    Tarjeta(borde = Acento) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Verified, null, tint = Acento, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Rotulo(if (c.deMentira) "Club de ejemplo (modo prueba)" else "He identificado al club")
        }
        Spacer(Modifier.height(10.dp))
        DatosClub(c)
        Spacer(Modifier.height(12.dp))
        if (buscando) Cursiva("Buscando otra vez…", color = Muted)
        else if (!otro) {
            BotonRedondo(textoSi, Modifier.fillMaxWidth()) { alSi() }
            Spacer(Modifier.height(8.dp))
            BotonRedondo("No, buscar otro", Modifier.fillMaxWidth(), lleno = false) { otro = true }
        } else {
            Campo("¿Cuál es?", precisa, "«El de Sevilla, no el filial», «el de Argentina», la ciudad…") { precisa = it.take(160) }
            Spacer(Modifier.height(8.dp))
            BotonRedondo("Buscar otra vez", Modifier.fillMaxWidth()) { alOtro(precisa) }
            Spacer(Modifier.height(8.dp))
            BotonRedondo("Cancelar", Modifier.fillMaxWidth(), lleno = false) { otro = false }
        }
        extra?.let { Spacer(Modifier.height(8.dp)); it() }
    }
}

/**
 * «Tu club», en Perfil (y en Inicio si falta): el identificado, el que espera tu «Sí, es este» o el botón para buscarlo. Lo de buscar
 * cuesta unos céntimos (Sonnet y hasta tres búsquedas web).
 */
@Composable
fun SeccionClub(u: Universo, enInicio: Boolean = false) {
    val ambito = rememberCoroutineScope()
    val va = EnCurso.ids["c" + u.id]
    var aviso by remember { mutableStateOf<String?>(null) }
    fun busca(precisa: String = "") {
        if (EnCurso.ids["c" + u.id] != null) return
        EnCurso.ids["c" + u.id] = "Buscando tu club en la web…"; aviso = null
        ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.identifica(u.id, precisa) }; EnCurso.ids.remove("c" + u.id) }
    }
    val pendiente = u.clubPorConfirmar
    when {
        pendiente != null -> ConfirmaClub(pendiente, va != null, alSi = { Motor.confirmaClub(u.id) }, alOtro = { busca(it) })
        u.clubIdentificado && !enInicio -> {
            val c = u.clubInfo!!
            Tarjeta { DatosClub(c); Spacer(Modifier.height(10.dp)); PildoraMarco(va ?: "Volver a buscarlo") { busca() } }
        }
        !u.clubIdentificado -> Tarjeta(bordeIzq = Acento) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Stadium, null, tint = Acento, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                TituloTarjeta(if (u.clubInfo != null && !u.clubInfo.es(u.club)) "Identifica tu club nuevo: ${u.club}" else "Identifica tu club: ${u.club}", Modifier.weight(1f))
            }
            TextoTarjeta("FcFeed busca en la web qué club es (su estadio, su ciudad, su entrenador, sus derbis, su momento) para que la prensa y la red hablen de él de verdad. " +
                (if (Prueba.activo) "Modo prueba: datos de ejemplo, gratis." else "Cuesta unos céntimos (Sonnet y hasta tres búsquedas web)."), Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            if (!ClaveApi.hay) TextoTarjeta("Pon tu clave en Ajustes → IA para poder buscarlo.", color = Acento)
            else BotonRedondo(va ?: "Buscar el club", Modifier.fillMaxWidth()) { busca() }
        }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
}

/** El botón de buscar, con su icono (para crear la carrera). */
@Composable
fun BotonBuscarClub(texto: String, modifier: Modifier = Modifier, alHacer: () -> Unit) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = Acento, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        EnlaceRevista(texto, acento = true) { alHacer() }
    }
}
