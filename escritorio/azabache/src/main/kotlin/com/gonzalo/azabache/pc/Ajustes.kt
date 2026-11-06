package com.gonzalo.azabache.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.AjustesCuenta
import com.gonzalo.familia.pc.AjustesVersion
import com.gonzalo.familia.pc.KDrive
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una clave de servicios de la cuenta: su nombre en la cuenta, cómo se llama y para qué es. */
class ClaveServicio(val nombre: String, val titulo: String, val para: String, val secreta: Boolean = true)

/** Las claves de la cuenta (`/claves/<uid>`): se ponen aquí una vez y las tienen todas las apps, en el móvil y en el ordenador. */
val CLAVES = listOf(
    ClaveServicio("kdrive_token", "kDrive: token", "Los libros, las ROMs y las versiones de las apps, en tu kDrive (se crea en manager.infomaniak.com → Desarrollador → Tokens de API)."),
    ClaveServicio("kdrive_id", "kDrive: número", "El número de tu kDrive (sale en su dirección: kdrive.infomaniak.com/app/drive/<número>).", secreta = false),
    ClaveServicio("steamgriddb", "SteamGridDB", "Portadas de juegos (steamgriddb.com → Preferencias → API)."),
    ClaveServicio("steam_key", "Steam: clave", "Las horas y la biblioteca de Steam (steamcommunity.com/dev/apikey)."),
    ClaveServicio("steam_id", "Steam: tu id", "Tu id de Steam (17 cifras).", secreta = false),
    ClaveServicio("igdb_id", "IGDB: Client ID", "Datos de juegos (dev.twitch.tv/console)."),
    ClaveServicio("igdb_secreto", "IGDB: Client Secret", "Datos de juegos."),
    ClaveServicio("rawg", "RAWG", "Datos de juegos (rawg.io/apidocs)."),
    ClaveServicio("tmdb", "TMDB", "Películas y series (themoviedb.org → Ajustes → API)."),
)

@Composable
fun PantAjustes(nav: Nav) {
    var pestana by remember { mutableStateOf(0) }
    Pagina(ancho = 900.dp) {
        Cabecera(84.sp, texto = "AJUSTES")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Cuenta", "Servicios", "Categorías", "Apariencia", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> AjustesCuenta("azabache")
            1 -> AjustesServicios(nav)
            2 -> AjustesCategorias(nav)
            3 -> AjustesApariencia()
            else -> AjustesVersion("azabache", "Azabache")
        }
    }
}

@Composable
fun AjustesServicios(nav: Nav) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    var prueba by remember { mutableStateOf("") }
    Antetitulo("Las claves de la cuenta")
    Spacer(Modifier.height(6.dp))
    Cuerpo("Se ponen una vez y las usan todas: Hiedra, Marfil, Cobalto y Azabache, en el móvil y en el ordenador. Solo las puede leer tu sesión (ni tu pareja).", tam = 14)
    Spacer(Modifier.height(14.dp))
    CLAVES.forEach { c ->
        var v by remember(c.nombre, LaFamilia.cambios) { mutableStateOf(LaFamilia.clave(c.nombre)) }
        var ver by remember { mutableStateOf(!c.secreta) }
        val cambiada = v.trim() != LaFamilia.clave(c.nombre)
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(v, { v = it }, Modifier.weight(1f), label = { Text(c.titulo) }, singleLine = true,
                    visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { if (c.secreta) EnlaceRevista(if (ver) "Ocultar" else "Ver") { ver = !ver } })
                Spacer(Modifier.width(12.dp))
                BotonSuave(if (cambiada) "Guardar" else "Guardada") {
                    if (cambiada) scope.launch { withContext(Dispatchers.IO) { LaFamilia.familia.ponClave(c.nombre, v.trim()); LaFamilia.sincronizaYa() }; nav.aviso("${c.titulo}: guardada en la cuenta") }
                }
            }
            Cuerpo(c.para, tam = 12)
        }
    }
    Spacer(Modifier.height(12.dp))
    BotonSuave("Probar el kDrive") { scope.launch { prueba = withContext(Dispatchers.IO) { runCatching { KDrive().prueba() }.getOrElse { it.message ?: "No responde." } } } }
    if (prueba.isNotBlank()) Cuerpo(prueba, tam = 13)
}

@Composable
fun AjustesCategorias(nav: Nav) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    var editar by remember { mutableStateOf<Kind?>(null) }
    var nueva by remember { mutableStateOf(false) }
    Antetitulo("Tus categorías")
    Spacer(Modifier.height(6.dp))
    Cuerpo("Además de los videojuegos y los libros: películas, series, discos… lo que quieras valorar. Son de la cuenta: las tienes igual en el móvil.", tam = 14)
    Spacer(Modifier.height(10.dp))
    Cuenta.propias().forEach { k ->
        Fila(k.title, (k.labels.drop(1).joinToString(" · ") + " · «${k.statuses[1]}»" + when { k.isMovie -> " · busca en TMDB"; k.isTv -> " · busca en TVmaze"; else -> "" } +
            if (CosasMostaza.de(k)) " · las de Mostaza" else ""), alHacer = { editar = k }) {
            Text("${Cuenta.fichas(k.id).size}", color = Muted, fontFamily = Body, fontSize = 13.sp)
        }
        Filete(1.dp, Line)
    }
    Spacer(Modifier.height(14.dp))
    BotonSuave("+ Nueva categoría") { nueva = true }
    val k = editar
    if (k != null || nueva) DialogoCategoria(k, nav) { editar = null; nueva = false }
}

@Composable
fun DialogoCategoria(k: Kind?, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var titulo by remember { mutableStateOf(k?.title.orEmpty()) }
    val crit = remember { mutableStateListOf(*(k?.labels?.drop(1) ?: listOf("Disfrute")).let { it + List(4 - it.size) { "" } }.take(4).toTypedArray()) }
    var progreso by remember { mutableStateOf(k?.statuses?.get(1) ?: "En curso") }
    var fuente by remember { mutableStateOf(when { k?.isMovie == true -> 1; k?.isTv == true -> 2; else -> 0 }) }
    Dialogo(if (k == null) "Nueva categoría" else k.title, onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Guardar", modifier = Modifier.width(130.dp), alto = 44.dp) {
            if (titulo.isNotBlank()) scope.launch {
                val nuevo = customKind(k?.id ?: Cuenta.siguienteKind(), titulo, crit.toList(), progreso, k?.color ?: 0, when (fuente) { 1 -> SRC_MOVIE; 2 -> SRC_TV; else -> "" })
                withContext(Dispatchers.IO) { Cuenta.guardaKind(nuevo) }
                onCerrar(); nav.aviso("Categoría guardada")
            }
        }
    }) {
        Campo("Nombre", titulo) { titulo = it }
        Text("Lo que se valora (hasta cuatro, además de la nota)", color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (0 until 4).forEach { i -> Campo("Criterio ${i + 1}", crit[i], Modifier.weight(1f)) { crit[i] = it } } }
        Campo("«En curso» se dice…", progreso) { progreso = it }
        MagChoice(listOf("A mano", "Películas (TMDB)", "Series (TVmaze)"), fuente, "Dónde se busca") { fuente = it }
        if (CosasMostaza.activa() && (fuente == 1 || fuente == 2)) Cuerpo("Sus fichas son las de tu biblioteca de Mostaza (las mismas que en el móvil).", tam = 12)
    }
}

@Composable
fun AjustesApariencia() {
    Antetitulo("Apariencia")
    Spacer(Modifier.height(6.dp))
    Cuerpo("Azabache del ordenador lleva los colores de tu Azabache del móvil (tema, fondo y acento), que vienen de la cuenta. Aquí eliges si quieres verlo claro u oscuro en este ordenador.", tam = 14)
    Spacer(Modifier.height(12.dp))
    MagChoice(listOf("Como Azabache del móvil", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
}
