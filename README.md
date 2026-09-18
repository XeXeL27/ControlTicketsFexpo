# Control de Tickets de Entrada (QR) — UAP

Sistema de control de ingreso con tickets QR para estudiantes, administrativos, docentes y
externos, con monitoreo en tiempo real de quién está dentro.

## Arranque rápido

**Backend** (puerto 9099):
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=jarv
```
Swagger: http://localhost:9099/swagger-ui.html

El usuario administrador inicial se define en `src/main/resources/application-jarv.properties`
(`app.admin.username` / `app.admin.password`). Ese archivo **no está en el repo**:
copiá `application.properties` y completá los valores de tu entorno.

**Frontend** (puerto 5900):
```
cd frontend && npm install && npm run dev
```
App: http://localhost:5900

## Documentación

### Docentes

- Sección **Docentes**: alta individual, carga CSV con vista previa y emisión de
  tickets individuales o de todos los faltantes. Reimportar un código docente
  actualiza sus datos. El código del padrón es distinto del ticket `DOC-000001`.
- CSV: `codigo docente,nombre completo,ci,carrera`, con coma o punto y coma.
  Los cuatro campos son obligatorios. [Archivo de ejemplo](docs/docentes-ejemplo.csv).
- **Ver ticket** permite previsualizar y descargar el PDF individual. En
  **Impresión → Docentes** se generan pliegos con las medidas de estudiantes,
  solo reversos blancos: título DOCENTE, datos a la izquierda y QR a la derecha.
- En una base PostgreSQL existente, ejecutar
  [la actualización de categorías](docs/sql/2026-09-15-docentes.sql) y reiniciar
  el backend. Con `ddl-auto=update`, Hibernate crea la tabla `docente` y la relación
  `ticket.id_docente`; el SQL actualiza el CHECK anterior de categorías.
  Una base nueva ya se crea con DOCENTE permitido.

### Control de acceso

- Seleccionar **Entrada** o **Salida** y escanear el QR; también se acepta el código
  impreso (`EST-…`, `ADM-…`, `DOC-…`, `EXT-…`). Una lectura repetida en el mismo modo
  no registra otro movimiento ni invierte el estado.
- Estudiantes consultan SIGSE; sin confirmación de matrícula no se autoriza una
  entrada. Los demás tipos se validan localmente. La falta de confirmación no impide
  salir a un estudiante que ya estaba dentro.
- **Control de acceso** muestra exclusivamente el escáner y la validación.
  **Personas dentro** (`/personas-dentro`) es una vista independiente disponible en el menú.
- La vista **Personas dentro** sincroniza el listado de todos los puntos de control cada 2 segundos
  mientras está visible. Muestra la última actualización
  y advierte si pierde conexión; conserva entonces los últimos datos conocidos.
- El total cuenta personas únicas entre los tickets que figuran dentro. Una persona
  con tickets de varias categorías puede aparecer en más de un subtotal por tipo.
- El seguimiento usa el estado registrado de los tickets: cada salida debe escanearse
  para que la persona deje de figurar dentro.

### Reporte de accesos por persona

- Disponible en **Reporte de accesos** (`/reportes/personas`) para administradores
  y control. Muestra una fila por persona con ticket, entradas, salidas, último
  movimiento y estado actual; se actualiza cada 5 segundos mientras está visible.
- Agrupa todos los tickets de la persona y conserva movimientos de tickets antiguos.
  Solo tickets activos determinan el estado Dentro/Fuera. No cuenta intentos
  denegados ni lecturas que no registraron un movimiento.
- **Ver historial** muestra fecha, hora, tipo y ticket, en páginas de 50 movimientos.
  **Actualizar historial** recarga el detalle. La descarga CSV respeta el filtro de
  estado y contiene todas sus personas, independientemente del buscador de la tabla.

### Guías

- **[CLAUDE.md](CLAUDE.md)** — cerebro del proyecto: alcance, arquitectura,
  seguridad, estado y hoja de ruta.
- **[docs/GUIA-COMPRENSION.md](docs/GUIA-COMPRENSION.md)** — cómo funciona el
  código paso a paso (para aprender).

## Estado
- ✅ Fase 1: autenticación + CRUD de Persona/Usuario/Rol (backend + frontend).
- ⬜ Fase 2: tickets, QR, importación CSV, PDF y monitoreo en tiempo real.
