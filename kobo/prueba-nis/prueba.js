// Prueba de extremo a extremo de «vincular kepub con EPUB» (SesKobo), con el NickelInkSync de verdad (compilado desde su código) y
// un servidor KOSync de mentira, en un Kobo de mentira (la base de datos y los libros de prueba de NickelInkSync):
//   1. el programa del Kobo (seshat-kobo vincula) empareja el kepub con su EPUB original, como lo dejaría SesKobo;
//   2. el Kobo sube su posición en el kepub (push --live, como el plugin): llega al servidor como posición del EPUB original y, en el
//      lector de SesKobo, cae en el mismo texto que la frase del Kobo;
//   3. SesKobo sube una posición en el EPUB (la calcula el lector de verdad): el Kobo la recibe (pull --write) como frase del kepub, y
//      esa frase es el mismo texto.
// Uso: NIS=<nickelinksync compilado> FIXTURES=<NickelInkSync/tests/fixtures> KOBO=<seshat-kobo compilado> NODE_PATH=<playwright> node prueba.js
const { chromium } = require('playwright')
const http = require('http'), fs = require('fs'), path = require('path'), os = require('os'), crypto = require('crypto')
const { execFileSync, execFile } = require('child_process')
const NIS = process.env.NIS, FX = process.env.FIXTURES, KOBO = process.env.KOBO
const raizLector = path.resolve(__dirname, '../../marfil/src/main/assets')

let fallos = 0, bien = 0
const ok = (c, msg) => { if (c) { bien++; console.log('OK    ' + msg) } else { fallos++; console.log('FALLA ' + msg) } }

/** La huella de KOReader (la de KOSync): MD5 de 1 KiB en 0, 1 KiB, 4 KiB, 16 KiB… */
function huella(f) {
  const b = fs.readFileSync(f), h = crypto.createHash('md5')
  for (const o of [0, ...Array.from({ length: 11 }, (_, i) => 1024 * 4 ** i)]) { if (o >= b.length) break; h.update(b.subarray(o, o + 1024)) }
  return h.digest('hex')
}
const md5 = s => crypto.createHash('md5').update(s).digest('hex')

// ---- El Kobo de mentira (firmware 6.0 del Clara BW), con el kepub de «Book R» y su base de datos
const D = fs.mkdtempSync(path.join(os.tmpdir(), 'kobo-'))
fs.mkdirSync(path.join(D, '.kobo')); fs.mkdirSync(path.join(D, 'Books'))
fs.writeFileSync(path.join(D, '.kobo/version'), 'N000000000000,4.9.77,6.0.279225,4.9.77,4.9.77,00000000-0000-0000-0000-000000000391\n')
execFileSync('python3', ['-c', `import sqlite3,sys;d=sqlite3.connect(sys.argv[1]);d.executescript(open(sys.argv[2]).read());d.commit()`,
  path.join(D, '.kobo/KoboReader.sqlite'), path.join(FX, 'db/kobo-libra2-4.38-probe-books.sql')])
fs.copyFileSync(path.join(FX, 'epub/book-r.kepub.epub'), path.join(D, 'Books/book-r.kepub.epub'))
const KEPUB = 'file:///mnt/onboard/Books/book-r.kepub.epub'
const original = path.join(FX, 'epub/book-r.epub'), hOriginal = huella(original)

// ---- El servidor KOSync de mentira
const usuarios = { ana: md5('secreto') }, progreso = {}
const srv = http.createServer((q, r) => {
  let cuerpo = ''; q.on('data', c => cuerpo += c); q.on('end', () => {
    const res = (c, o) => { r.writeHead(c, { 'Content-Type': 'application/json' }); r.end(JSON.stringify(o)) }
    if (usuarios[q.headers['x-auth-user']] !== q.headers['x-auth-key']) return res(401, {})
    if (q.url === '/users/auth') return res(200, { authorized: 'OK' })
    if (q.method === 'PUT') { const o = JSON.parse(cuerpo); o.timestamp = Math.floor(Date.now() / 1000); progreso[o.document] = o; return res(200, { document: o.document, timestamp: o.timestamp }) }
    if (q.url.startsWith('/syncs/progress/')) return res(200, progreso[q.url.split('/').pop()] || {})
    res(404, {})
  })
}).listen(0)
const servidor = () => 'http://127.0.0.1:' + srv.address().port

// ---- Lo que deja SesKobo al preparar el Kobo y vincular: NickelInkSync con su configuración, el original (.orig) y la lista
const dn = path.join(D, '.adds/NickelInkSync'), ds = path.join(D, '.adds/seshat')
fs.mkdirSync(dn, { recursive: true }); fs.mkdirSync(path.join(ds, 'originales'), { recursive: true })
fs.copyFileSync(NIS, path.join(dn, 'nickelinksync')); fs.chmodSync(path.join(dn, 'nickelinksync'), 0o755)
const config = path.join(dn, 'config.ini')
fs.copyFileSync(original, path.join(ds, 'originales', hOriginal + '.orig'))
fs.writeFileSync(path.join(ds, 'vinculos.json'), JSON.stringify({ vinculos: [{ kepub: KEPUB, original: hOriginal + '.orig', titulo: 'Book R' }] }))

// (sin bloquear: el servidor de mentira está en este mismo proceso y tiene que poder contestar)
const nis = (...args) => new Promise(z => execFile(path.join(dn, 'nickelinksync'), args,
  { env: { ...process.env, NICKELINKSYNC_DATA_DIR: path.join(ds, 'nis'), HOME: path.join(ds, 'nis') }, encoding: 'utf8' },
  (e, out, err) => z((e ? 'ERROR ' + e.code + '\n' : '') + (out || '') + (err || ''))))
const fila = () => execFileSync('python3', ['-c', `import sqlite3,sys;d=sqlite3.connect(sys.argv[1]);print(d.execute("select ChapterIDBookmarked from content where ContentID=?",(sys.argv[2],)).fetchone()[0])`,
  path.join(D, '.kobo/KoboReader.sqlite'), KEPUB], { encoding: 'utf8' }).trim()

// ---- El lector de SesKobo (para leer posiciones en los dos libros)
let libro
const tipos = { '.js': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.ttf': 'font/ttf' }
const web = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); if (p.startsWith('/libro/')) { r.writeHead(200); r.end(libro); return }
  fs.readFile(path.join(raizLector, p), (e, d) => { if (e) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'Content-Type': tipos[path.extname(p)] || 'application/octet-stream' }); r.end(d) }) }).listen(0)
async function lector(b, archivo) {
  libro = fs.readFileSync(archivo)
  const pg = await (await b.newContext({ viewport: { width: 393, height: 851 } })).newPage()
  await pg.addInitScript(() => { const st = { title: 'T', file: 't.epub', location: null, fraction: 0, goto: null, settings: { theme: 'papel' }, annotations: [], bookmarks: [], stats: null, device: 'a' }
    window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) }) })
  await pg.goto('http://localhost:' + web.address().port + '/lector/index.html')
  await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 30000 })
  return pg
}
/** El texto que sigue a una posición de KOReader en el EPUB, y a una marca del Kobo en el kepub (lo que se ve al abrir ahí). */
const textoXp = (pg, xp) => pg.evaluate(async xp => {
  const i = +/DocFragment\[(\d+)\]/.exec(xp)[1] - 1, doc = await window.__view.book.sections[i].createDocument()
  const r = window.__xp.xpRango(doc, xp); if (!r) return null
  r.setEndAfter(doc.body.lastChild); return r.toString().replace(/\s+/g, ' ').trim().slice(0, 60)
}, xp)
const textoMarca = (pg, marca) => pg.evaluate(async marca => {
  const [cap, frag] = marca.split('#'), secs = window.__view.book.sections, i = secs.findIndex(s => s.id === cap)
  if (i < 0) return null
  const doc = await secs[i].createDocument(), r = window.__xp.koboRango(doc, frag); if (!r) return null
  r.setEndAfter(doc.body.lastChild); return r.toString().replace(/\s+/g, ' ').trim().slice(0, 60)
}, marca)

;(async () => {
  await new Promise(z => setTimeout(z, 300))
  fs.writeFileSync(config, `server = ${servidor()}\nuser = ana\nkey = ${md5('secreto')}\nconfirm = never\nnotify = errors\nposition = paragraph\n`, { mode: 0o600 })

  // 1. Vincular, como lo hace el programa del Kobo
  execFileSync(KOBO, ['-bd', path.join(D, '.kobo/KoboReader.sqlite'), '-registro', path.join(D, 'registro.txt'), 'vincula'], { stdio: 'ignore' })
  const map = JSON.parse(fs.readFileSync(path.join(dn, 'mappings.json'), 'utf8'))
  const hKepub = huella(path.join(D, 'Books/book-r.kepub.epub'))
  ok(map.mappings?.length === 1 && JSON.stringify(map).includes(hOriginal), 'emparejado en mappings.json (kepub → original ' + hOriginal + ')')
  ok(fs.existsSync(path.join(dn, 'tables', hKepub + '.json')), 'tabla de traducción del kepub')
  ok(fs.existsSync(path.join(dn, 'library.json')), 'índice library.json')
  execFileSync(KOBO, ['-bd', path.join(D, '.kobo/KoboReader.sqlite'), '-registro', path.join(D, 'registro.txt'), 'vincula'], { stdio: 'ignore' })
  const reg = fs.readFileSync(path.join(D, 'registro.txt'), 'utf8').trim().split('\n')
  ok(/ 1 kepub vinculados$/.test(reg[0]) && / 0 kepub vinculados$/.test(reg[reg.length - 1]), 'la segunda vez no vuelve a vincular lo ya vinculado: ' + reg.join(' / '))

  // 2. El Kobo sube su posición en el kepub (como el plugin al salir del libro)
  const marcaKobo = fila()
  console.log('  push: ' + (await nis('push', '--live', '--device', D, '--config', config, '--content-id', KEPUB, '--force')).trim().split('\n').slice(-2).join(' | '))
  const llega = progreso[hOriginal]
  ok(!!llega, `el servidor recibe la posición bajo la huella del EPUB original (${llega?.progress})`)
  const b = await chromium.launch()
  const pgK = await lector(b, path.join(D, 'Books/book-r.kepub.epub')), pgE = await lector(b, original)
  if (llega) {
    const tK = await textoMarca(pgK, marcaKobo), tE = await textoXp(pgE, llega.progress)
    ok(tK && tE && (tK.startsWith(tE.slice(0, 25)) || tE.startsWith(tK.slice(0, 25))) || (tK && tE && tK.includes(tE.slice(0, 20))), `del Kobo a SesKobo, mismo texto: Kobo «${tK}» → SesKobo «${tE}»`)
    // Y el lector de SesKobo, al saltar ahí (como al abrir el libro), abre la página que tiene ese sitio
    const salto = await pgE.evaluate(async xp => {
      const fue = await window.atrioIrXPointer(xp, 0.5, 'el Kobo')
      await new Promise(z => setTimeout(z, 800))
      const v = window.__view.lastLocation, dest = window.__xp.xpRango(v.range.startContainer.ownerDocument, xp)
      return { fue, dentro: !!dest && v.range.comparePoint(dest.startContainer, dest.startOffset) === 0, pagina: window.atrioXPointer()?.xp }
    }, llega.progress)
    ok(salto.fue && salto.dentro, `SesKobo salta a la página de la posición del Kobo: ${JSON.stringify(salto)}`)
    await pgE.evaluate(() => window.__view.goTo(0)); await new Promise(z => setTimeout(z, 500))
  }

  // 3. SesKobo sube una posición en el EPUB (la del principio de una página más adelante, calculada por el lector de verdad)
  const xpS = await pgE.evaluate(async () => {
    const v = window.__view, i = v.book.sections.findIndex(s => /chapter4/.test(s.id))
    await v.goTo(i); await new Promise(z => setTimeout(z, 600)); v.next(); await new Promise(z => setTimeout(z, 600)); v.next(); await new Promise(z => setTimeout(z, 800))
    return window.atrioXPointer()
  })
  ok(!!xpS?.xp, 'el lector de SesKobo da su posición en el EPUB: ' + xpS?.xp)
  progreso[hOriginal] = { document: hOriginal, progress: xpS.xp, percentage: xpS.fraction, device: 'SesKobo', device_id: 'ABC', timestamp: Math.floor(Date.now() / 1000) + 60 }
  // Como lo hace el plugin en el Kobo encendido: el programa decide y el plugin escribe con las funciones del propio Kobo
  const emite = await nis('pull', '--live', '--emit', '--device', D, '--config', config, '--content-id', KEPUB)
  const lineaEmit = emite.split('\n').find(l => l.startsWith('NICKELINKSYNC_POSITION')) || ''
  console.log('  pull --live --emit: ' + lineaEmit.replace(/\t/g, ' '))
  const marcaEmit = (/([\w./-]+\.x?html#kobo\.\d+\.\d+(?::\d+)?)/.exec(lineaEmit) || [])[1]
  console.log('  pull: ' + (await nis('pull', '--device', D, '--config', config, '--content-id', KEPUB, '--write', '--force')).trim().split('\n').slice(-3).join(' | '))
  const marcaNueva = fila()
  ok(marcaNueva !== marcaKobo && /#kobo\.\d+\.\d+/.test(marcaNueva), `el Kobo apunta la posición nueva como frase del kepub: ${marcaNueva}`)
  ok(!!marcaEmit && marcaEmit.split(':')[0] === marcaNueva.split(':')[0], `con el Kobo encendido (pull --live --emit) decide la misma frase: ${marcaEmit}`)
  const tS = await textoXp(pgE, xpS.xp), tK2 = await textoMarca(pgK, marcaNueva)
  ok(tS && tK2 && (tS.includes(tK2.slice(0, 20)) || tK2.includes(tS.slice(0, 20))), `de SesKobo al Kobo, mismo texto: SesKobo «${tS}» → Kobo «${tK2}»`)

  await b.close(); srv.close(); web.close()
  console.log(`${bien} bien, ${fallos} fallos`)
  process.exit(fallos ? 1 : 0)
})().catch(e => { console.log('ERR', e.stack); process.exit(1) })
