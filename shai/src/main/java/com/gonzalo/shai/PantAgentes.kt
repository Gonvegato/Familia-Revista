package com.gonzalo.shai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Elegir agente: los tres candidatos ([Universo.candidatos]) con quién es cada uno, cómo es, qué quiere hacer con tu carrera, lo bueno,
 * lo malo y su comisión. Al empezar la carrera hay que elegir; si es para cambiar, también se puede seguir con el de ahora.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantAgentes(u: Universo, alVolver: () -> Unit) {
    val ambito = rememberCoroutineScope()
    val clave = "ag" + u.id
    val va = EnCurso.ids[clave]
    var aviso by remember { mutableStateOf<String?>(null) }
    val actual = u.suAgente ?: u.entorno.firstOrNull { Gente.rolUnico(it.rol) == "representante" }
    PaginaRevista {
        Spacer(Modifier.statusBarsPadding().height(14.dp))
        Text(if (actual == null) "ANTES DE EMPEZAR" else "CAMBIAR DE AGENTE", color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp)
        Text("Elige a tu agente", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp, modifier = Modifier.padding(top = 4.dp))
        Cuerpo(if (actual == null) "Tres personas quieren llevar tu carrera. El que elijas negocia tus contratos, te trae ofertas y opina de todo, y se queda su parte de lo que firmes y cobres. Los otros dos no se olvidan."
            else "Tres personas quieren llevar tu carrera en lugar de ${actual.nombre}. Si cambias, ${actual.nombre.substringBefore(' ')} se enterará (y reaccionará a su manera).", Modifier.padding(top = 6.dp))
        u.candidatos.forEachIndexed { i, c ->
            Spacer(Modifier.height(12.dp))
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.nombre, 46.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp)
                        Text(c.relacion, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
                if (c.rasgos.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.rasgos.forEach { Dato(it) }
                }
                if (c.vision.isNotBlank()) Cursiva(c.vision, Modifier.padding(top = 10.dp), tam = 15)
                if (c.bueno.isNotBlank()) Linea("Lo bueno: ", c.bueno, Color(0xFF00BA7C))
                if (c.malo.isNotBlank()) Linea("Lo malo: ", c.malo, Coral)
                Text("Se queda el ${c.comision} % de lo que firmes y cobres", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                BotonRedondo(if (va != null) va else "Con ${c.nombre.substringBefore(' ')}", Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    if (va != null) return@BotonRedondo
                    EnCurso.ids[clave] = "Se lo estás contando…"; aviso = null
                    ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.eligeAgente(u.id, i) }; EnCurso.ids.remove(clave) }
                }
            }
        }
        aviso?.let { Nota("Elegido, pero no ha llegado cómo se lo toman: $it", Modifier.padding(top = 10.dp), color = Coral) }
        if (actual != null) BotonRedondo("Seguir con ${actual.nombre.substringBefore(' ')}", Modifier.fillMaxWidth().padding(top = 14.dp), lleno = false) {
            Motor.descartaCandidatos(u.id); alVolver()
        }
    }
}

@Composable
private fun Linea(et: String, t: String, color: Color) {
    Text(androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.Bold)); append(et); pop(); append(t)
    }, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
}

/** «Elegir agente» / «Cambiar de agente» (pide los tres candidatos; al llegar, sale la pantalla de elegir). */
@Composable
fun BotonAgente(u: Universo, modifier: Modifier = Modifier) {
    if (!ClaveApi.hay && !Prueba.activo) return
    val ambito = rememberCoroutineScope()
    val clave = "agc" + u.id
    val va = EnCurso.ids[clave]
    var aviso by remember { mutableStateOf<String?>(null) }
    val tiene = u.suAgente != null || u.entorno.any { Gente.rolUnico(it.rol) == "representante" }
    Column(modifier.fillMaxWidth()) {
        BotonRedondo(va ?: if (tiene) "Cambiar de agente" else "Elegir a tu agente", Modifier.fillMaxWidth(), lleno = false) {
            if (va != null) return@BotonRedondo
            EnCurso.ids[clave] = "Buscando quién te quiere llevar…"; aviso = null
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.candidatos(u.id) }; EnCurso.ids.remove(clave) }
        }
        u.suAgente?.let { Text("Ahora te lleva ${it.nombre} (se queda el ${u.comision} %).", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp)) }
        aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Coral) }
    }
}
