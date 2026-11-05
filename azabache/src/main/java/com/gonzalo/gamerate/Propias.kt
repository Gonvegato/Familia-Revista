package com.gonzalo.gamerate

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Coleccion
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Lo propio de Azabache que es tuyo (no de este móvil): tus categorías y sus fichas (películas, series, las tuyas), el diario, las
 * sagas, las maquetas, la lista negra, lo que tienes en tu nube y lo leído de cada libro. Está en la cuenta (colección `azabache`)
 * y se lee y se escribe ahí, sin copia: cada elemento de cada lista es un nodo `azabache/<lista>~<id>` con `o` = el elemento (texto
 * JSON); una lista sin números, un nodo `azabache/<lista>` con `v`. Es el mismo formato que subía Azabache 1.3, así que lo que ya
 * estaba en la cuenta sirve tal cual.
 *
 * Lo demás (la apariencia de este móvil, los avisos, la sesión…) sigue en las preferencias de siempre ([PrefsCuenta]).
 */
object Propias {
    private val FIJAS = setOf("sagas", "nube", "lectura", "maquetas", "diario", "negra", "kinds")

    /** ¿Es esta preferencia una lista de la cuenta? */
    fun es(key: String): Boolean = key in FIJAS || key.startsWith("items_") || key.startsWith("iplays_")

    private fun col(): Coleccion = LaFamilia.coleccion("azabache")
    private val principal by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var oyendoDe: Any? = null
    private val cache = HashMap<String, String?>()
    @Volatile private var avisoPendiente = false

    private fun prepara(c: Context) {
        LaFamilia.inicia(c)
        val fam = LaFamilia.familia
        if (oyendoDe === fam) return
        synchronized(this) {
            if (oyendoDe === fam) return
            oyendoDe = fam
            cache.clear()
            col().alCambiar {
                synchronized(this) { cache.clear() }
                Store.otraGeneracion()
                if (!avisoPendiente) { avisoPendiente = true; principal.postDelayed({ avisoPendiente = false; Hermanas.repinta() }, 60) }
            }
        }
    }

    // ================================================================ desde cuándo

    private val migrando = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile var migrarEnElMomento = false

    /**
     * ¿Ya se lee de la cuenta? Desde que, con la sesión abierta y la colección bajada entera, lo que hubiera aquí sin subir ha subido.
     * Antes (sin haber entrado nunca), las listas siguen en este móvil como siempre.
     */
    fun activa(c: Context): Boolean {
        val p = c.getSharedPreferences("propias_cuenta", Context.MODE_PRIVATE)
        if (p.getBoolean("migradas", false)) return true
        return runCatching { intenta(c, p) }.getOrDefault(false)
    }

    private fun intenta(c: Context, p: SharedPreferences): Boolean {
        prepara(c)
        if (!LaFamilia.familia.sesion.dentro || !col().completa) return false
        if (!migrando.compareAndSet(false, true)) return false
        val hazla = {
            try {
                // Lo que hubiera aquí sin subir, a la cuenta (elemento a elemento, gana lo más nuevo); y desde ahora, de la cuenta
                runCatching { AzabacheNube.sube(c, Store(c)) }
                p.edit().putBoolean("migradas", true).apply()
                synchronized(this) { cache.clear() }
            } finally { migrando.set(false) }
            Store.otraGeneracion()
            Hermanas.repinta()
        }
        if (migrarEnElMomento) { hazla(); return true }
        kotlin.concurrent.thread(name = "azabache-lo-propio") { runCatching { hazla() } }
        return false
    }

    // ================================================================ leer y escribir

    private fun elementos(key: String): Map<Long, JSONObject> {
        val pre = "$key~"
        val out = sortedMapOf<Long, JSONObject>()
        for (n in col().vivos()) {
            if (!n.ref.startsWith(pre)) continue
            val id = n.ref.substring(pre.length).toLongOrNull() ?: continue
            n.texto("o").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { out[id] = it }
        }
        return out
    }

    /** La preferencia (como texto JSON) tal como está en la cuenta, o null si la cuenta no la tiene. */
    fun lee(c: Context, key: String): String? {
        prepara(c)
        synchronized(this) {
            if (cache.containsKey(key)) return cache[key]
            val els = elementos(key)
            val v = if (els.isNotEmpty()) JSONArray().also { a -> els.values.forEach { a.put(it) } }.toString()
                else col().nodo(key)?.takeIf { !it.borrado }?.texto("v")?.takeIf { it.startsWith("[") }
                    ?.let { runCatching { JSONArray(it).get(0) }.getOrNull() }?.toString()
            cache[key] = v
            return v
        }
    }

    /**
     * Escribe una preferencia de la cuenta: solo lo que ha cambiado, elemento a elemento (lo que ya no está en la lista se borra de la
     * cuenta). [v] null: se quita entera.
     */
    fun escribe(c: Context, key: String, v: String?) {
        prepara(c)
        synchronized(this) {
            val col = col()
            val antes = elementos(key)
            val valor = v?.let { runCatching { JSONTokener(it).nextValue() }.getOrNull() }
            if (v == null || (valor is JSONArray && valor.length() == 0)) {
                antes.keys.forEach { col.borra("$key~$it") }
                col.nodo(key)?.takeIf { !it.borrado }?.let { if (v == null) col.borra(key) else col.cambia(key, mapOf("v" to JSONArray().put(JSONArray()).toString())) }
            } else if (valor is JSONArray && (0 until valor.length()).all { (valor.opt(it) as? JSONObject)?.has("id") == true }) {
                val ids = HashSet<Long>()
                for (i in 0 until valor.length()) {
                    val e = valor.getJSONObject(i); val id = e.optLong("id"); ids += id
                    val a = antes[id]
                    if (a == null || Store.canon(a) != Store.canon(e)) col.cambia("$key~$id", mapOf("o" to e.toString(), "b" to 0L))
                }
                (antes.keys - ids).forEach { col.borra("$key~$it") }
            } else if (valor != null) {
                col.cambia(key, mapOf("v" to JSONArray().put(valor).toString()))
            }
            cache.remove(key)
        }
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { synchronized(this) { cache.clear(); oyendoDe = null } }
}

/**
 * Las preferencias de Azabache con lo que es de la cuenta ([Propias.es]) leído y escrito en la cuenta, no aquí (desde que [Propias]
 * está activa). Lo demás, en las de siempre.
 */
class PrefsCuenta(private val base: SharedPreferences, private val ctx: Context) : SharedPreferences {
    private fun deLaCuenta(key: String?) = key != null && Propias.es(key) && Propias.activa(ctx)

    override fun getString(key: String?, defValue: String?): String? = if (deLaCuenta(key)) Propias.lee(ctx, key!!) ?: defValue else base.getString(key, defValue)
    override fun contains(key: String?): Boolean = if (deLaCuenta(key)) Propias.lee(ctx, key!!) != null else base.contains(key)
    override fun getAll(): MutableMap<String, *> = base.all
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = base.getStringSet(key, defValues)
    override fun getInt(key: String?, defValue: Int) = base.getInt(key, defValue)
    override fun getLong(key: String?, defValue: Long) = base.getLong(key, defValue)
    override fun getFloat(key: String?, defValue: Float) = base.getFloat(key, defValue)
    override fun getBoolean(key: String?, defValue: Boolean) = base.getBoolean(key, defValue)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = base.registerOnSharedPreferenceChangeListener(listener)
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = base.unregisterOnSharedPreferenceChangeListener(listener)
    override fun edit(): SharedPreferences.Editor = Editor(base.edit())

    private inner class Editor(private val e: SharedPreferences.Editor) : SharedPreferences.Editor {
        private val aLaCuenta = LinkedHashMap<String, String?>()
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (deLaCuenta(key)) aLaCuenta[key!!] = value else e.putString(key, value); return this }
        override fun remove(key: String?): SharedPreferences.Editor { if (deLaCuenta(key)) aLaCuenta[key!!] = null else e.remove(key); return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { e.putStringSet(key, values); return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { e.putInt(key, value); return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { e.putLong(key, value); return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { e.putFloat(key, value); return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { e.putBoolean(key, value); return this }
        override fun clear(): SharedPreferences.Editor { e.clear(); return this }
        private fun aLaCuentaYa() { val l = aLaCuenta.toList(); aLaCuenta.clear(); l.forEach { (k, v) -> Propias.escribe(ctx, k, v) } }
        override fun commit(): Boolean { aLaCuentaYa(); return e.commit() }
        override fun apply() { aLaCuentaYa(); e.apply() }
    }
}
