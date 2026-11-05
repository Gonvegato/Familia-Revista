package com.gonzalo.azabache.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate

/** La portada: lo que tienes entre manos, lo último que has terminado y cómo va el año. */
@Composable
fun PantInicio(nav: Nav) {
    LaFamilia.cambios
    val kinds = Cuenta.kinds()
    val todas = kinds.flatMap { k -> Cuenta.fichas(k.id).map { k to it } }
    val ahora = todas.filter { !it.second.valorada && it.second.estado == 1 }.sortedByDescending { it.second.actividad }
    val ano = LocalDate.now().year
    val inicioAno = LocalDate.of(ano, 1, 1).toEpochDay()
    val delAno = todas.filter { it.second.valorada && it.second.terminado >= inicioAno }
    val recientes = todas.filter { it.second.valorada }.sortedByDescending { it.second.terminado.takeIf { d -> d > 0 }?.times(86_400_000L) ?: it.second.actividad }.take(12)
    Pagina {
        Text("Número de ${diaLargo(LocalDate.now()).lowercase()}".uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Cabecera(96.sp, texto = "AZABACHE")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Cifra(delAno.size.toString(), "terminados en $ano", acento = true)
            kinds.take(4).forEach { k -> Cifra(delAno.count { it.first.id == k.id }.toString(), k.doneWord.lowercase() + " · " + k.title.lowercase()) }
            val (objetivo, deAno) = Cuenta.objetivoLibros()
            if (objetivo > 0 && (deAno == 0 || deAno == ano)) Cifra("${delAno.count { it.first.id == BOOKS.id }} / $objetivo", "reto de lectura")
        }
        Seccion("Ahora")
        if (ahora.isEmpty()) Vacio("Nada entre manos", "Lo que empieces en Hiedra, Marfil o aquí sale en esta portada.")
        else FilaDeTarjetas(ahora.take(12), nav, Pantalla.Inicio)
        Seccion("Lo último que has terminado")
        if (recientes.isEmpty()) Vacio("Aún nada terminado") else FilaDeTarjetas(recientes, nav, Pantalla.Inicio)
    }
}

/** Unas tarjetas en filas de seis. */
@Composable
fun FilaDeTarjetas(l: List<Pair<Kind, Ficha>>, nav: Nav, volver: Pantalla, porFila: Int = 6) {
    l.chunked(porFila).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            fila.forEach { (k, f) -> Tarjeta(f, k, Modifier.weight(1f)) { nav.ir(Pantalla.Detalle(k.id, f.id, volver)) } }
            repeat(porFila - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
