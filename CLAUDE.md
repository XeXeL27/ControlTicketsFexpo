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

Pendiente:
- **Arte (plantillas PNG) de administrativo y particular** + generalizar
  `TicketRenderer` (hoy `pdfPliegoEstudiantes`/`DatosTicketEstudiante` tienen
  incrustada la de estudiante) y poner `plantillaDisponible()` en `true` para ellas.
- **Flujo de particular/externo** (venta manual): NO existe emisión (`emitirParticular`)
  ni pantalla todavía. La pestaña Particulares en Impresión ya está, pero sin tickets.
- Endpoint de **escaneo**: registrar ENTRADA/SALIDA por `qrToken` y alternar el
  flag `Ticket.dentro`. Es lo único que usa el rol CONTROL (ya creado en la BD)
  y la entidad `Acceso` (ya creada, todavía sin escrituras).
- **Monitoreo en tiempo real**: lo tomarán otros colaboradores. Decisión pendiente
  entre SSE (push) o polling.

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

**Roles del sistema:** `ADMINISTRADOR` (gestiona todo) y `CONTROL` (portero/escáner,
para la fase 2). Se crean solos al arrancar (`AdminInitializer`).

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
./mvnw test             # NO hay tests todavía: src/test ni siquiera existe.
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

**Tickets** (ADMINISTRADOR + CONTROL en consultas; emisión e impresión solo ADMINISTRADOR) — `/api/tickets`
- `GET /listar` · `GET /obtener?idTicket=`
- `GET /{idTicket}/png` · `GET /{idTicket}/pdf` — rinden el ticket con datos de BD.
- `GET /{idTicket}/qr` — solo el QR del ticket (PNG 320px, contenido = `qrToken`);
  sirve para las categorías que todavía no tienen plantilla (administrativo).
- `POST /emitir-estudiante?idEstudiante=` — **solo ADMINISTRADOR**; idempotente.
- `POST /emitir-administrativo?idAdministrativo=` — **solo ADMINISTRADOR**; idempotente.
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

Ticket  (control de ingreso)
  idTicket (PK)
  categoria             ESTUDIANTE | ADMINISTRATIVO | EXTERNO
  codigoIdentificacion  (único, legible: "CODIGO TICKETS" del diseño)
  qrToken               (único, UUID; contenido del QR)
  dentro                (boolean; estado actual para el monitoreo)
  impreso · fechaImpresion  (avance de la tirada en la imprenta, ver 8.1)
  persona (FK)          -> nombre completo + CI
  estudiante / administrativo / particular (FK opcionales; solo una según categoría)
  + auditoría / estado

Acceso  (validación / log de escaneos)
  idAcceso (PK)
  ticket (FK)
  tipo      ENTRADA | SALIDA
  fechaHora
  + auditoría / estado (el usuario CONTROL que escanea queda en _registro_id_usuario)
```
El campo `dentro` en Ticket permite responder rápido "¿quién está adentro?"; el
histórico completo queda en `Acceso`.

### Pendiente
Emisión y relleno del ticket para **administrativo** y **particular** (falta el arte
PNG + generalizar `TicketRenderer`), emisión/pantalla de **particular** (venta manual,
no existe aún), endpoint de **escaneo** y **monitoreo**. Ver sección 2.
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
