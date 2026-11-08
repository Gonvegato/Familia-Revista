package com.gonzalo.shai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La foto de tu personaje: solo en este móvil (no va a la cuenta). */
object Fotos {
    var version by mutableIntStateOf(0)
    private fun archivo(ctx: Context, id: String) = java.io.File(java.io.File(ctx.filesDir, "fotos").apply { mkdirs() }, "$id.jpg")
    fun carga(ctx: Context, id: String): android.graphics.Bitmap? { version; return archivo(ctx, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
    fun guarda(ctx: Context, id: String, uri: Uri) {
        val b = android.util.Base64.decode(fotoParaClaude(ctx, uri), android.util.Base64.NO_WRAP)
        archivo(ctx, id).writeBytes(b); version++
    }
    fun borra(ctx: Context, id: String) { archivo(ctx, id).delete(); version++ }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TabVida(u: Universo) {
    var aviso by remember { mutableStateOf<String?>(null) }
    fun haz(f: () -> Universo) { aviso = try { Carreras.guarda(f()); null } catch (e: Exception) { Motor.mensaje(e) } }
    Rotulo("Tu dinero")
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // (la cifra sin «€», que va en la etiqueta: así cabe en una línea)
        Contador(sinEuro(Formato.eurosCorto(u.dinero)), "€ en el banco", Modifier.weight(1f).fillMaxHeight(), destacado = true)
        Contador(u.contrato?.let { sinEuro(Formato.eurosCorto(it.sueldo)) } ?: "0", if (u.contrato != null) "€ a la semana" else "€: libre, sin sueldo", Modifier.weight(1f).fillMaxHeight())
        Contador(sinEuro(Formato.eurosCorto(u.patrimonio)), "€ de patrimonio", Modifier.weight(1f).fillMaxHeight())
    }
    Tarjeta(Modifier.padding(top = 8.dp)) {
        if (u.patrocinio > 0) MagFact("Patrocinios a la semana", Formato.euros(u.patrocinio))
        u.contrato?.takeIf { it.porGol > 0 }?.let { MagFact("Por cada gol", Formato.euros(it.porGol)) }
        val rentas = u.bienes.sumOf { it.renta }
        if (rentas != 0L) MagFact("Tus cosas, cada jornada", (if (rentas > 0) "+" else "") + Formato.euros(rentas))
        u.contrato?.let { MagFact("Contrato", "${it.club}, hasta ${it.hasta}") } ?: MagFact("Contrato", "ninguno: eres libre")
    }
    aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
    Spacer(Modifier.height(18.dp)); Rotulo("Fuera del campo"); Spacer(Modifier.height(8.dp))
    Tarjeta { Fuera.TODOS.forEach { (k, n) -> BarraValor(n, u.valorFuera(k)) } }
    Spacer(Modifier.height(18.dp)); Rotulo("Lo tuyo"); Spacer(Modifier.height(8.dp))
    if (u.bienes.isEmpty()) Nota("Nada todavía: abajo, en «De compras», lo que te puedes permitir.")
    u.bienes.forEach { b ->
        Tarjeta(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TituloTarjeta(b.nombre, tam = 18)
                    TextoTarjeta(listOf("vale ${Formato.eurosCorto(b.valor)}", "pagaste ${Formato.eurosCorto(b.compra)}", (if (b.renta >= 0) "+" else "") + Formato.eurosCorto(b.renta) + " por jornada").joinToString(" · "))
                }
                Spacer(Modifier.width(10.dp))
                BotonRedondo("Vender", lleno = false) { haz { Mercado.vende(u, b.id) } }
            }
        }
    }
    Spacer(Modifier.height(18.dp)); Rotulo("La bolsa")
    Text("Empresas inventadas; sus acciones se mueven con cada jornada.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
    Mercado.VALORES.forEach { v ->
        val h = u.precios[v.simbolo].orEmpty()
        val p = h.lastOrNull() ?: v.inicial
        val antes = h.getOrNull(h.size - 2) ?: p
        val cambio = if (antes > 0) (p - antes) * 100.0 / antes else 0.0
        val tiene = u.cartera[v.simbolo] ?: 0L
        Tarjeta(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    TituloTarjeta(v.nombre, tam = 17)
                    Text("${v.simbolo} · ${v.sector}", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Formato.centimos(p), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
                    Text((if (cambio >= 0) "▲ +" else "▼ ") + Formato.decimal(cambio) + " %", color = if (cambio >= 0) Acento else Coral, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            if (tiene > 0) Text("Tienes ${Formato.cifra(tiene)} acciones (${Formato.eurosCorto(tiene * p / 100)})", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10L, 100L, 1000L).forEach { n -> PildoraMarco("Comprar ${Formato.cifra(n)}") { haz { Mercado.compraAcciones(Carreras.de(u.id) ?: u, v.simbolo, n) } } }
                if (tiene > 0) PildoraMarco("Vender todas") { haz { Mercado.vendeAcciones(Carreras.de(u.id) ?: u, v.simbolo, tiene) } }
            }
        }
    }
    Spacer(Modifier.height(18.dp)); Rotulo("De compras")
    Text("Lo que te puedes permitir y lo que tienes cerca; según crezca tu dinero, habrá más.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    val catalogo = Mercado.catalogo(u)
    listOf(Bien.CASA to "Casas", Bien.COCHE to "Coches y caprichos", Bien.NEGOCIO to "Negocios").forEach { (t, n) ->
        Text(n, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        catalogo.filter { it.tipo == t }.forEach { a ->
            val llega = Mercado.llega(u, a)
            Tarjeta(Modifier.padding(bottom = 8.dp)) {
                TituloTarjeta(a.nombre, tam = 17, modifier = Modifier)
                TextoTarjeta(a.descripcion, Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Formato.euros(a.precio), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                        Text((if (a.renta >= 0) "deja +" else "cuesta ") + Formato.eurosCorto(kotlin.math.abs(a.renta)) + " por jornada", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    if (llega) BotonRedondo("Comprar") { haz { Mercado.compra(Carreras.de(u.id) ?: u, a) } }
                    else Sello("No te llega", Muted)
                }
            }
        }
    }
}

