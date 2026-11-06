package com.gonzalo.marfil

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lo que eliges y Android guarda en las preferencias de la app (el orden de cada sección, cómo se juega, el emulador de cada consola…)
 * es tuyo, no de este móvil: está también en tu cuenta, en `ajustes/<nodo>`, **un campo por ajuste** con el mismo nombre que tiene en
 * las preferencias, y de cada uno gana el cambio más reciente (`nube/ESQUEMA.md`). Las preferencias de aquí son la copia para leerlo al
 * momento y sin red: lo que cambias se escribe aquí y en la cuenta; lo que llega de otro dispositivo, aquí.
 *
 * La primera vez con la cuenta en este dispositivo, lo que ya tenía pasa a la cuenta sin perder nada: lo que la cuenta no tenía, tal
 * cual; lo que tenía distinto, gana el dispositivo que lo cambió más tarde (la hora de sus preferencias); y las listas que solo crecen
 * ([Grupo.juntar]) se juntan.
 *
 * (El mismo código está en Hiedra, Marfil y Azabache.)
 */
object EspejoCuenta {
    enum class Tipo { SI_NO, ENTERO, LARGO, DECIMAL, TEXTO }

    /**
     * Unas preferencias de Android que viajan con la cuenta. [tipo]: qué ajustes viajan y de qué tipo son (null: ese se queda aquí, porque
     * es de este dispositivo). [juntar]: para lo que son listas que solo crecen, cómo se juntan la de la cuenta y la de aquí al pasar lo de
     * antes (para no perder lo de ninguno). [alLlegar]: ha llegado algo de otro dispositivo (en el hilo principal).
     */
    class Grupo(
        val archivo: String,
        val nodo: String,
        val tipo: (String) -> Tipo?,
        val juntar: (String) -> ((String, String) -> String)? = { null },
        val alLlegar: (Context) -> Unit = {},
    )

    const val COLECCION = "ajustes"

    /** Sube cada vez que llega algo de otro dispositivo: las pantallas que leen estas preferencias las vuelven a leer. */
    private val ver = mutableIntStateOf(0)
    val version: Int get() = ver.intValue

    private val grupos = ConcurrentHashMap<String, Grupo>()
    private val principal by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var app: Context? = null
    @Volatile private var de: Any? = null
    private val ocupado = AtomicBoolean(false)

    /** Lo que lleva la cuenta de este mecanismo en este dispositivo (qué se ha pasado ya, a qué hora se cambió cada cosa sin cuenta). */
    private fun libro(c: Context) = c.getSharedPreferences("espejo_cuenta", Context.MODE_PRIVATE)

    /** Las preferencias tal cual (sin pasar por la cuenta): para leer y para lo que no es un cambio tuyo. */
    fun base(c: Context, g: Grupo): SharedPreferences = c.getSharedPreferences(g.archivo, Context.MODE_PRIVATE)

    /** Las preferencias de [g]: se leen de aquí; lo que se escribe va también a la cuenta. */
    fun prefs(c: Context, g: Grupo): SharedPreferences { registra(c, g); return Espejo(c.applicationContext, g) }

    private fun registra(c: Context, g: Grupo) {
        app = c.applicationContext
        grupos.putIfAbsent(g.nodo, g)
        val l = libro(c)
        // La hora de lo que había aquí: la última vez que se cambiaron estas preferencias antes de que viajaran (antes de escribir nada)
        if (!l.contains("hora:${g.archivo}")) {
            val f = File(c.applicationInfo.dataDir ?: "", "shared_prefs/${g.archivo}.xml")
            l.edit().putLong("hora:${g.archivo}", if (f.isFile) f.lastModified() else 0L).apply()
        }
    }

    /**
     * Al arrancar la app (con la cuenta ya iniciada): escucha lo que llega de tus otros dispositivos y, en cuanto se puede (con la sesión
     * abierta y los ajustes de la cuenta bajados), pasa a la cuenta lo que este dispositivo tenía de antes.
     */
    fun inicia(c: Context, lista: List<Grupo>) {
        val a = c.applicationContext
        lista.forEach { registra(a, it) }
        val fam = runCatching { LaFamilia.familia }.getOrNull() ?: return
        var nueva = false
        if (de !== fam) synchronized(this) {
            if (de !== fam) {
                de = fam; nueva = true
                val col = LaFamilia.coleccion(COLECCION)
                col.alCambiar { refs -> app?.let { x -> refs.forEach { r -> grupos[r]?.let { g -> if (hecho(x, g)) runCatching { aplica(x, g, col) } } } } }
                val previo = fam.alSincronizar
                fam.alSincronizar = { inf -> previo?.invoke(inf); if (inf.ok) app?.let { x -> runCatching { intenta(x) } } }
            }
        }
        // (luego, tras cada sincronización: [Familia.alSincronizar])
        if (nueva) kotlin.concurrent.thread(name = "espejo-cuenta") { runCatching { intenta(a) } }
    }

    /** Pasa lo de antes y lo cambiado sin cuenta, si ya se puede. Devuelve si todo está ya en la cuenta. (Las pruebas lo llaman a mano.) */
    fun intenta(c: Context): Boolean {
        val fam = runCatching { LaFamilia.familia }.getOrNull() ?: return false
        if (!fam.sesion.dentro) return false
        if (!ocupado.compareAndSet(false, true)) return false
        try {
            val col = LaFamilia.coleccion(COLECCION)
            grupos.values.forEach { g -> subeSucios(c, g, col) }
            if (!col.completa) return false
            for (g in grupos.values) {
                if (hecho(c, g)) continue
                migra(c, g, col)
                libro(c).edit().putBoolean(clavePaso(g), true).commit()
                aplica(c, g, col)
            }
            return true
        } finally { ocupado.set(false) }
    }

    private fun clavePaso(g: Grupo) = "hecho:${runCatching { LaFamilia.familia.sesion.uid }.getOrDefault("")}:${g.archivo}"
    private fun hecho(c: Context, g: Grupo) = libro(c).getBoolean(clavePaso(g), false)

    /** La hora de lo que este dispositivo tenía de antes (nunca en el futuro; 1 si no se sabe: entonces gana lo que ya hubiera en la cuenta). */
    private fun horaDeAqui(c: Context, g: Grupo): Long = libro(c).getLong("hora:${g.archivo}", 0L).coerceAtMost(System.currentTimeMillis()).coerceAtLeast(1L)

    /** Lo de antes de este dispositivo, a la cuenta: el servidor se queda con lo más reciente de cada ajuste; las listas, juntas. */
    private fun migra(c: Context, g: Grupo, col: Coleccion) {
        val n = col.nodo(g.nodo)
        val hora = horaDeAqui(c, g)
        val campos = LinkedHashMap<String, Any?>()
        for ((k, v) in base(c, g).all) {
            if (g.tipo(k) == null) continue
            val local = aCuenta(v) ?: continue
            val f = campo(k)
            val remoto = n?.campos?.get(f)
            val juntar = g.juntar(k)
            if (juntar != null && remoto != null && remoto != "") {
                val unido = juntar(remoto.comoTexto(), local.comoTexto())
                if (unido != remoto.comoTexto()) col.pon(g.nodo, mapOf(f to unido), maxOf(hora, (n.m[f] ?: 0L) + 1L))
                continue
            }
            if (remoto == null || !Coleccion.igual(remoto, local)) campos[f] = local
        }
        if (campos.isNotEmpty()) col.pon(g.nodo, campos, hora)
    }

    /** Lo que dice la cuenta (lo de tus otros dispositivos, o lo de aquí aún sin subir), a las preferencias de aquí. */
    private fun aplica(c: Context, g: Grupo, col: Coleccion) {
        val n = col.nodo(g.nodo) ?: return
        val b = base(c, g)
        val todo = b.all
        val ed = b.edit()
        var cambio = false
        for ((f, v) in n.campos) {
            if (f == Nodo.B) continue
            val k = clave(f)
            val t = g.tipo(k) ?: continue
            if (v == null || v == "") { if (todo.containsKey(k)) { ed.remove(k); cambio = true }; continue }
            val nuevo = deCuenta(t, v)
            if (todo[k] != nuevo) { pon(ed, k, nuevo); cambio = true }
        }
        if (!cambio) return
        ed.commit()
        principal.post { ver.intValue++; runCatching { g.alLlegar(c) } }
    }

    /** Lo que acaba de cambiar aquí: a la cuenta (sin cuenta todavía, se apunta con su hora para subirlo luego). */
    private fun sube(c: Context, g: Grupo, cambios: Map<String, Any?>) {
        if (cambios.isEmpty()) return
        val campos = cambios.entries.associate { (k, v) -> campo(k) to (aCuenta(v) ?: "") }
        val col = runCatching { LaFamilia.familia; LaFamilia.coleccion(COLECCION) }.getOrNull()
        if (col == null || runCatching { col.pon(g.nodo, campos) }.isFailure) apuntaSucios(c, g, campos.keys)
    }

    private fun apuntaSucios(c: Context, g: Grupo, campos: Set<String>) {
        val l = libro(c)
        val o = runCatching { JSONObject(l.getString("sucio:${g.archivo}", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val ahora = System.currentTimeMillis()
        campos.forEach { o.put(it, ahora) }
        l.edit().putString("sucio:${g.archivo}", o.toString()).commit()
    }

    private fun subeSucios(c: Context, g: Grupo, col: Coleccion) {
        val l = libro(c)
        val o = l.getString("sucio:${g.archivo}", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        val todo = base(c, g).all
        for (f in o.keys()) col.pon(g.nodo, mapOf(f to (aCuenta(todo[clave(f)]) ?: "")), o.optLong(f).coerceAtLeast(1L))
        l.edit().remove("sucio:${g.archivo}").commit()
    }

    // ---------------------------------------------------------------- de las preferencias a la cuenta y al revés (sin red: se prueba)

    /** El nombre del campo en la cuenta: el de la preferencia (lo que Firebase no admite en un nombre, como %XX). */
    fun campo(k: String): String {
        val sb = StringBuilder()
        for (ch in k) if (ch in ".$#[]/%" || ch.code < 32 || ch.code == 127) sb.append('%').append("%02X".format(ch.code)) else sb.append(ch)
        val s = sb.toString()
        return if (s == Nodo.M || s == Nodo.U || s == Nodo.B) "%" + "%02X".format(s[0].code) else s
    }

    fun clave(f: String): String {
        if ('%' !in f) return f
        val sb = StringBuilder(); var i = 0
        while (i < f.length) {
            val h = if (f[i] == '%' && i + 2 < f.length) f.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (h != null) { sb.append(h.toChar()); i += 3 } else { sb.append(f[i]); i++ }
        }
        return sb.toString()
    }

    fun aCuenta(v: Any?): Any? = when (v) {
        null -> null
        is Boolean, is Long, is Double, is String -> v
        is Int -> v.toLong()
        is Float -> v.toString().toDouble()
        is Set<*> -> v.map { it.toString() }.sorted()
        else -> v.toString()
    }

    fun deCuenta(t: Tipo, v: Any?): Any = when (t) {
        Tipo.SI_NO -> v.comoBool()
        Tipo.ENTERO -> v.comoLong().toInt()
        Tipo.LARGO -> v.comoLong()
        Tipo.DECIMAL -> v.comoDouble().toFloat()
        Tipo.TEXTO -> v.comoTexto()
    }

    private fun pon(ed: SharedPreferences.Editor, k: String, v: Any) {
        when (v) { is Boolean -> ed.putBoolean(k, v); is Int -> ed.putInt(k, v); is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v); else -> ed.putString(k, v.toString()) }
    }

    // ---------------------------------------------------------------- cómo se juntan dos listas (para [Grupo.juntar])

    /** «a,b,c» y «c,d»: «a,b,c,d». */
    fun juntaComas(a: String, b: String): String = (a.split(',') + b.split(',')).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /** Dos listas JSON de valores sueltos: todos, sin repetir. */
    fun juntaLista(a: String, b: String): String {
        val r = JSONArray(); val vistos = HashSet<String>()
        for (s in listOf(a, b)) runCatching { JSONArray(s) }.getOrNull()?.let { x -> for (i in 0 until x.length()) { val v = x.get(i); if (vistos.add(v.toString())) r.put(v) } }
        return r.toString()
    }

    /** Dos listas JSON de objetos: todos, sin repetir los que tienen el mismo [id] (se queda el de la cuenta). */
    fun juntaObjetos(a: String, b: String, id: (JSONObject) -> String): String {
        val r = JSONArray(); val vistos = HashSet<String>()
        for (s in listOf(a, b)) runCatching { JSONArray(s) }.getOrNull()?.let { x -> for (i in 0 until x.length()) x.optJSONObject(i)?.let { o -> if (vistos.add(id(o))) r.put(o) } }
        return r.toString()
    }

    /** (Para las pruebas: como si la app acabara de abrirse.) */
    fun olvidaParaPruebas() { de = null; ocupado.set(false) }

    /** Las preferencias con lo que se escribe también en la cuenta. */
    private class Espejo(private val c: Context, private val g: Grupo) : SharedPreferences {
        private val b = base(c, g)
        override fun getAll(): MutableMap<String, *> = b.all
        override fun getString(k: String?, d: String?): String? = b.getString(k, d)
        override fun getStringSet(k: String?, d: MutableSet<String>?): MutableSet<String>? = b.getStringSet(k, d)
        override fun getInt(k: String?, d: Int) = b.getInt(k, d)
        override fun getLong(k: String?, d: Long) = b.getLong(k, d)
        override fun getFloat(k: String?, d: Float) = b.getFloat(k, d)
        override fun getBoolean(k: String?, d: Boolean) = b.getBoolean(k, d)
        override fun contains(k: String?) = b.contains(k)
        override fun edit(): SharedPreferences.Editor = Ed(b.edit())
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = b.registerOnSharedPreferenceChangeListener(l)
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = b.unregisterOnSharedPreferenceChangeListener(l)

        private inner class Ed(private val e: SharedPreferences.Editor) : SharedPreferences.Editor {
            /** Lo que se pone (null: se quita), en orden. */
            private val puestos = LinkedHashMap<String, Any?>()
            private var vaciar = false
            private fun apunta(k: String?, v: Any?): SharedPreferences.Editor { if (k != null) puestos[k] = v; return this }
            override fun putString(k: String?, v: String?): SharedPreferences.Editor { e.putString(k, v); return apunta(k, v) }
            override fun putStringSet(k: String?, v: MutableSet<String>?): SharedPreferences.Editor { e.putStringSet(k, v); return apunta(k, v?.toSet()) }
            override fun putInt(k: String?, v: Int): SharedPreferences.Editor { e.putInt(k, v); return apunta(k, v) }
            override fun putLong(k: String?, v: Long): SharedPreferences.Editor { e.putLong(k, v); return apunta(k, v) }
            override fun putFloat(k: String?, v: Float): SharedPreferences.Editor { e.putFloat(k, v); return apunta(k, v) }
            override fun putBoolean(k: String?, v: Boolean): SharedPreferences.Editor { e.putBoolean(k, v); return apunta(k, v) }
            override fun remove(k: String?): SharedPreferences.Editor { e.remove(k); return apunta(k, null) }
            override fun clear(): SharedPreferences.Editor { e.clear(); vaciar = true; return this }

            /** Solo lo que de verdad cambia (si se guardan todos los ajustes a la vez, viaja solo el que has tocado). */
            private fun cambios(): Map<String, Any?> {
                val antes = b.all
                val despues = HashMap<String, Any?>(if (vaciar) emptyMap() else antes)
                puestos.forEach { (k, v) -> if (v == null) despues.remove(k) else despues[k] = v }
                val r = LinkedHashMap<String, Any?>()
                for (k in antes.keys + despues.keys) if (g.tipo(k) != null && antes[k] != despues[k]) r[k] = despues[k]
                return r
            }
            override fun apply() { val cs = cambios(); e.apply(); sube(c, g, cs) }
            override fun commit(): Boolean { val cs = cambios(); val r = e.commit(); sube(c, g, cs); return r }
        }
    }
}
