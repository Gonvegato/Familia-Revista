// Capturas del lector de Seshat con un window.Atrio simulado. Uso: node capturas.js <puerto> <carpeta_salida>
const { chromium } = require('playwright')
const fs = require('fs')
const [puerto, out] = process.argv.slice(2)
fs.mkdirSync(out, { recursive: true })

function atrio(theme) {
  return `(() => {
    const st = { title: 'Morning Star', file: 'prueba.epub', device: 'prueba',
      settings: { theme: '${theme}', size: 100, font: 'original' },
      accent: { light: '#C68B00', dark: '#E0A82E' },
      annotations: [], bookmarks: [{ value: 'x', label: 'La guerra', fraction: .3, created: 1 }], stats: { total: 3060000, days: {} } }
    const base = { state: () => JSON.stringify(st) }
    window.Atrio = new Proxy(base, { get: (o, k) => k in o ? o[k] : (k === 'brilloActual' ? () => -1 : () => '') })
  })()`
}

;(async () => {
  const b = await chromium.launch()
  for (const theme of ['papel', 'noche']) {
    const ctx = await b.newContext({ viewport: { width: 411, height: 860 }, deviceScaleFactor: 2 })
    const p = await ctx.newPage()
    p.on('pageerror', e => console.log('ERR', theme, e.message))
    await p.addInitScript(atrio(theme))
    await p.goto(`http://127.0.0.1:${puerto}/lector/index.html`)
    await p.waitForSelector('.carga.fuera', { timeout: 20000 }).catch(() => console.log('sin carga.fuera'))
    await p.waitForTimeout(1500)
    // Ir al capítulo 4 para que haya índice con avance
    await p.evaluate(() => document.querySelector('foliate-view')?.goToFraction?.(0.52))
    await p.waitForTimeout(1500)
    const foto = async n => { await p.waitForTimeout(500); await p.screenshot({ path: `${out}/lector-${theme}-${n}.png` }) }
    await foto('1-reposo')
    await p.mouse.click(205, 430); await p.waitForTimeout(600)
    if (await p.$eval('#arriba', e => e.classList.contains('oculta'))) await p.evaluate(() => { document.body.classList.add('menu'); document.querySelector('#arriba').classList.remove('oculta'); document.querySelector('#abajo').classList.remove('oculta') })
    await foto('2-menu')
    await p.evaluate(() => document.querySelector('#b-ajustes').click()); await foto('3-letra')
    await p.evaluate(() => document.querySelector('#velo').click()); await p.waitForTimeout(400)
    await p.evaluate(() => { document.body.classList.add('menu'); document.querySelector('#arriba').classList.remove('oculta'); document.querySelector('#abajo').classList.remove('oculta') })
    await p.evaluate(() => document.querySelector('#b-indice').click()); await p.waitForTimeout(800); await foto('4-indice')
    await p.evaluate(() => document.querySelector('#velo').click()); await p.waitForTimeout(400)
    await p.evaluate(() => { document.body.classList.add('menu'); document.querySelector('#arriba').classList.remove('oculta'); document.querySelector('#abajo').classList.remove('oculta') })
    await p.evaluate(() => document.querySelector('#b-notas').click()); await p.waitForTimeout(600); await foto('5-notas')
    await p.evaluate(() => document.querySelector('#velo').click()); await p.waitForTimeout(400)
    // Un subrayado: la hoja de acciones con su cita
    await p.evaluate(() => { const s = document.querySelector('#sel'); s.classList.remove('oculta'); s.style.bottom = '0px'
      document.querySelector('#s-cita').textContent = '«No soy vuestro», dijo, y la voz le salió más firme de lo que esperaba.' })
    await foto('6-subrayado')
    await ctx.close()
  }
  await b.close()
})()
