// Prueba (Seshat 0.22.6): dejar el libro al principio de un capítulo, guardar la posición como la app y volver a abrir: tiene que salir la misma página
// (antes, con los botones de capítulo ocultos, a veces salía la siguiente). Uso: NODE_PATH=<carpeta de playwright> node reabrir.js
const { chromium } = require('playwright')
const http = require('http'), fs = require('fs'), path = require('path'), zlib = require('zlib')
const root = path.resolve(__dirname, '../../../marfil/src/main/assets'), port = 8795
const src = fs.readFileSync(path.join(__dirname, 'tableta.js'), 'utf8')
eval(src.match(/function zip[\s\S]*?\n}\n/)[0])
const parrafo = 'La vieja fue admitida por la puerta lateral, a lo largo del pasadizo abovedado, junto a la habitación, y se le permitió echar un vistazo al muchacho tendido en su cama.'
const VARIANTES = {
  simple: t => `<h2>${t}</h2>`,
  seccion: t => `<section><div class="cap"><h2 class="t">${t}</h2></div>`,
  margen: t => `<div style="margin-top:3em"><h2 style="margin-top:4em">${t}</h2></div>`,
  salto: t => `<div style="page-break-before:always;break-before:page"><p class="num">43</p><h2>${t}</h2></div>`,
}
function libro(variante) {
  const caps = ['Uno', 'Dos', 'Tres', 'Vivirlo de nuevo', 'Cinco']
  const a = { mimetype: 'application/epub+zip', 'META-INF/container.xml': '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="c.opf" media-type="application/oebps-package+xml"/></rootfiles></container>',
    'c.opf': `<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>T</dc:title><dc:language>es</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>${caps.map((_, i) => `<item id="c${i}" href="c${i}.xhtml" media-type="application/xhtml+xml"/>`).join('')}</manifest><spine>${caps.map((_, i) => `<itemref idref="c${i}"/>`).join('')}</spine></package>`,
    'nav.xhtml': `<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Índice</title></head><body><nav epub:type="toc"><ol>${caps.map((t, i) => `<li><a href="c${i}.xhtml">${t}</a></li>`).join('')}</ol></nav></body></html>` }
  const cierre = variante === 'seccion' ? '</section>' : ''
  caps.forEach((t, i) => a[`c${i}.xhtml`] = `<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${t}</title></head><body>${VARIANTES[variante](t)}${Array.from({ length: 25 }, () => `<p>${parrafo}</p>`).join('')}${cierre}</body></html>`)
  return zip(a)
}
let epub
const tipos = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' }
const srv = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); if (p.startsWith('/libro/')) { r.writeHead(200); r.end(epub); return }
  fs.readFile(path.join(root, p), (e, d) => { if (e) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'Content-Type': tipos[path.extname(p)] || 'application/octet-stream' }); r.end(d) }) }).listen(port)
async function abre(b, w, h, location, settings) {
  const pg = await (await b.newContext({ viewport: { width: w, height: h }, hasTouch: true, isMobile: true, deviceScaleFactor: 2.625 })).newPage()
  await pg.addInitScript(([loc, s]) => {
    const st = { title: 'T', file: 't.epub', location: loc, fraction: 0, goto: null, settings: s, annotations: [], bookmarks: [], stats: null, device: 'a' }
    window.__guardado = []
    window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5, saveProgress: (c, f) => window.__guardado.push(c) }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
  }, [location, settings])
  await pg.goto('http://localhost:' + port + '/lector/index.html')
  await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
  await pg.waitForTimeout(1200)
  return pg
}
const estado = pg => pg.evaluate(() => { const r = window.__view.renderer; return { page: r.page, idx: window.__view.lastLocation?.index ?? null, cfi: window.__view.lastLocation?.cfi, txt: (window.__view.lastLocation?.range?.toString() || '').slice(0, 40) } })
;(async () => {
  const b = await chromium.launch()
  let fallos = 0
  for (const v of Object.keys(VARIANTES)) for (const [w, h] of [[393, 851], [412, 915], [360, 780]]) for (const s of [{ theme: 'papel' }, { theme: 'papel', fontSize: 22, lineHeight: 1.6 }]) {
    epub = libro(v)
    const pg = await abre(b, w, h, null, s)
    await pg.evaluate(() => window.__view.goTo('c3.xhtml')); await pg.waitForTimeout(900)
    const antes = await estado(pg)
    await pg.waitForTimeout(800)
    const cfi = await pg.evaluate(() => window.__guardado[window.__guardado.length - 1])
    await pg.context().close()
    const pg2 = await abre(b, w, h, cfi, s)
    const despues = await estado(pg2)
    const bien = despues.page === antes.page && despues.txt === antes.txt
    if (!bien) fallos++
    console.log((bien ? 'OK    ' : 'FALLA ') + `${v} ${w}x${h} ${JSON.stringify(s.fontSize || '')}: antes pág ${antes.page} «${antes.txt}» → al volver pág ${despues.page} «${despues.txt}» cfi=${cfi}`)
    await pg2.context().close()
  }
  await b.close(); srv.close(); console.log(fallos ? fallos + ' fallos' : 'todo bien')
})().catch(e => { console.log('ERR', e.stack); process.exit(1) })
