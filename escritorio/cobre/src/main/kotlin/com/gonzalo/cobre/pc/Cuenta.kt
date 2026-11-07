package com.gonzalo.cobre.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.TipoLista
import com.gonzalo.iptv.Visto
import java.io.File

/*
 * Lo de Cobre en la cuenta de la familia, campo a campo y con los mismos nombres y valores que escribe Cobre del móvil (`nube/ESQUEMA.md`;
 * los nombres de los nodos y los campos son los de `iptv/DatosCuenta`, el mismo código en los dos):
 *  - tus listas, solo en `/claves/<uid>/cobre_listas` ([Listas]);
 *  - tus favoritos y su orden, los grupos que escondes, el último canal y lo visto de cada película y episodio ([MiCuenta]);
 *  - lo que eliges, en `ajustes/cobre_<grupo>` (un campo por ajuste: [AjustesCuenta]) y la apariencia en `ajustes/cobre` (la de la familia).
 * En el móvil hay además una copia en las preferencias de Android (EspejoCuenta); aquí no hace falta: la colección de la cuenta ya tiene su
 * copia en el ordenador (y lo que cambies sin conexión sube solo). Lo que es de este ordenador (por dónde ibas antes de que llegue a la
 * cuenta, mpv a pantalla completa, su número de dispositivo) se queda en él ([Local]).
 */

/** Un favorito tuyo (un nodo `cobre_favoritos/<lista>~<canal>`). */
data class Favorito(val ref: String, val lista: String, val canal: String, val nombre: String, val orden: Long)

/**
 * Tus listas (M3U y logins de Xtream). Son tuyas y viajan con la cuenta, pero **solo en `/claves/<uid>/cobre_listas`**: sus direcciones y
 * tus contraseñas son secretos, y `/claves` es lo único de la cuenta que no puede leer ni tu pareja (las reglas). Nunca van a
 * `/users/<uid>`, ni a un registro, ni a un mensaje de error. En el ordenador, la copia de las claves la lleva la cuenta de la familia, cifrada
 * con el llavero (como el pase). En el ordenador siempre se está dentro de la cuenta (sin ella, la ventana pide entrar), así que no hay listas
 * «sin cuenta». Cobre no trae ninguna lista ni ningún canal: las pones tú.
 */
object Listas {
    /** En las pruebas de pantallas sin cuenta: las listas, fijas. */
    @Volatile var fijas: List<Lista>? = null

    fun lista(): List<Lista> {
        fijas?.let { return it }
        LaFamilia.cambios
        val t = LaFamilia.clave(DatosCuenta.CLAVE)
        return if (t.isBlank()) emptyList() else DatosCuenta.listasDeTexto(t)
    }

    fun por(id: String): Lista? = lista().firstOrNull { it.id == id }

    /** Las que se pueden usar en este ordenador (las de archivo, solo donde está el archivo). */
    fun usables(): List<Lista> = lista().filter { it.tipo != TipoLista.ARCHIVO || it.dispositivo == Local.dispositivo }

    fun guarda(l: List<Lista>) {
        if (fijas != null) { fijas = l; return }
        if (!LaFamilia.dentro) return
        LaFamilia.familia.ponClave(DatosCuenta.CLAVE, DatosCuenta.listasATexto(l))
    }

    /** Pone una lista (nueva, o la misma con lo cambiado, en su sitio). */
    fun pon(n: Lista) { val l = lista(); guarda(if (l.any { it.id == n.id }) l.map { if (it.id == n.id) n else it } else l + n) }

    fun quita(id: String) = guarda(lista().filter { it.id != id })

    fun mueve(id: String, d: Int) {
        val l = lista().toMutableList(); val i = l.indexOfFirst { it.id == id }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); guarda(l)
    }
}

/**
 * Lo tuyo de Cobre en la cuenta (`nube/ESQUEMA.md`), campo a campo y en todos tus dispositivos: tus favoritos y su orden, los grupos que
 * escondes, el último canal que viste y lo visto de cada película y episodio. Nada de esto es secreto (ni direcciones, ni usuarios, ni
 * contraseñas: solo ids y nombres). Es lo mismo que `MiCuenta` de Cobre del móvil, regla a regla.
 */
object MiCuenta {
    /** En las pruebas de pantallas: lo de la cuenta, fijo. */
    @Volatile var fijo: Fijo? = null
    class Fijo(val favoritos: List<Favorito> = emptyList(), val progresos: List<Progreso> = emptyList(), val ocultos: Map<String, Set<String>> = emptyMap(), val ultimo: Favorito? = null)

    private fun col(n: String): Coleccion? = runCatching { LaFamilia.coleccion(n) }.getOrNull()
    private val dentro: Boolean get() = runCatching { LaFamilia.familia.sesion.dentro }.getOrDefault(false)

    // ---------------------------------------------------------------- favoritos

    fun favoritos(): List<Favorito> {
        LaFamilia.cambios
        fijo?.let { return it.favoritos }
        return col(DatosCuenta.FAVORITOS)?.vivos().orEmpty().map { n -> Favorito(n.ref, n.texto("lista"), n.texto("canal"), n.texto("n"), n.entero("o")) }
            .filter { it.lista.isNotBlank() && it.canal.isNotBlank() }.sortedWith(compareBy({ it.orden }, { it.nombre }))
    }

    fun esFavorito(lista: String, clave: String) = favoritos().any { it.lista == lista && it.canal == clave }

    /** Lo pone o lo quita (quitar es `b`: si otro dispositivo lo vuelve a poner después, vuelve). */
    fun alternaFavorito(lista: String, c: Canal) {
        if (!dentro || fijo != null) return
        val col = col(DatosCuenta.FAVORITOS) ?: return
        val ref = DatosCuenta.refFavorito(lista, c.clave)
        if (esFavorito(lista, c.clave)) col.borra(ref)
        else {
            val ultimo = favoritos().maxOfOrNull { it.orden } ?: 0L
            col.pon(ref, DatosCuenta.camposFavorito(lista, c, ultimo + 1) + mapOf("b" to 0L))
        }
    }

    /** Cambia el sitio de un favorito (arriba o abajo): se renumeran todos (solo cambia el campo `o` de los que se mueven). */
    fun mueveFavorito(ref: String, d: Int) {
        if (!dentro || fijo != null) return
        val col = col(DatosCuenta.FAVORITOS) ?: return
        val l = favoritos().toMutableList()
        val i = l.indexOfFirst { it.ref == ref }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        l.forEachIndexed { k, f -> if (f.orden != (k + 1).toLong()) col.cambia(f.ref, mapOf("o" to (k + 1).toLong())) }
    }

    // ---------------------------------------------------------------- grupos escondidos

    fun ocultos(lista: String): Set<String> {
        LaFamilia.cambios
        fijo?.let { return it.ocultos[lista].orEmpty() }
        return col(DatosCuenta.GRUPOS)?.vivos().orEmpty().filter { it.texto("lista") == lista && it.si("oculto") }.map { it.texto("g") }.toSet()
    }

    fun ocultaGrupo(lista: String, grupo: String, oculto: Boolean) {
        if (!dentro || fijo != null) return
        col(DatosCuenta.GRUPOS)?.pon(DatosCuenta.refGrupo(lista, grupo), DatosCuenta.camposGrupo(lista, grupo, oculto))
    }

    // ---------------------------------------------------------------- el último canal

    fun ultimo(): Favorito? {
        LaFamilia.cambios
        fijo?.let { return it.ultimo }
        val n = col(DatosCuenta.ESTADO)?.nodo(DatosCuenta.ULTIMO) ?: return null
        if (n.texto("lista").isBlank()) return null
        return Favorito(n.ref, n.texto("lista"), n.texto("canal"), n.texto("n"), n.entero("ul"))
    }

    fun ponUltimo(lista: String, c: Canal, ahora: Long = System.currentTimeMillis()) {
        if (!dentro || fijo != null) return
        val u = ultimo()
        if (u != null && u.lista == lista && u.canal == c.clave && ahora - u.orden < 60_000L) return
        col(DatosCuenta.ESTADO)?.pon(DatosCuenta.ULTIMO, DatosCuenta.camposUltimo(lista, c, ahora))
    }

    // ---------------------------------------------------------------- lo visto del videoclub

    fun progresos(): List<Progreso> {
        LaFamilia.cambios
        fijo?.let { return it.progresos }
        return col(DatosCuenta.PROGRESO)?.vivos().orEmpty().mapNotNull { n -> DatosCuenta.progresoDe(n.campos) }
    }

    fun progreso(lista: String, video: String): Progreso? = progresos().firstOrNull { it.lista == lista && it.video == video }

    fun ponProgreso(p: Progreso) {
        if (!dentro || fijo != null) return
        col(DatosCuenta.PROGRESO)?.pon(DatosCuenta.refProgreso(p.lista, p.video), DatosCuenta.camposProgreso(p))
    }

    /** «Seguir viendo», de las listas que tienes. */
    fun seguir(): List<Progreso> = Visto.seguir(progresos(), Listas.lista().map { it.id }.toSet())

    /** Marca visto (o no) a mano. */
    fun marcaVisto(p: Progreso, visto: Boolean) = ponProgreso(p.copy(visto = visto, pos = if (visto) p.dur.coerceAtLeast(p.pos) else 0L, ultima = System.currentTimeMillis()))
}

/**
 * Lo que eliges en Cobre es tuyo, no de este ordenador: viaja con la cuenta, en `ajustes/cobre_<grupo>`, un campo por ajuste, con los mismos
 * nombres y valores que escribe Cobre del móvil. De cada campo gana el cambio más reciente, venga de donde venga.
 */
object AjustesCuenta {
    const val COLECCION = "ajustes"
    /** En las pruebas sin cuenta: los ajustes en memoria. */
    @Volatile var enMemoria: MutableMap<String, MutableMap<String, Any?>>? = null

    private fun col(): Coleccion? = runCatching { LaFamilia.coleccion(COLECCION) }.getOrNull()

    /** El valor de un ajuste ("" o null = no está: se usa el de por defecto). Leído desde Compose, repinta con los cambios de la cuenta. */
    fun lee(nodo: String, campo: String): Any? {
        enMemoria?.let { return it[nodo]?.get(campo)?.takeIf { v -> v != "" } }
        LaFamilia.cambios
        return col()?.nodo(nodo)?.get(campo)?.takeIf { it != "" }
    }

    fun texto(nodo: String, campo: String, d: String): String = lee(nodo, campo)?.comoTexto() ?: d
    fun entero(nodo: String, campo: String, d: Int): Int = lee(nodo, campo)?.let { it.comoLong().toInt() } ?: d
    fun si(nodo: String, campo: String, d: Boolean): Boolean = lee(nodo, campo)?.comoBool() ?: d

    /**
     * Cambia ajustes (solo los que de verdad cambian viajan). Los enteros van como números enteros, como los escribe Android. Un campo que
     * aún no está en la cuenta se escribe aunque sea `false` o `0` (para la cuenta, un campo que no existe «es» `false`, pero el de por
     * defecto de un ajuste puede ser `true`: «Volver a conectar solo» apagado tiene que llegar).
     */
    fun pon(nodo: String, campos: Map<String, Any?>) {
        val limpios = campos.mapValues { (_, v) -> when (v) { is Int -> v.toLong(); null -> ""; else -> v } }
        enMemoria?.let { m -> m.getOrPut(nodo) { LinkedHashMap() }.putAll(limpios); return }
        val c = col() ?: return
        val actual = c.nodo(nodo)
        val distintos = limpios.filter { (k, v) -> actual?.get(k) == null || !Coleccion.igual(actual[k], v) }
        if (distintos.isNotEmpty()) c.pon(nodo, distintos)
    }
}

/** El reproductor (`ajustes/cobre_reproductor`). `ahorro` (con datos del móvil) y `externo` (un paquete de Android) son del móvil: aquí no se tocan. */
object AjustesReproductor {
    const val NODO = "cobre_reproductor"
    /** Cómo se ve la imagen (los mismos nombres que en el móvil). */
    enum class Aspecto(val nombre: String) { AJUSTAR("Ajustar"), LLENAR("Llenar"), ZOOM("Zoom"), ANCHO("16:9"), ESTRECHO("4:3") }
    fun aspecto() = Aspecto.entries.firstOrNull { it.name == AjustesCuenta.texto(NODO, "aspecto", "") } ?: Aspecto.AJUSTAR
    fun ponAspecto(a: Aspecto) = AjustesCuenta.pon(NODO, mapOf("aspecto" to a.name))
    /** El idioma de los subtítulos («spa», «eng»…; "" = sin ellos). */
    fun subs() = AjustesCuenta.texto(NODO, "subs", "")
    fun ponSubs(v: String) = AjustesCuenta.pon(NODO, mapOf("subs" to v))
    /** El idioma de audio preferido cuando un canal o una película trae varios. */
    fun audio() = AjustesCuenta.texto(NODO, "audio", "spa")
    fun ponAudio(v: String) = AjustesCuenta.pon(NODO, mapOf("audio" to v))
    /** Si se corta un canal, se vuelve a conectar solo. */
    fun reconectar() = AjustesCuenta.si(NODO, "reconectar", true)
    fun ponReconectar(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("reconectar" to v))
    /** Al terminar un episodio, el siguiente solo. */
    fun siguiente() = AjustesCuenta.si(NODO, "siguiente", true)
    fun ponSiguiente(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("siguiente" to v))
    /** Arriba es el canal anterior (como en muchas teles) en vez del siguiente. */
    fun invertirCanales() = AjustesCuenta.si(NODO, "invertirCanales", false)
    fun ponInvertirCanales(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("invertirCanales" to v))
    /** Cuánto salta adelante o atrás en una película (segundos). */
    fun salto() = AjustesCuenta.entero(NODO, "salto", 10).coerceIn(5, 60)
    fun ponSalto(v: Int) = AjustesCuenta.pon(NODO, mapOf("salto" to v))
}

/** La guía (`ajustes/cobre_guia`): cada cuánto se baja y cuántos días. `soloWifi` es del móvil (un ordenador no tiene datos móviles). */
object AjustesGuia {
    const val NODO = "cobre_guia"
    fun horas() = AjustesCuenta.entero(NODO, "horas", 12).coerceIn(3, 72)
    fun ponHoras(v: Int) = AjustesCuenta.pon(NODO, mapOf("horas" to v))
    fun dias() = AjustesCuenta.entero(NODO, "dias", 4).coerceIn(1, 7)
    fun ponDias(v: Int) = AjustesCuenta.pon(NODO, mapOf("dias" to v))
}

/** Los canales (`ajustes/cobre_directo`): su orden, dónde estabas y qué hacer al abrir. */
object AjustesDirecto {
    const val NODO = "cobre_directo"
    enum class Orden(val nombre: String) { LISTA("Como la lista"), NUMERO("Por número"), NOMBRE("Por nombre") }
    fun orden() = Orden.entries.firstOrNull { it.name == AjustesCuenta.texto(NODO, "orden", "") } ?: Orden.LISTA
    fun ponOrden(o: Orden) = AjustesCuenta.pon(NODO, mapOf("orden" to o.name))
    /** La lista y el grupo que estabas mirando (sus ids, sin nada secreto). */
    fun lista() = AjustesCuenta.texto(NODO, "lista", "")
    fun grupo() = AjustesCuenta.texto(NODO, "grupo", "")
    fun ponDonde(lista: String, grupo: String) = AjustesCuenta.pon(NODO, mapOf("lista" to lista, "grupo" to grupo))
    /** Al abrir Cobre, el último canal que viste (como una tele). */
    fun alAbrir() = AjustesCuenta.si(NODO, "alAbrir", false)
    fun ponAlAbrir(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("alAbrir" to v))
}

/**
 * Lo que es de ESTE ordenador y no viaja (`nube/ESQUEMA.md`, «No viaja»): su número de dispositivo (`cobre-linux-…` / `cobre-mac-…`, el de
 * las listas de archivo), por dónde ibas de una película antes de que llegue a la cuenta, y mpv a pantalla completa. En la carpeta de Cobre
 * (`Sitio.de("cobre")`), en un JSON pequeño.
 */
object Local {
    /** En las pruebas: otra carpeta. */
    @Volatile var dirPruebas: File? = null
    val dir: File get() = dirPruebas?.also { it.mkdirs() } ?: Sitio.de("cobre")
    private fun archivo() = File(dir, "local.json")
    @Volatile private var datos: MutableMap<String, Any?>? = null
    @Volatile var dispositivoPruebas: String? = null

    /** Este ordenador para Cobre («cobre-linux-1a2b3c4d»): el `dev` de las listas de archivo. */
    val dispositivo: String get() = dispositivoPruebas ?: Sitio.dispositivo("cobre")

    @Synchronized private fun todo(): MutableMap<String, Any?> = datos ?: LinkedHashMap(runCatching { Json.lee(archivo().readText()).comoMapa() }.getOrDefault(emptyMap())).also { datos = it }

    @Synchronized fun lee(k: String): Any? = todo()[k]
    @Synchronized fun pon(k: String, v: Any?) {
        val t = todo(); if (v == null) t.remove(k) else t[k] = v
        runCatching { val tmp = File(dir, "local.json.parcial"); tmp.writeText(Json.escribe(t)); if (!tmp.renameTo(archivo())) { archivo().writeText(Json.escribe(t)); tmp.delete() } }
    }

    fun olvidaParaPruebas() { datos = null }
}

/** Dónde lo dejaste en ESTE ordenador (lo de la cuenta, en [MiCuenta]): gana lo más reciente de los dos, como en el móvil. */
object Reanudar {
    private fun clave(lista: String, video: String) = "reanudar:$lista~$video"

    fun pon(lista: String, video: String, pos: Long, dur: Long, ahora: Long = System.currentTimeMillis()) = Local.pon(clave(lista, video), "$pos;$dur;$ahora")

    /** (posición, duración, cuándo) de aquí. */
    fun de(lista: String, video: String): Triple<Long, Long, Long>? = (Local.lee(clave(lista, video)) as? String)?.split(';')?.let { s ->
        if (s.size < 3) null else Triple(s[0].toLongOrNull() ?: 0L, s[1].toLongOrNull() ?: 0L, s[2].toLongOrNull() ?: 0L)
    }

    /** Desde dónde seguir: lo más reciente entre lo de la cuenta (lo visto en otro dispositivo) y lo de aquí ([Visto.desde]: las reglas de siempre). */
    fun desde(lista: String, video: String): Long {
        val cuenta = MiCuenta.progreso(lista, video)
        val aqui = de(lista, video)
        val (pos, dur) = when {
            cuenta != null && (aqui == null || cuenta.ultima >= aqui.third) -> cuenta.pos to cuenta.dur
            aqui != null -> aqui.first to aqui.second
            else -> return 0L
        }
        return Visto.desde(pos, dur)
    }
}

/** Abrir mpv a pantalla completa (es de este ordenador: no viaja). */
object PantallaCompleta {
    fun si(): Boolean = Local.lee("mpvPantallaCompleta")?.comoBool() ?: true
    fun pon(v: Boolean) = Local.pon("mpvPantallaCompleta", v)
}
