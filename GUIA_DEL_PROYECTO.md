# Guía del proyecto Kode App

Guía pensada para alguien que usa **Android Studio** y **Kotlin** por primera vez. Explica qué es cada parte del proyecto, qué hace el código y cómo se conectan las piezas.

> ⚠️ **Actualización:** este documento se escribió cuando la app usaba **SQLite**. La app ya se migró a **Supabase** (Auth + PostgreSQL). Lo que cambió (base de datos, sesión, repositorios, corrutinas, `AppResult`, RLS) está explicado en [`docs/MIGRACION_SQLITE_A_SUPABASE.md`](docs/MIGRACION_SQLITE_A_SUPABASE.md). Las secciones 2, 3, 5, 8 y 9 (Android, navegación, ViewBinding y sintaxis de Kotlin) siguen vigentes; las secciones **4 (dependencias), 6 (capa de datos) y 7 (pantallas)** describen la versión SQLite anterior y se mantienen como referencia histórica.

---

## 1. ¿Qué es Kode App?

Una app Android para **descubrir eventos, inscribirse y recibir un pase con código QR**. También permite **crear y gestionar eventos propios**.

Lo que puede hacer un usuario:

| Acción | Dónde ocurre |
|---|---|
| Ver la lista de eventos, buscar y filtrar (por categoría o "esta semana") | Inicio (`HomeFragment`) |
| Registrarse e iniciar sesión | `RegisterFragment`, `LoginFragment` |
| Ver el detalle de un evento e inscribirse ("Quiero asistir") | `EventDetailFragment` |
| Recibir un pase con **código QR** al inscribirse | `TicketFragment` |
| Ver "eventos a los que asistiré" y "eventos que organizo" | Mis eventos (`MyEventsFragment`) |
| Crear un evento (nombre, fecha, hora, capacidad…) | `EventFormFragment` |
| Ver sus datos y cerrar sesión | Perfil (`ProfileFragment`) |
| Como creador, ver cuántos se inscribieron y cuántos asistieron | Detalle del evento (modo creador) |

**Datos de la app:** se guardan **en el propio teléfono** con una base de datos SQLite. No hay servidor ni internet.

---

## 2. Conceptos básicos de Android (léelos primero)

- **Activity:** una "ventana" de la app. Aquí solo hay una: `MainActivity`.
- **Fragment:** un "pedazo de pantalla" que vive dentro de una Activity. Cada pantalla de la app (Inicio, Login, Perfil…) es un Fragment. Se cambian con `replace(...)` sin abrir otra Activity.
- **Layout (XML):** archivo que describe **cómo se ve** una pantalla (botones, textos, listas). Están en `res/layout/`.
- **`R`:** clase que Android genera automáticamente. Te da acceso a recursos por nombre: `R.id.mainContainer`, `R.layout.item_event`, etc.
- **ViewBinding:** clase generada por cada layout que te da acceso directo a sus vistas y evita `findViewById`. Ver sección 8.
- **RecyclerView:** lista que reutiliza filas para ser eficiente. Necesita un **Adapter** (`EventAdapter`) que le dice cómo dibujar cada fila.
- **SharedPreferences:** almacenamiento clave-valor pequeño. Se usa para recordar la sesión.
- **SQLite:** base de datos local con tablas y consultas SQL.
- **Gradle:** el sistema que compila el proyecto y descarga librerías. Se configura con archivos `.gradle.kts`.

---

## 3. Estructura de carpetas

```
kodeapp/
├── build.gradle.kts            → Configuración global de Gradle
├── settings.gradle.kts         → Nombre del proyecto y repositorios (Google, Maven)
├── gradle/libs.versions.toml   → Catálogo de versiones de librerías
├── gradlew / gradlew.bat       → Ejecutan Gradle sin instalarlo
├── local.properties            → Ruta del SDK de Android en tu PC (no se sube a Git)
├── docs/                       → Documentos del proyecto
├── GUIA_DEL_PROYECTO.md        → Este archivo
└── app/                        → El módulo de la aplicación
    ├── build.gradle.kts        → Configuración del módulo app
    └── src/main/
        ├── AndroidManifest.xml → "Carnet de identidad" de la app
        ├── java/com/kode/app/kode_app/   → Código Kotlin
        │   ├── MainActivity.kt
        │   ├── components/EventAdapter.kt
        │   ├── core/SessionManager.kt
        │   ├── data/                  → Base de datos y repositorios
        │   ├── model/                 → Clases de datos (Event, User)
        │   └── ui/                    → Pantallas (fragments)
        └── res/                → Recursos: layouts, menú, colores, textos, íconos
```

> Aunque la carpeta se llame `java`, el código está escrito en **Kotlin** (`.kt`). Es solo el nombre tradicional de la carpeta.

---

## 4. Archivos de configuración

### `app/build.gradle.kts`
Define cómo se compila la app:

- `namespace` / `applicationId = "com.kode.app.kode_app"`: identificador único de la app.
- `minSdk = 24`: versión mínima de Android soportada (Android 7.0).
- `targetSdk = 37` / `compileSdk = 37`: versión de Android para la que se compila.
- `buildFeatures { viewBinding = true }`: activa ViewBinding.
- `dependencies`: librerías usadas:
  - **appcompat, core-ktx, activity-ktx:** base de Android con utilidades de Kotlin.
  - **constraintlayout:** diseño flexible de pantallas.
  - **material:** componentes visuales de Google (BottomNavigation, TabLayout…).
  - **zxing core:** librería que **genera los códigos QR**.
  - `testImplementation` / `androidTestImplementation`: librerías para pruebas.

### `gradle/libs.versions.toml`
Un solo lugar donde se declaran las versiones de las librerías. En `build.gradle.kts` se usan como `libs.material`, `libs.androidx.appcompat`, etc.

### `AndroidManifest.xml`
Le dice al sistema Android qué contiene la app:
- El nombre, el ícono y el tema (`Theme.Kodeapp`).
- Declara `MainActivity` como pantalla de inicio (`intent-filter` con `MAIN` y `LAUNCHER`).
- `windowSoftInputMode="adjustResize"`: cuando aparece el teclado, la pantalla se reajusta para no taparlo.

Toda Activity de la app **debe** estar declarada aquí.

---

## 5. Cómo funciona la navegación

`activity_main.xml` tiene dos partes:

1. `FrameLayout` con id `mainContainer`: el **contenedor** donde se muestran los fragments.
2. `BottomNavigationView` con id `bottomNavigation`: el **menú inferior** (Inicio, Mis eventos, Crear evento, Mi perfil), definido en `res/menu/bottom_nav_menu.xml`.

`MainActivity` escucha los toques del menú y cambia el fragment:

```kotlin
binding.bottomNavigation.setOnItemSelectedListener { item ->
    when (item.itemId) {
        R.id.nav_home -> { openFragment(HomeFragment()); true }
        ...
    }
}
```

Y `openFragment` hace el cambio:

```kotlin
supportFragmentManager.beginTransaction()
    .replace(R.id.mainContainer, fragment)   // reemplaza lo que hay en el contenedor
    .commit()                                // ejecuta el cambio
```

- `addToBackStack(null)` (en otros fragments) permite volver atrás con el botón "atrás".
- **No se usa `startActivity(Intent)`** porque no hay más Activities: toda la app es una Activity con fragments.

### Flujo cuando no has iniciado sesión
Si tocas "Crear evento" o "Quiero asistir" sin sesión, se abre `LoginFragment` con un **destino pendiente** (`destination`). Al iniciar sesión, la app te lleva a donde ibas:

| `destination` | A dónde te lleva tras el login |
|---|---|
| `REGISTER_EVENT` | Te inscribe en el evento y muestra el pase QR |
| `CREATE_EVENT` | Formulario de crear evento |
| `MY_EVENTS` | Mis eventos |
| `PROFILE` | Perfil |
| otro | Inicio |

Esos datos viajan en los **arguments** del fragment (un `Bundle`), y se crean con `newInstance(...)` en el `companion object`.

---

## 6. Capa de datos (carpeta `data/` y `model/`)

### Modelos (`model/`)
Son `data class`: clases que solo guardan datos. Kotlin genera automáticamente `equals`, `toString`, `copy`, etc.

```kotlin
data class Event(
    val id: Int,
    val creatorId: Long?,        // el "?" significa que puede ser null (eventos de ejemplo no tienen creador)
    val title: String,
    ...
    val registeredCount: Int = 0, // valor por defecto
    val attendedCount: Int = 0
)
```

`User` guarda: id, nombre, DNI, teléfono, género, edad y correo.

### `AppDatabaseHelper`
Administra la base de datos SQLite `kode.db`. Es un **Singleton**: solo existe una instancia en toda la app (`getInstance`), para no abrir la base varias veces.

Crea **3 tablas**:

| Tabla | Guarda | Detalles clave |
|---|---|---|
| `users` | Usuarios | `dni` y `email` son `UNIQUE` (no se repiten); guarda `password_hash`, **no** la contraseña real |
| `events` | Eventos | `creator_id` apunta al usuario creador; `status = 'PUBLISHED'`; capacidad por defecto 100 |
| `registrations` | Inscripciones | Une `event_id` + `user_id`; `qr_code` único; `checked_in` (asistió o no); `UNIQUE(event_id, user_id)` evita inscribirse dos veces |

Puntos importantes:
- `onCreate`: se ejecuta **una vez**, cuando la base no existe. Crea las tablas y mete **3 eventos de ejemplo** (`insertInitialEvents`).
- `onUpgrade`: si subes `DATABASE_VERSION`, **borra todas las tablas y las recrea** (se pierden los datos). Es aceptable en desarrollo, pero no en una app real.
- `setForeignKeyConstraintsEnabled(true)`: SQLite obliga a respetar las relaciones entre tablas.
- `"""..."""`: texto de varias líneas en Kotlin; `$TABLE_USERS` inserta el valor de esa constante (interpolación).

### `UserRepository`
Todo lo relacionado con usuarios:

- `registerUser(...)`: guarda un usuario. Limpia con `trim()`, pone el correo en minúsculas y guarda la contraseña **cifrada con SHA-256** (`hashPassword`).
- `login(email, password)`: busca un usuario cuyo correo y hash de contraseña coincidan. Devuelve `User?` (null si no existe).
- `userExistsByEmail` / `userExistsByDni`: para evitar duplicados.
- `getUserById`: obtiene un usuario por su id.
- `readUser(cursor)`: convierte una fila de la base en un objeto `User`.

Conceptos que verás:
- **`Cursor`:** el "puntero" con los resultados de una consulta. `moveToFirst()` / `moveToNext()` avanzan por las filas.
- **`.use { }`:** cierra el cursor automáticamente al terminar.
- **`rawQuery(sql, args)`:** ejecuta SQL. Los `?` se reemplazan por `args` (protege contra *SQL injection*).

### `EventRepository`
Todo lo relacionado con eventos:

- `getEvents()`: todos los eventos.
- `getEventById`, `getEventsByCategory`, `getEventsCreatedByUser`, `getEventsRegisteredByUser`.
- `getCategories()`: categorías distintas (para los botones de filtro).
- `createEvent(...)`: valida que el creador exista y guarda el evento.
- `updateEvent(...)`: solo el creador puede editar, y no permite bajar la capacidad por debajo de los ya inscritos.
- `queryEvents(where, args)`: la consulta central. Calcula con subconsultas `registered_count` (inscritos) y `attended_count` (asistentes con `checked_in = 1`).

### `RegistrationRepository`
Maneja las inscripciones:

`registerUserInEvent(eventId, userId)` devuelve el **código QR** (`String?`). Comprueba, en orden:
1. Que los ids sean válidos y el usuario exista.
2. Que el evento exista.
3. Que el usuario **no sea el creador** del evento.
4. Si ya estaba inscrito, devuelve su QR existente.
5. Que **haya cupo** (`registeredCount < capacity`).
6. Genera el código con el formato `KODE-E{evento}-U{usuario}-{8 caracteres aleatorios}` y lo guarda.

Devuelve `null` si algo falla.

### `SessionManager` (carpeta `core/`)
Recuerda quién inició sesión usando **SharedPreferences** (archivo `kode_session`):

- `createSession(...)`: guarda `logged_in`, `user_id`, `name`, `email`.
- `isLoggedIn()`: `true` si hay sesión y el id es válido.
- `getUserId()`, `getName()`, `getEmail()`.
- `logout()`: borra todo.

---

## 7. Pantallas (carpeta `ui/`)

Cada fragment sigue la misma estructura:

```kotlin
class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null   // puede ser null
    private val binding get() = _binding!!               // acceso seguro mientras la vista exista

    override fun onCreateView(...): View {               // 1. se crea la vista
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(...) { ... }              // 2. la vista ya existe: aquí va la lógica
    override fun onDestroyView() { _binding = null }     // 3. se destruye: se libera el binding
}
```

### `LoginFragment`
- Lee correo y contraseña, valida que no estén vacíos y llama a `userRepository.login(...)`.
- Si es correcto, crea la sesión con `SessionManager` y ejecuta `continuePendingAction` (ver tabla de destinos en la sección 5).
- El texto "Regístrate" abre `RegisterFragment` conservando el destino pendiente.

### `RegisterFragment`
Formulario con nombre, DNI, celular, género (Spinner), edad, correo y contraseña. Validaciones:
- Todos los campos obligatorios.
- DNI de **8 dígitos**, celular de **9 dígitos**.
- Género distinto de "Selecciona".
- Edad entre 1 y 120.
- Correo con formato válido (`Patterns.EMAIL_ADDRESS`).
- Contraseña de al menos 6 caracteres.
- Correo y DNI no registrados antes.

Si todo está bien, crea el usuario, inicia sesión y continúa con la acción pendiente.

### `HomeFragment`
- Carga los eventos y los muestra en un `RecyclerView`.
- **Filtros:** crea botones dinámicamente: "Todos", "Esta semana" y una por categoría.
- **Búsqueda:** un `TextWatcher` filtra en vivo por título, descripción, categoría y ubicación.
- `isEventThisWeek`: calcula si la fecha cae entre el lunes de esta semana y 7 días después.
- `parseEventDate`: intenta varios formatos de fecha hasta que uno funcione.
- Botón "Crear evento": si no hay sesión, va a Login.

### `EventDetailFragment`
Muestra un evento y **cambia según quién lo mira**:

- **Participante:** botón con texto según el caso: "Aforo completo" (deshabilitado), "Ver mi pase" (ya inscrito) o "Quiero asistir".
- **Creador:** se oculta el botón de inscripción y aparece el panel de gestión con barras de progreso de **Registrados** y **Asistieron**, más el botón "Editar".

`calculatePercentage` calcula el porcentaje y lo limita entre 0 y 100 con `coerceIn`.

### `TicketFragment`
Muestra el pase: nombre del usuario, evento, código y la **imagen QR**.

`generateQRCode(text)` usa ZXing: crea una matriz de bits (`BitMatrix`) de 512×512 y pinta cada píxel de negro o blanco en un `Bitmap`.

### `MyEventsFragment`
Dos pestañas (`TabLayout`): **Asistiré** y **Organizados**. Si una lista está vacía, muestra un mensaje con un botón de acción ("Explorar eventos" o "+ Crear evento"). Si no hay sesión, invita a iniciar sesión.

### `EventFormFragment`
Formulario para crear eventos:
- `DatePickerDialog` y `TimePickerDialog` para elegir fecha y hora (no permite fechas pasadas).
- Valida que todos los campos estén llenos, la capacidad sea mayor que 0 y la fecha sea futura.
- Guarda la fecha con formato `yyyy-MM-dd HH:mm:ss` en la base.

### `ProfileFragment`
Muestra los datos del usuario y el botón "Cerrar sesión". Si la sesión no es válida, redirige al login.

### `EventAdapter` (carpeta `components/`)
El adapter del `RecyclerView`. Tiene tres partes:

- `onCreateViewHolder`: infla `item_event.xml` (crea una fila).
- `onBindViewHolder`: rellena la fila con los datos del evento. El botón dice **"Gestionar"** si el usuario es el creador y **"Ver evento"** si no.
- `getItemCount`: cantidad de eventos.

Recibe una función `onEventClick` (una *lambda*) que se ejecuta al tocar el botón.

---

## 8. ViewBinding: qué cambió y por qué

Antes:

```kotlin
val etEmail = view.findViewById<EditText>(R.id.etEmail)
```

Ahora:

```kotlin
val etEmail = binding.etEmail
```

- Android genera una clase por layout: `fragment_login.xml` → `FragmentLoginBinding`; `item_event.xml` → `ItemEventBinding`.
- Ventajas: el tipo es correcto siempre, y si el id no existe el error aparece **al compilar**, no al ejecutar.
- En fragments, el binding se anula en `onDestroyView` para no retener memoria de vistas ya destruidas.

---

## 9. Diccionario de sintaxis de Kotlin (con ejemplos de TU app)

Cada entrada explica: **qué es**, **para qué sirve** y **dónde la usas** en Kode App.

### 9.1 Variables y tipos

#### `val` y `var`
- **Qué es:** declara una variable. `val` = valor fijo (no se reasigna). `var` = variable que puede cambiar.
- **Para qué sirve:** `val` da seguridad (no se cambia por error); `var` se usa cuando el valor debe cambiar.
- **En tu app:**
  ```kotlin
  val email = etEmail.text.toString().trim()        // no cambia después
  private var currentFilter: String = FILTER_ALL    // HomeFragment: cambia al tocar un filtro
  private var dateSelected = false                  // EventFormFragment: pasa a true al elegir fecha
  ```
- **Regla práctica:** usa `val` siempre y cambia a `var` solo si el compilador te lo exige.

#### Tipos: `String`, `Int`, `Long`, `Boolean`
- **Qué es:** el tipo de dato. `String` texto, `Int` entero normal, `Long` entero grande, `Boolean` verdadero/falso.
- **En tu app:** `Event.id` es `Int`, pero `User.id` y `creatorId` son `Long`. Por eso verás `-1L` (la `L` indica que es `Long`) y `.toString()` para pasarlos a SQL.

#### Tipo con `?` (nullable)
- **Qué es:** `String?` significa "puede ser texto o `null` (nada)". Sin `?`, nunca puede ser `null`.
- **Para qué sirve:** Kotlin te obliga a manejar el `null` y evita el error `NullPointerException`.
- **En tu app:**
  ```kotlin
  val creatorId: Long?                     // Event: los eventos de ejemplo no tienen creador
  fun login(...): User?                    // devuelve un User o null si el login falla
  fun registerUserInEvent(...): String?    // devuelve el código QR o null si no se pudo
  ```

#### Inferencia de tipos
- **Qué es:** Kotlin adivina el tipo; no siempre hace falta escribirlo.
- **En tu app:** `val session = SessionManager(requireContext())` no dice `: SessionManager`, Kotlin lo deduce.

#### `lateinit var`
- **Qué es:** variable que **prometes inicializar más tarde**, no en la declaración.
- **Para qué sirve:** en fragments, muchas cosas (repositorios, vistas) solo pueden crearse cuando el fragment ya está listo (`onViewCreated`), no antes.
- **En tu app:** `private lateinit var eventRepository: EventRepository` (HomeFragment). Si la usas antes de inicializarla, la app se cae.

#### `const val`
- **Qué es:** constante conocida al compilar, con nombre en MAYÚSCULAS.
- **En tu app:** `private const val ARG_EVENT_ID = "event_id"`. Evita escribir el mismo texto en varios sitios y equivocarte.

### 9.2 Manejo de `null` (seguridad)

#### `?.` (llamada segura)
- **Qué es:** "llama a esto solo si no es `null`; si es `null`, todo el resultado es `null`".
- **En tu app:** `arguments?.getString(ARG_DESTINATION)`. `arguments` puede ser `null` en un fragment, por eso se usa `?.`.

#### `?:` (operador Elvis)
- **Qué es:** "si lo de la izquierda es `null`, usa lo de la derecha".
- **Para qué sirve:** dar un valor por defecto o salir de la función.
- **En tu app:**
  ```kotlin
  arguments?.getString(ARG_EVENT_TITLE) ?: ""                 // si es null, texto vacío
  arguments?.getInt(ARG_EVENT_ID) ?: -1                       // si es null, -1
  val event = events.getEventById(eventId) ?: return null     // si no existe, sale de la función
  ```

#### `!!` (aserción no nula)
- **Qué es:** "te aseguro que no es `null`". Si lo es, **la app se cae**.
- **En tu app:** `private val binding get() = _binding!!`. Es seguro porque solo usas `binding` entre `onCreateView` y `onDestroyView`, cuando `_binding` sí existe.

#### `if (x != null)` / `if (x == null)`
- **Qué es:** comprobación clásica. Tras comprobar, Kotlin ya sabe que no es `null` (*smart cast*).
- **En tu app:** `if (qrCode == null) { showMessage(...); return }`. Después de esto `qrCode` se usa como `String` normal.

### 9.3 Funciones

#### `fun`
- **Qué es:** declara una función. Formato: `fun nombre(parámetros): TipoRetorno { ... }`.
- **En tu app:** `fun isLoggedIn(): Boolean { ... }`.
- Si no devuelve nada no se escribe tipo (equivale a `Unit`): `private fun showMessage(message: String) { ... }`.

#### `private`
- **Qué es:** solo se puede usar dentro de esa clase. Sin `private`, es público.
- **Para qué sirve:** esconder detalles internos. Ej.: `private fun hashPassword(...)` solo lo usa `UserRepository`.

#### `override`
- **Qué es:** "estoy reemplazando una función que ya existe en la clase padre".
- **En tu app:** `override fun onCreateView(...)`, `onViewCreated(...)`, `onDestroyView()`: son funciones de `Fragment` que tú personalizas. También `onCreate` y `onUpgrade` en la base de datos.

#### Parámetros con nombre
- **Qué es:** al llamar una función, escribes el nombre del parámetro.
- **Para qué sirve:** legibilidad y no confundir el orden.
- **En tu app:** `userRepository.login(email = email, password = password)`.

#### Parámetros por defecto
- **Qué es:** un parámetro con valor ya asignado; si no lo pasas, usa ese valor.
- **En tu app:** `fun newInstance(destination: String?, eventId: Int = -1, eventTitle: String? = null)`. Puedes llamar `LoginFragment.newInstance(destination = "PROFILE")` sin pasar los otros dos.

#### `return`
- **Qué es:** termina la función y devuelve un valor.
- **En tu app:** `return preferences.getBoolean("logged_in", false)`. Dentro de una lambda se usa `return@etiqueta` (ver 9.6).

### 9.4 Clases y objetos

#### `class`
- **Qué es:** molde para crear objetos. `class SessionManager(context: Context)` recibe un `Context` al crearse.
- **En tu app:** `SessionManager(requireContext())` crea un objeto de esa clase.

#### `data class`
- **Qué es:** clase pensada solo para guardar datos. Kotlin genera solo `toString`, `equals`, `copy`, etc.
- **En tu app:** `Event` y `User`.

#### Constructor primario
- **Qué es:** los parámetros van en el encabezado de la clase, entre paréntesis.
- **En tu app:** `class EventAdapter(private val events: List<Event>, private val currentUserId: Long?, private val onEventClick: (Event) -> Unit)`. Poner `val` o `private val` los convierte en propiedades de la clase.

#### Herencia con `:`
- **Qué es:** `:` significa "extiende" (hereda de) o "implementa".
- **En tu app:** `class LoginFragment : Fragment()` es un `Fragment`. `class MainActivity : AppCompatActivity()` es una Activity.

#### `companion object`
- **Qué es:** el equivalente a `static` de Java: cosas que pertenecen a la clase, no a cada objeto.
- **En tu app:** constantes (`ARG_EVENT_ID`) y las "fábricas" `newInstance(...)`. Se llama así: `LoginFragment.newInstance(...)`, sin crear objeto antes.

#### `private constructor` y Singleton
- **Qué es:** constructor privado: nadie puede crear el objeto con `AppDatabaseHelper(...)`; solo a través de `getInstance`.
- **Para qué sirve:** que exista **una sola** base de datos abierta en toda la app.
- **En tu app:**
  ```kotlin
  @Volatile private var instance: AppDatabaseHelper? = null
  fun getInstance(context: Context): AppDatabaseHelper {
      return instance ?: synchronized(this) {
          instance ?: AppDatabaseHelper(context.applicationContext).also { instance = it }
      }
  }
  ```
  `@Volatile` y `synchronized` evitan que dos hilos creen dos instancias a la vez.

#### Propiedad con `get()`
- **Qué es:** una propiedad que se calcula cada vez que la lees.
- **En tu app:** `private val binding get() = _binding!!`.

#### `object : Interfaz { ... }` (objeto anónimo)
- **Qué es:** crear al vuelo un objeto que implementa una interfaz.
- **En tu app:** el buscador de `HomeFragment` (`object : TextWatcher { ... }`) y las pestañas de `MyEventsFragment` (`object : TabLayout.OnTabSelectedListener { ... }`).

#### `as`
- **Qué es:** convierte un tipo en otro (cast).
- **En tu app:** `startOfWeek.clone() as Calendar`. `clone()` devuelve un tipo genérico y se convierte a `Calendar`.

### 9.5 Colecciones

#### `List<T>`, `listOf`, `emptyList`, `mutableListOf`
- `List<Event>`: lista de solo lectura de eventos.
- `listOf("a", "b")`: crea una lista fija. En tu app: los géneros del Spinner y los formatos de fecha.
- `emptyList()`: lista vacía (valor inicial de `allEvents`).
- `mutableListOf<String>()`: lista a la que puedes agregar (`add`). En tu app: `getCategories()` va agregando categorías.

#### `filter { }`
- **Qué es:** se queda solo con los elementos que cumplen la condición.
- **En tu app:** `eventRepository.getEvents().filter { event -> isEventThisWeek(event.date) }` y el buscador (`title.lowercase().contains(search)`).

#### `forEach { }`
- **Qué es:** repite el bloque para cada elemento.
- **En tu app:** `categories.forEach { category -> addFilterButton(text = category) { ... } }` crea un botón por categoría.

#### `firstOrNull()`
- **Qué es:** el primer elemento o `null` si la lista está vacía. En tu app: `getEventById`.

#### `arrayOf(...)`
- **Qué es:** un arreglo. En tu app: los argumentos de las consultas SQL, `arrayOf(eventId.toString())`.

#### `for` y `until`
- **En tu app:** `for (x in 0 until size)` (TicketFragment) recorre de `0` hasta `size - 1` para pintar los píxeles del QR. `until` excluye el último número.

#### `coerceIn(min, max)`
- **Qué es:** limita un número a un rango. En tu app: `(value * 100 / maximum).coerceIn(0, 100)` asegura que el porcentaje no pase de 100.

### 9.6 Lambdas y funciones como valores

#### Lambda `{ x -> ... }`
- **Qué es:** un bloque de código que se puede guardar o pasar como argumento. Los parámetros van antes de `->`.
- **En tu app:** `filter { event -> ... }`. Si solo hay un parámetro, puedes usar `it`.

#### Lambda como último argumento
- **Qué es:** si el último parámetro es una función, el bloque `{ }` va **fuera** de los paréntesis.
- **En tu app:**
  ```kotlin
  binding.btnLogin.setOnClickListener { ... }                      // se ejecuta al tocar el botón
  showEmpty(message = "...", actionText = "...") { ... }           // el bloque es el parámetro onAction
  ```

#### Tipo función `() -> Unit` y `(Event) -> Unit`
- **Qué es:** el tipo de una función. `() -> Unit` = no recibe nada ni devuelve nada útil. `(Event) -> Unit` = recibe un `Event`.
- **En tu app:** `onClick: () -> Unit` (botones de filtro) y `onEventClick: (Event) -> Unit` (`EventAdapter`). Quien crea el adapter decide **qué pasa al tocar** un evento; el adapter solo lo ejecuta.

#### `return@etiqueta`
- **Qué es:** dentro de una lambda, `return` a secas saldría de la función externa. `return@setOnClickListener` sale solo de esa lambda.
- **En tu app:** las validaciones: `if (email.isEmpty()) { showMessage("..."); return@setOnClickListener }`.

#### `_` (guion bajo)
- **Qué es:** "recibo este parámetro pero no lo uso".
- **En tu app:** `{ _, year, month, day -> ... }` (el `DatePickerDialog` entrega la vista, pero solo importan año, mes y día) y `catch (_: Exception)`.

#### `= Unit`
- **En tu app:** `override fun onTabUnselected(tab: TabLayout.Tab) = Unit`. Significa "esta función existe pero no hace nada".

### 9.7 Control de flujo

#### `if / else` (también como expresión)
- **Qué es:** en Kotlin `if` puede **devolver un valor**.
- **En tu app:**
  ```kotlin
  val fragment = if (session.isLoggedIn()) { EventFormFragment() } else { LoginFragment.newInstance(destination = "CREATE_EVENT") }
  ```

#### `when`
- **Qué es:** como `switch`, pero más potente; también devuelve valor.
- **En tu app:** decide a dónde ir tras el login: `when (destination) { "REGISTER_EVENT" -> ... "CREATE_EVENT" -> ... else -> ... }`. En `MainActivity`: qué hacer según el botón del menú.

#### `&&`, `||`, `!`
- **Qué es:** "y", "o", "no". En tu app: `loggedIn && userId > 0`, `name.isEmpty() || dni.isEmpty()`, `!session.isLoggedIn()`.

#### `return` temprano (*guard clauses*)
- **Qué es:** salir pronto si algo está mal, en vez de anidar muchos `if`.
- **En tu app:** `if (eventId <= 0) { showMessage(...); return }`. Es la técnica más repetida en tus fragments y repositorios.

#### `try / catch`
- **Qué es:** captura errores para que la app no se caiga.
- **En tu app:** `parseEventDate` prueba varios formatos de fecha y, si uno falla, `catch` lo ignora y prueba el siguiente.

### 9.8 Texto

#### Plantillas `$variable` y `${expresión}`
- **En tu app:** `"Bienvenido ${user.name}"`, `"Registrados: ${event.registeredCount} / ${event.capacity}"`, `"KODE-E$eventId-U$userId-$random"`.

#### Texto multilínea con tres comillas y `.trimIndent()`
- **Qué es:** texto de varias líneas; `trimIndent()` quita la sangría sobrante.
- **En tu app:** todas las consultas SQL (`CREATE TABLE`, `SELECT`).

#### Funciones de texto

| Función | Qué hace | Dónde |
|---|---|---|
| `.trim()` | Quita espacios al inicio y final | Todos los formularios |
| `.lowercase()` / `.uppercase()` | Minúsculas / mayúsculas | Correos, búsqueda, código QR |
| `.isEmpty()` | ¿Está vacío? | Validaciones |
| `.contains("x")` | ¿Contiene el texto? | Buscador de `HomeFragment` |
| `.toIntOrNull()` | Convierte a número o devuelve `null` si no puede | Edad y capacidad |
| `.take(8)` | Los primeros 8 caracteres | Generación del código QR |
| `.replace("-", "")` | Reemplaza texto | Limpia el UUID |
| `.toString()` | Convierte a texto | Casi en todas partes |

### 9.9 Funciones de alcance (`apply`, `also`, `use`)

#### `apply { }`
- **Qué es:** configura un objeto y **lo devuelve**. Dentro, `this` es el objeto.
- **En tu app:**
  ```kotlin
  val values = ContentValues().apply { put("title", title); put("date", date) }    // prepara los datos a guardar
  LoginFragment().apply { arguments = Bundle().apply { putString(ARG_DESTINATION, destination) } }
  ```

#### `also { }`
- **Qué es:** hace algo extra con el objeto y lo devuelve. En tu app: `AppDatabaseHelper(...).also { instance = it }` guarda la instancia recién creada.

#### `use { }`
- **Qué es:** cierra el recurso automáticamente al terminar, aunque haya un error.
- **En tu app:** `rawQuery(...).use { ... }` cierra el `Cursor` de SQLite. Sin esto habría fugas de memoria.

### 9.10 Anotaciones

#### `@Volatile`
- Hace que un cambio de variable sea visible de inmediato para todos los hilos. Se usa en el Singleton de la base.

### 9.11 Paquetes e importaciones

- `package com.kode.app.kode_app.ui.auth`: la "carpeta lógica" del archivo.
- `import ...`: trae clases de otros paquetes. `import com.kode.app.kode_app.databinding.FragmentLoginBinding` es la clase que genera ViewBinding.
- `import ....AppDatabaseHelper.Companion.TABLE_USERS`: importa una constante del `companion object` para usarla sin escribir el nombre completo.

### 9.12 Elementos de Android que verás mucho

| Elemento | Qué es | Dónde |
|---|---|---|
| `requireContext()` | El `Context` (acceso a recursos y servicios) del fragment; falla si el fragment no está adjunto | Casi todos los fragments |
| `arguments` | `Bundle` con los datos que le pasaste al fragment | `newInstance` y `arguments?.getString(...)` |
| `Bundle` | "Caja" de pares clave-valor (`putString`, `putInt`, `getString`) | Pasar datos entre pantallas |
| `parentFragmentManager` | Administrador que cambia fragments | `replace(R.id.mainContainer, ...)` |
| `Toast.makeText(...).show()` | Mensaje flotante breve | `showMessage(...)` |
| `View.GONE` / `View.VISIBLE` | Ocultar (sin ocupar espacio) / mostrar una vista | Modo creador vs participante |
| `setOnClickListener { }` | Qué hacer al tocar una vista | Todos los botones |
| `ContentValues` | Mapa de columna a valor para insertar/actualizar en SQLite | Repositorios |
| `Calendar` / `SimpleDateFormat` | Manejo y formato de fechas | `EventFormFragment`, `HomeFragment` |
| `Locale.getDefault()` | Idioma/región del teléfono para formatear | Formatos de fecha |
| `UUID.randomUUID()` | Genera un identificador aleatorio único | Código QR |
| `MessageDigest.getInstance("SHA-256")` | Cifra (hash) la contraseña | `UserRepository.hashPassword` |
| `Bitmap`, `Color`, `BitMatrix` | Imagen, colores y matriz del QR | `TicketFragment` |
| `Patterns.EMAIL_ADDRESS` | Expresión regular de Android para validar correos | `RegisterFragment` |
| `Spinner` + `ArrayAdapter` | Lista desplegable y su adaptador | Selector de género |
| `TextWatcher` | Escucha cada cambio de texto | Buscador en vivo |
| `dpToPx` (tu función) | Convierte `dp` (unidad de diseño) a píxeles reales | Márgenes de los botones de filtro |

---

## 10. Flujo completo de ejemplo: inscribirse a un evento

1. En Inicio tocas **"Ver evento"** → `EventAdapter` ejecuta `onEventClick` → se abre `EventDetailFragment`.
2. Tocas **"Quiero asistir"** → `registerOrLogin(event)`.
3. **Sin sesión:** se abre `LoginFragment(destination = "REGISTER_EVENT")`. Tras iniciar sesión continúa el flujo.
4. **Con sesión:** `RegistrationRepository.registerUserInEvent` valida y guarda la inscripción.
5. Se abre `TicketFragment` con el QR generado.
6. Desde ahí, en el detalle, el botón pasa a decir **"Ver mi pase"**.

---

## 11. Cómo ejecutar el proyecto

1. Abre la carpeta del proyecto en **Android Studio**.
2. Espera a que termine el **Gradle Sync**.
3. Crea un emulador en **Device Manager** o conecta un teléfono con depuración USB.
4. Pulsa el botón **Run ▶**.

Por línea de comandos (desde la raíz): `./gradlew.bat assembleDebug`.

---

## 12. Observaciones y posibles mejoras

Cosas que encontré al revisar el proyecto. No afectan el funcionamiento actual:

- **Editar evento está a medias:** el botón "Editar" abre `EventFormFragment.newEditInstance(id)`, pero el formulario todavía **no lee** ese id ni carga los datos, así que siempre actúa como "crear". `EventRepository.updateEvent` ya existe y está listo para usarse.
- **Marcar asistencia:** la base tiene `checked_in`, pero no hay pantalla que lo cambie (la carpeta `ui/checkin/` está vacía). Por eso "Asistieron" siempre será 0.
- **`EventDetailFragment`** contiene constantes y un `newEditInstance` duplicado que apunta a `EventFormFragment`; sobran ahí.
- **Contraseñas:** SHA-256 sin "salt" es débil para una app real. Para producción convendría usar un algoritmo como bcrypt/Argon2.
- **`onUpgrade`** borra todos los datos al cambiar la versión de la base.
- **Fechas como texto:** se guardan como `String`, lo que obliga a `parseEventDate` a probar varios formatos.
- Hay imports sin usar en varios archivos (Android Studio los marca en gris; se limpian con `Ctrl + Alt + O`).
- Textos escritos directamente en el código: lo recomendado es moverlos a `res/values/strings.xml`.
