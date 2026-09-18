# KODE — Visión general

Este es el primero de tres documentos que explican el proyecto:

1. **Visión general** (este archivo): qué es la app, con qué está hecha, cómo está organizada y cómo se navega.
2. **[Datos y lógica](02-datos-y-logica.md)**: base de datos, repositorios, sesión y reglas de negocio.
3. **[Pantallas y estado](03-pantallas-y-estado.md)**: cada pantalla, los flujos de usuario, lo que falta y errores conocidos.

---

## 1. ¿Qué es KODE?

Una app Android para **publicar eventos y asistir a ellos**. Tiene dos roles que no son cuentas distintas: cualquier usuario puede ser *organizador* (crea eventos) y *asistente* (se inscribe a los de otros).

Lo que se puede hacer hoy:

| Acción | Requiere sesión |
|---|---|
| Ver la lista de eventos, buscar y filtrar | No |
| Ver el detalle de un evento | No |
| Registrarse / iniciar sesión | — |
| Inscribirse a un evento ("Quiero asistir") y recibir un pase con código | **Sí** |
| Crear un evento | **Sí** |
| Ver mis eventos (inscritos y organizados) | **Sí** |
| Ver mi perfil y cerrar sesión | **Sí** |

Cuando una acción requiere sesión y no la hay, la app manda al login y **al terminar continúa con lo que el usuario quería hacer** (ver la sección 5).

---

## 2. Tecnología

| Aspecto | Valor |
|---|---|
| Lenguaje | Kotlin |
| UI | Vistas clásicas con XML (no Compose, no ViewBinding: se usa `findViewById`) |
| Arquitectura de pantallas | **Una sola Activity** (`MainActivity`) + Fragments |
| Diseño | Material 3 (`Theme.Material3.DayNight.NoActionBar`) |
| Base de datos | SQLite directo con `SQLiteOpenHelper` (sin Room) |
| Sesión | `SharedPreferences` |
| Listas | `RecyclerView` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 37 / 37 |
| Paquete | `com.kode.app.kode_app` |
| Dependencias | appcompat, activity-ktx, core-ktx, constraintlayout, material |

No hay red ni servidor: **todo vive en el dispositivo**.

---

## 3. Estructura del proyecto

```
app/src/main/java/com/kode/app/kode_app/
│
├── MainActivity.kt            ← Única Activity: barra inferior y contenedor de fragments
│
├── core/
│   └── SessionManager.kt      ← Quién ha iniciado sesión (SharedPreferences)
│
├── data/                      ← Acceso a SQLite, dividido por responsabilidad
│   ├── AppDatabaseHelper.kt   ← Esquema, migraciones, datos iniciales, instancia única
│   ├── UserRepository.kt      ← Usuarios: registro, login, consultas
│   ├── EventRepository.kt     ← Eventos: consultas, crear, editar
│   └── RegistrationRepository.kt ← Inscripciones y códigos QR
│
├── model/
│   ├── Event.kt               ← data class de un evento
│   └── User.kt                ← data class de un usuario
│
├── components/
│   └── EventAdapter.kt        ← Adapter del RecyclerView de eventos (Home y Mis eventos)
│
└── ui/
    ├── home/HomeFragment.kt        ← Inicio: lista, búsqueda y filtros
    ├── events/
    │   ├── EventDetailFragment.kt  ← Detalle (vista de asistente o de creador)
    │   ├── EventFormFragment.kt    ← Formulario para crear evento
    │   ├── MyEventsFragment.kt     ← Mis eventos (pestañas)
    │   └── TicketFragment.kt       ← Pase del asistente
    ├── auth/
    │   ├── LoginFragment.kt
    │   └── RegisterFragment.kt
    ├── profile/ProfileFragment.kt  ← Perfil y cerrar sesión
    └── checkin/                    ← Vacía: reservada para el check-in

app/src/main/res/
├── layout/   ← Un XML por pantalla + item_event.xml (tarjeta de evento)
├── menu/     ← bottom_nav_menu.xml (los 4 botones de la barra inferior)
└── values/   ← themes.xml, colors.xml, strings.xml
```

**Regla de dependencias** (quién puede usar a quién):

```
ui  ──►  data  ──►  model
 │        ▲
 └──► core (SessionManager)
```

Las pantallas (`ui`) llaman a los repositorios (`data`) y al `SessionManager` (`core`). Los repositorios nunca conocen a las pantallas. Los modelos no dependen de nada.

---

## 4. Navegación

### Una Activity, muchos Fragments

`MainActivity` carga `activity_main.xml`, que tiene dos partes:

- `FrameLayout` **`mainContainer`**: donde se muestra el fragment actual.
- `BottomNavigationView` **`bottomNavigation`**: la barra con 4 pestañas.

Cambiar de pantalla siempre es `replace(R.id.mainContainer, fragment)`. Cuando el usuario debe poder volver atrás se agrega `addToBackStack(null)`.

### Barra inferior

| Botón | Qué abre |
|---|---|
| Inicio | `HomeFragment` |
| Mis eventos | `MyEventsFragment` |
| Crear evento | `EventFormFragment` si hay sesión, si no `LoginFragment` con destino `CREATE_EVENT` |
| Mi perfil | `ProfileFragment` |

Al abrir la app por primera vez se selecciona Inicio (`savedInstanceState == null`).

### Mapa de pantallas

```
                        ┌───────────────┐
                        │  MainActivity │
                        └───────┬───────┘
      ┌──────────────┬──────────┼───────────────┬───────────────┐
      ▼              ▼          ▼               ▼
    Home         MyEvents   EventForm         Profile
      │              │      (o Login)        (o Login)
      │              │
      └───► EventDetail ◄───┘
                │
     "Quiero asistir"
                │
        ¿hay sesión? ── no ──► Login ◄──► Register
                │ sí                │
                ▼                   │ (al terminar, continúa)
             Ticket ◄───────────────┘
```

---

## 5. El patrón central: "acción pendiente"

Es la idea más importante del proyecto. Cuando el usuario intenta algo que requiere sesión:

1. La pantalla abre `LoginFragment.newInstance(destination = "...")`, guardando **qué quería hacer** (y, si aplica, el evento).
2. Si el usuario no tiene cuenta, el login pasa esos mismos datos al `RegisterFragment`.
3. Tras iniciar sesión o registrarse, `continuePendingAction()` lee el destino y **ejecuta lo pendiente**.

| `destination` | Quién lo envía | Qué ocurre al autenticarse |
|---|---|---|
| `REGISTER_EVENT` | Botón "Quiero asistir" del detalle | Inscribe al usuario y abre su pase (`TicketFragment`) |
| `CREATE_EVENT` | Barra inferior o botón "+ Crear evento" del Home | Abre `EventFormFragment` |
| `MY_EVENTS` | Pestaña Mis eventos sin sesión | Abre `MyEventsFragment` |
| `PROFILE` | Pestaña Perfil sin sesión | Abre `ProfileFragment` |
| cualquier otro / vacío | Login normal | Abre `HomeFragment` |

Para agregar una acción nueva que requiera sesión hay que tocar **dos sitios**: el `when` de `LoginFragment.continuePendingAction` y el de `RegisterFragment.continuePendingAction`.

---

## 6. Cómo compilar y ejecutar

Desde la raíz del proyecto:

```bash
./gradlew :app:installDebug     # compila e instala en el emulador/dispositivo conectado
```

O abrir el proyecto en Android Studio y pulsar **Run**. Desde `adb` se puede inspeccionar la base de datos de un build debug:

```bash
adb exec-out run-as com.kode.app.kode_app cat databases/kode.db > kode.db
```

Luego se abre `kode.db` con cualquier visor de SQLite.

> Al cambiar `DATABASE_VERSION` la app **borra todos los datos** (ver documento 2). Durante el desarrollo es cómodo; antes de entregar hay que cambiarlo.
