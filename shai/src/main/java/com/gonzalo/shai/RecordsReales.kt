package com.gonzalo.shai

/**
 * Los 200 récords del fútbol de verdad que pasó Gonzalo («200 Récords Históricos del Fútbol Mundial», actualizada a la temporada 2025/26),
 * tal cual vienen en su lista salvo las correcciones que se comprobaron en internet (noviembre de 2026):
 *  - #127 el Barça tiene 32 Copas del Rey (no 31); la de 2026 la ganó la Real Sociedad.
 *  - #139 Thomas Müller tiene 13 Bundesligas (no 12), y Manuel Neuer las igualó en 2025/26.
 *  - #178 Stanley Matthews no marcó con 50 años: su récord es haber jugado en la Primera inglesa con 50 años y 5 días (1965).
 *  - #85 Dick Advocaat dirigió en el Mundial 2026 con 78 años (los días exactos varían según la fuente).
 * Comprobados como ciertos: #58 (Mbappé, 22 goles en Mundiales), #62 (Cristiano marcó en 6 Mundiales), #143 (Unai Simón, 650 minutos) y
 * #198 (Mundial 2026, 308 goles). Los marcados [RecordReal.dudoso] (cifras que no cuadran entre fuentes, marcas «+» que ya han subido,
 * nunca verificados) no se usan para decir que se bate nada. El resto, sin comprobar uno a uno: su fuente es la lista.
 *
 * [RecordReal.clave] dice con qué cifra de la carrera en Shai se puede comparar (null: es para leerlo, no se compara). Ver [Historicos].
 */
data class RecordReal(
    val n: Int,
    val categoria: Int,
    val record: String,
    val quien: String,
    /** La marca tal cual se cuenta («17 años y 40 días», «50 goles»…). */
    val marca: String,
    /** La marca en número (goles, partidos, días…); en las de edad, [anios] y [dias]. */
    val valor: Double? = null,
    val unidad: String = "",
    val tipo: TipoRecord = TipoRecord.MAS,
    val clave: String? = null,
    val anios: Int = -1,
    /** -1: solo se sabe el año (se compara por años). */
    val dias: Int = -1,
    val dudoso: Boolean = false,
    val nota: String = "",
    val fuente: String = LISTA,
) {
    val deEdad: Boolean get() = anios >= 0
    /** Se puede usar para decir que se bate o se iguala. */
    val usable: Boolean get() = clave != null && !dudoso

    companion object {
        const val LISTA = "lista del usuario (temporada 2025/26)"
        const val COMPROBADO = "comprobado en internet (noviembre de 2026)"
        val CATEGORIAS = listOf("Precocidad y juventud", "Goles y eficiencia", "Mundiales y selecciones", "Champions y clubes",
            "Títulos y palmarés", "Portería y defensa", "Asistencias, apariciones y longevidad", "Marcas insólitas y rachas")
    }
}

/** Qué cuenta como batirlo: ser más joven, más veterano, tener más o (en las de «único»/«empate») igualarlo. */
enum class TipoRecord { JOVEN, VETERANO, MAS }

object RecordsReales {
    private fun cat(n: Int) = when (n) { in 1..25 -> 1; in 26..55 -> 2; in 56..85 -> 3; in 86..115 -> 4; in 116..140 -> 5; in 141..165 -> 6; in 166..185 -> 7; else -> 8 }

    /** Uno de edad. */
    private fun e(n: Int, record: String, quien: String, a: Int, d: Int, clave: String? = null, tipo: TipoRecord = TipoRecord.JOVEN, dudoso: Boolean = false, nota: String = "", fuente: String = RecordReal.LISTA) =
        RecordReal(n, cat(n), record, quien, if (d >= 0) "$a años y $d días" else "$a años", a * 1000.0 + maxOf(d, 0), "edad", tipo, clave, a, d, dudoso, nota, fuente)

    /** Uno de cifra. */
    private fun c(n: Int, record: String, quien: String, marca: String, valor: Double? = null, unidad: String = "", clave: String? = null, dudoso: Boolean = false, nota: String = "", fuente: String = RecordReal.LISTA) =
        RecordReal(n, cat(n), record, quien, marca, valor, unidad, TipoRecord.MAS, clave, dudoso = dudoso, nota = nota, fuente = fuente)

    private const val MIN = "La marca va con «+»: ya ha subido y no se sabe cuánto"

    val TODOS: List<RecordReal> = listOf(
        // 1. Precocidad y juventud
        e(1, "Campeón del Mundo más joven", "Pelé (Brasil) – Suecia 1958", 17, 249, "campeon_joven:mundial"),
        e(2, "Goleador en final de Mundial más joven", "Pelé (Brasil) – doblete ante Suecia, 1958", 17, 249),
        e(3, "Hat-trick en Mundial más joven", "Pelé (Brasil) – ante Francia (semifinal de 1958)", 17, 244, "hat_joven:mundial"),
        e(4, "Goleador más joven en la Eurocopa", "Lamine Yamal (España) – ante Francia (2024)", 16, 362, "gol_joven:eurocopa"),
        e(5, "Jugador más joven en disputar la Eurocopa", "Lamine Yamal (España) – ante Croacia (2024)", 16, 338, "debut_joven:eurocopa"),
        e(6, "Asistente más joven en la Eurocopa", "Lamine Yamal (España) – ante Croacia (2024)", 16, 338, "asist_joven:eurocopa"),
        e(7, "Campeón de la Eurocopa más joven", "Lamine Yamal (España) – ante Inglaterra (2024)", 17, 1, "campeon_joven:eurocopa"),
        e(8, "Goleador más joven en la Champions League", "Ansu Fati (FC Barcelona) – ante el Inter de Milán (2019)", 17, 40, "gol_joven:champions"),
        e(9, "Debutante más joven en la historia del Mundial", "Norman Whiteside (Irlanda del Norte) – ante Yugoslavia (1982)", 17, 41, "debut_joven:mundial"),
        e(10, "Goleador más joven en la historia de los Mundiales", "Pelé (Brasil) – ante Gales (1958)", 17, 239, "gol_joven:mundial"),
        e(11, "Ganador del Balón de Oro más joven", "Ronaldo Nazário (Inter de Milán / Brasil) – 1997", 21, 96),
        e(12, "Ganador del FIFA World Player más joven", "Ronaldo Nazário (FC Barcelona / Brasil) – 1996", 20, 89),
        e(13, "Debutante profesional más joven registrado", "Mauricio Baldivieso (Aurora, Bolivia) – 2009", 12, 362, "debut_joven:pro"),
        e(14, "Goleador más joven de una liga del Top 5", "Lamine Yamal (FC Barcelona) – ante el Granada (2023)", 16, 87, "gol_joven:top5"),
        e(15, "Capitán más joven en la Champions League", "Rúben Neves (FC Porto) – ante el Maccabi Tel Aviv (2015)", 18, 221),
        e(16, "Hat-trick más joven en la Champions League", "Raúl González (Real Madrid) – ante el Ferencváros (1995)", 18, 114, "hat_joven:champions"),
        e(17, "Goleador más joven en una final de Champions", "Patrick Kluivert (Ajax) – ante el AC Milan (1995)", 18, 327),
        e(18, "Goleador más joven de la Copa América", "Johnnier Montaño (Colombia) – ante Argentina (1999)", 16, 171, "gol_joven:copaamerica"),
        e(19, "Goleador más joven en la Copa Libertadores", "Angelo Gabriel (Santos FC) – ante el Deportivo Lara (2021)", 16, 105, "gol_joven:libertadores"),
        e(20, "Debutante más joven en la Premier League", "Ethan Nwaneri (Arsenal) – ante el Brentford (2022)", 15, 181, "debut_joven:premier"),
        e(21, "Debutante más joven en LaLiga", "Luka Romero (RCD Mallorca) – ante el Real Madrid (2020)", 15, 219, "debut_joven:laliga"),
        e(22, "Debutante más joven en la Serie A", "Wisdom Amey (Bologna) – ante el Genoa (2021)", 15, 274, "debut_joven:seriea"),
        e(23, "Debutante más joven en la Bundesliga", "Youssoufa Moukoko (Borussia Dortmund) – 2020", 16, 1, "debut_joven:bundesliga"),
        e(24, "Goleador más joven en la Bundesliga", "Youssoufa Moukoko (Borussia Dortmund) – 2020", 16, 28, "gol_joven:bundesliga"),
        e(25, "Centenario en partidos oficiales más joven", "Cesc Fàbregas (Arsenal FC)", 19, 208, "cien_joven"),

        // 2. Goles y eficiencia
        c(26, "Máximo goleador en la historia del fútbol", "Cristiano Ronaldo (partidos oficiales)", "Más de 900 goles oficiales", 900.0, "goles", "goles_total", dudoso = true, nota = MIN),
        c(27, "Máximo goleador en un año natural", "Lionel Messi (FC Barcelona / Argentina) – 2012", "91 goles en 69 partidos", 91.0, "goles", "goles_ano"),
        c(28, "Máximo goleador en una temporada de club", "Lionel Messi (FC Barcelona) – 2011/12", "73 goles en 60 partidos", 73.0, "goles", "goles_temporada:club"),
        c(29, "Máximo goleador de selecciones nacionales", "Cristiano Ronaldo (Portugal)", "Más de 133 goles", 133.0, "goles", "goles_seleccion", dudoso = true, nota = MIN),
        c(30, "Máximo goleador con un mismo club", "Lionel Messi (FC Barcelona)", "672 goles", 672.0, "goles"),
        c(31, "Más goles en un solo partido oficial", "Archie Thompson (Australia) – ante Samoa Americana (2001)", "13 goles", 13.0, "goles", "goles_partido"),
        c(32, "Gol más rápido registrado", "Gavin Stokes (Maryhill FC) – 2017", "2,1 segundos", 2.1, "segundos", dudoso = true, nota = "Nunca se verificó oficialmente (otras fuentes dan 3,2 segundos)"),
        c(33, "Gol más rápido en un partido internacional", "Christoph Baumgartner (Austria) – ante Eslovaquia (2024)", "6,3 segundos", 6.3, "segundos"),
        c(34, "Gol más rápido en los Mundiales", "Hakan Şükür (Turquía) – ante Corea del Sur (2002)", "10,8 segundos", 10.8, "segundos"),
        c(35, "Gol más rápido en la Champions League", "Roy Makaay (Bayern de Múnich) – ante el Real Madrid (2007)", "10,12 segundos", 10.12, "segundos"),
        c(36, "Hat-trick más rápido de la historia", "Tommy Ross (Ross County) – 1964", "90 segundos", 90.0, "segundos"),
        c(37, "Hat-trick más rápido en la Premier League", "Sadio Mané (Southampton) – ante el Aston Villa (2015)", "2 minutos y 56 segundos", 176.0, "segundos"),
        c(38, "Hat-trick más rápido en la Champions League", "Mohamed Salah (Liverpool) – ante el Rangers (2022)", "6 minutos y 12 segundos", 372.0, "segundos"),
        c(39, "Más goles como suplente en un partido", "Robert Lewandowski (Bayern) – ante el Wolfsburgo (2015)", "5 goles en 9 minutos", 5.0, "goles"),
        c(40, "Racha más larga marcando en LaLiga", "Lionel Messi (FC Barcelona) – LaLiga 2012/13", "21 jornadas seguidas (33 goles)", 21.0, "partidos", "racha_gol:laliga"),
        c(41, "Más Botas de Oro europeas", "Lionel Messi (2010, 2012, 2013, 2017, 2018 y 2019)", "6 Botas de Oro", 6.0, "premios"),
        c(42, "Máximo goleador histórico de LaLiga", "Lionel Messi (FC Barcelona)", "474 goles", 474.0, "goles", "goles_total:laliga"),
        c(43, "Máximo goleador en una edición de LaLiga", "Lionel Messi (FC Barcelona) – 2011/12", "50 goles", 50.0, "goles", "goles_temporada:laliga"),
        c(44, "Máximo goleador histórico de la Premier League", "Alan Shearer (Blackburn / Newcastle)", "260 goles", 260.0, "goles", "goles_total:premier"),
        c(45, "Máximo goleador en una temporada de Premier", "Erling Haaland (Manchester City) – 2022/23", "36 goles", 36.0, "goles", "goles_temporada:premier"),
        c(46, "Máximo goleador en una temporada de Bundesliga", "Robert Lewandowski (Bayern de Múnich) – 2020/21", "41 goles", 41.0, "goles", "goles_temporada:bundesliga"),
        c(47, "Máximo goleador de falta directa", "Juninho Pernambucano (Brasil)", "77 goles de tiro libre", 77.0, "goles"),
        c(48, "Más temporadas seguidas con 50 goles o más", "Cristiano Ronaldo (Real Madrid)", "6 temporadas seguidas", 6.0, "temporadas", "temporadas50"),
        c(49, "Más hat-tricks en el siglo XXI", "Cristiano Ronaldo", "66 hat-tricks", 66.0, "hat-tricks", "hats_total"),
        c(50, "Más goles de cabeza en la historia oficial", "Cristiano Ronaldo", "Más de 150 goles de cabeza", 150.0, "goles", dudoso = true, nota = MIN),
        c(51, "Máximo goleador de penalti", "Cristiano Ronaldo", "Más de 160 goles de penalti", 160.0, "goles", dudoso = true, nota = MIN),
        c(52, "Goleador en más fases finales de selecciones", "Cristiano Ronaldo (Eurocopas y Mundiales)", "10 torneos seguidos", 10.0, "torneos"),
        c(53, "Más goles en un partido de Champions", "Lionel Messi (ante el Leverkusen) y Luiz Adriano (ante el BATE)", "5 goles en un partido", 5.0, "goles", "goles_partido:champions"),
        c(54, "Gol desde más lejos", "Tom King (Newport County, portero) – 2021", "96,01 metros", 96.01, "metros"),
        c(55, "Más pókers (4 goles) en LaLiga", "Cristiano Ronaldo (Real Madrid)", "8 pókers", 8.0, "pókers", "pokers:laliga"),

        // 3. Mundiales y selecciones
        c(56, "Más Mundiales ganados (jugador)", "Pelé (Brasil – 1958, 1962 y 1970)", "3 Copas del Mundo", 3.0, "títulos", "titulos:mundial"),
        c(57, "Más Mundiales ganados (selección)", "Brasil (1958, 1962, 1970, 1994 y 2002)", "5 títulos", 5.0, "títulos"),
        c(58, "Máximo goleador histórico de los Mundiales", "Kylian Mbappé (Francia – de 2018 a 2026)", "22 goles", 22.0, "goles", "goles_total:mundial", fuente = RecordReal.COMPROBADO),
        c(59, "Más goles en un solo Mundial", "Just Fontaine (Francia – Suecia 1958)", "13 goles", 13.0, "goles", "goles_edicion:mundial"),
        c(60, "Más partidos jugados en Mundiales", "Lionel Messi (Argentina – de 2006 a 2026)", "28 partidos", 28.0, "partidos", "partidos_total:mundial", dudoso = true, nota = "Las fuentes no coinciden tras el Mundial 2026 (hasta 34 partidos según algunas)"),
        c(61, "Más minutos jugados en Mundiales", "Lionel Messi (Argentina)", "2.525 minutos", 2525.0, "minutos", dudoso = true, nota = "Cifra anterior al cierre del Mundial 2026"),
        c(62, "Único jugador en marcar en 6 Mundiales", "Cristiano Ronaldo (2006, 2010, 2014, 2018, 2022 y 2026)", "6 Mundiales distintos", 6.0, "Mundiales", "mundiales_marcando", fuente = RecordReal.COMPROBADO),
        c(63, "Más asistencias en Mundiales", "Pelé (Brasil)", "10 asistencias", 10.0, "asistencias", "asist_total:mundial", dudoso = true, nota = "El conteo de asistencias de la época de Pelé varía según la fuente"),
        c(64, "Más asistencias en eliminatorias de un Mundial", "Lionel Messi (Argentina)", "6 asistencias", 6.0, "asistencias"),
        c(65, "Más partidos como capitán en Mundiales", "Lionel Messi (Argentina)", "19 partidos como capitán", 19.0, "partidos"),
        c(66, "Más victorias en Mundiales (jugador)", "Miroslav Klose (Alemania)", "17 victorias", 17.0, "victorias", "victorias_total:mundial", dudoso = true, nota = "Puede haber cambiado en el Mundial 2026"),
        c(67, "Goleador en todas las fases de un mismo Mundial", "Jairzinho (1970) y Lionel Messi (2022)", "De la fase de grupos a la final"),
        c(68, "Hat-trick en una final del Mundial", "Geoff Hurst (1966) y Kylian Mbappé (2022)", "3 goles en la final", 3.0, "goles"),
        c(69, "Más goles en finales del Mundial", "Kylian Mbappé (Francia – 2018 y 2022)", "4 goles en finales", 4.0, "goles"),
        c(70, "Más veces mejor jugador del partido en Mundiales", "Lionel Messi (Argentina)", "11 premios", 11.0, "premios", dudoso = true, nota = "Cifra anterior al Mundial 2026"),
        c(71, "Máximo goleador de las eliminatorias sudamericanas", "Lionel Messi (Argentina)", "Más de 34 goles", 34.0, "goles", dudoso = true, nota = MIN),
        c(72, "Máximo goleador de las eliminatorias europeas", "Cristiano Ronaldo (Portugal)", "Más de 36 goles", 36.0, "goles", dudoso = true, nota = MIN),
        c(73, "Más partidos internacionales", "Cristiano Ronaldo (Portugal)", "Más de 210 internacionalidades", 210.0, "partidos", "partidos_seleccion", dudoso = true, nota = MIN),
        c(74, "Mayor goleada en un Mundial", "Hungría 10-1 El Salvador (España 1982)", "9 goles de diferencia", 9.0, "goles de diferencia", "goleada:mundial"),
        c(75, "Mayor goleada en la clasificación para un Mundial", "Australia 31-0 Samoa Americana (2001)", "31-0", 31.0, "goles de diferencia", "goleada:clasificacion"),
        c(76, "Racha invicta más larga de una selección", "Italia (con Roberto Mancini, 2018-2021)", "37 partidos invicta", 37.0, "partidos"),
        c(77, "Más Balones de Oro del Mundial", "Lionel Messi (2014 y 2022)", "2 Balones de Oro del Mundial", 2.0, "premios"),
        c(78, "Más Mundiales jugados", "Carbajal, Matthäus, Márquez, Buffon, Messi, Ronaldo y Ochoa", "5 Mundiales", 5.0, "Mundiales", dudoso = true, nota = "Desactualizado tras el Mundial 2026"),
        c(79, "Selección con más finales del Mundial", "Alemania (1954, 1966, 1974, 1982, 1986, 1990, 2002 y 2014)", "8 finales", 8.0, "finales"),
        c(80, "Más goles de una selección en Mundiales", "Brasil", "237 goles", 237.0, "goles", dudoso = true, nota = "Cifra anterior al Mundial 2026"),
        c(81, "Campeón del Mundo ganándolo todo", "Brasil (México 1970: 6 de 6; Corea-Japón 2002: 7 de 7)", "100 % de victorias"),
        c(82, "Más goles en un partido del Mundial", "Oleg Salenko (Rusia) – ante Camerún (1994)", "5 goles", 5.0, "goles", "goles_partido:mundial"),
        e(83, "Jugador más veterano en jugar un Mundial", "Essam El-Hadary (Egipto) – ante Arabia Saudí (2018)", 45, 161, "jugar_veterano:mundial", TipoRecord.VETERANO),
        e(84, "Goleador más veterano de un Mundial", "Roger Milla (Camerún) – ante Rusia (1994)", 42, 39, "gol_veterano:mundial", TipoRecord.VETERANO),
        e(85, "Entrenador más veterano en un Mundial", "Dick Advocaat (Curazao – Mundial 2026; debutó el 14 de junio ante Alemania)", 78, -1, "veterano_mister:mundial", TipoRecord.VETERANO,
            nota = "Los días exactos varían según la fuente: se compara por años", fuente = RecordReal.COMPROBADO),

        // 4. Champions y clubes
        c(86, "Club con más Copas de Europa", "Real Madrid (de 1956 a 2024)", "15 títulos", 15.0, "títulos"),
        c(87, "Jugador con más Champions League", "Paco Gento, Dani Carvajal, Luka Modrić, Toni Kroos y Nacho", "6 Copas de Europa", 6.0, "títulos", "titulos:champions"),
        c(88, "Máximo goleador histórico de la Champions", "Cristiano Ronaldo (Manchester United, Real Madrid y Juventus)", "140 goles", 140.0, "goles", "goles_total:champions"),
        c(89, "Más asistencias en la Champions", "Cristiano Ronaldo", "42 asistencias", 42.0, "asistencias", "asist_total:champions"),
        c(90, "Máximo goleador en una edición de la Champions", "Cristiano Ronaldo (Real Madrid) – 2013/14", "17 goles", 17.0, "goles", "goles_temporada:champions"),
        c(91, "Más partidos en la Champions", "Cristiano Ronaldo", "183 partidos", 183.0, "partidos", "partidos_total:champions"),
        c(92, "Goleador en más finales de Champions", "Cristiano Ronaldo (2008, 2014 y 2017)", "3 finales distintas", 3.0, "finales"),
        c(93, "Más goles en fases de grupos de Champions", "Lionel Messi", "80 goles en la liguilla", 80.0, "goles"),
        c(94, "Goleador contra más rivales en la Champions", "Lionel Messi", "40 equipos distintos", 40.0, "rivales", "rivales:champions"),
        c(95, "Más hat-tricks en la Champions", "Cristiano Ronaldo y Lionel Messi", "8 hat-tricks cada uno", 8.0, "hat-tricks", "hats_total:champions"),
        c(96, "Más Copas de Europa seguidas", "Real Madrid (1956, 1957, 1958, 1959 y 1960)", "5 títulos seguidos", 5.0, "títulos"),
        c(97, "Único club en ganar tres Champions seguidas (formato actual)", "Real Madrid (2016, 2017 y 2018, con Zinedine Zidane)", "3 seguidas", 3.0, "títulos"),
        c(98, "Entrenador con más Champions League", "Carlo Ancelotti (AC Milan y Real Madrid)", "5 títulos (2003, 2007, 2014, 2022 y 2024)", 5.0, "títulos", "titulos_mister:champions"),
        c(99, "Entrenador con más finales de Champions", "Carlo Ancelotti", "6 finales", 6.0, "finales"),
        c(100, "Goleador en todos los partidos de la fase de grupos", "Cristiano Ronaldo (2017/18) y Sébastien Haller (2021/22)", "6 de 6 jornadas", 6.0, "partidos"),
        c(101, "Máximo goleador histórico de la Libertadores", "Alberto Spencer (Peñarol / Barcelona SC)", "54 goles", 54.0, "goles", "goles_total:libertadores"),
        c(102, "Club con más Copas Libertadores", "Independiente (Argentina)", "7 títulos", 7.0, "títulos"),
        c(103, "Club con más Copas Sudamericanas", "Boca, Independiente, Athletico Paranaense, Independiente del Valle y LDU Quito", "2 títulos (empate)", 2.0, "títulos"),
        c(104, "Más victorias seguidas en la Champions", "Bayern de Múnich (2019-2020)", "15 victorias seguidas", 15.0, "victorias", "victorias_seguidas:champions"),
        c(105, "Campeón de la Champions ganándolo todo", "Bayern de Múnich (2019/20)", "11 victorias en 11 partidos", 11.0, "victorias"),
        e(106, "Goleador más veterano en una final de Champions", "Paolo Maldini (AC Milan) – ante el Liverpool (2005)", 36, 333, tipo = TipoRecord.VETERANO),
        e(107, "Jugador más veterano en la Champions", "Marco Ballotta (Lazio) – ante el Real Madrid (2007)", 43, 252, "jugar_veterano:champions", TipoRecord.VETERANO),
        c(108, "Más penaltis parados en una tanda de Champions", "Helmuth Duckadam (Steaua de Bucarest) – final de 1986", "4 penaltis seguidos", 4.0, "penaltis"),
        c(109, "Mayor goleada en una eliminatoria de Champions", "Bayern 12-1 Sporting de Lisboa (2009)", "12-1 en el global (5-0 y 7-1)"),
        c(110, "Más goles en un partido de Champions", "Borussia Dortmund 8-4 Legia de Varsovia (2016)", "12 goles en total", 12.0, "goles", "partido_goles:champions"),
        c(111, "Goleador en más ediciones seguidas de la Champions", "Lionel Messi y Karim Benzema", "18 temporadas seguidas", 18.0, "temporadas", "temporadas_marcando:champions"),
        c(112, "Más Copas de la UEFA / Europa League", "Sevilla FC (2006, 2007, 2014, 2015, 2016, 2020 y 2023)", "7 títulos", 7.0, "títulos"),
        c(113, "Más semifinales de Champions seguidas", "Real Madrid (de 2010/11 a 2017/18)", "8 semifinales seguidas", 8.0, "semifinales"),
        c(114, "Máximo goleador en las eliminatorias de la Champions", "Cristiano Ronaldo", "67 goles (de octavos a la final)", 67.0, "goles"),
        c(115, "Más goles de Champions en un año natural", "Cristiano Ronaldo (2017)", "19 goles", 19.0, "goles", "goles_ano:champions"),

        // 5. Títulos y palmarés
        c(116, "Jugador con más títulos oficiales", "Lionel Messi (Barcelona, PSG, Inter Miami y Argentina)", "46 títulos", 46.0, "títulos", "titulos_total"),
        c(117, "Segundo jugador con más títulos", "Dani Alves (Bahia, Sevilla, Barça, Juventus, PSG y Brasil)", "43 títulos", 43.0, "títulos"),
        c(118, "Entrenador con más títulos", "Sir Alex Ferguson (St. Mirren, Aberdeen y Manchester United)", "49 títulos", 49.0, "títulos", "titulos_mister"),
        c(119, "Jugador con más Balones de Oro", "Lionel Messi (2009, 2010, 2011, 2012, 2015, 2019, 2021 y 2023)", "8 Balones de Oro", 8.0, "premios"),
        c(120, "Más veces en el podio del Balón de Oro", "Lionel Messi", "14 podios", 14.0, "podios"),
        c(121, "Más Balones de Oro seguidos", "Lionel Messi (de 2009 a 2012)", "4 seguidos", 4.0, "premios"),
        c(122, "Clubes que ganaron el sextete", "FC Barcelona (2009) y Bayern de Múnich (2020)", "6 títulos en un año", 6.0, "títulos"),
        c(123, "Más ligas seguidas en el Top 5", "Bayern de Múnich (Bundesliga, de 2013 a 2023)", "11 ligas seguidas", 11.0, "ligas"),
        c(124, "Club con más ligas nacionales", "Linfield FC (Irlanda del Norte)", "56 ligas", 56.0, "ligas"),
        c(125, "Dos tripletes seguidos con dos clubes", "Samuel Eto'o (Barça 2009 e Inter 2010)", "2 tripletes con 2 clubes", 2.0, "tripletes", dudoso = true, nota = "El título de la lista («3 tripletes») no cuadra con su marca («2 tripletes»)"),
        c(126, "Entrenador con más tripletes", "Pep Guardiola (FC Barcelona 2009 y Manchester City 2023)", "2 tripletes", 2.0, "tripletes"),
        c(127, "Más Copas del Rey", "FC Barcelona", "32 Copas del Rey", 32.0, "títulos", nota = "Corregido: la lista decía 31; la 32.ª fue la de 2024/25. La de 2026 la ganó la Real Sociedad", fuente = RecordReal.COMPROBADO),
        c(128, "Más FA Cups", "Arsenal FC", "14 FA Cups", 14.0, "títulos"),
        c(129, "Más Copas de la Liga inglesa", "Liverpool FC", "10 Copas de la Liga", 10.0, "títulos"),
        c(130, "Más Mundiales de Clubes", "Real Madrid", "5 Mundiales de Clubes (8 con las Intercontinentales)", 5.0, "títulos"),
        c(131, "Más premios The Best de la FIFA", "Lionel Messi (2019, 2022 y 2023)", "3 premios", 3.0, "premios", dudoso = true, nota = "La lista mezcla a Cristiano y Lewandowski, que tienen 2"),
        c(132, "Más Copas América", "Argentina", "16 Copas América", 16.0, "títulos"),
        c(133, "Más Eurocopas", "España (1964, 2008, 2012 y 2024)", "4 Eurocopas", 4.0, "títulos"),
        c(134, "Única selección en ganar Euro, Mundial y Euro seguidos", "España (Euro 2008, Mundial 2010 y Euro 2012)", "3 grandes torneos seguidos", 3.0, "títulos"),
        c(135, "Club con más títulos internacionales", "Real Madrid", "Más de 30 títulos internacionales", 30.0, "títulos", dudoso = true, nota = MIN),
        c(136, "Jugador con más Premier League", "Ryan Giggs (Manchester United)", "13 Premier League", 13.0, "títulos", "titulos:premier"),
        c(137, "Jugador con más Serie A", "Gianluigi Buffon (Juventus)", "10 Scudetti", 10.0, "títulos", "titulos:seriea"),
        c(138, "Jugador con más LaLiga", "Paco Gento (Real Madrid)", "12 Ligas", 12.0, "títulos", "titulos:laliga"),
        c(139, "Jugador con más Bundesligas", "Thomas Müller (Bayern de Múnich); Manuel Neuer lo igualó en 2025/26", "13 Bundesligas", 13.0, "títulos", "titulos:bundesliga",
            nota = "Corregido: la lista decía 12", fuente = RecordReal.COMPROBADO),
        c(140, "Más Pichichis", "Lionel Messi (FC Barcelona)", "8 Pichichis", 8.0, "premios"),

        // 6. Portería y defensa
        c(141, "Más minutos de imbatibilidad en partidos oficiales", "Mazaropi (Vasco da Gama, Brasil) – 1977/78", "1.816 minutos (20 partidos)", 1816.0, "minutos"),
        c(142, "Más minutos sin encajar con una selección", "Dino Zoff (Italia) – de 1972 a 1974", "1.142 minutos", 1142.0, "minutos"),
        c(143, "Más minutos sin encajar en los Mundiales", "Unai Simón (España – Mundial 2026)", "650 minutos", 650.0, "minutos", fuente = RecordReal.COMPROBADO),
        c(144, "Más minutos sin encajar en la Champions", "Jens Lehmann (Arsenal FC) – 2005/06", "853 minutos", 853.0, "minutos"),
        c(145, "Más minutos sin encajar en la Premier", "Edwin van der Sar (Manchester United) – 2008/09", "1.311 minutos (14 partidos)", 1311.0, "minutos"),
        c(146, "Más minutos sin encajar en LaLiga", "Abel Resino (Atlético de Madrid) – 1990/91", "1.275 minutos", 1275.0, "minutos"),
        c(147, "Portero más goleador de la historia", "Rogério Ceni (São Paulo FC)", "129 goles oficiales", 129.0, "goles", "goles_portero"),
        c(148, "Más goles de falta de un portero", "Rogério Ceni (São Paulo FC)", "59 goles de falta", 59.0, "goles"),
        c(149, "Segundo portero más goleador", "José Luis Chilavert (Paraguay / Vélez)", "67 goles oficiales", 67.0, "goles"),
        c(150, "Único portero con un hat-trick oficial", "José Luis Chilavert (Vélez ante Ferro Carril Oeste) – 1999", "3 goles de penalti", 3.0, "goles", "hat_portero"),
        c(151, "Defensa más goleador de la historia", "Ronald Koeman (PSV, Barcelona y Países Bajos)", "253 goles", 253.0, "goles", dudoso = true, nota = "Es la cifra más difundida; la IFFHS le da 193 goles en 533 partidos oficiales"),
        c(152, "Segundo defensa más goleador", "Daniel Passarella (Argentina / River Plate)", "175 goles", 175.0, "goles"),
        c(153, "Tercer defensa más goleador", "Fernando Hierro (Real Madrid / España)", "163 goles", 163.0, "goles"),
        c(154, "Menos goles encajados en una temporada de Premier", "Chelsea FC (con José Mourinho, 2004/05)", "15 goles en 38 partidos", 15.0, "goles"),
        c(155, "Más porterías a cero en una temporada de Premier", "Petr Čech (Chelsea FC) – 2004/05", "24 porterías a cero", 24.0, "porterías a cero", "cero_temporada:premier"),
        c(156, "Más porterías a cero en la historia de la Premier", "Petr Čech (Chelsea / Arsenal)", "202 porterías a cero", 202.0, "porterías a cero", "cero_total:premier"),
        c(157, "Más penaltis parados en LaLiga", "Diego Alves (Valencia CF / Almería)", "22 penaltis parados", 22.0, "penaltis"),
        c(158, "Único portero con Balón de Oro", "Lev Yashin, «la Araña Negra» (Unión Soviética) – 1963", "1 Balón de Oro", 1.0, "premios"),
        c(159, "Más porterías a cero en Mundiales", "Peter Shilton (Inglaterra) y Fabien Barthez (Francia)", "10 partidos sin encajar", 10.0, "porterías a cero", "cero_total:mundial"),
        e(160, "Portero más veterano en ganar el Mundial", "Dino Zoff (Italia) – España 1982", 40, 133, "campeon_veterano_portero:mundial", TipoRecord.VETERANO),
        c(161, "Más penaltis parados en una Eurocopa", "Gianluigi Donnarumma (Italia – Euro 2020)", "3 penaltis en tandas", 3.0, "penaltis"),
        c(162, "Más minutos sin encajar en la Serie A", "Gianluigi Buffon (Juventus) – 2015/16", "974 minutos", 974.0, "minutos"),
        c(163, "Despejes y regates en una temporada", "Virgil van Dijk (Liverpool FC) – 2018/19", "65 partidos sin que le regatearan", 65.0, "partidos", dudoso = true, nota = "El título de la lista no cuadra con su marca"),
        c(164, "Portero con más partidos", "Peter Shilton (Inglaterra)", "1.390 partidos oficiales", 1390.0, "partidos"),
        c(165, "Menos goles encajados en una fase de grupos de Champions", "Varios (Manchester United, PSG, Juventus y Ajax)", "1 gol encajado", 1.0, "goles"),

        // 7. Asistencias, apariciones y longevidad
        c(166, "Máximo asistente de la historia", "Lionel Messi (partidos oficiales)", "Más de 375 asistencias", 375.0, "asistencias", dudoso = true, nota = MIN),
        c(167, "Más asistencias en un año natural", "Lionel Messi (2011)", "36 asistencias", 36.0, "asistencias", "asist_ano"),
        c(168, "Más asistencias en una temporada de Premier", "Thierry Henry (2002/03) y Kevin De Bruyne (2019/20)", "20 asistencias", 20.0, "asistencias", "asist_temporada:premier"),
        c(169, "Más asistencias en una temporada de LaLiga", "Lionel Messi (FC Barcelona) – 2019/20", "21 asistencias", 21.0, "asistencias", "asist_temporada:laliga"),
        c(170, "Más asistencias en la historia de la Premier", "Ryan Giggs (Manchester United)", "162 asistencias", 162.0, "asistencias", "asist_total:premier"),
        c(171, "Jugador con más partidos oficiales", "Peter Shilton (Inglaterra, portero)", "1.390 partidos", 1390.0, "partidos", "partidos_total"),
        c(172, "Jugador de campo con más partidos", "Cristiano Ronaldo", "Más de 1.240 partidos oficiales", 1240.0, "partidos", dudoso = true, nota = MIN),
        c(173, "Futbolista profesional en activo de más edad", "Kazuyoshi Miura, «King Kazu» (Japón)", "Más de 57 años", 57.0, "años", dudoso = true, nota = MIN),
        e(174, "Goleador más veterano de la Champions", "Pepe (FC Porto) – ante el Royal Antwerp (2023)", 40, 254, "gol_veterano:champions", TipoRecord.VETERANO),
        e(175, "Goleador más veterano de la Premier", "Teddy Sheringham (West Ham) – ante el Portsmouth (2006)", 40, 268, "gol_veterano:premier", TipoRecord.VETERANO),
        e(176, "Goleador más veterano de LaLiga", "Donato (Deportivo de La Coruña) – ante el Athletic (2003)", 40, 138, "gol_veterano:laliga", TipoRecord.VETERANO),
        e(177, "Goleador más veterano de la Serie A", "Zlatan Ibrahimović (AC Milan) – ante el Udinese (2023)", 41, 166, "gol_veterano:seriea", TipoRecord.VETERANO),
        e(178, "Jugador más veterano en la Primera inglesa", "Sir Stanley Matthews (Stoke City) – ante el Fulham, febrero de 1965", 50, 5, tipo = TipoRecord.VETERANO,
            nota = "Corregido: la lista decía «goleador más longevo»; no marcó con 50 años. Kazuyoshi Miura jugó un partido de liga con 50 años y 7 días en 2017", fuente = RecordReal.COMPROBADO),
        c(179, "Más partidos seguidos en la Premier", "Brad Friedel (Blackburn, Aston Villa y Tottenham; portero)", "310 partidos seguidos", 310.0, "partidos", "racha_partidos:premier"),
        c(180, "Más partidos seguidos en LaLiga", "Iñaki Williams (Athletic Club)", "251 partidos seguidos", 251.0, "partidos", "racha_partidos:laliga"),
        c(181, "Más partidos en LaLiga", "Andoni Zubizarreta y Joaquín Sánchez", "622 partidos", 622.0, "partidos", "partidos_total:laliga"),
        c(182, "Más partidos en la Serie A", "Gianluigi Buffon", "657 partidos", 657.0, "partidos", "partidos_total:seriea"),
        c(183, "Más partidos en la Premier", "Gareth Barry (Aston Villa, Manchester City, Everton y West Brom)", "653 partidos", 653.0, "partidos", "partidos_total:premier"),
        e(184, "Jugador más veterano en la Eurocopa", "Pepe (Portugal) – ante Francia (2024)", 41, 130, "jugar_veterano:eurocopa", TipoRecord.VETERANO),
        e(185, "Goleador más veterano de la Eurocopa", "Luka Modrić (Croacia) – ante Italia (2024)", 38, 289, "gol_veterano:eurocopa", TipoRecord.VETERANO),

        // 8. Marcas insólitas y rachas
        c(186, "Fichaje más caro de la historia", "Neymar Jr. (del FC Barcelona al PSG, 2017)", "222 millones de euros", 222_000_000.0, "euros"),
        c(187, "Racha invicta más larga en una liga europea", "Steaua de Bucarest (liga rumana, de 1986 a 1989)", "104 partidos invicto", 104.0, "partidos"),
        c(188, "Racha invicta más larga en una liga del Top 5", "AC Milan (Serie A, de 1991 a 1993)", "58 partidos invicto", 58.0, "partidos", "invicto_liga:top5"),
        c(189, "Temporada de Premier sin perder", "Arsenal FC, «los Invencibles» (2003/04)", "38 partidos (26 victorias y 12 empates)", 38.0, "partidos"),
        c(190, "Invicto en una temporada en todas las competiciones", "Bayer Leverkusen (con Xabi Alonso, 2023/24)", "51 partidos invicto", 51.0, "partidos", "invicto_temporada"),
        c(191, "Más victorias seguidas", "Al-Hilal (Arabia Saudí, 2023/24)", "34 victorias seguidas", 34.0, "victorias", "victorias_seguidas"),
        c(192, "Más puntos en una liga del Top 5", "Juventus (Serie A 2013/14); Real Madrid y Barça llegaron a 100", "102 puntos", 102.0, "puntos", "puntos_liga:top5"),
        c(193, "Expulsión más rápida", "Lee Todd (Cross Park Farm FC) – 2000", "2 segundos (por un insulto)", 2.0, "segundos"),
        c(194, "Más tarjetas rojas en una carrera", "Gerardo Bedoya (Colombia; defensa y pivote)", "46 tarjetas rojas", 46.0, "tarjetas"),
        c(195, "Partido con más tarjetas rojas", "Claypole contra Victoriano Arenas (Argentina, 2011)", "36 tarjetas rojas", 36.0, "tarjetas"),
        c(196, "Tanda de penaltis más larga", "Washington contra Bedlington Terriers (2022)", "54 penaltis (25-24)", 54.0, "penaltis"),
        c(197, "Mayor marcador oficial (en autogoles)", "AS Adema 149-0 SO l'Emyrne (Madagascar, 2002)", "149-0 (en protesta)"),
        c(198, "Mundial con más goles", "Mundial 2026 (Estados Unidos, México y Canadá)", "308 goles", 308.0, "goles", fuente = RecordReal.COMPROBADO),
        c(199, "Velocidad más alta registrada", "Kylian Mbappé y Micky van de Ven", "38 km/h", 38.0, "km/h", dudoso = true, nota = "Las mediciones varían según la fuente"),
        c(200, "Entrenador con más partidos", "Sir Alex Ferguson (del East Stirlingshire al Manchester United)", "2.155 partidos", 2155.0, "partidos", "partidos_mister"),
    )

    fun de(n: Int): RecordReal? = TODOS.firstOrNull { it.n == n }
}
