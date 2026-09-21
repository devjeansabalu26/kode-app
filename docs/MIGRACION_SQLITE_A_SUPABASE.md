# Migración SQLite → Supabase

> Documento vivo. **Estado: código migrado y compilando (`assembleDebug` OK). Faltan los pasos que solo puedes hacer tú: ejecutar el SQL en Supabase y probar en el emulador (sección 17).**

## Cómo terminar la migración (pasos para ti)

1. **Supabase → SQL Editor:** ejecuta `supabase/01_diagnostico.sql` y guarda el resultado (sirve para comparar y para avisarme si algo no cuadra).
2. Ejecuta `supabase/02_migracion.sql` (tablas, RLS, vista, funciones). Es idempotente. ⚠️ Reemplaza las políticas RLS que ya existieran en las 3 tablas.
3. *(Opcional)* ejecuta `supabase/03_eventos_de_ejemplo.sql` para tener los 3 eventos de ejemplo que antes traía la app.
4. **Supabase → Authentication → Providers → Email:** para desarrollo, desactiva **"Confirm email"** (hoy está activado: sin desactivarlo, cada registro exige abrir un correo antes de poder entrar). La app funciona en ambos casos.
5. Verifica que `local.properties` tenga `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` (ya los tiene) y ejecuta la app.
6. Haz la **prueba con dos usuarios** (sección 17).

---

## 1. Objetivo

Pasar la persistencia de Kode App de **SQLite local** (`kode.db`, un archivo dentro de cada teléfono) a **Supabase** (PostgreSQL en la nube + Supabase Auth). Así todos los dispositivos que instalen la app leen y escriben la **misma** información.

## 2. Arquitectura anterior

```
Fragment
   ↓
Repository (UserRepository / EventRepository / RegistrationRepository)
   ↓
AppDatabaseHelper (SQLiteOpenHelper)  →  kode.db (solo en ese teléfono)

SessionManager → SharedPreferences (logged_in, user_id, name, email)
```

## 3. Arquitectura nueva

```
Fragment  (viewLifecycleOwner.lifecycleScope.launch)
   ↓
Repository  (AuthRepository / EventRepository / RegistrationRepository)  → devuelve AppResult<T>
   ↓
RemoteDataSource  (AuthRemoteDataSource / EventRemoteDataSource / RegistrationRemoteDataSource)
   ↓
SupabaseProvider  (un solo SupabaseClient: Auth + Postgrest)
   ↓
Supabase Auth  +  Data API (PostgREST)  →  PostgreSQL (profiles, events, registrations)
```

`ViewModel` **no se introdujo** para no romper demasiado código de golpe. Los Repository ya son `suspend` y devuelven `AppResult<T>` (Success/Failure con error tipado), así que pasar a MVVM consiste en mover la llamada del Fragment a un `ViewModel` con `viewModelScope`.

Estructura de carpetas resultante:

```
data/
├── remote/        SupabaseProvider, AuthRemoteDataSource, EventRemoteDataSource, RegistrationRemoteDataSource
│   └── dto/       Dtos.kt (clases @Serializable que coinciden con las tablas)
└── repository/    AuthRepository, EventRepository, RegistrationRepository
core/              AppResult (+AppError), DateFormats, SessionManager
model/             Event, Profile
ui/                UiExtensions.kt, auth/PendingAction.kt + las pantallas
supabase/          01_diagnostico.sql, 02_migracion.sql, 03_eventos_de_ejemplo.sql
```

## 4. Base de datos anterior (SQLite, leída de `AppDatabaseHelper.kt`)

`DATABASE_NAME = kode.db`, `DATABASE_VERSION = 2`.

**users**

| Columna | Tipo | Restricciones |
|---|---|---|
| id | INTEGER | PK AUTOINCREMENT |
| name | TEXT | NOT NULL |
| dni | TEXT | NOT NULL UNIQUE |
| phone | TEXT | NOT NULL |
| gender | TEXT | NOT NULL |
| age | INTEGER | NOT NULL |
| email | TEXT | NOT NULL UNIQUE |
| password_hash | TEXT | NOT NULL (SHA-256 sin salt) |
| created_at | TEXT | NOT NULL |

**events**

| Columna | Tipo | Restricciones |
|---|---|---|
| id | INTEGER | PK AUTOINCREMENT |
| creator_id | INTEGER | FK → users(id), nullable (eventos de ejemplo) |
| title | TEXT | NOT NULL |
| description | TEXT | |
| date | TEXT | NOT NULL (texto libre, varios formatos) |
| location | TEXT | NOT NULL |
| category | TEXT | NOT NULL |
| capacity | INTEGER | NOT NULL DEFAULT 100 |
| status | TEXT | NOT NULL DEFAULT 'PUBLISHED' |
| created_at | TEXT | NOT NULL |

**registrations**

| Columna | Tipo | Restricciones |
|---|---|---|
| id | INTEGER | PK AUTOINCREMENT |
| event_id | INTEGER | NOT NULL, FK → events(id) |
| user_id | INTEGER | NOT NULL, FK → users(id) |
| qr_code | TEXT | NOT NULL UNIQUE |
| checked_in | INTEGER | NOT NULL DEFAULT 0 |
| registered_at | TEXT | NOT NULL |
| checked_in_at | TEXT | nullable |
| | | UNIQUE(event_id, user_id) |

Además, 3 eventos de ejemplo se insertaban en `onCreate` (con `creator_id = NULL`).

### 4.1 Reglas de negocio que se preservaron

- DNI de 8 dígitos, celular de 9, género distinto de "Selecciona", edad 1–120, correo válido, contraseña ≥ 6, correo y DNI únicos.
- Un usuario no puede inscribirse dos veces; si ya estaba inscrito se le devuelve su QR.
- El creador no puede inscribirse en su propio evento.
- No se puede superar la capacidad; no se puede bajar la capacidad por debajo de los inscritos; solo el creador edita.
- QR con formato `KODE-E{evento}-U{usuario}-{8 caracteres}` (el usuario ahora son los 8 primeros caracteres de su UUID).
- `registeredCount` = inscripciones; `attendedCount` = inscripciones con `checked_in`.
- Home: búsqueda por título/descripción/categoría/ubicación, filtro por categoría y "Esta semana".

### 4.2 Inconsistencias encontradas en el código original (no se ocultaron)

1. **"Editar evento" estaba incompleto:** `EventDetailFragment` abría `EventFormFragment.newEditInstance(id)`, pero el formulario nunca leía ese id y siempre creaba un evento nuevo; `updateEvent` existía sin uso. → **Se implementó** (el formulario carga el evento, cambia el botón a "Guardar cambios" y llama a `updateEvent`).
2. **No existe pantalla de check-in:** `checked_in` nunca cambiaba ("Asistieron" siempre 0). → Se dejó la función `check_in_registration` en la BD lista para usarse; **la pantalla sigue sin existir**.
3. `EventDetailFragment` tenía código muerto (`newEditInstance` duplicado y varios `ARG_*` nunca leídos). → Eliminado.
4. Las fechas eran `String` en varios formatos y `HomeFragment.parseEventDate` probaba 5. → Ahora `Instant` + `DateFormats`.

## 5. Base de datos nueva (Supabase / PostgreSQL)

Auditoría del proyecto Supabase real (solo lectura, con la Publishable Key) **antes** de escribir el SQL:

| Hallazgo | Consecuencia |
|---|---|
| `events` **ya existía** con `id` bigint, `creator_id` uuid, `event_date` timestamptz, `capacity` integer, `status`, `created_at`, `updated_at` | El código usa `Long` para el id y `event_date`; el script solo agrega lo que falte |
| `profiles` y `registrations` existían pero **no eran legibles** con la clave pública | No se pudieron ver sus columnas: el script usa `create table if not exists` + `add column if not exists`, y `01_diagnostico.sql` permite comparar |
| `mailer_autoconfirm = false` | El correo exige confirmación (ver sección 12) |

### Esquema (lo que crea/garantiza `02_migracion.sql`)

**auth.users** (lo gestiona Supabase Auth: correo, contraseña cifrada, sesiones)

**public.profiles**

| Columna | Tipo | Restricciones |
|---|---|---|
| id | uuid | PK, FK → auth.users(id) ON DELETE CASCADE |
| name, dni, phone, gender | text | NOT NULL |
| age | integer | NOT NULL, CHECK 1–120 |
| email | text | NOT NULL, **UNIQUE**. Lo pone el trigger `on_auth_user_created` desde `NEW.email`; la app nunca lo envía |
| created_at, updated_at | timestamptz | default now() |
| índices | | UNIQUE (dni), UNIQUE (email) |

`profiles` **no** tiene contraseña ni `password_hash`: la contraseña es responsabilidad exclusiva de Supabase Auth.

**public.events**

| Columna | Tipo | Restricciones |
|---|---|---|
| id | bigint | PK, identity |
| creator_id | uuid | FK → auth.users(id) ON DELETE SET NULL, `default auth.uid()` |
| title, location, category | text | NOT NULL |
| description | text | |
| event_date | timestamptz | NOT NULL |
| capacity | integer | NOT NULL default 100, CHECK > 0 |
| status | text | NOT NULL default 'PUBLISHED' |
| created_at, updated_at | timestamptz | default now() |
| índices | | event_date, category, creator_id |

**public.registrations**

| Columna | Tipo | Restricciones |
|---|---|---|
| id | bigint | PK, identity |
| event_id | bigint | NOT NULL, FK → events(id) ON DELETE CASCADE |
| user_id | uuid | NOT NULL, FK → auth.users(id) ON DELETE CASCADE |
| qr_code | text | NOT NULL, UNIQUE |
| checked_in | boolean | NOT NULL default false |
| registered_at | timestamptz | default now() |
| checked_in_at | timestamptz | nullable |
| | | UNIQUE (event_id, user_id) |
| índices | | user_id, event_id |

**Vista `public.events_with_counts`:** eventos publicados + `registered_count` y `attended_count` **calculados**. Son datos calculados, no columnas físicas (separación pedida entre datos almacenados y calculados).

## 6. Mapeo de datos

| SQLite | Supabase / Kotlin |
|---|---|
| `users.id` (INTEGER) | `auth.users.id` (UUID) = `profiles.id` → `String` |
| `users.email` | `auth.users.email` **y** `profiles.email` (UNIQUE; copiado por el trigger desde `NEW.email`) |
| `users.password_hash` | **ELIMINADO** → lo gestiona Supabase Auth |
| `users.name/dni/phone/gender/age` | `profiles.name/dni/phone/gender/age` |
| `events.id` (INTEGER) | `events.id` (bigint) → `Long` |
| `events.creator_id` (INTEGER) | `events.creator_id` (uuid) → `String?` |
| `events.date` (TEXT) | `events.event_date` (timestamptz) → `java.time.Instant` |
| `registrations.user_id` (INTEGER) | `registrations.user_id` (uuid) |
| `registrations.checked_in` (0/1) | `registrations.checked_in` (boolean) |
| `registeredCount`, `attendedCount` | calculados en la vista `events_with_counts` |

No se hicieron conversiones de `Long` a UUID: los ids de usuario pasaron a `String` (UUID) en toda la app.

### 6.1 Auditoría columna por columna (SQLite → Supabase)

Regla aplicada: **ningún atributo funcional se pierde**. Solo cambian tipos por mejora técnica, y el único atributo eliminado es `password_hash`, justificado abajo.

**users → auth.users + public.profiles**

| Columna SQLite | Dónde queda en Supabase | Cambio |
|---|---|---|
| `id` INTEGER | `profiles.id` uuid (= `auth.users.id`) | tipo: INTEGER → UUID (lo exige Supabase Auth) |
| `name` | `profiles.name` | igual |
| `dni` UNIQUE | `profiles.dni` UNIQUE | igual |
| `phone` | `profiles.phone` | igual |
| `gender` | `profiles.gender` | igual |
| `age` | `profiles.age` (CHECK 1–120) | igual + restricción que antes solo validaba la app |
| `email` UNIQUE | `profiles.email` UNIQUE **y** `auth.users.email` | se conserva; el valor sale de `NEW.email` en el trigger, no de Android |
| `password_hash` | **ELIMINADO** | Supabase Auth guarda la contraseña con su propio hash seguro (bcrypt); duplicarla o guardar un SHA-256 sin salt en `profiles` sería un riesgo. Es la única columna eliminada |
| `created_at` TEXT | `profiles.created_at` timestamptz | tipo: TEXT → timestamptz |
| *(nuevo)* | `profiles.updated_at` | mejora técnica |

**events**

| Columna SQLite | Supabase | Cambio |
|---|---|---|
| `id` INTEGER | `events.id` bigint identity | tipo |
| `creator_id` INTEGER | `events.creator_id` uuid | tipo (apunta a `auth.users`) |
| `title`, `description`, `location`, `category` | igual | — |
| `date` TEXT | `events.event_date` timestamptz | nombre y tipo (ya existía así en tu Supabase) |
| `capacity` INTEGER DEFAULT 100 | `events.capacity` integer DEFAULT 100 + CHECK > 0 | restricción extra |
| `status` TEXT DEFAULT 'PUBLISHED' | `events.status` (default 'PUBLISHED') | igual; se sigue filtrando por publicado |
| `created_at` TEXT | `events.created_at` timestamptz | tipo |
| *(nuevo)* | `events.updated_at` | ya existía en tu Supabase |

**registrations**

| Columna SQLite | Supabase | Cambio |
|---|---|---|
| `id` INTEGER | `registrations.id` bigint identity | tipo |
| `event_id` INTEGER | `registrations.event_id` bigint FK → events | tipo |
| `user_id` INTEGER | `registrations.user_id` uuid FK → auth.users | tipo |
| `qr_code` UNIQUE | igual | — |
| `checked_in` INTEGER (0/1) | `registrations.checked_in` boolean | tipo |
| `registered_at` TEXT | `registrations.registered_at` timestamptz | tipo |
| `checked_in_at` TEXT | `registrations.checked_in_at` timestamptz | tipo |
| `UNIQUE(event_id, user_id)` | igual (`registrations_event_id_user_id_key`) | — |

Todos los atributos funcionales originales siguen existiendo.

## 7. Archivos creados

| Ruta | Para qué sirve |
|---|---|
| `core/AppResult.kt` | `AppResult` (Success/Failure), `AppError` (errores con mensaje amigable), `appCall{}` que convierte excepciones en errores tipados y traduce sin internet / credenciales / duplicados / aforo / RLS |
| `core/DateFormats.kt` | Único lugar que formatea y compara fechas de eventos |
| `model/Profile.kt` | Modelo del perfil (reemplaza a `User`) |
| `data/remote/SupabaseProvider.kt` | Único `SupabaseClient` (Auth + Postgrest) con URL y Publishable Key de `BuildConfig` |
| `data/remote/dto/Dtos.kt` | Clases `@Serializable` que coinciden con las tablas/vista |
| `data/remote/AuthRemoteDataSource.kt` | Registro, login, logout, sesión actual, perfil, RPC `is_dni_available` |
| `data/remote/EventRemoteDataSource.kt` | Consultas a `events_with_counts`; insert/update en `events` |
| `data/remote/RegistrationRemoteDataSource.kt` | RPC `register_for_event`; lectura de mis inscripciones |
| `data/repository/AuthRepository.kt` | Reglas de auth (DNI libre, correo repetido) y perfil actual |
| `data/repository/EventRepository.kt` | Mismos métodos que el antiguo, ahora `suspend` + `AppResult` |
| `data/repository/RegistrationRepository.kt` | Inscripción, QR existente, ¿inscrito? |
| `ui/UiExtensions.kt` | `showMessage`, `showError`, `navigateTo` (evita repetir el bloque de `FragmentManager`) |
| `ui/auth/PendingAction.kt` | `continuePendingAction` (estaba duplicado en Login y Register) y constantes `ARG_*` |
| `supabase/01_diagnostico.sql` | Consultas de solo lectura del estado de la BD |
| `supabase/02_migracion.sql` | Tablas, RLS, vista, triggers y funciones |
| `supabase/03_eventos_de_ejemplo.sql` | Opcional: los 3 eventos de ejemplo |
| `docs/MIGRACION_SQLITE_A_SUPABASE.md` | Este documento |

## 8. Archivos modificados

| Archivo | ANTES | DESPUÉS | POR QUÉ |
|---|---|---|---|
| `app/build.gradle.kts` | minSdk 24, sin Supabase | minSdk 26, plugin serialization, `buildConfigField` desde `local.properties`, dependencias Supabase/Ktor/coroutines, `buildConfig = true` | Ver secciones 10–11 |
| `build.gradle.kts` (raíz) | solo plugin Android | + plugin `kotlin-serialization` (`apply false`) | Serialización de DTOs |
| `gradle/libs.versions.toml` | sin Supabase | + versiones y librerías/plugin de Supabase | Catálogo de versiones |
| `AndroidManifest.xml` | sin permiso de red | + `INTERNET` | Supabase es remoto |
| `MainActivity.kt` | leía sesión de SharedPreferences | espera `awaitReady()` del SDK y muestra Inicio | La sesión la carga el SDK de forma asíncrona |
| `model/Event.kt` | `id: Int`, `creatorId: Long?`, `date: String` | `id: Long`, `creatorId: String?`, `date: Instant` | IDs de Supabase y `timestamptz` |
| `core/SessionManager.kt` | guardaba `logged_in/user_id/name/email` en SharedPreferences | **solo lee** la sesión del SDK (sin red, sin guardar nada). `getUserId()` devuelve `String?` | No guardar tokens/datos a mano |
| `components/EventAdapter.kt` | `currentUserId: Long?`, mostraba fecha cruda | `currentUserId: String?`, fecha con `DateFormats` | Tipos nuevos |
| `ui/auth/LoginFragment.kt` | `UserRepository.login` síncrono | `AuthRepository.login` en corrutina, botón deshabilitado mientras carga | Supabase Auth |
| `ui/auth/RegisterFragment.kt` | consultaba SQLite y guardaba hash | `AuthRepository.register`; contempla correo sin confirmar | Supabase Auth + profiles |
| `ui/home/HomeFragment.kt` | 3 listas locales + `parseEventDate` (5 formatos) | consultas remotas por filtro; "Esta semana" con `DateFormats` | Datos remotos, fechas consistentes |
| `ui/events/EventDetailFragment.kt` | recibía 10 argumentos, leía SQLite | recibe solo el id y vuelve a pedir el evento; inscripción por RPC | Datos frescos, aforo atómico |
| `ui/events/EventFormFragment.kt` | solo creaba | crea **y edita**; fecha a `Instant` | Ver 4.2 (1) |
| `ui/events/MyEventsFragment.kt` | SQLite | consultas remotas | Migración |
| `ui/events/TicketFragment.kt` | `eventId: Int`, `SessionManager(context)` | `eventId: Long`, `SessionManager()` | Tipos nuevos |
| `ui/profile/ProfileFragment.kt` | `getUserById` | `getCurrentProfile()` (el correo se muestra desde `profiles.email`); logout con `signOut` | Migración. Sin cambios de código en la corrección del email: ya mostraba `user.email` |
| `data/remote/dto/Dtos.kt` | `ProfileDto` sin correo | `ProfileDto` con `email` | `profiles` ahora conserva el correo |
| `model/Profile.kt` | comentario: correo "viene de Auth" | comentario actualizado (el correo está en `profiles`); campos iguales | Coherencia |
| `data/repository/AuthRepository.kt` | `Profile(..., remote.currentEmail())` (correo de Auth) | `Profile(..., dto.email)` (correo de `profiles`) | Una sola fuente: la tabla `profiles` |
| `data/remote/AuthRemoteDataSource.kt` | — | comentario: el correo no se envía como dato de perfil, lo toma el trigger de Auth | Documentar que la app no puede falsear el correo |

Los layouts XML, colores, temas y la navegación **no se tocaron**.

## 9. Archivos eliminados

| Archivo | Motivo |
|---|---|
| `data/AppDatabaseHelper.kt` | SQLite ya no es fuente de datos |
| `data/UserRepository.kt` | reemplazado por `AuthRepository` (+ tabla `profiles`); incluía `password_hash` SHA-256 |
| `data/EventRepository.kt`, `data/RegistrationRepository.kt` | reemplazados por los de `data/repository/` |
| `model/User.kt` | reemplazado por `Profile` |

Se eliminaron **después** de tener sus reemplazos escritos y compilando.

## 10. Dependencias agregadas

| Nombre | Versión | Función |
|---|---|---|
| `io.github.jan-tennert.supabase:bom` | 3.2.6 | Fija la versión de todos los módulos Supabase |
| `supabase auth-kt` | (BOM) | Registro, login, sesión |
| `supabase postgrest-kt` | (BOM) | Consultas a la base de datos y RPC |
| `io.ktor:ktor-client-android` | 3.3.1 | Motor HTTP de Supabase en Android |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | Corrutinas en el hilo principal de Android |
| plugin `org.jetbrains.kotlin.plugin.serialization` | 2.2.10 | Genera los serializadores de los DTOs |

`kotlinx-serialization-json` llega como dependencia transitiva de Supabase. Todas las versiones están en `gradle/libs.versions.toml`.

**Por qué 3.2.6 y no la última (3.8.0):** la 3.7+ exige Kotlin 2.4 y el proyecto compila con Kotlin **2.2.10** (el que trae AGP 9.3.2). La 3.2.6 está compilada con Kotlin 2.2.x y es compatible.

**minSdk:**

```
ANTES:   minSdk = 24
DESPUÉS: minSdk = 26
```

**Por qué:** el build **no falla** con `minSdk = 24` (lo probé), pero la app se caería en ejecución en Android 7.x. Las fechas ahora usan `java.time` (`Instant`, `OffsetDateTime`, `DateTimeFormatter`, en `DateFormats` y `EventRepository`), y `java.time` solo existe desde Android 8.0 (API 26). Supabase Kotlin también depende de `kotlinx-datetime`, que se apoya en `java.time`. La alternativa a subir el `minSdk` era activar *core library desugaring*; se prefirió subirlo (Android 8.0 o superior cubre a la gran mayoría de dispositivos actuales).

## 11. Configuración Supabase

- `local.properties` (NO se sube a Git; `.gitignore` ya lo excluye, verificado con `git check-ignore`):
  ```
  SUPABASE_URL=https://<tu-proyecto>.supabase.co
  SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
  ```
- `app/build.gradle.kts` lee esas dos claves (con `providers.fileContents`, para que Gradle detecte cambios) y genera `BuildConfig.SUPABASE_URL` y `BuildConfig.SUPABASE_PUBLISHABLE_KEY`.
- `SupabaseProvider` las usa. Si faltan, falla con un mensaje claro.
- Verificado: la clave configurada es una **Publishable Key** (`sb_publishable_…`), no una secreta; y no aparece escrita en ningún archivo fuera de `local.properties`.
- **Nunca** va en la app: contraseña de la base de datos, cadena de conexión, `service_role`, `sb_secret_…`, JWT secret.
- La `sdk.dir` de `local.properties` no se tocó.

## 12. Supabase Auth

- **Registro:** `AuthRepository.register` → (1) llama a la función pública `is_dni_available` (el DNI es único); (2) `auth.signUpWith(Email)` enviando nombre, DNI, celular, género y edad como **metadatos**; (3) un **trigger** en la BD (`on_auth_user_created`) crea la fila de `profiles` con esos metadatos y con `email = NEW.email` (el correo sale de Auth; Android no lo envía como dato de perfil, así no se puede falsear). Otro trigger (`on_auth_user_email_changed`) mantiene `profiles.email` igual si el correo cambia en Auth. Así el perfil se crea aunque todavía no haya sesión (caso "confirmar correo"), sin dar permiso de INSERT a la app.
- **Confirmación de correo:** el proyecto tiene `mailer_autoconfirm = false`. Resultados posibles del registro: `LOGGED_IN` (continúa como antes), `CONFIRMATION_REQUIRED` (mensaje "Confirma tu correo…" y pasa a Login) o `ALREADY_REGISTERED` ("Este correo ya está registrado"; con confirmación activa Supabase no da error, devuelve un usuario sin identidades, y así se detecta).
- **Login:** `auth.signInWith(Email)`. Errores traducidos: credenciales incorrectas, correo sin confirmar, sin internet.
- **Logout:** `auth.signOut()`.
- **Sesión:** la guarda y renueva el **SDK de Supabase**. La app no guarda tokens ni contraseñas. `SessionManager` ahora solo **lee** de forma síncrona (`currentUserOrNull()`); ya no escribe en SharedPreferences.
- **Contraseñas:** las gestiona Supabase Auth. Se eliminó el SHA-256 manual y `password_hash`.

## 13. RLS

RLS activado en `profiles`, `events`, `registrations`. Privilegios mínimos: se revocó todo a `anon` y `authenticated` y solo se concede lo necesario.

| Tabla | Política | Quién | Regla |
|---|---|---|---|
| profiles | `profiles_select_own` | authenticated | ver solo su perfil (`id = auth.uid()`) |
| profiles | `profiles_update_own` | authenticated | editar solo su perfil. Además, por privilegios de columna solo puede modificar `name`, `phone`, `gender` y `age`: **`id`, `dni` y `email` no se pueden editar desde la app**. No hay INSERT: lo hace el trigger |
| events | `events_select_published` | anon, authenticated | ver eventos publicados (o los propios) |
| events | `events_insert_own` | authenticated | crear solo con `creator_id = auth.uid()` |
| events | `events_update_own` | authenticated | editar solo el creador |
| events | `events_delete_own` | authenticated | borrar solo el creador |
| registrations | `registrations_select_own_or_organizer` | authenticated | ver las propias, o las de eventos que organizo |
| registrations | *(sin INSERT/UPDATE/DELETE)* | — | nadie escribe directo; solo las funciones |

Qué puede hacer cada rol:

- **anon (sin sesión):** ver eventos publicados y sus conteos (vista); consultar si un DNI está libre. Nada más.
- **authenticated (usuario):** ver/editar su perfil; crear eventos; ver sus inscripciones; inscribirse solo a través de `register_for_event`.
- **organizador (creador del evento):** editar/borrar su evento; ver las inscripciones de su evento; marcar asistencia con `check_in_registration`.

Decisiones importantes:

- **Aforo sin condiciones de carrera:** `register_for_event` es una función de PostgreSQL que, en **una transacción**, bloquea la fila del evento (`SELECT … FOR UPDATE`), valida (existe, no eres el creador, ya inscrito → devuelve tu QR, aforo) e inserta. Dos usuarios simultáneos no pueden superar la capacidad porque el segundo espera al primero. Además la app **no tiene** permiso de INSERT en `registrations`, así que no hay forma de saltarse la función.
- **Trigger `events_before_write`:** fuerza `creator_id = auth.uid()` (no se confía en lo que mande la app), impide cambiar el creador, actualiza `updated_at` y rechaza bajar la capacidad por debajo de los inscritos (`CAPACITY_BELOW_REGISTERED`).
- **Vista `events_with_counts`:** todos deben ver cuántos hay inscritos (para "Aforo completo") sin ver las inscripciones ajenas. La vista corre con permisos del propietario y **solo expone números y eventos publicados**. Supabase puede mostrar el aviso "Security Definer View": es intencional.
- **`check_in_registration`:** solo el organizador puede marcar `checked_in`; ningún usuario puede modificarlo arbitrariamente.
- No se deshabilitó RLS en ningún momento.

## 14. Migración de Repository

| Método antiguo (SQLite) | Método nuevo (Supabase) |
|---|---|
| `UserRepository.registerUser` | `AuthRepository.register` → Auth `signUp` + trigger → `profiles` |
| `UserRepository.login` | `AuthRepository.login` → Auth `signInWith(Email)` |
| `UserRepository.userExistsByDni` | RPC `is_dni_available` |
| `UserRepository.userExistsByEmail` | Auth detecta el correo repetido (`identities` vacío / `user_already_exists`) |
| `UserRepository.getUserById` | `AuthRepository.getCurrentProfile` (fila de `profiles`, que incluye el correo) |
| `EventRepository.getEvents` | `EventRepository.getEvents` → vista `events_with_counts` ordenada por `event_date` |
| `getEventById` | `getEventById` → `EVENT_NOT_FOUND` si no existe |
| `getEventsByCategory` | `getEventsByCategory` |
| `getEventsCreatedByUser(Long)` | `getEventsCreatedByUser(String uuid)` |
| `getEventsRegisteredByUser(Long)` | `getEventsRegisteredByUser(String uuid)`: ids de mis inscripciones + `events_with_counts` (`in`) |
| `getCategories` | `getCategories` (categorías distintas, ordenadas) |
| `eventExists` | eliminado: no tenía usos; `getEventById` lo cubre |
| `createEvent(creatorId, …)` | `createEvent(…)`: **sin `creatorId`**, lo pone la BD con `auth.uid()` |
| `updateEvent(eventId, creatorId, …)` | `updateEvent(eventId, …)`: valida capacidad y RLS |
| `RegistrationRepository.registerUserInEvent(eventId, userId)` | `registerUserInEvent(eventId)` → RPC `register_for_event` |
| `getRegistrationQr`, `isUserRegisteredInEvent` | igual, sobre `registrations` (RLS: solo las mías) |
| `SessionManager.createSession/logout` | los hace el SDK (`signIn`/`signOut`) |

## 15. Migración de pantallas

| Pantalla | Qué cambió |
|---|---|
| `MainActivity` | Espera la carga de sesión del SDK antes de mostrar Inicio |
| `LoginFragment` | Login remoto; botón deshabilitado durante la llamada; errores amigables |
| `RegisterFragment` | Mismas validaciones; registro remoto; si hay que confirmar el correo, avisa y pasa a Login conservando el destino |
| `HomeFragment` | Filtros/categorías/búsqueda igual de cara al usuario; ahora piden los datos a Supabase |
| `EventDetailFragment` | Pide el evento por id; "Ver mi pase"/"Aforo completo"/"Quiero asistir" según Supabase; modo creador con progreso |
| `EventFormFragment` | Crea y **edita** (antes solo creaba) |
| `MyEventsFragment` | "Asistiré" y "Organizados" desde Supabase |
| `TicketFragment` | Sin cambios visuales; QR igual |
| `ProfileFragment` | Perfil desde Supabase; cerrar sesión con `signOut` |

Cambios de comportamiento visibles (necesarios por la migración): si la inscripción falla justo tras el login, ahora se muestra el error y se va a Inicio (antes se quedaba en Login); se muestran errores diferenciados (sin internet, aforo completo, etc.).

## 16. Problemas encontrados

| Problema | Causa | Solución |
|---|---|---|
| Supabase Kotlin 3.8.0 no es compatible | Exige Kotlin 2.4; el proyecto usa 2.2.10 | Se usa 3.2.6 |
| El registro no da sesión | `mailer_autoconfirm = false` | La app contempla ambos casos; recomendado desactivar "Confirm email" en desarrollo |
| Columna de fecha `event_date`, no `date` | Nombre real en Supabase | El código usa `event_date` |
| `profiles`/`registrations` no legibles con la clave pública | Correcto por seguridad, pero impide ver su esquema | SQL idempotente + `01_diagnostico.sql` |
| No se puede crear el perfil desde la app sin sesión | Con correo sin confirmar no hay sesión, y RLS exige `auth.uid()` | Trigger en `auth.users` que crea el perfil con los metadatos |
| El DNI duplicado daría un error genérico de Auth | El trigger fallaría por el índice único | Comprobación previa con `is_dni_available` |
| Editar evento no funcionaba | Ver 4.2 (1) | Implementado |
| El shell fallaba con archivos grandes | Comillas en el contenido | Se usó la herramienta de escritura de archivos |
| La primera versión de `profiles` omitía el correo | Se asumió que bastaba con `auth.users.email` | Corregido: `profiles.email` (UNIQUE, desde `NEW.email`); ver 6.1 |
| Con `profiles.email` editable, la app podría cambiarlo | El permiso de UPDATE era sobre toda la fila | Privilegio de UPDATE solo sobre `name`, `phone`, `gender`, `age` |

## 17. Pruebas realizadas y por realizar

Realizadas por mí:

- [x] `assembleDebug` completo: **BUILD SUCCESSFUL**, sin warnings de compilación.
- [x] Búsqueda global: no queda `SQLiteDatabase`, `SQLiteOpenHelper`, `Cursor`, `ContentValues`, `rawQuery`, `password_hash`, `AppDatabaseHelper`, `MessageDigest` ni `getSharedPreferences` en el código.
- [x] Sintaxis del SQL validada con el parser de PostgreSQL (60 sentencias + las 4 funciones plpgsql).
- [x] Lecturas con la clave pública contra tu proyecto: `events` accesible; `profiles`/`registrations` protegidas.
- [x] La clave configurada es la pública; no hay credenciales escritas en archivos versionables.

**No realizadas (no puedo hacerlas yo):** ejecutar el SQL en tu Supabase y usar la app en un emulador. Por eso **no está probado en ejecución real**. Prueba a hacer, en este orden:

**Prueba con dos usuarios (objetivo principal)**

1. *Emulador 1 — Usuario A:* registrarse → crear un evento.
2. *Emulador 2 (o un teléfono) — Usuario B:* registrarse → ver el evento de A en Inicio → "Quiero asistir" → aparece el QR.
3. *Usuario A:* abrir el evento → "Registrados: 1 / N".
4. *Usuario B:* volver a abrir el evento → botón "Ver mi pase" (mismo QR).
5. *Usuario A:* abrir su evento → "Editar" → cambiar el título → "Guardar cambios".
6. *Prueba de aforo:* crear un evento con capacidad 1; que B se inscriba; un tercer usuario debe ver "Aforo completo".

## 18. Pendientes / Checklist

### Fase 1 - Auditoría
- [x] revisar AppDatabaseHelper
- [x] revisar UserRepository
- [x] revisar EventRepository
- [x] revisar RegistrationRepository
- [x] revisar SessionManager
- [x] revisar modelos
- [x] revisar Fragments

### Fase 2 - Configuración Supabase
- [x] dependencias
- [x] BuildConfig
- [x] local.properties
- [x] Internet permission
- [x] SupabaseProvider

### Fase 3 - Auth
- [x] registro (código)
- [x] login (código)
- [x] logout (código)
- [x] sesión (SDK)
- [x] profile (trigger + `getCurrentProfile`)

### Fase 4 - Eventos
- [x] listar
- [x] detalle
- [x] categorías
- [x] crear
- [x] editar
- [x] eventos organizados

### Fase 5 - Inscripciones
- [x] registrar usuario (RPC)
- [x] verificar inscripción
- [x] aforo (transacción con bloqueo)
- [x] QR
- [x] checked_in (columna + función; **sin pantalla**)
- [x] eventos a los que asistiré

### Fase 6 - Limpieza
- [x] eliminar SQLite
- [x] eliminar AppDatabaseHelper
- [x] eliminar password_hash
- [x] eliminar imports SQLite
- [x] eliminar código muerto

### Fase 7 - Validación
- [x] build
- [ ] ejecutar `supabase/02_migracion.sql` en Supabase (tú)
- [ ] registro
- [ ] login
- [ ] crear evento
- [ ] listar evento
- [ ] editar
- [ ] inscripción
- [ ] QR
- [ ] Mis eventos
- [ ] perfil
- [ ] logout
- [ ] prueba con dos usuarios

### Pendientes técnicos (fuera del alcance pedido)
- [ ] Pantalla de check-in (escanear QR) que use `check_in_registration`.
- [ ] Pasar a MVVM (`ViewModel` + `viewModelScope`).
- [ ] Limitación conocida: si Android mata el proceso y restaura una pantalla que consulta la sesión antes de que el SDK termine de cargarla, esa pantalla puede creer momentáneamente que no hay sesión. Se resolvería con `ViewModel` observando `sessionStatus`.
- [ ] El DNI duplicado en una carrera entre dos registros simultáneos daría el error genérico (la comprobación previa cubre el caso normal; el índice único garantiza la integridad).

## 19. Estado final

- **Ya usa Supabase:** registro, login, logout, sesión, perfil, eventos (lectura, creación, edición), categorías, inscripciones, QR, "Mis eventos", conteos de inscritos/asistentes.
- **SQLite:** ya no queda **ninguna** dependencia. Los archivos y el código de SQLite fueron eliminados y una búsqueda global no encuentra referencias.
- **La app compila.** Para que funcione en ejecución hay que aplicar `supabase/02_migracion.sql` en tu proyecto (sección "Cómo terminar la migración").
- **Datos antiguos:** los usuarios y eventos que existían en SQLite eran locales de cada teléfono y **no se migran** (no hay servidor desde el cual copiarlos; las contraseñas además estaban con hash SHA-256 y no son transferibles a Supabase Auth). Todo se empieza limpio en Supabase.
