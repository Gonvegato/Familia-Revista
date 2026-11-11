package com.gonzalo.gamerate

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/*
 * Perfil (Atrio 6): lo que antes estaba repartido entre el menú y Ajustes, ordenado en pocas tarjetas:
 * estilo, tu PC, objetivos, cuenta y pareja, y el resto de ajustes (claves, importar, copias) en una página aparte.
 */

@Composable
fun ProfileScreen(
    store: Store, @Suppress("UNUSED_PARAMETER") partner: Partner?,
    @Suppress("UNUSED_PARAMETER") onStyle: () -> Unit, @Suppress("UNUSED_PARAMETER") onSettings: () -> Unit, @Suppress("UNUSED_PARAMETER") onSuite: () -> Unit,
    onMassScores: () -> Unit,
    onDuelos: () -> Unit = {},
    /** Pedir permiso para los avisos (Android 13+). */
    onPedirAvisos: () -> Unit = {},
    onNegraAnadir: (String) -> Unit = {},
    onNegraQuitar: (Vetado) -> Unit = {},
    goalGames: Int, goalBooks: Int, onGoal: (Int, Int) -> Unit,
    /** (la cuenta y la pareja están en la pestaña «Cuenta») */
    @Suppress("UNUSED_PARAMETER") couple: @Composable () -> Unit = {},
    /** Ir a otra pestaña de Ajustes. */
    ir: (Screen) -> Unit = {},
    /** Tus categorías propias (discos, viajes…): crearlas y editarlas. */
    categorias: (@Composable () -> Unit)? = null,
    /** Buscar el estudio de los juegos que no lo tienen. */
    onFillStudios: () -> Unit = {}, studioMsg: String = "",
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    // La pestaña «Jonsu» de Ajustes (como «Juegos» en Bastet): los objetivos del año, tus categorías y tus notas
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        CabeceraAjustes(1, onBack, ir)
        Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 760.dp).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        SeccionAjustes("Objetivos de ${LocalDate.now().year}", primera = true)
        Text("Cuántos quieres terminar este año. 0 = sin objetivo.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(GAMES.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Stepper(goalGames, "", { onGoal(0, goalGames - 1) }, { onGoal(0, goalGames + 1) })
        }
        // Los de lectura los fija Marfil, que es quien lee: aquí solo se enseñan (así no hay dos que se contradigan)
        val meta = remember(Hermanas.version) { store.metaLectura }
        var aviso by remember { mutableStateOf(store.avisoLectura) }
        fun ponAviso(h: Int) { aviso = h; store.avisoLectura = h; LecturaAviso.programa(ctx) }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(BOOKS.title, fontWeight = FontWeight.Medium)
                Text("lo fija Seshat", color = Muted, fontSize = 12.sp)
            }
            Text(if (goalBooks > 0) "$goalBooks" else "sin reto", fontFamily = if (Skin.revista) Display else null, fontSize = 18.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lectura al día", fontWeight = FontWeight.Medium)
                Text("lo fija Seshat", color = Muted, fontSize = 12.sp)
            }
            Text(if (meta > 0) "$meta min" else "sin objetivo", fontFamily = if (Skin.revista) Display else null, fontSize = 18.sp)
        }
        if (Hermanas.instalada(ctx, Hermanas.PAQ_MARFIL)) {
            Text("Cambiarlos en Seshat\u00A0→", color = ColGeneral, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).clickable { Hermanas.abrir(ctx, Hermanas.PAQ_MARFIL, "ajustes") })
        } else Text("Se cambian en Seshat (Ajustes → Lectura).", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        if (meta > 0) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme si no lo cumplo", fontWeight = FontWeight.Medium)
                    Text(if (aviso >= 0) "a las ${"%02d".format(aviso)}:00, con el libro por el que vas" else "sin aviso", color = Muted, fontSize = 12.sp)
                }
                Interruptor(aviso >= 0) { on -> if (on) { onPedirAvisos(); ponAviso(21) } else ponAviso(-1) }
            }
            if (aviso >= 0) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Stepper(aviso, ":00", { ponAviso((aviso + 23) % 24) }, { ponAviso((aviso + 1) % 24) })
            }
        }

        // Tus categorías propias
        if (categorias != null) { SeccionAjustes("Tus categorías"); categorias() }

        SeccionAjustes("Tus notas")
        // Lista negra de juegos: no salen en ningún sitio
        var negra by remember { mutableStateOf(false) }
        MagLink("Lista negra de juegos", if (Negra.lista.isEmpty()) "Juegos que nunca deben salir (p. ej. Wallpaper Engine)" else "${Negra.lista.size} oculto${if (Negra.lista.size == 1) "" else "s"} en todos tus dispositivos") { negra = true }
        Spacer(Modifier.height(12.dp))
        if (negra) NegraDialogo(onAnadir = { n -> onNegraAnadir(n) }, onQuitar = { v -> onNegraQuitar(v) }, onCerrar = { negra = false })
        // Repasar notas (sobre todo tras importar): por duelos o una a una
        var repasar by remember { mutableStateOf(false) }
        MagLink("Repasar notas", "Cambiar varias notas seguidas") { onMassScores() }
        Spacer(Modifier.height(12.dp))
        if (repasar) androidx.compose.material3.AlertDialog(
            onDismissRequest = { repasar = false },
            title = { Text("Repasar notas", fontFamily = Display) },
            text = {
                Column {
                    Column(Modifier.fillMaxWidth().clickable { repasar = false; onDuelos() }.padding(vertical = 10.dp)) {
                        Text("Duelos de libros", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("«¿Cuál te gustó más?» de dos en dos; al final, notas sin empates", color = Muted, fontSize = 13.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Column(Modifier.fillMaxWidth().clickable { repasar = false; onMassScores() }.padding(vertical = 10.dp)) {
                        Text("Cambiar notas en masa", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Eliges varios y les cambias la nota uno tras otro", color = Muted, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ repasar = false }) { Text("Cancelar") } }
        )


        MagLink("Rellenar estudios", "Busca el estudio de los juegos que no lo tienen") { onFillStudios() }
        if (studioMsg.isNotEmpty()) Text(studioMsg, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

        // El pie, como en sus hermanas: la versión como etiqueta y una línea en cursiva
        Box(Modifier.padding(top = 22.dp).fillMaxWidth().height(2.dp).background(Charcoal))
        val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { null } ?: "" }
        Text("JONSU $ver", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
        Text("Jonsu es hermana de Bastet, Seshat, Thot, Hathor y Nut: lo que juegas, lees, escribes y ves allí aparece aquí para valorarlo.",
            color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp))
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Card6(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!Skin.revista) { Icon(icon, null, tint = Charcoal, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
                Text(sub, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (Skin.revista) Text("→", fontFamily = Display, fontSize = 24.sp) else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
        }
    }
}
