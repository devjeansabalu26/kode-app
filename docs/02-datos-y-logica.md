# KODE — Datos y lógica

Segundo documento. Explica dónde se guardan los datos, cómo se accede a ellos y qué reglas de negocio se aplican. Ver también: [Visión general](01-vision-general.md) · [Pantallas y estado](03-pantallas-y-estado.md).

---

## 1. Base de datos

Archivo: `kode.db` · versión actual: **2** · motor: SQLite con claves foráneas activadas (`setForeignKeyConstraintsEnabled(true)`).

### Tablas

**`users`**

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `name` | TEXT | |
| `dni` | TEXT | **UNIQUE** |
| `phone` | TEXT | |
| `gender` | TEXT | |
| `age` | INTEGER | |
| `email` | TEXT | **UNIQUE**, se guarda en minúsculas |
| `password_hash` | TEXT | SHA-256 en hexadecimal |
| `created_at` | TEXT | `yyyy-MM-dd HH:mm:ss` |

**`events`**

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `creator_id` | INTEGER | FK → `users.id`. **NULL** en los eventos de ejemplo |
| `title` | TEXT | |
| `description` | TEXT | |
| `date` | TEXT | Ver "Formato de fechas" |
| `location` | TEXT | |
| `category` | TEXT | Texto libre |
| `capacity` | INTEGER | Por defecto 100 |
| `status` | TEXT | Por defecto `PUBLISHED`. Solo estos se muestran |
| `created_at` | TEXT | |

**`registrations`** (inscripciones)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `event_id` | INTEGER | FK → `events.id` |
| `user_id` | INTEGER | FK → `users.id` |
| `qr_code` | TEXT | **UNIQUE**. Ej.: `KODE-E4-U1-9F3A21BC` |
| `checked_in` | INTEGER | 0 = no asistió, 1 = ya hizo check-in |
| `registered_at` | TEXT | |
| `checked_in_at` | TEXT | Vacío hasta el check-in |

Restricción: `UNIQUE(event_id, user_id)` → un usuario no puede inscribirse dos veces al mismo evento.

### Relaciones

```
users 1 ──── N events          (un usuario crea muchos eventos)
users 1 ──── N registrations   (un usuario se inscribe a muchos eventos)
events 1 ─── N registrations   (un evento tiene muchos inscritos)
```

`registrations` es la tabla intermedia de una relación muchos-a-muchos entre usuarios y eventos.

### Conteos derivados

`Event.registeredCount` y `Event.attendedCount` **no son columnas**: se calculan en cada consulta con subconsultas `COUNT(*)` sobre `registrations` (todas las filas, y solo las de `checked_in = 1`).

### Eventos de ejemplo

Al crear la base de datos se insertan 3 eventos sin creador (`creator_id` NULL): la conferencia de Tecnología, el festival de Diseño y el Summit de Comunidad.

### Formato de fechas

Hay **dos formatos** conviviendo:

| Origen | Ejemplo |
|---|---|
| Eventos de ejemplo | `20 Sep 2026 · 18:00` |
| Eventos creados en la app | `2026-09-19 07:00:00` |

`HomeFragment.parseEventDate` sabe leer ambos (y también `yyyy-MM-dd'T'HH:mm`) para el filtro "Esta semana". Consecuencia conocida: como `ORDER BY date` ordena texto, la lista **no queda ordenada cronológicamente** y las tarjetas de eventos creados muestran la fecha sin formato legible (ver documento 3).

### Migraciones

`onUpgrade` hace `DROP TABLE` de las tres tablas y las recrea. **Se pierden todos los datos** al subir la versión. Es aceptable en desarrollo, no en producción.

---

## 2. `AppDatabaseHelper` — el esquema

Es un `SQLiteOpenHelper` con constructor privado. Solo se ocupa de:

- Crear las tablas (`onCreate`) e insertar los eventos de ejemplo.
- Actualizar el esquema (`onUpgrade`).
- Activar las claves foráneas (`onConfigure`).
- Exponer los nombres de tabla como constantes (`TABLE_USERS`, `TABLE_EVENTS`, `TABLE_REGISTRATIONS`).
- Ofrecer una **instancia única**: `AppDatabaseHelper.getInstance(context)`. Así toda la app comparte una sola conexión.

Además, al final del archivo hay una función `internal fun currentDateTime()` usada por los tres repositorios para `created_at` / `registered_at`.

**No contiene consultas de negocio.** Esas viven en los repositorios.

---

## 3. Los repositorios

Cada repositorio se crea con `XRepository(context)` y obtiene internamente el helper compartido. Las pantallas los guardan en campos `userRepository`, `eventRepository` y `registrationRepository`.

### `UserRepository`

| Método | Qué hace |
|---|---|
| `registerUser(...)` | Inserta un usuario (email en minúsculas, contraseña hasheada). Devuelve el id o `-1` |
| `userExistsByEmail(email)` | ¿Ya hay una cuenta con ese correo? |
| `userExistsByDni(dni)` | ¿Ya hay una cuenta con ese DNI? |
| `login(email, password)` | Devuelve el `User` si correo y hash coinciden; si no, `null` |
| `getUserById(id)` | Devuelve el `User` o `null` |

Internamente: `findUser` (consulta), `exists` (comprobación), `readUser` (Cursor → `User`) y `hashPassword` (SHA-256).

### `EventRepository`

| Método | Qué hace |
|---|---|
| `getEvents()` | Todos los eventos publicados |
| `getEventById(id)` | Un evento o `null` |
| `getEventsByCategory(category)` | Filtra por categoría |
| `getEventsCreatedByUser(userId)` | Los que creó el usuario (pestaña "Organizados") |
| `getEventsRegisteredByUser(userId)` | Aquellos a los que se inscribió (pestaña "Asistiré") |
| `getCategories()` | Categorías distintas, para crear los botones de filtro |
| `eventExists(id)` | ¿Existe? |
| `createEvent(...)` | Valida que el creador exista e inserta. Devuelve el id o `-1` |
| `updateEvent(...)` | Solo el creador; no permite bajar el aforo por debajo de los inscritos |

Todas las lecturas pasan por **una sola consulta base** (`queryEvents(where, args)`) y un solo mapeo (`readEvent`). Cada método solo cambia el filtro `WHERE`. Si añades un campo a `Event`, hay que tocar solo `queryEvents` y `readEvent`.

### `RegistrationRepository`

| Método | Qué hace |
|---|---|
| `registerUserInEvent(eventId, userId)` | Inscribe y devuelve el código QR; `null` si no se puede |
| `getRegistrationQr(eventId, userId)` | El QR de una inscripción existente |
| `isUserRegisteredInEvent(eventId, userId)` | ¿Está inscrito? |

Usa internamente a `UserRepository` y `EventRepository` para validar.

---

## 4. Reglas de negocio

### Inscripción (`registerUserInEvent`)

Se evalúan en este orden y, si alguna falla, devuelve `null`:

1. `eventId` y `userId` deben ser mayores que 0.
2. El usuario debe existir.
3. El evento debe existir (y estar publicado).
4. **El creador no puede asistir a su propio evento.**
5. Si el usuario **ya está inscrito**, devuelve su QR existente (no crea otro).
6. **Aforo:** si `registeredCount >= capacity`, no hay cupo.
7. Se genera el QR y se inserta la inscripción.

### Código QR

Formato: `KODE-E{eventId}-U{userId}-{8 caracteres aleatorios}`. Ejemplo: `KODE-E4-U1-9F3A21BC`. Los 8 caracteres salen de un UUID en mayúsculas. Hoy el código se muestra **como texto**; no se dibuja una imagen QR.

### Crear evento (`createEvent`)

Requiere un `creatorId` válido que exista en `users`. El evento se guarda con `status = PUBLISHED`.

### Editar evento (`updateEvent`)

Solo lo puede modificar su creador y la nueva capacidad no puede ser menor que los inscritos actuales. **Existe en el repositorio pero ninguna pantalla lo llama todavía** (ver documento 3).

### Registro de usuario

Validaciones en `RegisterFragment`: campos obligatorios, DNI de **8** dígitos, celular de **9** dígitos, género elegido, edad entre 1 y 120, correo con formato válido, contraseña de **mínimo 6** caracteres, y correo y DNI no repetidos.

### Contraseñas

Se guarda `SHA-256(contraseña)` sin sal. Funciona para el proyecto, pero no es seguro para producción (ver documento 3).

---

## 5. Sesión — `SessionManager`

Guarda en `SharedPreferences` (archivo `kode_session`, modo privado):

| Clave | Contenido |
|---|---|
| `logged_in` | booleano |
| `user_id` | id del usuario |
| `name` | nombre |
| `email` | correo |

| Método | Qué hace |
|---|---|
| `createSession(userId, name, email)` | Guarda la sesión (tras login o registro) |
| `isLoggedIn()` | `true` solo si `logged_in` **y** `user_id > 0` |
| `getUserId()` / `getName()` / `getEmail()` | Lectura |
| `logout()` | Borra todo |

**Buena práctica ya aplicada:** las pantallas críticas (perfil, formulario, mis eventos) no se fían solo de `isLoggedIn()`. Además comprueban con `getUserById` que el usuario siga existiendo en la base de datos. Si no existe, cierran la sesión y mandan al login.

---

## 6. Modelos

```kotlin
data class Event(
    val id: Int,
    val creatorId: Long?,        // null = evento de ejemplo
    val title: String,
    val description: String,
    val date: String,
    val location: String,
    val category: String,
    val capacity: Int,
    val registeredCount: Int = 0,  // calculado
    val attendedCount: Int = 0     // calculado
)

data class User(
    val id: Long,
    val name: String,
    val dni: String,
    val phone: String,
    val gender: String,
    val age: Int,
    val email: String            // no incluye la contraseña
)
```

Nota de tipos: `Event.id` es `Int` pero `User.id` es `Long`. Por eso `creatorId` es `Long?` y los repositorios convierten con `toString()` al pasar argumentos a SQLite.

---

## 7. Cómo añadir algo nuevo (guía rápida)

**Un campo nuevo en el evento** (ej. `price`):
1. `AppDatabaseHelper`: añadir la columna al `CREATE TABLE events` y subir `DATABASE_VERSION`.
2. `Event.kt`: añadir la propiedad.
3. `EventRepository`: incluir la columna en el `SELECT` de `queryEvents`, en `readEvent` y en `createEvent`/`updateEvent`.
4. Layouts y fragments que la muestren o pidan.

**Una consulta nueva de eventos:** añadir un método en `EventRepository` que llame a `queryEvents(where = "...", args = ...)`.

**Una tabla nueva:** constante en `AppDatabaseHelper`, `CREATE TABLE`, subir versión, y un repositorio nuevo (o ampliar el más cercano).
