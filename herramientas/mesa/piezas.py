# Cambia la cara de las piezas básicas (Revista.kt y UiBase.kt) a la dirección D, con los mismos nombres y firmas.
import re, sys
d = sys.argv[1]

def cambia(s, nombre, nuevo, anot='@Composable'):
    # de la línea «fun nombre(» hasta el primer «\n}\n» (o fin de expresión de una línea)
    m = re.search(r'\nfun ' + re.escape(nombre) + r'\(', s)
    if not m: print('  (no está)', nombre); return s
    ini = m.start() + 1
    resto = s[ini:]
    primera = resto.split('\n', 1)[0]
    if primera.rstrip().endswith('{'):
        fin = resto.index('\n}\n') + 3
    else:
        # función de expresión: hasta la primera línea en blanco
        fin = resto.index('\n\n') + 1
    return s[:ini] + nuevo.strip('\n') + '\n' + s[ini + fin:]

p = d + '/Revista.kt'; s = open(p).read()
s = cambia(s, 'Antetitulo', '''
fun Antetitulo(texto: String, modifier: Modifier = Modifier, tam: TextUnit = 13.sp, color: Color = Charcoal, conCuadro: Boolean = true) {
    // Dirección D: el de sección, un título en minúsculas normales; el de casilla, un rótulo apagado
    if (conCuadro) Text(texto, modifier, color = color, fontFamily = Grotesca, fontSize = (tam.value * 1.4f).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    else Text(texto, modifier, color = if (color == Charcoal) Muted else color, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
''')
s = cambia(s, 'EnlaceRevista', '''
fun EnlaceRevista(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    Text(texto, color = color ?: if (acento) Acento else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.semantics { role = Role.Button }.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp))
}
''')
# pestañas: píldoras
s = re.sub(r'\.background\(if \(on\) Acento else Color\.Transparent\)\n(\s*)\.padding\(horizontal = 12\.dp, vertical = 11\.dp\)', r'.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(if (on) Charcoal else Sand)\n\1.padding(horizontal = 14.dp, vertical = 9.dp)', s)
s = s.replace('Text(n.uppercase(), color = if (on) SobreAcento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)',
              'Text(n, color = if (on) Paper else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)')
s = s.replace('Text(" $c", color = if (on) SobreAcento else Muted, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)',
              'Text(" $c", color = if (on) Paper.copy(alpha = 0.7f) else Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1)')
s = s.replace('            }\n        }\n        Filete(2.dp)\n    }\n}\n\n/** Filtros u opciones', '            }\n        }\n    }\n}\n\n/** Filtros u opciones')
s = cambia(s, 'FiltroRevista', '''
fun FiltroRevista(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Dirección D: una píldora; la encendida, rellena de tinta
    Text(texto, color = if (on) Paper else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.semantics { selected = on }.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
            .background(if (on) Charcoal else Sand).padding(horizontal = 14.dp, vertical = 8.dp))
}
''')
s = cambia(s, 'RevistaHeadline', '''
fun RevistaHeadline(title: String, overline: String?) {
    Column(Modifier.fillMaxWidth()) {
        if (overline != null) Text(overline, color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(title, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
    }
}
''')
s = cambia(s, 'Cabecera', '''
fun Cabecera(tam: TextUnit, modifier: Modifier = Modifier, color: Color = Charcoal, texto: String = "Seshat") {
    Box(modifier.fillMaxWidth()) {
        FitTitle(texto, tam, tam * 1.0f, color = color, maxLines = 1, modifier = Modifier.fillMaxWidth(), minSize = 12f, unaLinea = true, family = Grotesca, fontWeight = FontWeight.Bold)
    }
}
''')
s = cambia(s, 'MagLink', '''
fun MagLink(text: String, sub: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text("$text →", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, color = if (accent) Acento else Charcoal)
        if (sub != null) Text(sub, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
''')
s = cambia(s, 'MagChoice', '''
fun MagChoice(options: List<String>, selected: Int, label: String? = null, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (label != null) Text(label, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FiltroRevista(o, i == selected) { if (i != selected) onPick(i) } }
        }
    }
}
''')
s = cambia(s, 'MagFooter', '''
fun MagFooter(text: String = "Editar", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("$text →", color = Acento, fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 16.dp))
    }
}
''')
s = s.replace('if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))',
              'if (meta.isNotBlank()) Text(meta, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))')
s = s.replace('        derecha()\n    }\n    Filete(2.dp)\n}', '        derecha()\n    }\n    Filete(1.dp, Line)\n}')
s = s.replace('append(nombre.uppercase())', 'append(nombre)').replace('fontWeight = FontWeight.ExtraBold, fontSize = tam, lineHeight = tam * 1.0f, letterSpacing = (-0.015f * tam.value).sp', 'fontWeight = FontWeight.Bold, fontSize = tam, lineHeight = tam * 1.0f')
s = cambia(s, 'RejillaCifras', '''
fun RejillaCifras(bloques: List<BloqueCifra>, modifier: Modifier = Modifier) {
    // Dirección D: tarjetas redondas, sin bordes; todas en superficie (la cifra, en tinta)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        bloques.forEach { b ->
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Crudo).let { m -> if (b.alHacer != null) m.clickable(onClick = b.alHacer) else m }.padding(horizontal = 14.dp, vertical = 14.dp)) {
                FitTitle(b.cifra, 28.sp, 30.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.Bold, maxLines = 1, unaLinea = true, minSize = 14f, letterSpacing = 0.sp)
                Text(b.etiqueta, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
''')
s = cambia(s, 'BotonC', '''
fun BotonC(texto: String, modifier: Modifier = Modifier, fondo: Color = Charcoal, letra: Color = Paper, enabled: Boolean = true, onClick: () -> Unit) {
    // Dirección D: redondo; el de tinta de antes pasa a ser el del acento (el principal) y el de papel, el secundario (gris)
    val f = if (fondo == Charcoal || fondo == Acento) Acento else Sand
    val l = if (f == Acento) SobreAcento else Charcoal
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) f else f.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = l, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("→", color = l, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}
''')
for imp in ['import androidx.compose.foundation.shape.RoundedCornerShape', 'import androidx.compose.ui.draw.clip']:
    if imp not in s: s = s.replace('\nimport ', '\n' + imp + '\nimport ', 1)
open(p, 'w').write(s)

p = d + '/UiBase.kt'; s = open(p).read()
s = cambia(s, 'Titulo', '''
fun Titulo(t: String, tam: Int = 28, modifier: Modifier = Modifier, color: Color = Charcoal, lineas: Int = 2) =
    Text(t, modifier, color = color, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = (tam * 0.9f).sp, lineHeight = (tam * 1.02f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)
''')
s = cambia(s, 'Etiqueta', '''
fun Etiqueta(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, modifier, color = color, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
''')
s = cambia(s, 'BotonAcento', '''
fun BotonAcento(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, alto: Dp = 52.dp, alHacer: () -> Unit) {
    Row(
        modifier.heightIn(min = alto).clip(RoundedCornerShape(16.dp)).background(Acento).clickable(onClick = alHacer).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = SobreAcento, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
    }
}
''')
s = cambia(s, 'AccionGrande', '''
fun AccionGrande(icono: ImageVector?, texto: String, modifier: Modifier = Modifier, acento: Boolean = true, enabled: Boolean = true, alHacer: () -> Unit) {
    val fondo = if (acento) Acento else Sand
    val letra = if (acento) SobreAcento else Charcoal
    Row(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) fondo else fondo.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = alHacer)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icono != null) { Icon(icono, null, tint = letra, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        // (con la letra grande, en dos líneas antes que cortado)
        Text(texto, color = letra, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("→", color = letra, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}
''')
s = cambia(s, 'BotonSuave', '''
fun BotonSuave(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, peligro: Boolean = false, alHacer: () -> Unit) {
    val col = if (peligro) Acento else Charcoal
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(Sand).clickable(onClick = alHacer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = col, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = col, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}
''')
s = cambia(s, 'Chip', '''
fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, cuenta: Int? = null, alHacer: () -> Unit) {
    FiltroRevista(texto + (if (cuenta != null) " $cuenta" else ""), activo, modifier.padding(end = 8.dp), alHacer)
}
''')
s = cambia(s, 'Panel', '''
fun Panel(modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    // Dirección D: una tarjeta redonda en superficie, sin borde
    val forma = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(forma).background(Crudo).let { if (alHacer != null) it.clickable(onClick = alHacer) else it }.padding(16.dp),
        content = contenido
    )
}
''')
s = s.replace('Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp)', 'Text(titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)')
s = s.replace('if (descripcion != null) Cuerpo(descripcion, tam = 12)', 'if (descripcion != null) Cuerpo(descripcion, tam = 13)')
open(p, 'w').write(s)
print('hecho', d)
