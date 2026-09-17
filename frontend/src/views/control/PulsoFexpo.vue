<script setup lang="ts">
// PULSO FEXPO — monitoreo en vivo del recinto, en 3D.
//
// Este es el mismo diseño que se probó como demo (recinto real del plano
// "FEXPO UAP V2.0": zonas, camino serpenteante, letrero, y los 3 ingresos
// peatonales), ahora conectado a DATOS REALES por WebSocket en vez de una
// simulación: cada validación de boleto (en cualquier puesto de Control)
// llega acá al instante y mueve el enjambre, hace latir una puerta y
// actualiza los números.
//
// Limitación conocida (queda para un ajuste posterior): el backend no
// registra por qué puerta física entró cada boleto (Boleto/MovimientoBoleto
// no tienen ese dato todavía), así que la puerta que "late" en cada evento
// real se elige al azar entre las 3 — es decorativo; los NÚMEROS (dentro,
// ingresos, salidas, feed) sí son 100% reales.
//
// El puerto imperativo (crear elementos de la cabina de mando con
// document.createElement en vez de v-for) es a propósito: así el código es
// casi calco del prototipo ya validado, con el mínimo riesgo de que algo se
// vea distinto "por las dudas de Vue" en una primera integración.
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as THREE from 'three'
import { resumenBoletos } from '@/api/control-boleto.service'
import { conectarBoletosWs } from '@/api/ws-boletos'
import type { EventoBoletoDto } from '@/types/boleto.type'

const router = useRouter()
const enVivo = ref(false)

let cerrarWs: (() => void) | undefined
let raf = 0
let limpiarEscena: (() => void) | undefined

onMounted(async () => {
  inyectarFuentes()
  const contenedor = document.getElementById('pulso-fexpo-root')!

  // ---------------------------------------------------------------------
  // Geometría del recinto (diseño de bloques 3D con título de Javier)
  // ---------------------------------------------------------------------
  // Cada zona es un bloque 3D con su forma real del croquis: caja (default),
  // torre (alta y delgada, el frontis), losa (plana, la plaza) o domo (media
  // esfera, el coliseo). px/py = centro en % del predio; w/d = tamaño en % del
  // ancho/fondo; alto = altura en unidades base (se multiplica por ESCALA).
  // Los bloques del croquis sin rótulo van con sinTitulo. contorno = solo
  // alambre (como el Laboratorio FIT, dibujado en blanco sin relleno).
  const WORLD_W = 320, WORLD_D = 233
  const ESCALA = WORLD_W / 132
  const fx = (px: number) => (px / 100 - 0.5) * WORLD_W
  const fz = (py: number) => (py / 100 - 0.5) * WORLD_D

  type Forma = 'caja' | 'torre' | 'losa' | 'domo' | 'medialuna'
  interface ZonaBase {
    nombre: string
    px: number
    py: number
    w: number
    d: number
    color: number
    forma?: Forma
    alto?: number
    sinTitulo?: boolean
    contorno?: boolean
  }

  const ZONAS_BASE: ZonaBase[] = [
    // --- Bloques con título ---
    { nombre: 'Frontis Plaza', px: 17, py: 27, w: 18, d: 12, color: 0x8fd0ef, forma: 'medialuna', alto: 1 },
    { nombre: 'Bloque I', px: 24, py: 47, w: 11, d: 7, color: 0x1f8fd0, alto: 7 },
    { nombre: 'Plaza', px: 25, py: 61, w: 11, d: 8, color: 0x35d07f, forma: 'losa', alto: 0.7 },
    { nombre: 'Rueda de Negocios', px: 44, py: 61, w: 12, d: 9, color: 0x1f8fd0, alto: 8 },
    { nombre: 'Baño', px: 49, py: 50, w: 6, d: 5, color: 0x157d90, alto: 5 },
    { nombre: 'Laboratorio FIT', px: 83, py: 22, w: 13, d: 8, color: 0xdfe6ee, alto: 6, contorno: true },
    { nombre: 'CIMAA y Museo', px: 83, py: 58, w: 13, d: 9, color: 0x2b8fd6, alto: 7 },
    { nombre: 'Escenario', px: 68, py: 68, w: 17, d: 12, color: 0x2fbf6e, forma: 'losa', alto: 2 },
    { nombre: 'Coliseo', px: 86, py: 77, w: 14, d: 11, color: 0x8fd0ef, forma: 'domo' },
    { nombre: 'Ganadería e Innovación', px: 14, py: 88, w: 11, d: 7, color: 0x4bb8c9, alto: 6 },
    { nombre: 'Zona Agropecuaria', px: 28, py: 89, w: 11, d: 7, color: 0xe7cdd6, alto: 5 },
    { nombre: 'Cultura y Naturaleza', px: 42, py: 88, w: 9, d: 7, color: 0x1f8fd0, alto: 6 },
    { nombre: 'Zona Empresarial', px: 54, py: 85, w: 10, d: 8, color: 0x3a9fd8, alto: 6 },
    // --- Bloques del croquis sin rótulo (dan la densidad de "campus") ---
    { nombre: '', px: 38, py: 31, w: 9, d: 6, color: 0x6a6fd0, alto: 6, sinTitulo: true },
    { nombre: '', px: 50, py: 36, w: 7, d: 7, color: 0x2f9fb0, alto: 8, sinTitulo: true },
    { nombre: '', px: 65, py: 25, w: 6, d: 6, color: 0xbcd0f0, forma: 'torre', alto: 15, sinTitulo: true },
    { nombre: '', px: 76, py: 24, w: 6, d: 6, color: 0x9db8e8, forma: 'torre', alto: 15, sinTitulo: true },
    { nombre: '', px: 39, py: 44, w: 9, d: 7, color: 0x2b8fd6, alto: 6, sinTitulo: true },
    { nombre: '', px: 18, py: 58, w: 7, d: 7, color: 0x2f9fb0, alto: 8, sinTitulo: true },
    { nombre: '', px: 33, py: 61, w: 7, d: 7, color: 0x2b3f8c, alto: 8, sinTitulo: true },
    { nombre: '', px: 24, py: 74, w: 11, d: 6, color: 0x6a6fd0, alto: 6, sinTitulo: true },
  ]
  const ZONAS = ZONAS_BASE.map((z) => ({
    ...z,
    x: fx(z.px), zc: fz(z.py), wx: (z.w / 100) * WORLD_W, wz: (z.d / 100) * WORLD_D,
  }))

  const centro = { x: 0, z: 0 }
  ZONAS.forEach((z) => { centro.x += z.x; centro.z += z.zc })
  centro.x /= ZONAS.length; centro.z /= ZONAS.length

  // Los 2 ingresos del croquis (arriba, sobre la avenida), texto vertical.
  const GATES_BASE = [
    { id: 'A', corto: 'Ingreso 1', nombre: 'Ingreso 1', color: 0x33e0ff, px: 31, py: 12 },
    { id: 'B', corto: 'Ingreso 2', nombre: 'Ingreso 2', color: 0xffb020, px: 55, py: 12 },
  ] as const
  const GATES = GATES_BASE.map((g) => {
    const pos: [number, number, number] = [fx(g.px), 0, fz(g.py)]
    const rotY = Math.atan2(centro.x - pos[0], centro.z - pos[2])
    return { ...g, pos, rotY, ringMesh: null as THREE.Mesh | null }
  })

  // Predio rectangular con avenidas en 3 lados (arriba, izquierda, derecha);
  // el lado de abajo queda abierto. AVENIDAS marca cuáles llevan la calle.
  const PERIMETRO = [[5, 4], [95, 4], [95, 96], [5, 96]]
  const AVENIDAS: Array<[[number, number], [number, number]]> = [
    [[5, 4], [95, 4]],   // arriba
    [[5, 4], [5, 96]],   // izquierda
    [[95, 4], [95, 96]], // derecha
  ]

  // ---------------------------------------------------------------------
  // Escena Three.js
  // ---------------------------------------------------------------------
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  const $ = (id: string) => document.getElementById(id)!
  const fmt = (n: number) => n.toLocaleString('es-BO')

  const canvas = $('pulso-scene') as HTMLCanvasElement
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2))
  const scene = new THREE.Scene()
  scene.background = new THREE.Color(0x05070d)
  // Niebla más liviana que en el prototipo: antes cerraba la visibilidad muy
  // cerca del recinto, dando sensación de espacio chico. Ahora el horizonte
  // se pierde mucho más lejos.
  scene.fog = new THREE.FogExp2(0x05070d, 0.0016 / ESCALA)

  // El far-plane tiene que cubrir el piso-horizonte (WORLD*9) también con el
  // mapa más grande, si no sus bordes se recortan.
  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.5, 4000)

  function resize() {
    const w = contenedor.clientWidth, h = contenedor.clientHeight
    renderer.setSize(w, h)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
  }
  window.addEventListener('resize', resize)
  resize()

  function colorCss(hex: number) { return '#' + hex.toString(16).padStart(6, '0') }

  // --- Suelo: el plano del recinto, "blueprint" holográfico ---
  function texturaSuelo() {
    const s = 1400
    const c = document.createElement('canvas'); c.width = c.height = s
    const x = c.getContext('2d')!
    const px = (v: number) => (v / 100) * s
    x.fillStyle = '#070a13'; x.fillRect(0, 0, s, s)

    const glow = x.createRadialGradient(px(52), px(45), 0, px(52), px(45), s * 0.55)
    glow.addColorStop(0, 'rgba(51,224,255,.09)'); glow.addColorStop(1, 'rgba(51,224,255,0)')
    x.fillStyle = glow; x.fillRect(0, 0, s, s)

    x.strokeStyle = 'rgba(120,170,220,.10)'; x.lineWidth = 1
    const paso = s / 26
    for (let i = 0; i <= 26; i++) {
      x.beginPath(); x.moveTo(i * paso, 0); x.lineTo(i * paso, s); x.stroke()
      x.beginPath(); x.moveTo(0, i * paso); x.lineTo(s, i * paso); x.stroke()
    }

    // Recinto: borde tenue del predio.
    x.beginPath()
    PERIMETRO.forEach((p, idx) => {
      const mx = px(p[0]!), my = px(p[1]!)
      if (idx === 0) x.moveTo(mx, my); else x.lineTo(mx, my)
    })
    x.closePath()
    x.lineWidth = 2; x.strokeStyle = 'rgba(120,170,220,.22)'; x.stroke()

    // Avenidas (3 lados con calle): banda oscura + doble línea clara.
    AVENIDAS.forEach(([a, b]) => {
      x.beginPath(); x.moveTo(px(a[0]), px(a[1])); x.lineTo(px(b[0]), px(b[1]))
      x.lineCap = 'round'
      x.lineWidth = 26; x.strokeStyle = 'rgba(18,24,36,.95)'; x.stroke()
      x.lineWidth = 2; x.strokeStyle = 'rgba(180,210,240,.5)'; x.stroke()
      x.setLineDash([10, 10]); x.lineWidth = 1.5; x.strokeStyle = 'rgba(255,255,255,.32)'; x.stroke()
      x.setLineDash([])
    })

    // Zonas: huella rectangular de cada bloque en el piso (relleno + borde).
    // La media luna no lleva huella rectangular (su forma 3D ya se ve y el
    // rectángulo del piso no le calza).
    ZONAS.forEach((z) => {
      if (z.forma === 'medialuna') return
      const x0 = px(z.px - z.w / 2), y0 = px(z.py - z.d / 2), w = px(z.w), h = px(z.d)
      const col = colorCss(z.color)
      x.fillStyle = col + '18'; x.fillRect(x0, y0, w, h)
      x.strokeStyle = col + 'bb'; x.lineWidth = 2
      x.strokeRect(x0, y0, w, h)
    })

    // Arbolado: puntos verdes tenues a lo largo de las avenidas (detalle).
    AVENIDAS.forEach(([a, b]) => {
      const n = 14
      for (let i = 0; i <= n; i++) {
        const f = i / n
        const tx = px(a[0] + (b[0] - a[0]) * f), ty = px(a[1] + (b[1] - a[1]) * f)
        let nx = -(b[1] - a[1]), ny = b[0] - a[0]
        const nl = Math.sqrt(nx * nx + ny * ny) || 1; nx /= nl; ny /= nl
        const ox = tx + nx * 16, oy = ty + ny * 16
        const tg = x.createRadialGradient(ox, oy, 0, ox, oy, 8)
        tg.addColorStop(0, 'rgba(80,210,130,.4)'); tg.addColorStop(1, 'rgba(80,210,130,0)')
        x.fillStyle = tg; x.beginPath(); x.arc(ox, oy, 8, 0, Math.PI * 2); x.fill()
      }
    })

    // Viñeta del borde: antes oscurecía bastante desde el 32% del radio, dando
    // sensación de "isla flotando en el vacío". Se corre más afuera y se aclara
    // para que el recinto no se sienta encerrado.
    const edge = x.createRadialGradient(px(52), px(45), s * 0.46, px(52), px(45), s * 0.7)
    edge.addColorStop(0, 'rgba(0,0,0,0)'); edge.addColorStop(1, 'rgba(0,0,0,.4)')
    x.fillStyle = edge; x.fillRect(0, 0, s, s)

    const tex = new THREE.CanvasTexture(c); tex.anisotropy = 4
    return tex
  }
  const suelo = new THREE.Mesh(new THREE.PlaneGeometry(WORLD_W, WORLD_D), new THREE.MeshBasicMaterial({ map: texturaSuelo() }))
  suelo.rotation.x = -Math.PI / 2
  scene.add(suelo)

  // Terreno lejano: un piso mucho más grande alrededor del recinto para que al
  // alejar la cámara haya "mundo" hasta el horizonte en vez de que el plano
  // del recinto termine de golpe contra el vacío. Una grilla tenue que se va
  // espaciando (perspectiva) más allá del límite real mapeado.
  function texturaHorizonte() {
    const s = 1024
    const c = document.createElement('canvas'); c.width = c.height = s
    const x = c.getContext('2d')!
    const g = x.createRadialGradient(s / 2, s / 2, s * 0.1, s / 2, s / 2, s * 0.5)
    g.addColorStop(0, '#0a0e18'); g.addColorStop(1, '#04050a')
    x.fillStyle = g; x.fillRect(0, 0, s, s)
    x.strokeStyle = 'rgba(120,170,220,.055)'; x.lineWidth = 1
    const paso = s / 22
    for (let i = 0; i <= 22; i++) {
      x.beginPath(); x.moveTo(i * paso, 0); x.lineTo(i * paso, s); x.stroke()
      x.beginPath(); x.moveTo(0, i * paso); x.lineTo(s, i * paso); x.stroke()
    }
    return new THREE.CanvasTexture(c)
  }
  const horizonte = new THREE.Mesh(
    new THREE.PlaneGeometry(WORLD_W * 9, WORLD_D * 9),
    new THREE.MeshBasicMaterial({ map: texturaHorizonte() }),
  )
  horizonte.rotation.x = -Math.PI / 2
  horizonte.position.y = -0.12
  horizonte.position.set(centro.x, -0.12, centro.z)
  scene.add(horizonte)

  function texturaEtiqueta(texto: string, colorTexto: string, tam = 28) {
    const c = document.createElement('canvas'); c.width = 360; c.height = 92
    const x = c.getContext('2d')!
    x.font = tam + 'px Sora, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle'
    x.shadowColor = colorTexto; x.shadowBlur = 16
    x.fillStyle = colorTexto; x.fillText(texto, 180, 46)
    return new THREE.CanvasTexture(c)
  }

  // Título flotante sobre un bloque: texto en una "píldora" oscura con borde
  // del color de la zona, para que se lea sobre la escena.
  function texturaTitulo(texto: string, colorHex: number) {
    const c = document.createElement('canvas'); c.width = 512; c.height = 128
    const x = c.getContext('2d')!
    let fs = 48
    const fuente = (n: number) => `700 ${n}px Sora, sans-serif`
    x.font = fuente(fs)
    while (x.measureText(texto).width > 468 && fs > 20) { fs -= 2; x.font = fuente(fs) }
    const tw = x.measureText(texto).width, pw = tw + 52, ph = fs + 38
    const x0 = (512 - pw) / 2, y0 = (128 - ph) / 2
    x.fillStyle = 'rgba(8,12,22,.74)'
    x.beginPath(); if (x.roundRect) x.roundRect(x0, y0, pw, ph, ph / 2); else x.rect(x0, y0, pw, ph); x.fill()
    x.strokeStyle = colorCss(colorHex) + 'aa'; x.lineWidth = 3; x.stroke()
    x.textAlign = 'center'; x.textBaseline = 'middle'
    x.fillStyle = '#f2f6ff'; x.font = fuente(fs)
    x.fillText(texto, 256, 64)
    return new THREE.CanvasTexture(c)
  }

  // Bloques 3D: cada zona según su forma (caja/torre/losa/domo). Se dibujan
  // como cuerpo semitransparente + aristas brillantes (estilo holográfico del
  // Pulso) y, si tienen rótulo, un título flotante encima.
  ZONAS.forEach((z) => {
    const alto = (z.alto ?? 4) * ESCALA
    let geo: THREE.BufferGeometry
    let cy: number // altura del centro del cuerpo
    let topY: number // punto más alto (para colgar el título)
    let rotY = 0 // rotación del bloque (solo la media luna la usa)

    if (z.forma === 'domo') {
      const r = (Math.min(z.wx, z.wz) / 2) * 1.05
      geo = new THREE.SphereGeometry(r, 40, 20, 0, Math.PI * 2, 0, Math.PI / 2)
      cy = 0; topY = r
    } else if (z.forma === 'medialuna') {
      // Media luna plana (medio cilindro bajo, estilo losa): el corte recto
      // mira al centro del mapa y la curva hacia afuera. Por defecto el medio
      // cilindro (thetaLength=PI) abulta hacia +X local; se rota para que ese
      // +X apunte hacia AFUERA (lejos del centro).
      const r = Math.max(z.wx, z.wz) / 2
      geo = new THREE.CylinderGeometry(r, r, alto, 40, 1, false, 0, Math.PI)
      cy = alto / 2; topY = alto
      rotY = Math.atan2(-(z.zc - centro.z), z.x - centro.x)
    } else {
      geo = new THREE.BoxGeometry(z.wx, alto, z.wz)
      cy = alto / 2; topY = alto
    }

    // Cuerpo sólido translúcido (salvo los de contorno, ej. Laboratorio FIT).
    // Losa y media luna van más opacas para leerse como plataforma plana.
    if (!z.contorno) {
      const plana = z.forma === 'losa' || z.forma === 'medialuna'
      const cuerpo = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
        color: z.color, transparent: true, opacity: plana ? 0.4 : 0.16, depthWrite: false,
      }))
      cuerpo.position.set(z.x, cy, z.zc)
      cuerpo.rotation.y = rotY
      scene.add(cuerpo)
    }
    // Aristas brillantes.
    const edges = new THREE.EdgesGeometry(geo)
    const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({
      color: z.color, transparent: true, opacity: z.contorno ? 0.75 : 0.6,
    }))
    line.position.set(z.x, cy, z.zc)
    line.rotation.y = rotY
    scene.add(line)

    // Título flotante.
    if (z.nombre && !z.sinTitulo) {
      const label = new THREE.Sprite(new THREE.SpriteMaterial({
        map: texturaTitulo(z.nombre, z.color), transparent: true, depthWrite: false,
      }))
      const anchoBase = Math.min(30, Math.max(16, z.nombre.length * 1.6)) * ESCALA
      label.scale.set(anchoBase, anchoBase * (128 / 512), 1)
      // La media luna abulta hacia afuera: su centro visual (centroide del
      // semicírculo, 4r/3π) está corrido hacia la curva, así que el título va
      // sobre ese punto y no sobre el borde recto.
      let lx = z.x, lz = z.zc
      if (z.forma === 'medialuna') {
        const r = Math.max(z.wx, z.wz) / 2
        const L = Math.hypot(z.x - centro.x, z.zc - centro.z) || 1
        const off = (4 * r) / (3 * Math.PI)
        lx += ((z.x - centro.x) / L) * off
        lz += ((z.zc - centro.z) / L) * off
      }
      label.position.set(lx, topY + 3 * ESCALA, lz)
      scene.add(label)
    }
  })

  // Logo FEXPO como parte de la escena 3D: flota sobre el centro del recinto,
  // anclado en el mundo (orbita/escala con el mapa, no pegado a la pantalla).
  // Detrás lleva copias verdes en blending aditivo y a mayor escala → un halo
  // de iluminación verde claro (el verde del logo) alrededor de su silueta.
  {
    const loader = new THREE.TextureLoader()
    loader.load('/logo.png', (tex) => {
      tex.colorSpace = THREE.SRGBColorSpace
      const aspect = (tex.image && tex.image.width / tex.image.height) || 1.4
      const W = 88, H = W / aspect
      const y = 30 * ESCALA
      const verde = 0x9ade3a
      ;([[1.18, 0.1], [1.1, 0.16], [1.04, 0.24]] as const).forEach(([s, op]) => {
        const glow = new THREE.Sprite(new THREE.SpriteMaterial({
          map: tex, color: verde, transparent: true, opacity: op,
          blending: THREE.AdditiveBlending, depthWrite: false,
        }))
        glow.scale.set(W * s, H * s, 1)
        glow.position.set(centro.x, y, centro.z)
        scene.add(glow)
      })
      const logo = new THREE.Sprite(new THREE.SpriteMaterial({
        map: tex, transparent: true, opacity: 0.98, depthWrite: false,
      }))
      logo.scale.set(W, H, 1)
      logo.position.set(centro.x, y, centro.z)
      scene.add(logo)
    })
  }

  // Postes de luz a lo largo de las avenidas (detalle 3D).
  AVENIDAS.forEach(([a, b]) => {
    const n = 7
    for (let i = 1; i < n; i++) {
      const f = i / n
      const wx = fx(a[0] + (b[0] - a[0]) * f), wz = fz(a[1] + (b[1] - a[1]) * f)
      const h = 7 * ESCALA
      const poste = new THREE.Mesh(
        new THREE.CylinderGeometry(0.12 * ESCALA, 0.12 * ESCALA, h, 6),
        new THREE.MeshBasicMaterial({ color: 0x8fa6c4, transparent: true, opacity: 0.5 }),
      )
      poste.position.set(wx, h / 2, wz)
      scene.add(poste)
      const foco = new THREE.Sprite(new THREE.SpriteMaterial({
        map: texturaGlow(), color: 0xfff2c8, transparent: true, opacity: 0.9,
        blending: THREE.AdditiveBlending, depthWrite: false,
      }))
      foco.scale.set(2.4 * ESCALA, 2.4 * ESCALA, 1)
      foco.position.set(wx, h, wz)
      scene.add(foco)
    }
  })

  // Puertas.
  GATES.forEach((g) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(5.6 * ESCALA, 0.42 * ESCALA, 16, 48), new THREE.MeshBasicMaterial({ color: g.color }))
    ring.position.set(g.pos[0], 5.8 * ESCALA, g.pos[2])
    ring.rotation.y = g.rotY
    scene.add(ring)
    g.ringMesh = ring

    const haz = new THREE.Mesh(new THREE.CylinderGeometry(0.16 * ESCALA, 0.16 * ESCALA, 34 * ESCALA, 8, 1, true),
      new THREE.MeshBasicMaterial({ color: g.color, transparent: true, opacity: 0.22, blending: THREE.AdditiveBlending, depthWrite: false }))
    haz.position.set(g.pos[0], 17 * ESCALA, g.pos[2])
    scene.add(haz)

    const label = new THREE.Sprite(new THREE.SpriteMaterial({ map: texturaEtiqueta(g.nombre, colorCss(g.color), 26), transparent: true, depthWrite: false }))
    label.scale.set(16 * ESCALA, (16 * 92 * ESCALA) / 360, 1)
    label.position.set(g.pos[0], 12.8 * ESCALA, g.pos[2])
    scene.add(label)
  })

  // Pulsos.
  const pulsos: { mesh: THREE.Mesh; t0: number; dur: number }[] = []
  function lanzarPulso(gateIdx: number, colorHex: number) {
    const g = GATES[gateIdx]!
    const mesh = new THREE.Mesh(new THREE.RingGeometry(0.2 * ESCALA, 0.85 * ESCALA, 40),
      new THREE.MeshBasicMaterial({ color: colorHex, transparent: true, opacity: 0.9, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false }))
    mesh.position.set(g.pos[0], 5.8 * ESCALA, g.pos[2])
    mesh.rotation.y = g.rotY
    scene.add(mesh)
    pulsos.push({ mesh, t0: performance.now(), dur: 650 })
  }

  // ---------------------------------------------------------------------
  // Enjambre
  // ---------------------------------------------------------------------
  const POOL = 260
  function texturaGlow() {
    const s = 64
    const c = document.createElement('canvas'); c.width = c.height = s
    const x = c.getContext('2d')!
    const g = x.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2)
    g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(0.35, 'rgba(255,255,255,.7)'); g.addColorStop(1, 'rgba(255,255,255,0)')
    x.fillStyle = g; x.fillRect(0, 0, s, s)
    return new THREE.CanvasTexture(c)
  }
  const positions = new Float32Array(POOL * 3)
  const colorsArr = new Float32Array(POOL * 3)
  const geoSwarm = new THREE.BufferGeometry()
  geoSwarm.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geoSwarm.setAttribute('color', new THREE.BufferAttribute(colorsArr, 3))
  const matSwarm = new THREE.PointsMaterial({
    size: 1.6 * ESCALA, map: texturaGlow(), vertexColors: true, transparent: true,
    opacity: 0.95, blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true,
  })
  const swarmPoints = new THREE.Points(geoSwarm, matSwarm)
  // El enjambre arranca con todas las partículas ocultas (esperando el primer
  // dato real) y luego se despliega lejos de ahí; si se deja el culling por
  // bounding-sphere, Three.js lo calcula una sola vez con ese estado inicial
  // degenerado y termina descartando el objeto entero en cuadros posteriores.
  swarmPoints.frustumCulled = false
  scene.add(swarmPoints)

  const COL_ENTER = new THREE.Color(0x35d07f)
  const COL_WANDER = new THREE.Color(0x9fe8ff)
  const COL_EXIT = new THREE.Color(0xffb020)

  function puntoInterior(): [number, number] {
    const ang = Math.random() * Math.PI * 2
    const r = 6 + Math.random() * (Math.min(WORLD_W, WORLD_D) * 0.36)
    return [centro.x + Math.cos(ang) * r, centro.z + Math.sin(ang) * r]
  }

  interface Particula {
    activo: boolean; estado: 'inactivo' | 'entrando' | 'vagando' | 'saliendo'
    x: number; z: number; tx: number; tz: number; vel: number; fase: number
  }
  const particulas: Particula[] = []
  for (let i = 0; i < POOL; i++) {
    particulas.push({ activo: false, estado: 'inactivo', x: 0, z: 0, tx: 0, tz: 0, vel: (6 + Math.random() * 4) * ESCALA, fase: Math.random() * 10 })
  }
  let activos = 0

  function activarUna() {
    const p = particulas.find((p) => !p.activo)
    if (!p) return
    const gIdx = Math.floor(Math.random() * GATES.length), g = GATES[gIdx]!
    p.activo = true; p.estado = 'entrando'
    p.x = g.pos[0]; p.z = g.pos[2]
    const dest = puntoInterior(); p.tx = dest[0]; p.tz = dest[1]
    activos++
  }
  function retirarUna() {
    const candidatas = particulas.filter((p) => p.activo && p.estado === 'vagando')
    if (!candidatas.length) return
    const p = candidatas[Math.floor(Math.random() * candidatas.length)]!
    const g = GATES[Math.floor(Math.random() * GATES.length)]!
    p.estado = 'saliendo'; p.tx = g.pos[0]; p.tz = g.pos[2]
  }

  // ---------------------------------------------------------------------
  // Estado (alimentado por datos REALES, no simulados)
  // ---------------------------------------------------------------------
  // Aforo maximo del recinto: todavia no hay un dato real configurado, se usa
  // un valor de referencia hasta que se defina (ver CLAUDE.md / pendientes).
  const CAP = 5000
  const st = { dentro: 0, ingresos: 0, salidas: 0, reingresos: 0, ingUlt: [] as { t: number; in: boolean }[] }
  const vistosEntrada = new Set<string>() // para contar reingresos observados en vivo

  function anim(el: HTMLElement) { el.classList.remove('pf-flash'); void el.offsetWidth; el.classList.add('pf-flash') }

  function pintar(codigo: string, tipo: string, tag: string, cls: string) {
    $('pf-k-dentro').textContent = fmt(st.dentro); anim($('pf-k-dentro'))
    $('pf-k-in').textContent = fmt(st.ingresos)
    $('pf-k-out').textContent = fmt(st.salidas)
    $('pf-k-re').textContent = fmt(st.reingresos)
    const pct = Math.min(100, Math.round((st.dentro / CAP) * 100))
    const ring = $('pf-ring')
    ring.style.setProperty('--p', String(pct))
    const col = pct < 70 ? '#35d07f' : pct < 90 ? '#ffb020' : '#ff4d6d'
    ring.style.setProperty('--c', col)
    $('pf-k-aforo').textContent = pct + '%'; $('pf-k-aforo').style.color = col
    $('pf-k-cap').textContent = 'de ' + fmt(CAP) + ' · libres ' + fmt(Math.max(0, CAP - st.dentro))

    const hace60 = Date.now() - 60000
    st.ingUlt = st.ingUlt.filter((e) => e.t > hace60)
    const ingMin = st.ingUlt.filter((e) => e.in).length
    $('pf-k-ritmo').textContent = '+' + ingMin + ' / min'

    const ul = $('pulso-feed')
    const li = document.createElement('li')
    li.className = 'pf-row ' + cls
    li.innerHTML = `<span class="pf-code">${codigo}</span><span class="pf-tag ${tag}">${tipo}</span>`
    ul.insertBefore(li, ul.firstChild)
    while (ul.children.length > 6) ul.removeChild(ul.lastChild!)
  }

  function filaInvalida(codigo: string, motivo: string) {
    const ul = $('pulso-feed')
    const li = document.createElement('li')
    li.className = 'pf-row bad'
    li.innerHTML = `<span class="pf-code">${codigo}</span><span class="pf-tag bad">${motivo === 'NO_VALIDO' ? 'No válido' : motivo === 'YA_DENTRO' ? 'Ya dentro' : 'Ya fuera'}</span>`
    ul.insertBefore(li, ul.firstChild)
    while (ul.children.length > 6) ul.removeChild(ul.lastChild!)
  }

  /** Procesa un evento REAL llegado por WebSocket (una validación de boleto en cualquier puesto). */
  function procesarEventoReal(evento: EventoBoletoDto) {
    if (evento.tipo === 'BLOQUEADO' || evento.tipo === 'NO_VALIDO') {
      filaInvalida(evento.codigo, evento.motivo ?? evento.tipo)
      // Igual se ve un pulso tenue rojo en una puerta al azar: alguien lo intentó ahí.
      lanzarPulso(Math.floor(Math.random() * GATES.length), 0xff4d6d)
      return
    }
    const esSalida = evento.tipo === 'SALIDA'
    const gIdx = Math.floor(Math.random() * GATES.length) // no sabemos la puerta real (ver nota arriba)
    const g = GATES[gIdx]!
    st.dentro = evento.dentroAhora
    let tipoTexto: string
    if (esSalida) {
      st.salidas++
      tipoTexto = 'SALE'
      retirarUna()
    } else {
      st.ingresos++
      const esReingreso = vistosEntrada.has(evento.codigo)
      if (esReingreso) st.reingresos++
      vistosEntrada.add(evento.codigo)
      tipoTexto = esReingreso ? 'REINGRESA' : 'INGRESA'
      activarUna()
    }
    lanzarPulso(gIdx, g.color)
    st.ingUlt.push({ t: Date.now(), in: !esSalida })
    pintar(evento.codigo, tipoTexto, esSalida ? 'out' : 'in', esSalida ? 'out' : '')
  }

  function clock() { $('pulso-clock').textContent = new Date().toLocaleTimeString('es-BO') }
  clock()
  const clockInterval = setInterval(clock, 1000)

  // ---------------------------------------------------------------------
  // Cámara: órbita manual (drag + rueda) con auto-rotación en reposo
  // ---------------------------------------------------------------------
  let camAz = 0.6, camPolar = 0.9, camDist = 150 * ESCALA
  let arrastrando = false, lastX = 0, lastY = 0, ultimaInteraccion = 0
  canvas.style.cursor = 'grab'

  function onDown(e: PointerEvent) {
    arrastrando = true; canvas.style.cursor = 'grabbing'
    lastX = e.clientX; lastY = e.clientY
    canvas.setPointerCapture?.(e.pointerId)
  }
  function onMove(e: PointerEvent) {
    if (!arrastrando) return
    const dx = e.clientX - lastX, dy = e.clientY - lastY
    lastX = e.clientX; lastY = e.clientY
    camAz -= dx * 0.006
    camPolar = Math.max(0.18, Math.min(1.52, camPolar - dy * 0.005))
    ultimaInteraccion = performance.now()
    ocultarHint()
  }
  function onUp() { arrastrando = false; canvas.style.cursor = 'grab' }
  canvas.addEventListener('pointerdown', onDown)
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  function onWheel(e: WheelEvent) {
    e.preventDefault()
    // Rango amplio: se puede alejar para ver todo el recinto, o acercarse mucho
    // (hasta ~18 unidades) para "meterse" entre los bloques y recorrerlo.
    camDist = Math.max(18 * ESCALA, Math.min(560 * ESCALA, camDist + e.deltaY * 0.06 * ESCALA))
    ultimaInteraccion = performance.now(); ocultarHint()
  }
  canvas.addEventListener('wheel', onWheel, { passive: false })

  const hintEl = $('pulso-hint')
  let hintOculto = false
  function ocultarHint() { if (hintOculto) return; hintOculto = true; hintEl.style.opacity = '0' }
  const hintTimeout = setTimeout(ocultarHint, 6000)

  // ---------------------------------------------------------------------
  // Bucle de animación
  // ---------------------------------------------------------------------
  const reloj = new THREE.Clock()
  function animar() {
    raf = requestAnimationFrame(animar)
    const dt = Math.min(reloj.getDelta(), 0.05)
    const ahora = performance.now()

    if (!reduced && !arrastrando && ahora - ultimaInteraccion > 2200) camAz += dt * 0.04

    const x = centro.x + camDist * Math.sin(camPolar) * Math.sin(camAz)
    const z = centro.z + camDist * Math.sin(camPolar) * Math.cos(camAz)
    const y = camDist * Math.cos(camPolar)
    camera.position.set(x, y, z)
    camera.lookAt(centro.x, 6 * ESCALA, centro.z)

    GATES.forEach((g, idx) => {
      const s = 1 + Math.sin(ahora * 0.002 + idx) * 0.04
      g.ringMesh!.scale.set(s, s, s)
    })

    for (let pi = pulsos.length - 1; pi >= 0; pi--) {
      const pu = pulsos[pi]!, edad = (ahora - pu.t0) / pu.dur
      if (edad >= 1) {
        scene.remove(pu.mesh); pu.mesh.geometry.dispose(); (pu.mesh.material as THREE.Material).dispose()
        pulsos.splice(pi, 1); continue
      }
      const sc = 1 + edad * 6.5; pu.mesh.scale.set(sc, sc, sc)
      ;(pu.mesh.material as THREE.MeshBasicMaterial).opacity = (1 - edad) * 0.9
    }

    // Densidad del enjambre: 1 punto por persona dentro, hasta el tope visual del
    // pool. Antes se escalaba contra CAP (aforo máximo) y con conteos reales chicos
    // (muy por debajo de 5.000) el redondeo daba 0-3 puntos y no se veía nada.
    const deseados = Math.min(POOL, Math.max(0, Math.round(st.dentro)))
    if (activos < deseados) activarUna()
    if (activos > deseados) {
      const cand = particulas.find((p) => p.activo && p.estado === 'vagando')
      if (cand) {
        const g2 = GATES[Math.floor(Math.random() * GATES.length)]!
        cand.estado = 'saliendo'; cand.tx = g2.pos[0]; cand.tz = g2.pos[2]
      }
    }

    for (let i = 0; i < POOL; i++) {
      const p = particulas[i]!
      const base = i * 3
      if (!p.activo) {
        positions[base] = 0; positions[base + 1] = -999; positions[base + 2] = 0
        colorsArr[base] = 0; colorsArr[base + 1] = 0; colorsArr[base + 2] = 0
        continue
      }
      const ddx = p.tx - p.x, ddz = p.tz - p.z, dist = Math.sqrt(ddx * ddx + ddz * ddz)
      let col: THREE.Color
      let velEfectiva = p.vel
      if (p.estado === 'entrando') {
        col = COL_ENTER
        if (dist < 1.2 * ESCALA) { p.estado = 'vagando'; p.fase = ahora * 0.001; const d2 = puntoInterior(); p.tx = d2[0]; p.tz = d2[1] }
      } else if (p.estado === 'saliendo') {
        col = COL_EXIT
        if (dist < 1.2 * ESCALA) { p.activo = false; activos-- }
      } else {
        col = COL_WANDER
        // Caminando dentro del recinto: mucho más lento que entrando/saliendo por
        // la puerta (antes usaba la misma velocidad y se veía correr).
        velEfectiva = p.vel * 0.3
        if (dist < 1.5 * ESCALA || Math.random() < 0.0012) { const d3 = puntoInterior(); p.tx = d3[0]; p.tz = d3[1] }
      }
      if (dist > 0.001) {
        const paso = Math.min(1, (velEfectiva * dt) / dist)
        p.x += ddx * paso; p.z += ddz * paso
      }
      const bob = (reduced ? 0.6 : 0.6 + Math.sin(ahora * 0.003 + p.fase) * 0.25) * ESCALA
      positions[base] = p.x; positions[base + 1] = bob; positions[base + 2] = p.z
      colorsArr[base] = col.r; colorsArr[base + 1] = col.g; colorsArr[base + 2] = col.b
    }
    geoSwarm.attributes.position!.needsUpdate = true
    geoSwarm.attributes.color!.needsUpdate = true

    renderer.render(scene, camera)
  }
  animar()

  // ---------------------------------------------------------------------
  // Datos reales: estado inicial + WebSocket en vivo
  // ---------------------------------------------------------------------
  try {
    const res = await resumenBoletos()
    st.dentro = res.dentro
    st.ingresos = res.ingresosTotal
    st.salidas = res.salidasTotal
    // Pinta el estado inicial sin animar ninguna puerta en particular.
    const pct = Math.min(100, Math.round((st.dentro / CAP) * 100))
    $('pf-k-dentro').textContent = fmt(st.dentro)
    $('pf-k-in').textContent = fmt(st.ingresos)
    $('pf-k-out').textContent = fmt(st.salidas)
    $('pf-k-re').textContent = fmt(st.reingresos)
    const ring = $('pf-ring'); ring.style.setProperty('--p', String(pct))
    $('pf-k-aforo').textContent = pct + '%'
    $('pf-k-cap').textContent = 'de ' + fmt(CAP) + ' · libres ' + fmt(Math.max(0, CAP - st.dentro))
  } catch {
    // Sin conexión inicial: arranca en 0 : el WebSocket lo va a ir completando.
  }

  cerrarWs = conectarBoletosWs(procesarEventoReal, (conectado) => { enVivo.value = conectado })

  limpiarEscena = () => {
    window.removeEventListener('resize', resize)
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    canvas.removeEventListener('wheel', onWheel)
    clearInterval(clockInterval)
    clearTimeout(hintTimeout)
    renderer.dispose()
  }
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  cerrarWs?.()
  limpiarEscena?.()
})

/** Carga las 3 tipografías del diseño (una sola vez, se comparten si ya estaban). */
function inyectarFuentes() {
  if (document.getElementById('pulso-fexpo-fonts')) return
  const link = document.createElement('link')
  link.id = 'pulso-fexpo-fonts'
  link.rel = 'stylesheet'
  link.href = 'https://fonts.googleapis.com/css2?family=Sora:wght@600;700;800&family=Manrope:wght@400;500;600;700&family=IBM+Plex+Mono:wght@500;600&display=swap'
  document.head.appendChild(link)
}

function volver() { router.back() }
</script>

<template>
  <div id="pulso-fexpo-root" class="pulso-fexpo">
    <canvas id="pulso-scene"></canvas>
    <div class="pf-vignette"></div>
    <div class="pf-grain"></div>

    <div class="pf-hud">
      <div class="pf-top">
        <div class="pf-brand">
          <div class="pf-mk">MONITOREO FEXPO UAP <i>V2.0</i></div>
          <div class="pf-sub">Recinto FEXPO UAP · en vivo</div>
        </div>
        <div class="pf-live">
          <button type="button" class="pf-volver" @click="volver">← Volver</button>
          <span class="pf-clock" id="pulso-clock">--:--:--</span>
          <span class="pf-pill" :class="{ conectando: !enVivo }">
            <span class="pf-dot"></span>{{ enVivo ? 'En vivo' : 'Conectando…' }}
          </span>
        </div>
      </div>

      <div class="pf-kpis">
        <div class="pf-card pf-hero"><div class="pf-lbl">Dentro ahora</div><div class="pf-num" id="pf-k-dentro">0</div><div class="pf-sub2" id="pf-k-ritmo">— / min</div></div>
        <div class="pf-card pf-ring-card"><div class="pf-ring" id="pf-ring"><b id="pf-k-aforo">0%</b></div>
          <div><div class="pf-lbl">Aforo</div><div class="pf-sub2" id="pf-k-cap">de 5.000</div></div></div>
        <div class="pf-card"><div class="pf-lbl">Ingresos</div><div class="pf-num" style="font-size:22px;color:var(--pf-cyan)" id="pf-k-in">0</div></div>
        <div class="pf-card"><div class="pf-lbl">Salidas</div><div class="pf-num" style="font-size:22px;color:var(--pf-warn)" id="pf-k-out">0</div></div>
        <div class="pf-card"><div class="pf-lbl">Reingresos</div><div class="pf-num" style="font-size:22px;color:var(--pf-magenta)" id="pf-k-re">0</div></div>
      </div>

      <div class="pf-bottom">
        <div class="pf-feed"><h4>Últimos escaneos</h4><ul id="pulso-feed"></ul></div>
      </div>
    </div>

    <div class="pf-hint" id="pulso-hint">Arrastrá para orbitar · Rueda para hacer zoom</div>
  </div>
</template>

<style>
/* Estilos GLOBALES a propósito (no "scoped"): las puertas, el feed y las
   etiquetas se crean con document.createElement, y las clases "scoped" de
   Vue no llegan a nodos creados así. Para no filtrar nada al resto de la
   app, cada selector cuelga de .pulso-fexpo y todo va prefijado "pf-". */
.pulso-fexpo {
  --pf-bg:#05070d; --pf-glass:rgba(14,19,32,.58); --pf-glass2:rgba(10,14,24,.72);
  --pf-line:rgba(255,255,255,.09); --pf-ink:#eef2ff; --pf-soft:#93a0c2; --pf-faint:#5c6a8c;
  --pf-ok:#35d07f; --pf-ok-glow:rgba(53,208,127,.45);
  --pf-warn:#ffb020; --pf-alert:#ff4d6d; --pf-cyan:#33e0ff; --pf-magenta:#ff5fd1;
  --pf-disp:'Sora',system-ui,sans-serif; --pf-body:'Manrope',system-ui,sans-serif; --pf-mono:'IBM Plex Mono',monospace;

  position: fixed; inset: 0; z-index: 45;
  background: var(--pf-bg); color: var(--pf-ink); font-family: var(--pf-body);
  -webkit-font-smoothing: antialiased; overflow: hidden;
}
.pulso-fexpo * { box-sizing: border-box; }
.pulso-fexpo #pulso-scene { position: absolute; inset: 0; display: block; touch-action: none; }
.pulso-fexpo .pf-vignette { position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(130% 100% at 50% 45%, transparent 62%, rgba(3,4,9,.4) 100%); }
.pulso-fexpo .pf-grain { position: absolute; inset: 0; pointer-events: none; opacity: .035; mix-blend-mode: overlay;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E"); }

.pulso-fexpo .pf-hud { position: absolute; inset: 0; pointer-events: none;
  padding: calc(16px + env(safe-area-inset-top,0px)) 16px calc(16px + env(safe-area-inset-bottom,0px));
  display: flex; flex-direction: column; justify-content: space-between; }

.pulso-fexpo .pf-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap; }
.pulso-fexpo .pf-mk { font-family: var(--pf-disp); font-weight: 800; font-size: 19px; letter-spacing: .01em; }
.pulso-fexpo .pf-mk i { font-style: normal; color: var(--pf-cyan); }
.pulso-fexpo .pf-sub { font-size: 11px; color: var(--pf-soft); letter-spacing: .16em; text-transform: uppercase; margin-top: 3px; }
.pulso-fexpo .pf-live { display: flex; align-items: center; gap: 10px; pointer-events: auto; }
.pulso-fexpo .pf-volver { font-family: var(--pf-body); font-size: 12px; font-weight: 600; color: var(--pf-soft);
  background: var(--pf-glass); border: 1px solid var(--pf-line); padding: 7px 12px; border-radius: 10px;
  backdrop-filter: blur(10px); cursor: pointer; }
.pulso-fexpo .pf-volver:hover { color: var(--pf-ink); border-color: var(--pf-soft); }
.pulso-fexpo .pf-clock { font-family: var(--pf-mono); font-size: 13px; color: var(--pf-soft); font-variant-numeric: tabular-nums;
  background: var(--pf-glass); border: 1px solid var(--pf-line); padding: 6px 11px; border-radius: 10px; backdrop-filter: blur(10px); }
.pulso-fexpo .pf-pill { display: inline-flex; align-items: center; gap: 7px; font-size: 11px; font-weight: 700; letter-spacing: .13em;
  text-transform: uppercase; color: var(--pf-ok); background: rgba(53,208,127,.1);
  border: 1px solid rgba(53,208,127,.35); padding: 6px 11px; border-radius: 999px; backdrop-filter: blur(10px); }
.pulso-fexpo .pf-pill.conectando { color: var(--pf-soft); background: rgba(255,255,255,.05); border-color: var(--pf-line); }
.pulso-fexpo .pf-pill.conectando .pf-dot { background: var(--pf-soft); animation: none; }
.pulso-fexpo .pf-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--pf-ok); box-shadow: 0 0 0 0 var(--pf-ok-glow);
  animation: pulsoPing 1.6s ease-out infinite; }
@keyframes pulsoPing { 0%{box-shadow:0 0 0 0 var(--pf-ok-glow)} 70%{box-shadow:0 0 0 8px transparent} 100%{box-shadow:0 0 0 0 transparent} }

.pulso-fexpo .pf-kpis { pointer-events: auto; display: flex; flex-direction: column; align-items: stretch;
  align-self: flex-start; gap: 8px; margin-top: 14px; width: 212px; max-width: 62vw; }
.pulso-fexpo .pf-card { background: var(--pf-glass); border: 1px solid var(--pf-line); border-radius: 14px;
  padding: 12px 16px; backdrop-filter: blur(14px); min-width: 118px; }
.pulso-fexpo .pf-lbl { font-size: 10.5px; letter-spacing: .12em; text-transform: uppercase; color: var(--pf-soft); }
.pulso-fexpo .pf-num { font-family: var(--pf-disp); font-weight: 800; font-variant-numeric: tabular-nums;
  line-height: 1; margin-top: 7px; letter-spacing: -.01em; font-size: 26px; }
.pulso-fexpo .pf-hero .pf-num { font-size: 38px; color: var(--pf-ok); text-shadow: 0 0 22px var(--pf-ok-glow); }
.pulso-fexpo .pf-sub2 { font-size: 10.5px; color: var(--pf-faint); margin-top: 5px; }
.pulso-fexpo .pf-flash { animation: pulsoFlash .5s ease; }
@keyframes pulsoFlash { 0%{transform:scale(1)} 35%{transform:scale(1.08)} 100%{transform:scale(1)} }
.pulso-fexpo .pf-ring-card { display: flex; align-items: center; gap: 12px; }
.pulso-fexpo .pf-ring { --p:0; --c:var(--pf-ok); width: 52px; height: 52px; border-radius: 50%; flex: none; position: relative;
  background: conic-gradient(var(--c) calc(var(--p)*1%), rgba(255,255,255,.08) 0); }
.pulso-fexpo .pf-ring::before { content:""; position: absolute; inset: 5px; border-radius: 50%; background: #0d1220; }
.pulso-fexpo .pf-ring b { position: absolute; inset: 0; display: grid; place-items: center; font-family: var(--pf-mono); font-size: 11px; font-weight: 600; }

.pulso-fexpo .pf-bottom { pointer-events: auto; display: flex; align-items: flex-end; justify-content: flex-end; gap: 12px; flex-wrap: wrap; }

.pulso-fexpo .pf-feed { width: 270px; max-width: 36vw; background: var(--pf-glass2); border: 1px solid var(--pf-line);
  border-radius: 14px; padding: 10px; backdrop-filter: blur(16px); }
.pulso-fexpo .pf-feed h4 { margin: 2px 6px 8px; font-family: var(--pf-disp); font-size: 11.5px; font-weight: 700;
  letter-spacing: .1em; text-transform: uppercase; color: var(--pf-soft); }
.pulso-fexpo .pf-feed ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.pulso-fexpo .pf-row { display: flex; align-items: center; gap: 9px; background: rgba(255,255,255,.03);
  border-left: 2.5px solid var(--pf-ok); border-radius: 8px; padding: 7px 9px; animation: pulsoSlide .3s ease; }
.pulso-fexpo .pf-row.out { border-left-color: var(--pf-warn); }
.pulso-fexpo .pf-row.bad { border-left-color: var(--pf-alert); }
@keyframes pulsoSlide { from{opacity:0;transform:translateY(-6px)} to{opacity:1;transform:none} }
.pulso-fexpo .pf-code { font-family: var(--pf-mono); font-weight: 600; font-size: 12px; }
.pulso-fexpo .pf-tag { margin-left: auto; font-size: 9.5px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase;
  padding: 2.5px 7px; border-radius: 999px; white-space: nowrap; }
.pulso-fexpo .pf-tag.in { color: var(--pf-ok); background: rgba(53,208,127,.12); }
.pulso-fexpo .pf-tag.out { color: var(--pf-warn); background: rgba(255,176,32,.12); }
.pulso-fexpo .pf-tag.bad { color: var(--pf-alert); background: rgba(255,77,109,.12); }

.pulso-fexpo .pf-hint { position: absolute; left: 50%; bottom: 18px; transform: translateX(-50%); pointer-events: none;
  font-size: 12px; color: var(--pf-soft); background: var(--pf-glass); border: 1px solid var(--pf-line);
  padding: 7px 14px; border-radius: 999px; backdrop-filter: blur(12px); transition: opacity .6s ease; }

@media (max-width: 720px) {
  .pulso-fexpo .pf-kpis { gap: 8px; }
  .pulso-fexpo .pf-card { min-width: 100px; padding: 10px 12px; }
  .pulso-fexpo .pf-hero .pf-num { font-size: 30px; }
  .pulso-fexpo .pf-feed { width: 210px; max-width: 50vw; }
  .pulso-fexpo .pf-kpis { width: 170px; }
  .pulso-fexpo .pf-bottom { align-items: center; }
}
@media (prefers-reduced-motion: reduce) {
  .pulso-fexpo .pf-dot { animation: none; }
  .pulso-fexpo .pf-flash { animation: none; }
}
</style>
