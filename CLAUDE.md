# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Sistema de Control de Tickets de Entrada (QR)

> Cerebro del proyecto. Lee esto primero antes de trabajar en el repo.
> Un escritor a la vez (esta sesión de Claude Code o VS Code, no ambos a la vez).

---

## 1. Qué es

Sistema para **controlar el ingreso de personas a un evento/recinto mediante
tickets con QR único y código de identificación**. Tres categorías de asistentes:

| Categoría        | Alta de datos            | Campos del ticket                                    |
|------------------|--------------------------|-----------------------------------------------------|
| **Estudiante**   | Carga masiva por **CSV** | Nombre completo + **RU** + **CI** + carrera/facultad |
| **Administrativo** | Carga masiva por **CSV** | Nombre completo + **CI** + **código administrativo** |
| **Docente**      | Carga masiva por **CSV** | Nombre completo + **CI** + **código docente**        |
| **Externo** (particular) | Manual (venta puntual) | Nombre completo + **CI**                       |

Requisitos clave:
- El **QR** y el **ID de identificación** deben ser **únicos** por ticket.
- **Control de ingresos**: registrar entradas/salidas al escanear el QR.
- **Monitoreo en tiempo real**: ver quiénes están dentro y quiénes no.
- Externos: el ticket **se vende** y los nombres se cargan a mano.
- El diseño del ticket (arte) lo provee el usuario; nosotros rellenamos los campos.

---

## 2. Estado actual (fases)

### ✅ FASE 1 — Fundación (COMPLETA y verificada)
Autenticación completa + administración de accesos. Es la base de seguridad
sobre la que se montará todo lo demás.

- Seguridad JWT stateless + BCrypt + roles (misma arquitectura que `esccuela-tecnica`).
- CRUD de **Persona**.
- CRUD de **Usuario** (crear, editar, eliminar lógico, bloquear/desbloquear,
  restablecer contraseña, asignar/quitar roles).
- CRUD de **Rol**.
- Frontend **Vue 3 + Vite + TypeScript** con login (estilo institucional, logo UAP)
  y las 3 pantallas CRUD.
- Verificado end-to-end: login → 200, sin token → 403, con token → 200,
  validaciones de negocio → 400.

### 🟡 FASE 2 — Dominio de tickets (EN PROGRESO)
Hecho:
- **Entidades de dominio creadas**: `Estudiante` (persona + `ru` único + `facultad`
  + `carrera`), `Administrativo` (persona + `codigoAdministrativo` único),
  `Particular` (persona + `montoVenta`). Cada una referencia a `Persona` (patrón
  escuela-tecnica) y hereda auditoría/estado. Repositorios (Dao) creados.
- Tablas `estudiante`, `administrativo`, `particular` verificadas en la BD.
- `Persona` simplificada: se quitaron `correo`, `celular` y `fechaNacimiento`
  (innecesarios para el ticket). Quedan: nombre, paterno, materno, ci, genero.
- **Entidades de control y validación creadas y verificadas en BD**:
  - `Ticket` — `categoria`, `codigoIdentificacion` (único), `qrToken` (único),
    `dentro` (flag para monitoreo), FK a `Persona` + FKs opcionales a
    `Estudiante`/`Administrativo`/`Particular` (solo una según la categoría).
  - `Acceso` — log de validaciones: FK a `Ticket`, `tipo` (ENTRADA/SALIDA),
    `fechaHora`. El usuario CONTROL que escanea queda en la auditoría.
  - Enums `CategoriaTicket` (ESTUDIANTE/ADMINISTRATIVO/EXTERNO) y `TipoAcceso`.
  - Repositorios: `TicketDao` (findByQrToken, findByCodigoIdentificacion,
    findAllByDentroTrueAndEstado, counts…) y `AccesoDao` (historial por ticket,
    último movimiento, historial global).
- **Plantillas del ticket** movidas a `src/main/resources/plantillas/`:
  `ticket-estudiante-plantilla.png` (en blanco, para rellenar) y
  `ticket-estudiante-modelo.png` (ejemplo lleno). Diseño: FEXPO UAP v2.0, campos
  a rellenar = CARRERA, R.U., NOMBRE COMPLETO, CÓDIGO TICKETS + el QR.

- **Generador de QR (ZXing) listo y probado**: `Utils/qr/QrGenerator` (`generarPng`
  y `generarImagen`, corrección de errores nivel M) + endpoint de prueba
  `GET /api/qr/generar?contenido=&tamano=` (rol ADMINISTRADOR o CONTROL) que
  devuelve un PNG. Verificado: 403 sin token, PNG 320x320 válido con token.
- **Renderizador de tickets (estudiante) listo y probado**: `Utils/ticket/TicketRenderer`
  rellena la plantilla PNG con los datos + incrusta el QR y exporta a **PNG y PDF**
  (OpenPDF, paquete `org.openpdf.text`). Texto centrado y auto-ajustado por caja.
  Coordenadas de los recuadros (plantilla 2524x839): CARRERA (520,64,704,68),
  CÓDIGO arriba-der (2144,64,312,68), NOMBRE (1252,148,1208,68), R.U. (856,160,348,52),
  QR (1344,420,264,260), y **CÓDIGO en el talón izquierdo VERTICAL** (343,32,88,366)
  — el mismo código se imprime en ambos extremos. Endpoints de demo (datos de prueba):
  `GET /api/tickets-demo/estudiante.png` y `.../estudiante.pdf`. Verificado
  visualmente: todos los campos y el QR quedan bien ubicados.

- **Estudiantes end-to-end (backend) listo y probado**:
  - Importación **CSV** (`POST /api/estudiantes/importar`, multipart campo `archivo`).
    Columnas por POSICIÓN fija: **`ru, nombre completo, ci, carrera`** (así lo genera
    Javier; nada más). El nombre completo va en un solo campo → se guarda en
    `Persona.nombre` con `paterno=""`. Separador `,`/`;` autodetectado.
  - **Codificación**: Excel exporta CSV en tres codificaciones según la opción que
    se elija al guardar (UTF-8, Windows-1252 y **CP850** en "CSV (MS-DOS)"), y el
    archivo no dice cuál es. Se intenta UTF-8 **estricto**; si falla, se decodifica
    con cada candidata y gana la que produce más letras castellanas y menos símbolos
    raros (`puntuar()`). Adivinar mal no da error, da texto mojado: un CP850 leído
    como Windows-1252 da `Ingenier¡a`, y leído como UTF-8 destruye el byte (queda el
    carácter de reemplazo y **eso ya no se recupera**).
  - **Encabezado**: no se compara contra el literal "ru" (el archivo real trae
    "R.U."). La regla es que el RU de un estudiante siempre tiene dígitos; si la
    primera celda no tiene ninguno, es un rótulo y se salta.
  - **Reimportar corrige**: subir de nuevo el padrón **actualiza** los estudiantes
    que ya existen (se reconocen por el `ru`) en vez de fallar, y conserva los
    tickets ya emitidos. El `ru` tiene UNIQUE en la BD, así que una fila con borrado
    lógico lo sigue ocupando: por eso el alta revive esa fila en vez de insertar
    otra, y la `Persona` (que se busca por CI) se reescribe con el nombre del
    archivo. El alta **individual** sigue rechazando un RU repetido: solo la
    importación actualiza (`crearEstudiante(dto, actualizar)`).
  - Devuelve `{totalFilas, creados, actualizados, errores}`.
  - CRUD: `GET /listar`, `GET /obtener`, `POST /crear`, `DELETE /eliminar`.
  - **Emisión de ticket**: `POST /api/tickets/emitir-estudiante?idEstudiante=`
    crea el `Ticket` con `codigoIdentificacion` (EST-000001…) + `qrToken` (UUID)
    únicos. Es **idempotente** (si ya tiene ticket, lo devuelve).
  - **Generación desde BD**: `GET /api/tickets/{id}/png` y `.../{id}/pdf` rinden
    el ticket con los datos guardados + QR. `GET /api/tickets/listar`, `/obtener`.
  - Verificado: import 3/3, emisión EST-000001/EST-000002, PNG/PDF correctos.

- **Pantalla frontend de estudiantes lista** (`views/Estudiantes.vue`, ruta
  `/estudiantes` en el menú): importar CSV, listar, alta individual, emitir ticket,
  ver (PNG en modal) y descargar (PDF). Services `estudiante.service.ts` +
  `ticket.service.ts`; tipos `estudiante.type.ts` + `ticket.type.ts`. Los endpoints
  imagen/PDF se piden con axios `responseType:'blob'` + `URL.createObjectURL`
  (no `<img src>` directo, por el token JWT). `npm run build` OK.

- **Administrativos end-to-end (backend + pantalla) listo, a la par de estudiantes**:
  CRUD + emisión de ticket (ADM-000001…, idempotente) + importación CSV con la MISMA
  lógica que estudiante (autodetección de codificación, **previsualizar** y
  **reimport que actualiza**). Reconoce por `codigoAdministrativo`; detección de
  encabezado por el CI numérico (el código puede no serlo). Pantalla con dropzone,
  vista previa, filtro con/sin ticket y "generar faltantes". Falta el arte del ticket.

- **Docentes end-to-end (backend + pantalla): CALCADO de administrativo**. Entidad
  `Docente` (`codigoDocente` único), CRUD + CSV (codificación/preview/reimport) +
  emisión `DOC-000001…`, `Docentes.vue` (`/docentes`), pestaña en Impresión. CSV:
  `codigo docente, nombre completo, ci`. Falta el arte del ticket (como administrativo).
  > ⚠️ **CHECK constraint al sumar una categoría.** El enum `CategoriaTicket` se guarda
  > como STRING y Hibernate le puso un CHECK (`ticket_categoria_check`) con los valores
  > que existían al crear la tabla. `ddl-auto=update` **NO** actualiza ese CHECK, así
  > que insertar un ticket de la categoría nueva falla con
  > `violates check constraint "ticket_categoria_check"`. Hay que recrearlo a mano:
  > `ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_categoria_check;`
  > `ALTER TABLE ticket ADD CONSTRAINT ticket_categoria_check CHECK (categoria IN ('ESTUDIANTE','ADMINISTRATIVO','DOCENTE','EXTERNO'));`
  > Ya aplicado en la BD local; **en producción (ddl-auto=validate) hay que correrlo al desplegar.**
  >
  > ⚠️ **Mismo tipo de trampa con los boolean NOT NULL.** Un `@Column(nullable = false)`
  > sin `columnDefinition` genera `add column x boolean not null` **sin default**, y
  > Postgres lo RECHAZA si la tabla ya tiene filas: la columna no se crea y todas las
  > consultas de esa entidad pasan a dar 500. Le pasó a `Estudiante.tieneHuella`
  > (2026-09-17, tumbó Estudiantes/Impresión/Entrega). **Siempre**
  > `columnDefinition = "boolean not null default false"`, como `Ticket.dentro/impreso/entregado`.
  > `ddl-auto=update` falla en silencio: deja un WARN en el arranque y sigue.

- **Control de ENTREGA de tickets**: marcar a quién se entregó el ticket físico. Campos
  `Ticket.entregado` + `fechaEntrega` (quién marcó queda en la auditoría);
  `PATCH /api/tickets/entrega?idTicket=&entregado=`. Pantalla **`Entrega.vue`**
  (`/entrega`): resumen, buscador, filtros por categoría y por estado de entrega, y
  botón marcar/deshacer por fila. Es un control aparte de la impresión (un ticket
  puede estar impreso pero sin entregar).

- **Emisión masiva por LOTES con barra de progreso** (`ProgresoModal`): "Generar
  tickets" emite solo los que faltan y en lotes (estudiantes de a 25) para no colgar el
  servidor. Antes, "todos" mandaba sin lista y el backend recorría los ~7000 en una
  sola petición → timeout. La impresión muestra un modal indeterminado "Generando
  pliego…" con la cantidad.
  > ⚠️ **OOM al imprimir TODO de una categoría grande.** Armar un PDF con miles de
  > tickets (JPEG 300 DPI) supera el límite de array de Java (~2 GB) y **tumba el
  > backend** (`Required array length ... too large`). Imprimir por carrera/tanda anda
  > bien. Pendiente: poner un tope al botón "imprimir todos".

- **Toda la parte delicada del CSV extraída a `Utils/csv/CsvUtils`** (codificación
  UTF-8 estricto → windows-1252/CP850 por puntaje, separador, BOM, normalización,
  aviso de texto mal codificado). La comparten estudiante y administrativo; ya no
  está duplicada.

- **Impresión por CATEGORÍA** (cada categoría tiene arte distinto, no se mezclan en
  una hoja): `resumenImpresion`, `generarPliego` y `reiniciarImpresion` acotados a
  una `CategoriaTicket`. `ResumenImpresionDto` trae `plantillaDisponible` (hoy solo
  ESTUDIANTE). Generar el pliego de una categoría sin arte da 400 con mensaje claro,
  en vez de reventar. La pantalla `Impresion.vue` tiene **pestañas Estudiantes /
  Administrativos / Particulares**, columnas según la categoría y cartel "plantilla
  pendiente" para las que aún no tienen arte. Verificado: pliego estudiante 200/PDF,
  pliego administrativo 400 amable.

- **Componentes/base UI reutilizables del frontend** (ver sección 3): `ModalBase`,
  `Alerta` (banner inline) + `AlertasHost` (toasts) con `useAlertas`,
  `ConfirmDialog` con `useConfirmacion`, `CsvDropzone` y `utils/errores`. Ya no queda
  ningún `alert()`/`confirm()`/`prompt()` nativo ni markup de modal a mano; todas las
  vistas los usan.

- **Impresión por carrera y en el orden de la tabla**: filtro de carrera (acota el
  avance, el pliego y la tabla), botón "Imprimir todos los no impresos" y tanda por
  hojas como alternativa. El PDF sale en el orden en que se ve la tabla (clic en un
  encabezado para ordenar). Barra superior y menú fijos: solo scrollea el contenido.

- **Módulo CONTROL (escáner) listo** (mergeado desde la rama `control`):
  `ControlController` (`/api/control`) + `ControlServiceImpl` + vista
  `views/control/ControlValidador.vue` (ruta `/control`, componentes `PanelEscaneo`
  y `ConsultaRu`). **Escáneres dedicados**: cada escáner es de ENTRADA o de SALIDA
  (`tipoMovimiento`), no alterna solo. Reglas en `validar()` (`@Transactional`):
  - Busca el ticket por `qrToken` en la BD local (404 si no existe).
  - "Anti-clones": ENTRADA estando `dentro` → `YA_DENTRO`; SALIDA estando fuera →
    `YA_FUERA`. Responde **409** con `ValidacionTicketDto` (`bloqueado=true`,
    `motivo`) y **no escribe nada**.
  - Solo **ESTUDIANTE al ENTRAR** consulta la matrícula en **SIGSE** (API externa de
    la UAP); si no está matriculado → 409 `NO_MATRICULADO`. Si SIGSE no responde →
    `NegocioException` (400): la entrada queda bloqueada. La SALIDA no consulta SIGSE.
  - Si pasa: `Ticket.dentro` = (tipo == ENTRADA) + fila en `Acceso`.
  - Integración SIGSE en el paquete **`apivalidacaion`** (sic, así se llama):
    `ApiService` hace POST con header `x-api-key` a `sigse.url`/`sigse.api-key`
    (`SigseProperties`, `RestTemplate`).

- **Módulo HUELLAS (biométricos ZKTeco) listo**: traer los templates de huella
  de uno o varios equipos y marcar al estudiante como "con huella".
  - El **PIN del equipo es el RU**: se cruza con `EstudianteDao.findByRu`.
  - **Intermediario Python (pyzk)**: por defecto el backend NO habla directo al
    equipo, sino que ejecuta scripts Python (`src/main/resources/biometria/`:
    `zk_probar.py`, `zk_descargar.py`) que usan la librería **pyzk** y devuelven
    JSON por stdout. `PythonZktecoDriver` los corre con `ProcessBuilder`
    (timeout = timeout del equipo + 120 s), traduce los errores a castellano y
    elige el ejecutable con `app.biometria.python` (default `python3`, con
    fallback automático a `python`). Los scripts viajan dentro del jar
    (`BiometriaScripts` los extrae a un temporal al arrancar). Requisito del
    servidor: **Python 3 + `pip install pyzk`**.
  - `app.biometria.driver`: `python` (default) o `java` (el `ZktecoTcpDriver`
    propio queda como respaldo: frame TCP `0x5050, 0x7D82, largo` + cabecera LE
    + checksum, CONNECT → AUTH(`make_commkey`) → DISABLE → bloques → ENABLE →
    EXIT). `BiometricoDriverSelector` elige (simulación primero).
  - **Clave de comunicación**: `DispositivoBiometrico.claveComunicacion` (solo
    dígitos, opcional; en blanco al editar conserva la guardada y nunca sale
    al front, solo `tieneClave`). Si el CONNECT vuelve `UNAUTH` se manda
    `CMD_AUTH` con `make_commkey(clave, sesion)` (ticks=50); sin clave cargada
    el error lo dice, y con clave mala avisa "contraseña incorrecta".
  - Lecturas en bloque (una descarga por equipo, no una por dedo): usuarios con
    `CMD_USERTEMP_RRQ + int(5)` (registro exacto de 72 bytes
    `uid:u16, priv, pass:8, nombre:24, card:u32, x, grupo:7, x, pin:24`; el PIN
    son los últimos 24 bytes; si el tamaño difiere hay respaldo heurístico por
    "string de dígitos") y templates con `CMD_DB_RRQ + int(2)`
    (`[tam:u16, uid:u16, dedo:s8, valido:s8, template…]`), con respaldo por
    comando no documentado 88 (uid:i16 + dedo:i8) si el bloque no anda.
  - **Modo simulación** (`app.biometria.simulacion`, default `false` en el
    properties base): `SimulacionBiometricoDriver` devuelve 4 usuarios
    inventados (RU 1001 con huella, 1002 sin huella, 9999 inexistente, 1001
    repetido) para probar barra + reporte sin equipo. Con `true` se prueba el
    flujo entero en local.
  - Tablas: `dispositivo_biometrico` (nombre/IP/puerto/timeout/activo),
    `huella_digital` (estudiante + dedo 0-9 + template Base64 TEXT + equipo
    origen; UNIQUE por estudiante+dedo), `sincronizacion_huella` (job con
    contadores) + `sincronizacion_huella_detalle` (una fila por RU con
    `resultado` = CORRECTO/DUPLICADO/NO_ENCONTRADO/SIN_HUELLA/ERROR).
    `Estudiante` sumó `tieneHuella` + `fechaHuella` (flag rápido para filtros).
  - El job corre en **2º plano** (`@Async` pool `huellas`, `AsyncConfig`, un job
    por vez): publica el avance en **WS `/topic/huellas/{jobId}`** (+ polling a
    `/huellas/progreso` de respaldo) y guarda **una transacción chica por RU**
    (`HuellaUsuarioProcesador`, mismo patrón que la emisión masiva: un RU que
    falla no tumba el lote). Un equipo caído no frena a los demás (queda en
    `mensajeError`). Se puede **cancelar** desde la pantalla.
  - Frontend: `views/Huellas.vue` (`/huellas`, en el menú Tickets QR): ABM de
    equipos + probar conexión (diagnóstico) + sincronizar con checkboxes +
    `ProgresoModal` en vivo + reporte con 5 contadores y filtro por resultado +
    historial. `Estudiantes.vue` sumó columna ✓ y filtro Con/Sin huella.
  - ⚠️ **A calibrar contra el equipo real**: el protocolo pull no está
    documentado por ZKTeco y varía por firmware. `POST
    /api/biometricos/probar-conexion` dice en qué paso falla; ajustar
    `pedirTemplateSsr`/`pedirTemplateClasico`/`parsearUsuarios` según lo que
    responda el equipo (ver comentario arriba del driver).

Pendiente:
- **Arte (plantillas PNG) de administrativo y particular** + generalizar
  `TicketRenderer` (hoy `pdfPliegoEstudiantes`/`DatosTicketEstudiante` tienen
  incrustada la de estudiante) y poner `plantillaDisponible()` en `true` para ellas.
- **Flujo de particular/externo** (venta manual): NO existe emisión (`emitirParticular`)
  ni pantalla todavía. La pestaña Particulares en Impresión ya está, pero sin tickets.
- **Monitoreo en tiempo real**: ya existe `GET /api/control/dentro` (lista de
  quienes tienen `dentro=true`), pero sin push. Decisión pendiente entre SSE o polling.

- **Validaciones de jornada (hechas y verificadas)**. Ver §8.2.

---

## 3. Stack tecnológico

Copiado de `esccuela-tecnica` (misma base, mismo sistema de seguridad).

**Backend**
- Java 21, Spring Boot 4.0.6, Maven (wrapper `./mvnw`).
- Spring Web, Spring Data JPA, PostgreSQL.
- Spring Security + JWT (`io.jsonwebtoken` 0.12.6), BCrypt.
- ModelMapper, springdoc-openapi (Swagger), Apache POI (CSV/Excel).
- **ZXing** 3.5.3 (QR) y **OpenPDF** 3.0.5 (PDF) — añadidos para la fase 2.
- Lombok.

**Frontend**
- Vue 3 + Vite 6 + **TypeScript**, Vue Router 4, Axios. Sin monorepo pesado (a
  propósito, para que sea fácil de entender). Todo comentado en español.
- Convenciones tomadas de escuela-tecnica: `src/types/*.type.ts` (interfaces que
  reflejan los DTOs), `src/api/*.service.ts` (una función por endpoint, tipada),
  `src/store/auth.ts`. Alias `@` → `src`. Logo UAP en `public/logo.png`.
- **`views/Impresion.vue`** (ruta `/impresion`) — impresión agrupada **por categoría**
  (pestañas Estudiantes/Administrativos/Particulares): avance de la tirada, generación
  del pliego por tandas y marca impreso/pendiente por ticket. Columnas según la
  categoría; cartel "plantilla pendiente" cuando no hay arte.
- **Scroll**: `App.vue` ocupa `100vh`; la barra superior y el menú quedan fijos y
  el **único** elemento con scroll es `<main class="contenido">`, no `window`. Si una
  vista necesita llevar algo a la vista, lo hace dentro de ese elemento.

**Componentes y composables reutilizables** (usarlos en vez de reinventar; están
todos comentados en español):
- **`components/TablaDatos.vue`** — tabla reutilizable (buscador + paginación +
  estados de carga/vacío). La usan Estudiantes, Administrativos, Personas, Usuarios,
  Roles e Impresión; **toda lista nueva debería usarla en vez de escribir un
  `<table>` a mano**. Es genérica (`generic="T"`): los slots reciben la fila tipada.
  Se le pasan `columnas` (`ColumnaTabla[]`) y `filas`; se personaliza una celda con
  el slot `#col-<clave>` y los botones con `#acciones`, y se agregan filtros propios
  con `#herramientas`. El buscador recorre **todas** las columnas y compara sin
  tildes ("pena" encuentra "PEÑA"). Trae su propia `.card`: no envolverla en otra.
  **Ordena** con clic en el encabezado (A→Z, Z→A, original; `ordenable: false` en la
  columna o en la tabla lo apaga). El orden elegido se lee con `v-model:orden` y se
  aplica con `utils/orden.ts → ordenarFilas()` (collator `es`, numérico, sin tildes;
  la Ñ va después de la N). Si otra parte necesita el MISMO orden que la tabla, usar
  esa función y no otra: Impresión la usa para mandar el orden al pliego.
- **`components/ModalBase.vue`** — cáscara de modal (fondo, cerrar por click-afuera y
  **ESC**, slots `titulo`/cuerpo/`pie`). Reemplaza todo el markup `.modal-fondo` a mano.
  Truco de formularios: el `<form id="x">` va en el cuerpo y el botón submit en `#pie`
  con `form="x"` (el pie está fuera del form).
- **Alertas propias** (no usar `alert()`): **toasts** con `useAlertas()` +
  `<AlertasHost>` (montado una vez en `App.vue`) para resultados de acciones
  (`alertas.exito/error/info(...)`), y **banner inline** `<Alerta tipo="error|exito|info">`
  para validaciones/resultados dentro de la página.
- **Confirmación propia** (no usar `confirm()`): `useConfirmacion()` →
  `if (await confirmar({ titulo, mensaje, peligro }))`, con `<ConfirmDialog>` montado
  una vez en `App.vue`.
- **`components/CsvDropzone.vue`** — selector de archivo con arrastrar-y-soltar +
  validación `.csv` + ficha del archivo. `v-model` con el `File`; emite `elegido`/
  `quitado`. Lo usan Estudiantes y Administrativos; el botón "Importar" va en su slot
  `#acciones`.
- **`components/ProgresoModal.vue`** — modal de progreso para procesos largos (emisión
  en lote, generación de PDF). Barra + "X de Y" + %, o `indeterminado` (barra animada
  sin conteo) para procesos opacos como armar un PDF de una sola llamada. No se cierra
  mientras corre. Para que el contador se vea subir, el proceso tiene que avanzar en
  varios pasos (por eso la emisión va en lotes chicos).
- **`utils/errores.ts` → `mensajeError(e, def)`** — saca el mensaje legible de un error
  de axios (`data.mensaje` o los `campos` de validación). Único, ya no duplicado.
- Los **hosts globales** (`AlertasHost`, `ConfirmDialog`) están en `App.vue`, siempre
  montados. Los composables son singletons a nivel de módulo (importar y usar).

---

## 4. Arquitectura (capas del backend)

Paquete base: **`com.uap.control_tickets`** (bajo `src/main/java/`).

Flujo de una petición: **Controller → Service (interface + impl) → Repository (Dao) → BD**.
Los datos que entran/salen viajan como **DTOs**, nunca como entidades crudas.

```
controllers/   Reciben HTTP, validan (@Valid), aplican permisos (@PreAuthorize).
services/
  interfaces/  El "contrato" (qué operaciones existen).
  impl/        La lógica real (reglas de negocio, transacciones).
models/
  entity/      Tablas (JPA). Todas heredan de AuditoriaConfig.
  repository/  Interfaces JpaRepository (CRUD gratis + query methods).
dto/           Objetos de transferencia (entrada y salida) por módulo.
config/        Auditoría, WebConfig (/api), AdminInitializer, seguridad.
config/security/  JwtService, JwtAuthenticationFilter, SecurityConfig, AutorizacionService.
exception/     Excepciones de negocio + GlobalExceptionHandler.
enums/         EstadoRegistro (borrado lógico), Genero, CategoriaTicket, TipoAcceso,
               FormatoPliego (medidas del pliego de impresión, ver 8.1).
Utils/         Utilidades sin estado (ojo: U mayúscula, es el nombre real del paquete).
  qr/          QrGenerator (ZXing).
  ticket/      TicketRenderer (rellena la plantilla PNG y exporta PNG/PDF) +
               DatosTicketEstudiante (los campos que se imprimen).
  csv/         CsvUtils (codificación/separador/BOM/normalización del CSV, compartido
               por las importaciones de estudiante y administrativo).
apivalidacaion/  Cliente de SIGSE (matrícula por RU). Rompe la convención: su propio
               controller (`Api`), `Service/` y `Dto/` con mayúscula, sin interface.
controllers/control/  ControlController (escáner). services: ControlService(Impl).
```

**El prefijo `/api` NO se escribe en los controllers**: `WebConfig` se lo agrega a
todo `@RestController` con `addPathPrefix`. Un controller declara
`@RequestMapping("/tickets")` y el cliente llama `/api/tickets/...`.

**Las plantillas del ticket son recursos del classpath**
(`src/main/resources/plantillas/*.png`), así que viajan dentro del jar; se leen con
el ClassLoader, no con rutas del disco.

Ver **docs/GUIA-COMPRENSION.md** para el recorrido detallado de cada pieza.

---

## 5. Sistema de seguridad (idéntico a escuela-tecnica)

- **Stateless**: no hay sesiones en el servidor. Cada request lleva su JWT en
  `Authorization: Bearer <token>`.
- **Login** (`POST /api/auth/login`) valida credenciales con el
  `AuthenticationManager` y devuelve el token + roles.
- **JwtAuthenticationFilter** corre antes de cada request: valida el token,
  recarga el usuario **desde la BD** (fuente de verdad) y lo pone en el
  `SecurityContext`.
- **Autorización por método** con `@PreAuthorize("hasRole('ADMINISTRADOR')")`.
  Para reglas de "es el dueño del recurso" se usa `@autorizacionService.xxx(...)`,
  que siempre resuelve la identidad desde el usuario logueado, **nunca** desde
  datos del cliente.
- **Passwords** siempre hasheados con BCrypt; nunca salen en el JSON (`@JsonIgnore`).
- **Borrado lógico**: `_estado = ELIMINADO` en vez de DELETE físico.
- **Auditoría automática**: cada tabla guarda quién y cuándo creó/modificó la fila.
- **CORS**: solo se permite el origen del frontend (properties).
- Rutas públicas: `/api/auth/**` y Swagger. Todo lo demás exige token.

**Roles del sistema** (se crean solos en `AdminInitializer`): `ADMINISTRADOR`,
`CONTROL_CONCIERTO` (escáner del concierto), `CONTROL_FERIA` (escáner de la feria)
y `VENTA_FERIA` (vendedora de talonarios).

> ⚠️ **Todo rol nuevo hay que sumarlo a `rutaInicio()` en `router/index.ts`.** Si no
> tiene pantalla de inicio cae al `'/'`, que es solo de ADMINISTRADOR, y la guarda lo
> devuelve a `'/'` → **bucle infinito de redirección**. Ya pasó con `VENTA_FERIA`
> (2026-09-17). El fallback ahora cierra sesión en vez de girar, pero igual hay que
> mapear el rol.

---

## 6. Cómo correr (entorno local de Javier)

**Requisitos:** Java 21, PostgreSQL corriendo, Node 20+.

### Base de datos
Ya está creada: `bd_control_tickets_v1`. Si hay que recrearla:
```
PGPASSWORD=<clave> psql -h 127.0.0.1 -U <usuario> -d postgres -c "CREATE DATABASE bd_control_tickets_v1;"
```
> La conexión real la manda `application-jarv.properties` (no está en el repo).
> **No asumas el usuario/clave: leelos de ahí**, cambian entre máquinas.
> Para entrar con `psql` usá el mismo usuario/clave que el properties y
> **`-h 127.0.0.1`**: con `-h localhost` la resolución IPv6 puede pegarle a otra
> regla del `pg_hba` y fallar. Ojo que hay 4 clusters instalados (12, 16, 17, 18);
> el que usa el proyecto es el del puerto del properties (5432 = v16).

### Backend (puerto 9600)
```
cd ~/Descargas/control-tickets   # raíz real del proyecto en esta máquina
./mvnw spring-boot:run -Dspring-boot.run.profiles=jarv
```
- Swagger: http://localhost:9600/swagger-ui.html
- Usuario inicial: el que definan `app.admin.username` / `app.admin.password`
  en `application-jarv.properties` (ese archivo no está en el repo).

Otros comandos de build/verificación (Maven wrapper):
```
./mvnw compile          # verificación rápida de compilación
./mvnw clean package    # construye el jar
./mvnw test             # NO hay tests todavía: src/test/java existe pero está vacío.
                        # Para correr un test suelto una vez que existan:
                        #   ./mvnw test -Dtest=NombreDeLaClase#nombreDelMetodo
```

> **El perfil `jarv` no es opcional.** `application.properties` apunta a una BD de
> relleno (`.../NOMBRE_BD`), con usuario/clave vacíos, `ddl-auto=validate` y puerto
> 8080; sin `-Dspring-boot.run.profiles=jarv` el arranque falla. Lo mismo vale para el jar: `java -jar target/*.jar --spring.profiles.active=jarv`.

> `.vscode/launch.json` (config "Spring Boot-ControlTicketsApplication") apunta a un
> `envFile` `${workspaceFolder}/.env` que **no existe** y no fija el perfil, así que
> ese botón de VS Code no arranca la app. Usar `./mvnw spring-boot:run` como arriba.

### Frontend (puerto 5900)
```
cd ~/Descargas/control-tickets/frontend
npm install      # solo la primera vez
npm run dev
npm run typecheck  # solo vue-tsc, sin compilar (chequeo rápido)
npm run build      # vue-tsc -b && vite build · npm run preview sirve ese build
```
- App: http://localhost:5900
- **El backend tiene que estar levantado**: `vite.config.ts` proxea `/api` →
  `http://localhost:9600`, y `api/http.ts` usa `baseURL: '/api'` (relativo). No hay
  variable de entorno con la URL del backend; si se cambia el puerto 9600 hay que
  tocar el proxy. En producción el frontend debe servirse detrás de algo que
  mapee `/api` al backend.
- **`vite.config.js` y `vite.config.d.ts` no se editan a mano**: los emite
  `vue-tsc -b` desde `vite.config.ts` (porque `tsconfig.node.json` es `composite`
  sin `noEmit`) y están versionados. Vite carga el **`.js` antes que el `.ts`**, así
  que un cambio en `vite.config.ts` no toma efecto en `npm run dev` hasta que
  `npm run build` regenera el `.js`.
- Gestor de paquetes: **npm** (`package-lock.json`). Quedan restos de pnpm
  (`pnpm-lock.yaml`, más viejo que el de npm, y `pnpm-workspace.yaml` con un valor
  sin completar); no mezclar gestores.

---

## 7. Configuración y perfiles

- `application.properties` → base (para el servidor): credenciales **vacías** y la
  URL de la BD con un nombre de relleno (`NOMBRE_BD`).
- `application-jarv.properties` → entorno local de Javier. **Está en `.gitignore`,
  NO se sube** (tiene la clave de BD y el secreto JWT).
- Perfil activo en local: `jarv`. En servidor se usará otro perfil.

> ⚠️ **Ojo con el nombre del archivo local.** Spring carga el perfil `jarv` desde
> `application-jarv.properties` (con doble `p`) y `.gitignore` solo ignora ese
> nombre correcto (`application-*.properties`). Un archivo **mal escrito**
> `aplication-jarv.properties` (falta una `p`) ni lo lee Spring **ni lo ignora
> git**, así que aparece como untracked y sus credenciales quedan en riesgo de
> commit. Si existe, renombralo a `application-jarv.properties`.

### Git: subir y bajar sin errores

Repo: `origin` = `github.com/XeXeL27/ControlTicketsFexpo`, rama `main`. Otros
colaboradores integran por **pull requests en GitHub** (rama `control`), así que
`origin/main` suele tener commits de merge que la copia local todavía no tiene.

Los dos errores que ya pasaron (2026-09-15) y cómo quedaron resueltos:
- **Al bajar: "Your local changes to the following files would be overwritten by
  merge: CLAUDE.md".** Pasa cuando CLAUDE.md queda modificado sin commitear (lo
  editan las sesiones de Claude) y en GitHub también cambió. Descartarlo **pierde la
  documentación**. Resuelto con la config local `pull.rebase=true` +
  `rebase.autoStash=true` + `merge.autoStash=true` (guarda lo modificado, baja y lo
  vuelve a poner) y con `.gitattributes` → `CLAUDE.md merge=union` (si dos cambios
  tocan las mismas líneas, quedan las de los dos en vez de un conflicto).
- **Al subir: "rejected … non-fast-forward".** Pasa cuando GitHub tiene commits que
  la copia local no (los merge de PR). Hay que bajar antes de subir: en VS Code usar
  **Sincronizar cambios** (↻), no solo "Push"; o por consola `./subir.sh "mensaje"`,
  que commitea todo, hace `pull --rebase --autostash` y sube.

> **Regla para Claude:** si tocás CLAUDE.md, commitealo **junto con el código** del
> mismo cambio; no lo dejes modificado. `subir.sh` frena si ve archivos que pueden
> tener claves (`*.properties` que no sea el base, `.env`).
> La config `pull.rebase`/`autoStash` es **local** (`.git/config`); en otra máquina
> hay que repetir: `git config pull.rebase true && git config rebase.autoStash true
> && git config merge.autoStash true`. Un conflicto en **código** (dos personas en
> las mismas líneas) sí frena y se resuelve a mano: `union` es solo para CLAUDE.md.

Claves importantes del perfil `jarv`:
- BD: `jdbc:postgresql://localhost:5432/bd_control_tickets_v1`; usuario y clave
  salen del properties local, **no se escriben acá**.
- `ddl-auto=update` (Hibernate crea/actualiza tablas en desarrollo).
- Backend `server.port=9600`, CORS permite `http://localhost:5900`.
- `app.admin.username` / `app.admin.password`: credenciales del admin inicial.
- `sigse.url` / `sigse.api-key`: API de matrícula. Sin ellas el escáner no deja
  entrar a ningún estudiante. Ya **no** están en el properties versionado: ahí figuran
  como `${SIGSE_URL:}` / `${SIGSE_API_KEY:}` y los valores reales viven en el
  properties local (ignorado) o en variables de entorno. El `:` final hace que, si la
  variable no existe, quede vacío en vez de romper el arranque.
  > ⚠️ **La clave estuvo en git desde `cfc8aa2` (2026-09-15) y sigue en el historial**
  > de `origin/main` y `origin/control`. Sacarla del archivo no la borra de ahí:
  > **hay que pedir a la UAP que la rote**. En el servidor hay que exportar
  > `SIGSE_URL` y `SIGSE_API_KEY` al desplegar, o el escáner bloquea a todo estudiante.
- `app.biometria.simulacion` (base, default `false`): en `true` el módulo de huellas
  usa datos inventados en vez de la red, para probar barra + reporte sin equipo.
- `app.biometria.driver` (base, default `python`) + `app.biometria.python`
  (default `python3`): intermediario pyzk. En el servidor hace falta Python 3 con
  `pip install pyzk`; en Windows el ejecutable suele llamarse `python` (hay
  fallback automático).

---

## 8. Endpoints

Todos bajo el prefijo `/api` (lo agrega `WebConfig`).

**Auth (público)**
- `POST /api/auth/login` → `{ token, idUsuario, username, nombreCompleto, roles }`

**Personas** (ADMINISTRADOR) — `/api/personas`
- `GET /listar` · `GET /obtener?idPersona=` · `POST /crear` · `PUT /actualizar?idPersona=` · `DELETE /eliminar?idPersona=`

**Roles** (ADMINISTRADOR) — `/api/roles`
- `GET /listar` · `GET /obtener?idRol=` · `POST /crear` · `PUT /actualizar?idRol=` · `DELETE /eliminar?idRol=`

**Usuarios** (ADMINISTRADOR, salvo mi-perfil) — `/api/usuarios`
- `GET /listar` · `GET /mi-perfil` (cualquier autenticado) · `GET /obtener?idUsuario=`
- `POST /crear` · `PUT /actualizar?idUsuario=` · `DELETE /eliminar?idUsuario=`
- `PATCH /bloqueo?idUsuario=&bloqueado=` · `PATCH /password?idUsuario=`
- `POST /asignar-rol` · `POST /quitar-rol`  (body `{ idUsuario, idRol }`)

### Fase 2 (lo que ya funciona)

**Estudiantes** (ADMINISTRADOR) — `/api/estudiantes`
- `GET /listar` · `GET /obtener?idEstudiante=` · `POST /crear` · `DELETE /eliminar?idEstudiante=`
- `POST /importar` — multipart, campo **`archivo`**. CSV por POSICIÓN:
  `ru, nombre completo, ci, carrera`. Devuelve `{totalFilas, creados, actualizados, errores:[{fila,motivo}]}`.
- `POST /previsualizar` — multipart, campo `archivo`; ver bloque de previsualización abajo.

**Administrativos** (ADMINISTRADOR) — `/api/administrativos` — mismo patrón que estudiantes
- `GET /listar` · `GET /obtener?idAdministrativo=` · `POST /crear` · `DELETE /eliminar?idAdministrativo=`
- `POST /importar` — multipart, campo **`archivo`**. CSV por POSICIÓN:
  `codigo administrativo, nombre completo, ci`. Reimport **actualiza** (reconoce por
  código). Devuelve `{totalFilas, creados, actualizados, errores}`.
- `POST /previsualizar` — igual que estudiantes pero con columnas de administrativo.

**Docentes** (ADMINISTRADOR) — `/api/docentes` — calcado de administrativos
- `GET /listar` · `GET /obtener?idDocente=` · `POST /crear` · `DELETE /eliminar?idDocente=`
- `POST /importar` · `POST /previsualizar` — CSV: `codigo docente, nombre completo, ci`.

**Tickets** (ADMINISTRADOR + CONTROL en consultas; emisión e impresión solo ADMINISTRADOR) — `/api/tickets`
- `GET /listar` · `GET /obtener?idTicket=`
- `GET /{idTicket}/png` · `GET /{idTicket}/pdf` — rinden el ticket con datos de BD.
- `GET /{idTicket}/qr` — solo el QR del ticket (PNG 320px, contenido = `qrToken`);
  sirve para las categorías que todavía no tienen plantilla (administrativo).
- `POST /emitir-estudiante?idEstudiante=` — **solo ADMINISTRADOR**; idempotente.
- `POST /emitir-administrativo?idAdministrativo=` — **solo ADMINISTRADOR**; idempotente.
- `POST /emitir-docente?idDocente=` — **solo ADMINISTRADOR**; idempotente (DOC-…).
- `PATCH /entrega?idTicket=&entregado=` — **control de entrega física** del ticket
  (marcar/desmarcar entregado + fechaEntrega). Devuelve el ticket actualizado.
- `POST /emitir-estudiantes-masivo` — **solo ADMINISTRADOR**. Cuerpo opcional: una
  lista de ids; sin cuerpo emite para **todos** los estudiantes activos. Devuelve
  `{totalEstudiantes, emitidos, omitidos, errores}`. Ojo: el método del service
  **no** lleva `@Transactional` y llama a `emitirEstudiante` por el proxy (`self`),
  para que cada emisión tenga su transacción y un fallo no tumbe el lote entero.
- **Impresión POR CATEGORÍA** (`categoria=ESTUDIANTE|ADMINISTRATIVO|EXTERNO`, default
  ESTUDIANTE): cada categoría se imprime aparte porque tienen arte distinto.
  - `GET /impresion/resumen?formato=&categoria=` → `{categoria, plantillaDisponible,
    total, impresos, pendientes, porHoja, hojasPendientes, largoCm, altoCm}`
  - `POST /impresion/pliego?formato=&categoria=&cantidad=&soloPendientes=&marcar=` →
    **PDF del pliego** de esa categoría. Si la categoría no tiene arte
    (`plantillaDisponible=false`, hoy todo salvo ESTUDIANTE) → **400** con mensaje claro.
  - `PATCH /impresion/marcar?idTicket=&impreso=` · `POST /impresion/reiniciar?categoria=`
  - `carrera=` (opcional, solo ESTUDIANTE) acota `resumen` y `pliego` a una carrera
    (`ticket.estudiante.carrera`, comparación **exacta**: el frontend manda el valor
    tal como está guardado). `reiniciar` sigue siendo por categoría entera.
  - Cuerpo opcional del `pliego`: lista de `idTicket` en el orden en que se ven en la
    tabla. Solo cambia la **posición** en el PDF (y cuáles toma una tanda: los primeros
    N de esa lista); qué tickets entran lo sigue decidiendo el backend. Los que no
    vienen en la lista van al final por `idTicket`. Sin cuerpo = orden de emisión.

**Previsualización de CSV** — `POST /api/estudiantes/previsualizar` y
`POST /api/administrativos/previsualizar` (multipart, campo `archivo`). Corre el
**mismo** parser que la importación real pero sin tocar la BD, y devuelve la
codificación detectada, el separador, si hubo encabezado, cuántas filas son altas y
cuántas actualizaciones, más las primeras 15 filas ya parseadas. Alimenta la vista
previa de las pantallas de estudiantes y administrativos.

**Biométricos** (ADMINISTRADOR) — `/api/biometricos`
- `GET /listar` · `GET /obtener?idDispositivo=` · `POST /crear` · `PUT /actualizar?idDispositivo=` · `DELETE /eliminar?idDispositivo=`
- `POST /probar-conexion?idDispositivo=` — conecta al equipo y devuelve
  plataforma/serie/usuarios/huellas (o 400 con el paso donde falló).

**Huellas / sincronización** (ADMINISTRADOR) — `/api/huellas`
- `POST /sincronizar` — body opcional: lista de ids de equipos (vacío = todos los
  activos). Crea el job y lo lanza en 2º plano; devuelve `{jobId}` enseguida.
- `POST /cargar` — **carga masiva sistema→equipo**: body `{idDispositivo,
  campo: FACULTAD|CARRERA, valor}`. Crea/actualiza en UN equipo a los
  estudiantes del grupo **que tengan huella** (los demás se ignoran; PIN = RU).
  Intermediario `zk_cargar.py` (pyzk `set_user` + `save_user_template`) que
  avisa un RU por vez para la barra en vivo. Requiere driver python.
- `GET /estudiantes/facultades` · `GET /estudiantes/carreras` — valores
  distintos (alimentan los desplegables de la carga).
- `GET /progreso?jobId=` — `{estado, total, procesados, porcentaje, correctos,
  duplicados, noEncontrados, sinHuella, errores, ruActual, equipoActual}` (polling).
- WS **`/topic/huellas/{jobId}`** — mismo progreso en vivo por STOMP (igual patrón
  que `/topic/boletos`; ver `frontend/src/api/ws-huellas.ts`).
- `GET /resultado?jobId=&filtroEstado=` — reporte final (filtro: TODOS/CORRECTO/
  DUPLICADO/NO_ENCONTRADO/SIN_HUELLA/ERROR).
- `GET /historial` · `POST /cancelar?jobId=`
- `GET /estudiante?idEstudiante=` — las N huellas guardadas (dedo, equipo,
  versión, fecha; sin los bytes). La usa el modal "Huellas" de Estudiantes.
  > El **dedo es el slot 0-9 que informa el equipo** (no dice qué dedo
  > anatómico es): `DedoBiometrico.etiqueta()` lo muestra como "Dedo N". El
  > reporte dice qué dedos se guardaron por RU (ej. "Dedos guardados: 0, 1").

**Control / escáner** (ADMINISTRADOR + CONTROL) — `/api/control`
- `POST /validar` — body `{ codigo (qrToken), tipoMovimiento: ENTRADA|SALIDA }`.
  200 si registró; **409** con `ValidacionTicketDto` (`motivo` = `YA_DENTRO` |
  `YA_FUERA` | `NO_MATRICULADO`) si se rechazó. El frontend lee el 409 desde
  `error.response.data` (el comentario de `control.service.ts` todavía dice 400 para
  los duplicados; el backend manda 409).
- `GET /dentro` — personas con `dentro=true`.
- `POST /api/control/boletos/cierre-jornada` — deja a todos (tickets y boletos) fuera.
  Se usa al cerrar cada día; ver §8.2.
- `GET /sigse/{ru}` — consulta de matrícula en SIGSE sin tocar la BD (siempre
  devuelve los datos, matriculado o no).
- `GET /api/validacion/informacion/{ru}` — controller `Api` del paquete
  `apivalidacaion`, versión anterior de la misma consulta (409 si no matriculado).
  **No tiene `@PreAuthorize`**: cualquier usuario logueado puede consultar.

**QR y demo** (ADMINISTRADOR + CONTROL)
- `GET /api/qr/generar?contenido=&tamano=` → PNG del QR (utilidad de prueba).
- `GET /api/tickets-demo/estudiante.png` · `.../estudiante.pdf` → ticket con datos
  inventados, para ajustar coordenadas de la plantilla sin tocar la BD.

> Los endpoints que devuelven PNG/PDF exigen el header `Authorization`, por eso el
> frontend los pide con axios `responseType:'blob'` + `URL.createObjectURL` y nunca
> con `<img src="/api/...">`.

---

## 8.1 Impresión para la imprenta

> **La impresión es por categoría** (una tirada por categoría; ver sección 2 e
> impresión en la 8). Lo de abajo aplica a cada tirada. Hoy solo ESTUDIANTE tiene
> arte; el pliego de administrativo/particular está gateado por `plantillaDisponible`.

La hoja es **OFICIO en vertical: 21.5 x 33 cm**. El arte del ticket tiene proporción
**2524:839 = 3.008:1**.

**Lo que limita el tamaño del ticket es el ANCHO de la hoja, no el alto.** Para que
entre una columna de tickets verticales al costado de los horizontales tiene que
cumplirse `largo + alto <= 21.5 cm`, lo que topea el largo en **16.14 cm**. Por eso
bajar de 6 a 5 filas NO permite agrandar el ticket: solo desperdicia una fila.

Dos formatos, en el enum `FormatoPliego`:

| Formato | Ticket | Disposición | Por hoja | Hojas p/179 |
|---|---|---|---|---|
| `MIXTO_8` | 15.54 x 5.16 cm | 6 horizontales apilados + 2 verticales al costado | 8 | 23 |
| `HORIZONTAL_5` | 20 x 6 cm | 5 horizontales apilados (no entra columna lateral) | 5 | 36 |

> `HORIZONTAL_5` usa 20x6, que es proporción 3.33:1: **estira el arte ~11%**.
> El largo fiel a la proporción con 6 cm de alto sería 18.05 cm, y entran los mismos
> 5 por hoja. Está así porque es la medida que pidió la imprenta.

Detalles de implementación (`TicketRenderer.pdfPliegoEstudiantes`):
- El PDF se arma en **puntos PostScript** (1 cm = 72/2.54 pt), no en píxeles, para
  que salga impreso en tamaño real. El PDF de un ticket suelto (`pdfEstudiante`)
  **no** tiene tamaño físico útil: mapea 1 px = 1 pt y da una página de 89 cm.
- Los tickets van como **JPEG a 300 DPI**, no PNG. El arte es una foto y en PNG no
  comprime: una hoja de 8 pesaba **22 MB** (medio giga la tirada entera). En JPEG
  0.9 quedan 2.6 MB por hoja, sin diferencia visible en papel.
- Los verticales se rotan con la matriz `addImage(img, 0, largo, -alto, 0, x+alto, y)`.
  Si se intercambian `largo` y `alto` ahí, los tickets salen deformados y encimados.
- Los tickets se marcan impresos **después** de que el PDF se generó bien, para que
  un fallo a mitad de camino no los deje marcados sin haberse impreso.

---

## 8.2 Validaciones de jornada (día de feria y `dentro` colgado)

**Modelo (confirmado con Javier, no cambiarlo):** el **ticket** es el ingreso al
concierto y vale **las tres noches** — se perfora físicamente por día, por eso el
estudiante NO lleva boletos. El **boleto** es el ingreso a la feria, uno por día.
Solo administrativos y docentes reciben ticket + 3 boletos; se imprimieron por
separado y se unen en el sistema con
`POST /api/boletos/importar-administrativos|docentes` (CSV: código de persona +
los 3 códigos de boleto). Por eso `boleto` tiene FK a `administrativo`/`docente`
y **no** a `ticket` ni a `estudiante`: está bien así.

### El `dentro` colgado (lo que rompía el evento)

`dentro` es un booleano sin fecha. Si alguien entra una noche y se retira **sin
escanear la salida**, queda en `true` para siempre y al día siguiente el anti-clones
lo rechaza con `YA_DENTRO`: no puede entrar nunca más. Con el ticket valiendo tres
noches, le pasaba a cualquiera que saliera por una puerta sin control.

Resuelto en los dos validadores (`ControlServiceImpl` y `ControlBoletoServiceImpl`):
antes de aplicar el anti-clones se mira el **último movimiento**; si es de un día
anterior, el flag está viejo y se limpia. **El anti-clones del mismo día sigue
intacto** (verificado: reingreso al día siguiente → 200; duplicado el mismo día → 409).
Además `POST /api/control/boletos/cierre-jornada` fuerza el reseteo de todos
(tickets y boletos) al terminar el día.

### Día del boleto

`CalendarioFeria` + `FeriaProperties` (`app.feria.dia1|dia2|dia3`,
`validar-dia`, `zona-horaria`) son **el único lugar que sabe de fechas**. Al ENTRAR,
un boleto con `diaFeria` solo pasa si hoy es su día:
- hoy no es día de feria → 409 **`FUERA_DE_FECHA`**
- es otro día → 409 **`DIA_INCORRECTO`** (el mensaje dice de qué día es y qué día es hoy)
- boletos de **venta suelta** (`diaFeria = null`) no se validan por día.

> ⚠️ `validar-dia=false` en el perfil local **a propósito**: con la validación
> encendida no se puede probar nada fuera del 18-20. En producción va en `true`.
>
> **Para otros colaboradores:** el `application.properties` base trae
> `validar-dia=true`, y un perfil local que no defina `app.feria.*` hereda ese valor.
> Resultado: fuera del 18-20 **todos los boletos con día asignado rebotan con
> `FUERA_DE_FECHA`** (los de venta suelta pasan), y parece un bug. Cada uno tiene que
> poner `app.feria.validar-dia=false` en **su** properties local para desarrollar.
> La zona horaria importa: los movimientos se guardan en UTC y sin
> `America/La_Paz` un escaneo de las 21:00 contaría como del día siguiente.

### Contadores del día

`resumen()` sumaba todo el evento, así que el día 2 el tablero mostraba los ingresos
del día 1. Ahora devuelve además `ingresosHoy`, `salidasHoy`, `diaHoy` y `fechaHoy`
(los `...Total` siguen siendo acumulados).

---

## 8.3 Venta de boletos por talonario (feria)

**Es un universo SEPARADO del boleto que se escanea en la puerta.** Acá no se controla
ingreso: solo se registra **qué boletos de qué talonario se vendieron**, para poder
cuadrar con cada vendedora.

### El modelo de negocio (confirmado con Javier, no reinterpretarlo)

- **Ticket** = ingreso al concierto, vale las **tres noches**, se perfora por día.
  Por eso el estudiante **no lleva boletos**.
- **Boleto** = ingreso a la feria, uno por día. Solo administrativos y docentes
  reciben ticket + 3 boletos (ver §8.2).
- **Talonario** = bloque correlativo de boletos que se **venden** al público.

**Cada tipo lleva su propia numeración y todos arrancan en 1**: el boleto 250 del
`EVENTO_1` y el 250 del `COMBO` son distintos. Lo identifica `(talonario, numero)`,
**nunca el número solo** — por eso tiene tabla propia (`boleto_talonario`) y no se
mezcla con `boleto`, que tiene `UNIQUE(codigo)` y no admitiría cuatro veces el 250.

El **combo** es **un solo boleto** que habilita los tres días, no tres boletos. Vive
en `TipoTalonario` y **no** en `DiaFeria`: metido ahí rompería la validación de día.

### Reglas

- **Sin solapes dentro del mismo tipo.** Dos talonarios de `EVENTO_1` con 1-200 y
  150-350 dejarían 51 boletos en dos talonarios y el cuadre no cerraría nunca.
  Entre tipos distintos el solape es normal y está permitido.
- **Un ANULADO no revive con una marca masiva.** "Vendidos hasta el 137" pasa por
  encima de los anulados sin tocarlos y avisa; solo se los corrige nombrándolos
  **explícitamente** en la lista de números.
- **`vendidoPor` y `fechaVenta` son campos propios, no la auditoría.**
  `_modificacion_id_usuario` es `@LastModifiedBy` y lo pisa cualquier update posterior.
- **Aislamiento:** una vendedora solo ve y marca los talonarios asignados a ella
  (`listar?soloMios=true` + verificación en el service). El ADMINISTRADOR ve todos.
- El precio es **opcional** y va en **bolivianos (Bs)**.

### Endpoints — `/api/talonarios` (ADMINISTRADOR + VENTA_FERIA)

- `GET /listar?tipo=&soloMios=` · `GET /obtener?idTalonario=` · `GET /boletos?idTalonario=`
- `POST /crear` · `POST /generar` (varios correlativos de un tipo, sin huecos) —
  **solo ADMINISTRADOR**
- `PUT /actualizar?idTalonario=` — nombre, precio y vendedora. **El tipo y el rango
  no se editan**: ya generaron sus boletos.
- `DELETE /eliminar?idTalonario=` — rechaza si ya hay ventas.
- `PATCH /marcar` — `hastaNumero` (rendición al cierre), `desde`+`hasta`, o `numeros`
  sueltos. Se pueden combinar.

Pantallas: **`/talonarios`** (admin) y **`/mis-talonarios`** (vendedora, pensada para
el celular: buscador arriba, marcado abajo, grilla con toques de 44 px).

> Generar boletos NO es un problema de rendimiento: medido, **10.000 filas en 214 ms**.
> El OOM/timeout que hay documentado en §2 es de armar PDFs con miles de imágenes,
> no de insertar filas.

### Personas: de dónde viene cada una

`GET /api/personas/listar` devuelve `tipo` (`ESTUDIANTE` / `ADMINISTRATIVO` /
`DOCENTE` / `USUARIO` / `SIN_VINCULO`), para filtrarlas en la pantalla. Se resuelve
con **4 consultas de ids** y no una por persona: con 8200 registros la lista responde
en ~0.1 s; preguntando de a una sería inusable.

---

## 8.4 Editor del mapa 3D (Pulso FEXPO)

La pantalla `/pulso-fexpo` se puede **reacomodar desde la interfaz**: mover bloques e
ingresos, agregarlos, eliminarlos y cambiarles título, color, tamaño y transparencia.
Antes las posiciones estaban escritas en el código (`ZONAS_BASE`) y había que editar
números y recompilar.

### Dónde se guarda

En la **base de datos**, tabla `mapa_pulso`, **una sola fila** (`nombre='principal'`)
con todo el acomodo en un **JSON**. No usa `localStorage`: el Pulso se proyecta en
una pantalla pública, muchas veces desde otra máquina, así que el acomodo tiene que
verse igual para todos.

Se guarda como un documento y no como una tabla por zona porque el mapa se edita y se
graba **entero**; una fila por zona pediría altas, bajas y sincronización por zona
para algo que siempre se escribe completo.

- `GET /api/mapa-pulso` (ADMINISTRADOR + CONTROL_FERIA) — `personalizado:false` = nunca
  se editó, la pantalla usa el acomodo del código.
- `PUT /api/mapa-pulso` · `DELETE /api/mapa-pulso` (solo ADMINISTRADOR). El DELETE
  borra la fila: el mapa vuelve al original de fábrica.

> ⚠️ **Ese acomodo es trabajo manual de horas.** Antes de tocar el formato o de probar
> sobre esa pantalla, respaldarlo (`GET` a un archivo) y **nunca** hacerle `DELETE`.
> Al probar la edición, no apretar "Guardar".

### Versiones del documento

| Versión | Qué guarda |
|---|---|
| 1 | Solo la posición de las zonas del código |
| 2 | La definición completa de cada zona (permite agregar y quitar bloques) |
| 3 | Suma los **ingresos** y la **opacidad** |

`aplicarAcomodo()` soporta las tres: un acomodo v1 o v2 sigue cargando. **Al sumar
campos nuevos hay que mantener esa compatibilidad**, o el mapa acomodado se pierde.

### Trampas de la escena 3D

- **Cada zona e ingreso vive en un `THREE.Group`.** Mover el grupo mueve el cuerpo,
  las aristas, el título y la huella juntos. Antes estaban sueltos en la escena.
- **La huella del piso NO se pinta en la textura del suelo.** Se hacía así, y al mover
  un bloque quedaba pintada en la posición vieja como una **sombra**. Regla general:
  lo que se hornea en una textura no se puede mover después.
- **El título y el color están horneados en la textura del rótulo**, así que
  renombrar o recolorear obliga a **rehacer la malla**, igual que cambiar el tamaño.
- **Los pulsos y el enjambre salen de `gatesRender`, no de `GATES`**: los ingresos se
  mueven, y si se usa el arreglo original salen del lugar viejo.
- Posiciones topeadas a 0-100: sin eso un bloque se arrastra fuera del recinto y queda
  flotando en el vacío sin forma evidente de recuperarlo.

### Candado del arrastre

Dentro del modo edición hay un botón que arranca **bloqueado**: se puede girar y
acercar sin que un toque mueva algo por accidente. Con el candado cerrado un **clic
corto** (menos de 5 px) igual **selecciona** el bloque para editarlo por los campos;
un gesto largo gira la cámara.

---

## 9. Decisiones tomadas

- **Frontend ligero (Vue 3 + Vite + TypeScript)** en vez de replicar el monorepo
  vben de escuela-tecnica: el objetivo es que Javier entienda el código; vben es
  enorme y poco pedagógico. Se adoptó TypeScript y las convenciones de código de
  escuela-tecnica (types/*.type.ts, api/*.service.ts, store) y el estilo visual
  del login (logo UAP + panel azul institucional), sin arrastrar el framework vben.
- **Persona sin correo/celular/fechaNacimiento**: se quitaron por innecesarios
  para el ticket (pedido de Javier).
- **Roles ADMINISTRADOR y CONTROL** (portero para el escáner de la fase 2).
- **ZXing** para QR y **OpenPDF** para el PDF del ticket (ya en el `pom.xml`).
- **Tiempo real**: pendiente de decidir entre SSE (push) y polling (fase 2).
- Base y sistema de seguridad **replicados de `esccuela-tecnica`** por pedido explícito.

---

## 10. Modelo de datos

### Ya creado (fase 2, parcial)
```
Persona (simplificada)
  idPersona (PK) · nombre · paterno · materno · ci (único) · genero
  + auditoría / estado

Estudiante        Administrativo            Particular
  idEstudiante      idAdministrativo          idParticular
  ru (único)        codigoAdministrativo(u)   montoVenta
  facultad          persona (FK)              persona (FK)
  carrera
  persona (FK)

Docente  (mismo patrón que Administrativo)
  idDocente · codigoDocente (único) · persona (FK)

Ticket  (control de ingreso)
  idTicket (PK)
  categoria             ESTUDIANTE | ADMINISTRATIVO | DOCENTE | EXTERNO
  codigoIdentificacion  (único, legible: "CODIGO TICKETS" del diseño)
  qrToken               (único, UUID; contenido del QR)
  dentro                (boolean; estado actual para el monitoreo)
  impreso · fechaImpresion  (avance de la tirada en la imprenta, ver 8.1)
  entregado · fechaEntrega  (control de entrega física del ticket)
  persona (FK)          -> nombre completo + CI
  estudiante / administrativo / docente / particular (FK opcionales; solo una según categoría)
  + auditoría / estado

Acceso  (validación / log de escaneos)
  idAcceso (PK)
  ticket (FK)
  tipo      ENTRADA | SALIDA
  fechaHora
  + auditoría / estado (el usuario CONTROL que escanea queda en _registro_id_usuario)

DispositivoBiometrico (equipos ZKTeco)
  idDispositivo (PK) · nombre · ip · puerto (4370) · timeoutMs · activo
  + auditoría / estado

HuellaDigital (templates descargados del equipo)
  idHuella (PK) · estudiante (FK) · dedo (0-9) · template (TEXT Base64)
  · versionBiometrica · equipoOrigen · fechaCaptura
  + auditoría / estado; UNIQUE (estudiante, dedo)
  (Estudiante.tieneHuella/fechaHuella = flag rápido para filtros)

SincronizacionHuella (job de sincronización)
  idSincronizacion (PK) · estadoJob (EN_CURSO/FINALIZADO/ERROR/CANCELADO)
  · direccion (BAJADA = equipo→sistema, SUBIDA = carga masiva al equipo)
  · totalUsuarios · procesados · correctos/duplicados/noEncontrados/sinHuella/errores
  · equipos · mensajeError · fechaFin
  + auditoría / estado
SincronizacionHuellaDetalle (una fila por RU del reporte final)
  idDetalle (PK) · sincronizacion (FK) · ru · equipo
  · resultado (CORRECTO/DUPLICADO/NO_ENCONTRADO/SIN_HUELLA/CARGADO/ACTUALIZADO/ERROR) · mensaje
  + auditoría / estado (nunca se borra: es el historial)
```
El campo `dentro` en Ticket permite responder rápido "¿quién está adentro?"; el
histórico completo queda en `Acceso`.

### Pendiente
Emisión y relleno del ticket para **administrativo** y **particular** (falta el arte
PNG + generalizar `TicketRenderer`), emisión/pantalla de **particular** (venta manual,
no existe aún) y **monitoreo en tiempo real** (push). Ver sección 2.
Ya hecho también: escáner ENTRADA/SALIDA con validación SIGSE (escribe en `Acceso`).
Ya hecho: emisión estudiante y administrativo (código + QR), relleno PNG→PDF de
estudiante, impresión por categoría.

---

## 11. Verificación hecha

**Sesión 1 (fundación):**
- `./mvnw compile` OK. Arranque `jarv` OK (Tomcat 9600, tablas creadas).
- `AdminInitializer` creó roles ADMINISTRADOR/CONTROL y el usuario admin.
- Login OK (JWT + roles). Sin token → 403. Con token → 200. CI duplicado → 400.

**Sesión 2 (entidades dominio + TypeScript):**
- Backend recompila; tablas `estudiante`, `administrativo`, `particular` creadas en la BD.
- Persona recortada: crear persona sin correo/celular/fecha → 201, y el JSON ya no
  expone esos campos.
- Frontend migrado a TS: `npm run build` (vue-tsc typecheck + vite build) OK.

**Sesión 3 (entidades de control y validación + generador QR):**
- Entidades `Ticket` y `Acceso` + enums `CategoriaTicket`/`TipoAcceso` + Daos.
- `./mvnw compile` OK; arranque OK; tablas `ticket` y `acceso` creadas en la BD.
- Plantillas del ticket movidas a `src/main/resources/plantillas/`.
- `QrGenerator` (ZXing) + `GET /api/qr/generar`: 403 sin token; PNG 320x320 con token.
- `TicketRenderer` + `GET /api/tickets-demo/estudiante.png|.pdf`: ticket relleno con
  datos de prueba (CARRERA, R.U., NOMBRE, CÓDIGO + QR), PNG 2524x839 y PDF OK.

**Sesión 4 (estudiantes end-to-end):**
- Import CSV `POST /api/estudiantes/importar` (3/3 creados). Emisión
  `POST /api/tickets/emitir-estudiante` (EST-000001, idempotente). Generación
  `GET /api/tickets/{id}/png|pdf` con datos reales de BD → verificado visualmente.
- Nota: los ids de ticket pueden tener huecos si un insert falló (IDENTITY no
  reutiliza); el `codigoIdentificacion` sí es correlativo por categoría.

**Sesión 5 (componentes reutilizables + paridad administrativo + impresión por categoría):**
- Corregido: `aplication-jarv.properties` → `application-jarv.properties` (el perfil
  `jarv` no cargaba y quedaba fuera del `.gitignore`).
- Base UI reutilizable creada y aplicada a TODAS las vistas: `ModalBase`, `Alerta` +
  `AlertasHost` (`useAlertas`), `ConfirmDialog` (`useConfirmacion`), `CsvDropzone`,
  `utils/errores`. `npm run build` OK.
- Administrativo nivelado con estudiante: `Utils/csv/CsvUtils` compartido; importar
  con codificación + `previsualizar` + reimport-actualiza. Verificado en vivo:
  previa UTF-8/encabezado OK, import 2/0, reimport 0/2, CP850 autodetectado, y
  estudiante sin regresión.
- Impresión por categoría: resumen/pliego/reiniciar con `categoria`;
  `ResumenImpresionDto.plantillaDisponible`. Verificado: resumen por categoría OK,
  pliego ESTUDIANTE → 200/PDF 1 pág, pliego ADMINISTRATIVO → 400 con mensaje amable.
- `./mvnw compile` OK.

**Sesión 6 (layout fijo + impresión por carrera + orden por columna + git):**
- API real con `marcar=false` (no se tocó ninguna marca): conteos por carrera iguales
  a los tickets reales; pliego de una carrera = 23 hojas para 179 tickets. El orden
  del PDF se verificó **leyendo los QR de la hoja** con el ZXing del proyecto (sin
  orden / invertido / A→Z por nombre).
- Chrome headless: barra y menú no se mueven al scrollear; con la API simulada (no
  había pendientes reales), el cuerpo mandado al pliego = orden de la tabla; Ñ
  después de N; R.U. numérico.
- Git: reproducido en un clon aislado el error "would be overwritten by merge:
  CLAUDE.md" y verificado que con `pull.rebase` + `autoStash` + `merge=union` el pull
  y `subir.sh` pasan sin error, incluso con un merge de PR en el medio.

**Sesión 7 (categoría DOCENTE + control de entrega + progreso):**
- Docente calcado de administrativo. Verificado: import CSV 2/2 con tildes, emisión
  `DOC-000001`, resumen de impresión DOCENTE (plantillaDisponible=false).
- Al emitir el primer ticket DOCENTE saltó `violates check constraint
  "ticket_categoria_check"`; se recreó el CHECK con las 4 categorías (ver §2).
- Control de entrega: `PATCH /api/tickets/entrega` + `Entrega.vue`. Verificado marcar
  entregado (con fecha) y deshacer (fecha a null).
- Emisión masiva por lotes con `ProgresoModal` (arregla el timeout del "emitir todos").
- Diagnóstico: PDF de impresión con miles de tickets → OutOfMemory (~2 GB, límite de
  array de Java). Imprimir por carrera/tanda anda; falta topar "imprimir todos".
- `./mvnw compile` OK, `npm run build` OK.
