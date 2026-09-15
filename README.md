# Control de Tickets de Entrada (QR) — UAP

Sistema de control de ingreso con tickets QR para estudiantes, administrativos y
externos, con monitoreo en tiempo real de quién está dentro.

## Arranque rápido

**Backend** (puerto 9600):
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=jarv
```
Swagger: http://localhost:9600/swagger-ui.html

El usuario administrador inicial se define en `src/main/resources/application-jarv.properties`
(`app.admin.username` / `app.admin.password`). Ese archivo **no está en el repo**:
copiá `application.properties` y completá los valores de tu entorno.

**Frontend** (puerto 5900):
```
cd frontend && npm install && npm run dev
```
App: http://localhost:5900

## Documentación
- **[CLAUDE.md](CLAUDE.md)** — cerebro del proyecto: alcance, arquitectura,
  seguridad, estado y hoja de ruta.
- **[docs/GUIA-COMPRENSION.md](docs/GUIA-COMPRENSION.md)** — cómo funciona el
  código paso a paso (para aprender).

## Estado
- ✅ Fase 1: autenticación + CRUD de Persona/Usuario/Rol (backend + frontend).
- ⬜ Fase 2: tickets, QR, importación CSV, PDF y monitoreo en tiempo real.
