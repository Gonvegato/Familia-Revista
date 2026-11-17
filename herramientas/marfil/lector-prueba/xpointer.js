// Prueba (SesKobo): la posición de KOReader (la que usan KOSync y NickelInkSync en el Kobo) en el lector de Seshat. Con libros y
// subrayados hechos en KOReader de verdad (herramientas/casos/kosync, de NickelInkSync): cada posición tiene que caer justo en su
// palabra, ida y vuelta tiene que dar la misma posición, e ir a la última posición de KOReader tiene que abrir esa página.
// Uso: NODE_PATH=<carpeta de playwright> node xpointer.js
const { chromium } = require('playwright')
const http = require('http'), fs = require('fs'), path = require('path')
const root = path.resolve(__dirname, '../../../marfil/src/main/assets'), casos = path.resolve(__dirname, '../../casos/kosync'), port = 8796
let epub
const tipos = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' }
const srv = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); if (p.startsWith('/libro/')) { r.writeHead(200); r.end(epub); return }
  fs.readFile(path.join(root, p), (e, d) => { if (e) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'Content-Type': tipos[path.extname(p)] || 'application/octet-stream' }); r.end(d) }) }).listen(port)
function lua(nombre) {
  const t = fs.readFileSync(path.join(casos, nombre + '.metadata.epub.lua'), 'utf8')
  const subs = [], re = /\["pos0"\] = "([^"]+)",\s*\["pos1"\] = "([^"]+)",\s*\["text"\] = "([^"]*)"/g
  let m; while ((m = re.exec(t))) subs.push({ pos0: m[1], pos1: m[2], text: m[3] })
  const ultima = /\["last_xpointer"\] = "([^"]+)"/.exec(t)?.[1]
  return { subs, ultima }
}
;(async () => {
  const b = await chromium.launch()
  let fallos = 0, bien = 0
  const ok = (c, msg) => { if (c) bien++; else { fallos++; console.log('FALLA ' + msg) } }
  for (const nombre of ['book-p', 'book-q', 'book-a', 'book-p.kepub', 'book-q.kepub']) {
    epub = fs.readFileSync(path.join(casos, nombre + '.epub'))
    const { subs, ultima } = lua(nombre)
    const pg = await (await b.newContext({ viewport: { width: 393, height: 851 }, hasTouch: true, isMobile: true })).newPage()
    await pg.addInitScript(() => {
      const st = { title: 'T', file: 't.epub', location: null, fraction: 0, goto: null, settings: { theme: 'papel' }, annotations: [], bookmarks: [], stats: null, device: 'a' }
      window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
    })
    await pg.goto('http://localhost:' + port + '/lector/index.html')
    await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
    await pg.waitForTimeout(800)
    // 1. Cada subrayado de KOReader: de pos0 a pos1 está su texto, y la posición va y vuelve igual
    for (const s of subs) {
      const r = await pg.evaluate(async ({ pos0, pos1 }) => {
        const i = +/DocFragment\[(\d+)\]/.exec(pos0)[1] - 1
        const doc = await window.__view.book.sections[i].createDocument()
        const a = window.__xp.xpRango(doc, pos0), z = window.__xp.xpRango(doc, pos1)
        if (!a || !z) return { error: 'no encontrada' }
        const rg = doc.createRange(); rg.setStart(a.startContainer, a.startOffset); rg.setEnd(z.startContainer, z.startOffset)
        return { texto: rg.toString(), vuelta0: window.__xp.xpDe(i, a.startContainer, a.startOffset), vuelta1: window.__xp.xpDe(i, z.startContainer, z.startOffset) }
      }, s)
      ok(!r.error && r.texto === s.text, `${nombre} ${s.pos0}: «${r.texto}» (quiero «${s.text}») ${r.error || ''}`)
      ok(r.vuelta0 === s.pos0, `${nombre} ida y vuelta: ${r.vuelta0} (quiero ${s.pos0})`)
      ok(r.vuelta1 === s.pos1, `${nombre} ida y vuelta: ${r.vuelta1} (quiero ${s.pos1})`)
    }
    // 1c. kepub: la marca del Kobo («kobo.N.M:letras») de cada subrayado cae en su palabra, e ir a ella abre el libro ahí
    if (nombre.includes('kepub')) for (const s of subs) {
      const r = await pg.evaluate(async ({ pos0, pos1 }) => {
        const i = +/DocFragment\[(\d+)\]/.exec(pos0)[1] - 1
        const doc = await window.__view.book.sections[i].createDocument()
        const a = window.__xp.xpRango(doc, pos0), z = window.__xp.xpRango(doc, pos1)
        const span = a.startContainer.parentElement.closest('span[id^="kobo."]')
        if (!span) return { error: 'sin frase del Kobo' }
        // Las letras desde el principio de la frase hasta el sitio (como las cuenta el Kobo: unidades UTF-16)
        let off = 0
        const w = doc.createTreeWalker(span, NodeFilter.SHOW_TEXT)
        for (let t = w.nextNode(); t; t = w.nextNode()) { if (t === a.startContainer) { off += a.startOffset; break } off += t.data.length }
        const marca = span.id + ':' + off
        const k = window.__xp.koboRango(doc, marca)
        const rg = doc.createRange(); rg.setStart(k.startContainer, k.startOffset); rg.setEnd(z.startContainer, z.startOffset)
        const capitulo = window.__view.book.sections[i].id
        const fue = await window.atrioIrKobo(capitulo + '#' + marca, 'el Kobo')
        await new Promise(r => setTimeout(r, 500))
        // ¿La página que se ve contiene la frase? (no basta con que «vaya»: tiene que caer ahí, no al principio del capítulo)
        const v = window.__view.lastLocation, dest = window.__xp.koboRango(v.range.startContainer.ownerDocument, marca)
        const dentro = !!dest && v.range.comparePoint(dest.startContainer, dest.startOffset) === 0
        return { marca, texto: rg.toString(), fue, dentro, idx: window.__view.lastLocation?.section?.current, llegada: window.atrioXPointer()?.llegada }
      }, s)
      ok(!r.error && r.texto === s.text && r.fue && r.dentro && r.llegada === true, `${nombre} marca del Kobo ${r.marca}: «${r.texto}» (quiero «${s.text}») ${JSON.stringify(r)}`)
    }
    // 1b. Lo que añade el lector (los botones de capítulo) no cuenta: un punto dentro de ellos es el primer texto del libro que sigue
    if (subs.length) {
      const r = await pg.evaluate(async pos0 => {
        const i = +/DocFragment\[(\d+)\]/.exec(pos0)[1] - 1
        const doc = await window.__view.book.sections[i].createDocument()
        const sin = window.__xp.xpDe(i, doc.body, 0)
        const cap = doc.createElement('div'); cap.className = 'atrio-cap ant'; cap.innerHTML = '<button><span class="arriba">← Capítulo anterior</span></button>'
        doc.body.prepend(cap)
        const con = window.__xp.xpDe(i, doc.body, 0), dentro = window.__xp.xpDe(i, cap.querySelector('span').firstChild, 3)
        return { sin, con, dentro }
      }, subs[0].pos0)
      ok(r.sin && r.con === r.sin && r.dentro === r.sin, `${nombre} botones de capítulo: ${JSON.stringify(r)}`)
    }
    // 1d. Ir a cada subrayado (posiciones en mitad del capítulo, como la de alguien que deja de leer): la página que se abre lo contiene
    for (const s of subs) {
      const r = await pg.evaluate(async xp => {
        const fue = await window.atrioIrXPointer(xp, 0.5, 'el Kobo')
        await new Promise(z => setTimeout(z, 600))
        const v = window.__view.lastLocation, dest = window.__xp.xpRango(v.range.startContainer.ownerDocument, xp)
        return { fue, dentro: !!dest && v.range.comparePoint(dest.startContainer, dest.startOffset) === 0, pagina: window.atrioXPointer()?.xp }
      }, s.pos0)
      ok(r.fue && r.dentro, `${nombre} ir a ${s.pos0}: ${JSON.stringify(r)}`)
    }
    // 2. Ir a la última posición de KOReader (como si llegara del Kobo): la página que se abre la contiene
    if (ultima) {
      const r = await pg.evaluate(async xp => {
        const fue = await window.atrioIrXPointer(xp, 0.5, 'el Kobo')
        await new Promise(z => setTimeout(z, 900))
        const v = window.__view.lastLocation
        // ¿La página que se ve contiene el sitio? (su texto, desde el principio de la página, llega hasta él)
        const r = v.range, doc = r.startContainer.ownerDocument, dest = window.__xp.xpRango(doc, xp)
        const dentro = !!dest && r.comparePoint(dest.startContainer, dest.startOffset) === 0
        const ahora = window.atrioXPointer()
        return { fue, idx: v?.section?.current ?? v?.index, dentro, xpAhora: ahora?.xp, llegada: ahora?.llegada }
      }, ultima)
      const i = +/DocFragment\[(\d+)\]/.exec(ultima)[1] - 1
      ok(r.fue && r.idx === i && r.dentro, `${nombre} ir a ${ultima}: ${JSON.stringify(r)}`)
      ok(r.xpAhora && r.xpAhora.includes(`DocFragment[${i + 1}]`), `${nombre} posición de la página: ${r.xpAhora}`)
      ok(r.llegada === true, `${nombre} sin moverse de donde le trajo el Kobo, no se le devuelve: ${r.llegada}`)
      console.log(`${nombre}: última de KOReader ${ultima} → página que empieza en ${r.xpAhora}`)
    }
    await pg.context().close()
  }
  // 3. Capítulos largos (herramientas/casos/largo): saltar a una posición en mitad del capítulo abre su página, no la primera del capítulo
  const largo = path.resolve(__dirname, '../../casos/largo')
  for (const [nombre, saltos] of [
    ['largo.epub', [['xp', '/body[1]/DocFragment[3]/body[1]/div[1]/p[250]/text()[1].10', 'C2-P250'], ['xp', '/body[1]/DocFragment[2]/body[1]/div[1]/p[150]/text()[1].0', 'C1-P150']]],
    ['largo.kepub.epub', [['kobo', 'OEBPS/text/c2.xhtml#kobo.250.1:10', 'C2-P250'], ['kobo', 'OEBPS/text/c1.xhtml#kobo.150.1', 'C1-P150'],
      ['xp', '/body[1]/DocFragment[3]/body[1]/div[1]/div[1]/p[250]/span[1]/text()[1].10', 'C2-P250']]]]) {
    epub = fs.readFileSync(path.join(largo, nombre))
    const pg = await (await b.newContext({ viewport: { width: 393, height: 851 }, hasTouch: true, isMobile: true })).newPage()
    await pg.addInitScript(() => {
      const st = { title: 'T', file: 't.epub', location: null, fraction: 0, goto: null, settings: { theme: 'papel' }, annotations: [], bookmarks: [], stats: null, device: 'a' }
      window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
    })
    await pg.goto('http://localhost:' + port + '/lector/index.html')
    await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
    await pg.waitForTimeout(800)
    for (const [tipo, donde, texto] of saltos) {
      const r = await pg.evaluate(async ([tipo, donde, texto]) => {
        const fue = tipo === 'xp' ? await window.atrioIrXPointer(donde, 0.1, 'el Kobo') : await window.atrioIrKobo(donde, 'el Kobo')
        await new Promise(z => setTimeout(z, 700))
        const v = window.__view.lastLocation, doc = v.range.startContainer.ownerDocument
        const p = [...doc.querySelectorAll('p')].find(x => x.textContent.startsWith(texto + ' '))
        const dentro = !!p && v.range.intersectsNode(p)
        return { fue, dentro, pagina: window.atrioXPointer()?.xp, llegada: window.atrioXPointer()?.llegada }
      }, [tipo, donde, texto])
      ok(r.fue && r.dentro && r.llegada === true, `${nombre} saltar a ${donde} (${texto}): ${JSON.stringify(r)}`)
    }
    await pg.context().close()
  }
  await b.close(); srv.close()
  console.log(`${bien} bien, ${fallos} fallos`)
  process.exit(fallos ? 1 : 0)
})().catch(e => { console.log('ERR', e.stack); process.exit(1) })
