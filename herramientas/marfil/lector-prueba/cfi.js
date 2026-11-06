// Las posiciones (EPUB CFI) que calcula de verdad el lector del móvil (lector.js + foliate-js, en Chromium), para comprobar que
// Marfil del ordenador las calcula igual. Lee un caso (archivos del EPUB y frases), abre el lector con un window.Atrio de mentira y,
// para cada frase, apunta el CFI del tramo (como un subrayado: view.getCFI) y el texto que sale al resolverlo. Con «escritorio»
// en el caso (los CFI que calcula el ordenador), los resuelve con foliate y apunta el texto que cubren.
// Uso: node cfi.js <caso.json> [salida.json]   (con NODE_PATH donde esté playwright; CHROME=<navegador>; ASSETS=marfil/src/main/assets)
const { chromium } = require('playwright')
const http = require('http'), fs = require('fs'), path = require('path'), zlib = require('zlib')
const caso = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
const salida = process.argv[3] || process.argv[2]
const root = process.env.ASSETS || path.resolve(__dirname, '../../../marfil/src/main/assets')
const port = Number(process.env.PORT || 8781)

// Un zip sin comprimir (STORE): basta para un EPUB de prueba
function zip(archivos) {
    const partes = [], central = []; let off = 0
    for (const [nombre, texto] of Object.entries(archivos)) {
        const n = Buffer.from(nombre), d = Buffer.from(texto), crc = zlib.crc32(d)
        const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(0x0800, 6); h.writeUInt16LE(0, 8)
        h.writeUInt32LE(crc, 14); h.writeUInt32LE(d.length, 18); h.writeUInt32LE(d.length, 22); h.writeUInt16LE(n.length, 26)
        partes.push(h, n, d)
        const c = Buffer.alloc(46); c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8)
        c.writeUInt32LE(crc, 16); c.writeUInt32LE(d.length, 20); c.writeUInt32LE(d.length, 24); c.writeUInt16LE(n.length, 28); c.writeUInt32LE(off, 42)
        central.push(c, n)
        off += 30 + n.length + d.length
    }
    const cd = Buffer.concat(central), e = Buffer.alloc(22)
    e.writeUInt32LE(0x06054b50, 0); e.writeUInt16LE(central.length / 2, 8); e.writeUInt16LE(central.length / 2, 10); e.writeUInt32LE(cd.length, 12); e.writeUInt32LE(off, 16)
    return Buffer.concat([...partes, cd, e])
}
const epub = zip(caso.archivos)
const tipos = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' }
const srv = http.createServer((q, r) => {
    const p = decodeURIComponent(q.url.split('?')[0])
    if (p.startsWith('/libro/')) { r.writeHead(200, { 'Content-Type': 'application/epub+zip' }); r.end(epub); return }
    const f = path.join(root, p)
    fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'Content-Type': tipos[path.extname(f)] || 'application/octet-stream' }); r.end(d) })
}).listen(port)

;(async () => {
    const b = await chromium.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome' })
    const pg = await (await b.newContext({ viewport: { width: 800, height: 1000 } })).newPage()
    await pg.addInitScript(flow => {
        const st = { title: 'Dune', file: 't.epub', location: null, fraction: 0, goto: null, settings: { flow }, annotations: [], bookmarks: [], stats: null, fondo: null, accent: null }
        window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
    }, process.env.FLOW || 'paginated')
    const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 300)))
    await pg.goto('http://localhost:' + port + '/lector/index.html')
    await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
    await pg.waitForTimeout(800)
    const res = await pg.evaluate(async ({ frases, escritorio }) => {
        const v = window.__view
        const docDe = async i => {
            await v.goTo(i); await new Promise(r => setTimeout(r, 250))
            const d = (window.__docs || []).filter(x => x.index === i && x.doc.defaultView).at(-1)
            return d && d.doc
        }
        const out = { frases: [], inicios: {}, escritorio: [] }
        const secciones = [...new Set(frases.map(f => f.seccion))]
        for (const s of secciones) {
            const doc = await docDe(s)
            out.inicios[s] = { cfi: v.lastLocation?.cfi, botones: [...doc.querySelectorAll('.atrio-cap')].map(x => x.className) }
            for (const f of frases.filter(x => x.seccion === s)) {
                // El texto de la sección (sin los botones de capítulo) y dónde está la frase
                const w = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT)
                const nodos = []; let todo = '', n
                while ((n = w.nextNode())) { if (n.parentElement?.closest('.atrio-cap')) continue; nodos.push([n, todo.length]); todo += n.nodeValue }
                const at = todo.indexOf(f.texto)
                if (at < 0) { out.frases.push({ ...f, error: 'no está' }); continue }
                // (como una selección: el principio dentro del nodo de su primera letra y el final en el de la última)
                const inicio = k => { for (const [x, o] of nodos) if (k >= o && k < o + x.nodeValue.length) return [x, k - o] }
                const fin = k => { for (const [x, o] of nodos) if (k > o && k <= o + x.nodeValue.length) return [x, k - o] }
                const r = doc.createRange()
                r.setStart(...inicio(at)); r.setEnd(...fin(at + f.texto.length))
                const cfi = v.getCFI(s, r)
                const vuelta = v.resolveCFI(cfi)
                out.frases.push({ ...f, cfi, texto_resuelto: vuelta.anchor(doc).toString(), indice: vuelta.index })
            }
        }
        for (const cfi of escritorio || []) {
            try {
                const { index, anchor } = v.resolveCFI(cfi)
                const doc = await docDe(index)
                const r = anchor(doc)
                // Un punto: lo que se lee a partir de él (las 40 letras siguientes); un tramo: su texto
                let texto = r.toString()
                if (r.collapsed) {
                    const w = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT); let n, t = '', dentro = false
                    const c = r.startContainer.nodeType === 3 ? r.startContainer : null
                    while ((n = w.nextNode()) && t.length < 40) {
                        if (n.parentElement?.closest('.atrio-cap')) continue
                        if (c) { if (n === c) { dentro = true; t += n.nodeValue.slice(r.startOffset); continue } }
                        else if (!dentro && r.comparePoint(n, 0) >= 0) dentro = true
                        if (dentro) t += n.nodeValue
                    }
                    texto = t.replace(/\s+/g, ' ').trim().slice(0, 40)
                }
                out.escritorio.push({ cfi, indice: index, texto, punto: r.collapsed })
            } catch (e) { out.escritorio.push({ cfi, error: String(e) }) }
        }
        return out
    }, { frases: caso.frases, escritorio: caso.escritorio || [] })
    caso.foliate = { inicios: res.inicios, frases: res.frases, version: fs.readFileSync(path.join(root, 'lector/foliate/VERSION'), 'utf8').trim() }
    if (caso.escritorio) caso.foliate.escritorio = res.escritorio
    fs.writeFileSync(salida, JSON.stringify(caso, null, 1) + '\n')
    console.log(JSON.stringify(res, null, 1))
    if (errs.length) console.log('errores de la página:', errs)
    await b.close(); srv.close()
})().catch(e => { console.log('ERR', e.stack || e.message); process.exit(1) })
