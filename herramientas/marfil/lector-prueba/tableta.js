// Prueba: el lector del móvil en la tableta y en el móvil (Marfil 0.14.0). En la tableta tumbada el panel (Índice, Letra…) va de lado, a la
// derecha y de arriba abajo; en vertical, al centro y del ancho de una columna; en el móvil, de todo el ancho. El índice dice el % de cada
// capítulo y «Estás aquí»; la barra de lo leído lleva una marca donde empieza cada capítulo.
// Uso: NODE_PATH=<carpeta de playwright> node tableta.js   (CHROME=<navegador>; FOTOS=<carpeta> para guardar las capturas)
const { chromium } = require('playwright')
const http = require('http'), fs = require('fs'), path = require('path'), zlib = require('zlib')
const root = process.env.ASSETS || path.resolve(__dirname, '../../../marfil/src/main/assets'), port = Number(process.env.PORT || 8792)
function zip(archivos) {
    const partes = [], central = []; let off = 0
    for (const [nombre, texto] of Object.entries(archivos)) {
        const n = Buffer.from(nombre), d = Buffer.from(texto), crc = zlib.crc32(d)
        const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(0x0800, 6); h.writeUInt32LE(crc, 14); h.writeUInt32LE(d.length, 18); h.writeUInt32LE(d.length, 22); h.writeUInt16LE(n.length, 26)
        partes.push(h, n, d)
        const c = Buffer.alloc(46); c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8); c.writeUInt32LE(crc, 16); c.writeUInt32LE(d.length, 20); c.writeUInt32LE(d.length, 24); c.writeUInt16LE(n.length, 28); c.writeUInt32LE(off, 42)
        central.push(c, n); off += 30 + n.length + d.length
    }
    const cd = Buffer.concat(central), e = Buffer.alloc(22)
    e.writeUInt32LE(0x06054b50, 0); e.writeUInt16LE(central.length / 2, 8); e.writeUInt16LE(central.length / 2, 10); e.writeUInt32LE(cd.length, 12); e.writeUInt32LE(off, 16)
    return Buffer.concat([...partes, cd, e])
}
const parrafo = 'La vieja fue admitida por la puerta lateral, a lo largo del pasadizo abovedado, junto a la habitación, y se le permitió echar un vistazo al muchacho tendido en su cama.'
const caps = ['Uno', 'Dos', 'Tres', 'Cuatro', 'Cinco']
const archivos = { mimetype: 'application/epub+zip', 'META-INF/container.xml': '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="c.opf" media-type="application/oebps-package+xml"/></rootfiles></container>',
    'c.opf': `<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>T</dc:title><dc:language>es</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>${caps.map((_, i) => `<item id="c${i}" href="c${i}.xhtml" media-type="application/xhtml+xml"/>`).join('')}</manifest><spine>${caps.map((_, i) => `<itemref idref="c${i}"/>`).join('')}</spine></package>`,
    'nav.xhtml': `<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Índice</title></head><body><nav epub:type="toc"><ol>${caps.map((t, i) => `<li><a href="c${i}.xhtml">${t}</a></li>`).join('')}</ol></nav></body></html>` }
caps.forEach((t, i) => archivos[`c${i}.xhtml`] = `<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${t}</title></head><body><h2>${t}</h2>${Array.from({ length: 25 }, () => `<p>${parrafo}</p>`).join('')}</body></html>`)
const epub = zip(archivos)
const tipos = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' }
const srv = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); if (p.startsWith('/libro/')) { r.writeHead(200); r.end(epub); return }
    fs.readFile(path.join(root, p), (e, d) => { if (e) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'Content-Type': tipos[path.extname(p)] || 'application/octet-stream' }); r.end(d) }) }).listen(port)
let fallos = 0
const ok = (cond, que) => { console.log((cond ? 'OK    ' : 'FALLA ') + que); if (!cond) fallos++ }
;(async () => {
    const b = await chromium.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome' })
    for (const [nombre, w, h] of [['móvil', 400, 820], ['tableta tumbada', 1240, 900], ['tableta de pie', 900, 1240]]) {
        const pg = await (await b.newContext({ viewport: { width: w, height: h }, hasTouch: true, isMobile: true })).newPage()
        await pg.addInitScript(() => {
            const st = { title: 'T', file: 't.epub', location: null, fraction: 0, goto: null, settings: { theme: 'papel' }, annotations: [], bookmarks: [], stats: null, device: 'a' }
            window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
        })
        await pg.goto('http://localhost:' + port + '/lector/index.html')
        await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
        await pg.waitForTimeout(1500)
        await pg.evaluate(() => window.__view.goToFraction(0.5)); await pg.waitForTimeout(700)
        ok(await pg.evaluate(() => document.querySelectorAll('#p-marcas i').length) === caps.length - 1, `${nombre}: una marca por capítulo en la barra`)
        await pg.evaluate(() => document.querySelector('#b-indice').click()); await pg.waitForTimeout(800)
        const r = await pg.evaluate(() => { const p = document.querySelector('#panel').getBoundingClientRect(); return { x: p.left, w: p.width, top: p.top, h: p.height,
            pct: [...document.querySelectorAll('.lista li.cap .pct')].map(e => e.textContent) } })
        if (nombre === 'tableta tumbada') ok(Math.abs(r.x + r.w - w) < 2 && r.w < 500 && r.top < 2 && r.h > h - 2, `${nombre}: el panel, de lado a la derecha (${Math.round(r.x)}, ${Math.round(r.w)}×${Math.round(r.h)})`)
        else if (nombre === 'tableta de pie') ok(r.w <= 681 && Math.abs(r.x - (w - r.w) / 2) < 2, `${nombre}: el panel, al centro (${Math.round(r.w)} de ancho)`)
        else ok(Math.round(r.w) === w, `${nombre}: el panel, de todo el ancho`)
        ok(r.pct[0] === '0%' && r.pct.includes('Estás aquí') && r.pct.length === caps.length, `${nombre}: el índice con el % de cada capítulo y «Estás aquí» (${r.pct.join(', ')})`)
        if (process.env.FOTOS) await pg.screenshot({ path: path.join(process.env.FOTOS, `indice-${nombre.replace(/ /g, '-')}.png`) })
        await pg.context().close()
    }
    await b.close(); srv.close()
    console.log(fallos ? `${fallos} fallo(s)` : 'Todo bien')
    process.exit(fallos ? 1 : 0)
})().catch(e => { console.log('ERR', e.stack); process.exit(1) })
