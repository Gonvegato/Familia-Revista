// Prueba (SesKobo): los subrayados del Kobo caen en su sitio en el lector de Seshat. Con subrayados de verdad de dos Kobo (Clara BW 4.46 y
// Libra 2 4.38, en kepub y en EPUB: herramientas/casos/kosync/subrayados-kobo.json, de NickelInkSync): 1) la posición del Kobo, sola, da su
// texto; 2) al abrir el libro con ellos pendientes («kobo:…»), el lector los sitúa, los guarda como subrayados normales y los pinta.
// Uso: NODE_PATH=<carpeta de playwright> node subrayados-kobo.js
const { chromium } = require('playwright')
const http = require('http'), fs = require('fs'), path = require('path')
const root = path.resolve(__dirname, '../../../marfil/src/main/assets'), casos = path.resolve(__dirname, '../../casos/kosync'), port = 8797
const todos = JSON.parse(fs.readFileSync(path.join(casos, 'subrayados-kobo.json'), 'utf8'))
let epub
const tipos = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' }
const srv = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); if (p.startsWith('/libro/')) { r.writeHead(200); r.end(epub); return }
  fs.readFile(path.join(root, p), (e, d) => { if (e) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'Content-Type': tipos[path.extname(p)] || 'application/octet-stream' }); r.end(d) }) }).listen(port)
// El capítulo de un subrayado, desde lo que apunta el Kobo (kepub: «…kepub.epub!OEBPS!text/probe.xhtml»; EPUB: «…epub#(1)OEBPS/text/probe.xhtml»)
const capDe = s => s.contentId.includes('!') ? s.contentId.split('!').slice(1).join('/') : s.contentId.replace(/^.*#\(\d+\)/, '')
;(async () => {
  const b = await chromium.launch()
  let fallos = 0, bien = 0
  const ok = (c, msg) => { if (c) bien++; else { fallos++; console.log('FALLA ' + msg) } }
  for (const libro of [...new Set(todos.map(s => s.libro))]) {
    epub = fs.readFileSync(path.join(casos, libro))
    const subs = todos.filter(s => s.libro === libro)
    // Pendientes como llegan de la cuenta (lo que guarda SesKobo al leer el Kobo por cable)
    const pend = subs.map((s, i) => ({ value: 'kobo:' + s.kobo + ':' + i, color: 'subrayado', text: s.texto, note: s.nota || '', created: 1767225600000 + i,
      kobo: { cap: capDe(s), ini: s.ini, ioff: s.ioff, fin: s.fin, foff: s.foff } }))
    const pg = await (await b.newContext({ viewport: { width: 393, height: 851 }, hasTouch: true, isMobile: true })).newPage()
    await pg.addInitScript(p => {
      window.__guardado = null
      const st = { title: 'T', file: 't.epub', location: null, fraction: 0, goto: null, settings: { theme: 'papel' }, annotations: p, bookmarks: [], stats: null, device: 'a' }
      window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5, saveAnnotations: j => { window.__guardado = JSON.parse(j) } }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
    }, pend)
    await pg.goto('http://localhost:' + port + '/lector/index.html')
    await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
    // 1. La posición del Kobo, sola (sin buscar el texto), da su texto
    const r1 = await pg.evaluate(async subs => {
      const secs = window.__view.book.sections, out = []
      for (const s of subs) {
        const i = secs.findIndex(x => x.id.endsWith(s.cap)), doc = await secs[i].createDocument()
        const a = window.__xp.koboPunto(doc, s.ini, s.ioff), z = window.__xp.koboPunto(doc, s.fin, s.foff)
        let t = null
        if (a && z) { const r = doc.createRange(); r.setStart(a.startContainer, a.startOffset); r.setEnd(z.startContainer, z.startOffset); t = r.toString() }
        out.push({ t, quiero: s.texto, ini: s.ini })
      }
      return out
    }, subs.map(s => ({ ...s, cap: capDe(s) })))
    for (const x of r1) ok(x.t !== null && x.t.replace(/\s+/g, ' ').trim() === x.quiero.replace(/\s+/g, ' ').trim(), `${libro} posición del Kobo ${x.ini}: «${x.t}» (quiero «${x.quiero}»)`)
    // 2. Al abrir el libro: se sitúan, se guardan como subrayados normales y se pintan
    await pg.waitForFunction(() => window.__guardado, null, { timeout: 15000 }).catch(() => {})
    const r2 = await pg.evaluate(() => {
      const g = window.__guardado || []
      return { total: g.length, conCFI: g.filter(n => String(n.value).startsWith('epubcfi(') && typeof n.index === 'number').length, pendientes: g.filter(n => String(n.value).startsWith('kobo:')).length }
    })
    ok(r2.total === subs.length && r2.conCFI === subs.length && r2.pendientes === 0, `${libro} al abrir, todos a su sitio: ${JSON.stringify(r2)}`)
    // Y el CFI de cada uno, en la página tal como se pinta, da su texto (ir al subrayado y comprobar lo que se ve)
    const r3 = await pg.evaluate(async () => {
      const out = []
      for (const n of window.__guardado || []) {
        await window.__view.goTo(n.value); await new Promise(z => setTimeout(z, 250))
        const doc = window.__view.lastLocation.range.startContainer.ownerDocument
        let t = null; try { t = window.__view.resolveCFI(n.value).anchor(doc).toString() } catch (e) { t = 'ERROR ' + e.message }
        out.push({ t, quiero: n.text })
      }
      return out
    })
    for (const x of r3) ok(x.t && x.t.replace(/\s+/g, ' ').trim() === x.quiero.replace(/\s+/g, ' ').trim(), `${libro} en la página: «${x.t}» (quiero «${x.quiero}»)`)
    await pg.context().close()
  }
  // 3. Si la posición no cuadra (otro firmware, otra edición), se busca el texto
  epub = fs.readFileSync(path.join(casos, 'book-p.epub'))
  const pg = await (await b.newContext({ viewport: { width: 393, height: 851 } })).newPage()
  await pg.addInitScript(() => { const st = { title: 'T', file: 't.epub', settings: { theme: 'papel' }, annotations: [], bookmarks: [] }
    window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) }) })
  await pg.goto('http://localhost:' + port + '/lector/index.html')
  await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
  const s0 = todos.find(s => s.libro === 'book-p.epub')
  const r4 = await pg.evaluate(async ([s, cap]) => {
    const secs = window.__view.book.sections, i = secs.findIndex(x => x.id.endsWith(cap)), doc = await secs[i].createDocument()
    const r = window.__xp.koboSubrayado(doc, { ini: 'x#point(/1/99/1:0)', ioff: 0, fin: 'x#point(/1/99/1:3)', foff: 0 }, s.texto)
    return r ? r.toString() : null
  }, [s0, capDe(s0)])
  ok(r4 && r4.trim() === s0.texto.trim(), `posición rota: se busca el texto («${r4}», quiero «${s0.texto}»)`)
  await b.close(); srv.close()
  console.log(`${bien} bien, ${fallos} fallos`)
  process.exit(fallos ? 1 : 0)
})().catch(e => { console.log('ERR', e.stack); process.exit(1) })
