# Guía de comprensión del código

Escrito para **entender cómo funciona el sistema paso a paso**, no solo para
usarlo. Pensado para alguien que está aprendiendo Spring Boot + Vue.

---

## Índice
1. La idea general (capas)
2. El viaje de una petición HTTP (con y sin login)
3. Cómo funciona el login y el JWT
4. Cómo se protege cada endpoint
5. La auditoría y el borrado lógico
6. Anatomía de un CRUD (Persona, de punta a punta)
7. El frontend: cómo se conecta con el backend
8. Glosario de anotaciones

---

## 1. La idea general (capas)

El backend está partido en capas, cada una con **un solo trabajo**. Así el código
es ordenado y se prueba/cambia por partes:

```
Cliente (navegador)
   │  HTTP + JSON
   ▼
Controller     -> "recepcionista": recibe la petición, valida el formato,
   │               revisa permisos y llama al service. No tiene lógica de negocio.
   ▼
Service        -> "el que piensa": aplica las reglas (ej. CI único),
   │               coordina transacciones, convierte entidad <-> DTO.
   ▼
Repository     -> "el que habla con la BD": guarda y consulta filas.
   │
   ▼
Base de datos (PostgreSQL)
```

Regla de oro: los **DTO** son lo que entra y sale por la API; las **entidades**
son las tablas. Nunca exponemos entidades directamente (evita fugas de datos como
el password y evita bucles al serializar).

---

## 2. El viaje de una petición HTTP

### Caso A: petición SIN sesión a una ruta protegida
`GET /api/personas/listar` sin token:
1. `JwtAuthenticationFilter` mira el header `Authorization`. No hay → deja pasar
   **sin autenticar**.
2. `SecurityConfig` dice: esta ruta exige estar autenticado. Como no lo está →
   responde **403 Forbidden**.

### Caso B: petición CON token válido
`GET /api/personas/listar` con `Authorization: Bearer eyJ...`:
1. `JwtAuthenticationFilter` encuentra el token, lo **valida** (firma + expiración).
2. Extrae el username, **recarga el usuario desde la BD** y lo mete en el
   `SecurityContext` (memoria de "quién está pidiendo, solo durante esta request").
3. Llega al `PersonaController.listar()`. La anotación
   `@PreAuthorize("hasRole('ADMINISTRADOR')")` comprueba el rol → OK.
4. El controller llama a `personaService.listar()`.
5. El service pide al `PersonaDao` las personas ACTIVAS, las convierte a DTO y
   las devuelve.
6. Spring serializa la lista a JSON y responde **200**.

> Importante: el token se cree, pero el usuario se **recarga de la BD** en cada
> request. Si a alguien lo bloquean o eliminan, deja de tener acceso aunque su
> token siga "vivo".

---

## 3. Login y JWT

Archivo clave: `config/security/JwtService.java` y `services/impl/AuthServiceImpl.java`.

1. El cliente manda `POST /api/auth/login` con `{ username, password }`.
2. `AuthServiceImpl` le pide al `AuthenticationManager` de Spring que valide las
   credenciales. Spring usa:
   - `UserDetailsService` (definido en `SecurityConfig`) para buscar al usuario, y
   - `BCryptPasswordEncoder` para comparar el password con el hash guardado.
3. Si son correctas, `JwtService.generarToken()` crea un texto firmado (JWT) que
   contiene: username, idUsuario, nombre y roles, más la fecha de expiración.
4. Se devuelve ese token. El frontend lo guarda y lo manda en cada petición.

Un JWT tiene 3 partes separadas por puntos: `header.payload.firma`. El payload es
legible (base64), **no** es secreto; lo que impide falsificarlo es la **firma**,
hecha con la clave `app.jwt.secret`. Por eso esa clave nunca se sube al repo.

---

## 4. Cómo se protege cada endpoint

Dos niveles:

- **Global** (`SecurityConfig`): define rutas públicas (`/api/auth/**`, Swagger) y
  dice que todo lo demás exige estar autenticado.
- **Por método** (`@PreAuthorize` en los controllers): afina por rol o por dueño.
  - `@PreAuthorize("hasRole('ADMINISTRADOR')")` → solo admins.
  - `@PreAuthorize("isAuthenticated()")` → cualquiera logueado (ej. `mi-perfil`).
  - `@PreAuthorize("hasRole('ADMINISTRADOR') or @autorizacionService.esUsuarioActual(#idUsuario)")`
    → admin **o** el propio dueño del recurso.

`AutorizacionService` siempre saca la identidad del `SecurityContext` (el usuario
logueado), nunca de lo que manda el cliente. Así nadie puede "hacerse pasar" por
otro cambiando un id en la URL.

`hasRole('ADMINISTRADOR')` compara contra `ROLE_ADMINISTRADOR`. El prefijo
`ROLE_` lo agrega la entidad `Usuario.getAuthorities()`; por eso los nombres de
rol se guardan en MAYÚSCULAS y sin ese prefijo.

---

## 5. Auditoría y borrado lógico

Todas las entidades heredan de `config/AuditoriaConfig`, que agrega a cada tabla:
- `_fecha_registro`, `_registro_id_usuario` (quién y cuándo creó),
- `_fecha_modificacion`, `_modificacion_id_usuario` (quién y cuándo modificó),
- `_estado` = `ACTIVO` | `ELIMINADO`.

- Las fechas/usuarios se llenan **solos** gracias a `@EnableJpaAuditing` (en la
  clase principal) + `AuditorAwareImpl` (dice quién es el usuario actual).
- **Borrado lógico**: "eliminar" pone `_estado = ELIMINADO`. Las consultas de
  listar filtran por `ACTIVO`. Así no perdemos historial ni rompemos relaciones.

---

## 6. Anatomía de un CRUD (Persona, de punta a punta)

Sigamos "crear una persona" por todos los archivos:

1. **DTO de entrada** `dto/persona/PersonaDto.java`: define los campos que se
   aceptan y sus validaciones (`@NotBlank`, `@Email`). Si el JSON no cumple,
   `GlobalExceptionHandler` responde 400 con el detalle por campo.
2. **Controller** `controllers/PersonaController.java`:
   `@PostMapping("/crear")` + `@Valid @RequestBody PersonaDto` + `@PreAuthorize`.
   Llama a `personaService.crear(dto)` y responde 201.
3. **Service** `services/impl/PersonaServiceImpl.java`:
   - Regla de negocio: si el CI ya existe → `NegocioException` (→ 400).
   - Copia el DTO a una entidad `Persona` (`aplicarDatos`), la guarda con el Dao.
   - Convierte la entidad guardada a `PersonaDetalleDto` (DTO de salida) y la retorna.
4. **Repository** `models/repository/PersonaDao.java`: `existsByCi(...)` y `save(...)`.
   Spring Data implementa estos métodos solo, leyendo su nombre.
5. **Entidad** `models/entity/Persona.java`: el mapeo a la tabla `persona`.

El resto de operaciones (listar, obtener, actualizar, eliminar) siguen el mismo
recorrido. Usuario y Rol son iguales, con más reglas (roles, bloqueo, password).

---

## 7. El frontend: cómo se conecta

El frontend está en **TypeScript** y sigue las convenciones de escuela-tecnica:

- `src/types/*.type.ts`: interfaces que **reflejan los DTOs del backend**
  (ej. `PersonaDto`, `UsuarioDetalleDto`). Dan autocompletado y evitan errores.
- `src/api/http.ts`: instancia de **axios** con `baseURL: '/api'`. Dos
  interceptores: agrega el token a cada petición y, si el backend responde 401,
  cierra la sesión y va al login.
- `src/api/*.service.ts`: una función por endpoint, tipada (ej. `crearPersona`,
  `asignarRol`). Las vistas llaman estas funciones, no a axios directo.
- `src/store/auth.ts`: guarda token + datos del usuario en memoria y en
  `localStorage` (para sobrevivir a F5). Expone `autenticado` y `tieneRol()`.
- `src/router/index.ts`: define las páginas y un **guard** que bloquea las rutas
  privadas si no hay sesión.
- `vite.config.ts`: el **proxy** reenvía `/api` al backend en `:9099` (evita CORS
  en desarrollo) y define el alias `@` → `src`.
- Las vistas (`views/*.vue`, con `<script setup lang="ts">`) siguen el mismo patrón:
  `cargar()` (GET) → tabla → modal para crear/editar → `guardar()` (POST/PUT) →
  `eliminar()` (DELETE). Mirá `Personas.vue` como plantilla; `Usuarios.vue` es la
  versión más completa (roles, bloqueo, contraseña).

Flujo de login en el front: `Login.vue` hace `POST /auth/login`, guarda el token
con `auth.login(data)` y navega a `/`. A partir de ahí, `http.js` adjunta el token
en todo.

---

## 8. Glosario de anotaciones (las que más aparecen)

| Anotación | Para qué sirve |
|-----------|----------------|
| `@RestController` | La clase es un controller REST (devuelve JSON). |
| `@RequestMapping("/x")` | Ruta base del controller (se le antepone `/api`). |
| `@GetMapping/@PostMapping/...` | Método HTTP + subruta. |
| `@RequestBody` | El cuerpo JSON se convierte a un objeto Java. |
| `@RequestParam` | Un parámetro de la URL (`?idPersona=5`). |
| `@Valid` | Dispara las validaciones del DTO. |
| `@PreAuthorize(...)` | Regla de permisos antes de ejecutar el método. |
| `@Service` / `@Component` / `@Repository` | Marcan clases que Spring administra (beans). |
| `@RequiredArgsConstructor` (Lombok) | Crea el constructor con los `final` → inyección de dependencias. |
| `@Getter/@Setter/@Data` (Lombok) | Generan getters/setters/etc. sin escribirlos. |
| `@Entity` / `@Table` | La clase es una tabla de BD. |
| `@Transactional` | El método corre dentro de una transacción de BD. |
| `@Transactional(readOnly = true)` | Optimización para métodos que solo leen. |

---

Para el panorama completo, versiones, cómo correr y la hoja de ruta de la fase 2,
ver **../CLAUDE.md**.
