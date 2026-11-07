package com.gonzalo.cobre

import org.json.JSONArray
import org.json.JSONObject

/** De lo que da el lector de la cuenta (mapas y listas de Kotlin) a org.json y al revés. */
object JsonK {
    fun aJson(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), aJson(x) ?: JSONObject.NULL) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(aJson(it) ?: JSONObject.NULL) } }
        null -> null
        else -> v
    }

    fun aKotlin(v: Any?): Any? = when (v) {
        is JSONObject -> LinkedHashMap<String, Any?>().also { m -> v.keys().forEach { k -> m[k] = aKotlin(v.opt(k)) } }
        is JSONArray -> (0 until v.length()).map { aKotlin(v.opt(it)) }
        JSONObject.NULL -> null
        else -> v
    }
}
