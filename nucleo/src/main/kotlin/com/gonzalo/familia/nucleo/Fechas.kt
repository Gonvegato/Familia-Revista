package com.gonzalo.familia.nucleo

import java.time.LocalDate
import java.time.ZoneId

/**
 * Las fechas de la cuenta van en milisegundos. Atrio (y Azabache, que viene de él) guardaba el día de empezar y de terminar como
 * número de día (días desde el 1-1-1970): al traer lo de Atrio, ese número se tomó por milisegundos y los libros y juegos quedaron
 * «terminados» en enero de 1970. Un número de día no pasa de unos cien mil; cualquier fecha de verdad en milisegundos pasa de
 * novecientos mil millones: no hay forma de confundirlos.
 */
object Fechas {
    private const val DIA_MAXIMO = 200_000L   // el año 2517

    /** ¿Es esto un número de día (y no una fecha en milisegundos)? */
    fun esDia(v: Long): Boolean = v in 1..DIA_MAXIMO

    /** En milisegundos: un número de día pasa a las doce del mediodía de ese día (en la hora de este dispositivo); lo demás, igual. */
    fun ms(v: Long, zona: ZoneId = ZoneId.systemDefault()): Long =
        if (esDia(v)) LocalDate.ofEpochDay(v).atTime(12, 0).atZone(zona).toInstant().toEpochMilli() else v
}
