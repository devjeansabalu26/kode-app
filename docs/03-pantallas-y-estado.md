# KODE — Pantallas y estado del proyecto

Tercer documento. Describe cada pantalla, los flujos completos y cuál es el estado real del proyecto: qué falta y qué errores hay. Ver también: [Visión general](01-vision-general.md) · [Datos y lógica](02-datos-y-logica.md).

---

## 1. Pantallas

### Inicio — `HomeFragment` (`fragment_home.xml`)

- Muestra la lista de eventos publicados con `RecyclerView` + `EventAdapter`.
- **Buscador**: filtra por título, descripción, categoría y ubicación mientras se escribe.
- **Filtros** (botones en una fila horizontal que se crean por código):
  - *Todos*
  - *Esta semana* (de lunes a domingo, según la fecha del evento)
  - Una por cada **categoría** existente en la base de datos
- Botón **"+ Crear evento"**: con sesión abre el formulario; sin sesión abre el login con destino `CREATE_EVENT`.
- Botón "¿Tienes un código o invitación?": **todavía no hace nada**.
- Al tocar una tarjeta abre el detalle.
- El layout usa `NestedScrollView` (con `ScrollView` normal el `RecyclerView` se quedaba cortado y solo se veían 2 eventos).

### Detalle de evento — `EventDetailFragment` (`fragment_event_detail.xml`)

Siempre vuelve a consultar el evento a la base de datos (los inscritos o el aforo pueden haber cambiado). Tiene **dos modos** según quién mira:

| Modo | Cuándo | Qué muestra |
|---|---|---|
| **Asistente** | Cualquiera que no sea el creador (con o sin sesión) | Botón con tres estados: **"Quiero asistir"**, **"Ver mi pase"** (si ya está inscrito) o **"Aforo completo"** (deshabilitado) |
| **Creador** | El usuario que creó el evento | Oculta el botón de asistir. Muestra barras de progreso de **inscritos / aforo** y de **asistentes / aforo**, y el botón "Editar" |

"Quiero asistir" sin sesión abre el login con destino `REGISTER_EVENT`. Con sesión inscribe y abre el pase.

### Formulario — `EventFormFragment` (`fragment_event_form.xml`)

Crea un evento. Campos: nombre, descripción, categoría, ubicación, fecha (DatePicker), hora (TimePicker) y capacidad.

Validaciones: todos los campos obligatorios, capacidad entero mayor que 0 y **fecha/hora futura**. La fecha se guarda como `yyyy-MM-dd HH:mm:ss`. Si no hay sesión válida, se redirige al login. Al guardar vuelve al Inicio.

### Mis eventos — `MyEventsFragment` (`fragment_my_events.xml`)

Dos pestañas (`TabLayout`) que reutilizan `EventAdapter`:

| Pestaña | Contenido | Si está vacía |
|---|---|---|
| **Asistiré** | Eventos en los que estoy inscrito | Botón "Explorar eventos" → Inicio |
| **Organizados** | Eventos que creé | Botón "+ Crear evento" → formulario |

Sin sesión muestra "Inicia sesión para ver tus eventos" con un botón al login (destino `MY_EVENTS`).

### Pase — `TicketFragment` (`fragment_ticket.xml`)

Muestra el nombre del usuario, el título del evento y el **código** de la inscripción, como texto. Recibe estos datos por argumentos (`eventId`, `eventTitle`, `qrCode`).

### Login y Registro — `LoginFragment`, `RegisterFragment`

- **Login**: correo + contraseña. Si es correcto crea la sesión y ejecuta la acción pendiente. Enlace a registro.
- **Registro**: nombre, DNI, celular, género (spinner), edad, correo y contraseña. Crea el usuario, **inicia sesión automáticamente** y ejecuta la acción pendiente. Enlace a login.
- Ambos reciben `destination`, `eventId` y `eventTitle` (ver el patrón de acción pendiente en el documento 1).

### Perfil — `ProfileFragment` (`fragment_profile.xml`)

Muestra los datos leídos de SQLite (no solo de la sesión): nombre, correo, DNI, celular, género y edad. Botón **cerrar sesión**, que vuelve al login. Sin sesión abre el login con destino `PROFILE`.

### Componente compartido — `EventAdapter`

Pinta cada tarjeta (`item_event.xml`): categoría, título, descripción, fecha, ubicación y un botón. Si el usuario actual es el creador, el botón dice **"Gestionar"**; si no, **"Ver evento"**. Recibe una lambda `onEventClick`.

---

## 2. Flujos completos

### A. Un visitante quiere asistir a un evento

```
Home → toca tarjeta → EventDetail → "Quiero asistir"
   → no hay sesión → Login (REGISTER_EVENT + evento)
        ├─ tiene cuenta → inicia sesión ─┐
        └─ "Registrarme" → Register ─────┤  (crea cuenta + sesión)
                                         ▼
                        registerUserInEvent()  → genera QR
                                         ▼
                                     TicketFragment
```

### B. Un usuario crea un evento

```
Home → "+ Crear evento"  (o barra inferior → "Crear evento")
   → ¿hay sesión? no → Login (CREATE_EVENT) → EventForm
                   sí → EventForm
   → completa y valida → createEvent() → vuelve a Home
```

### C. Consultar mis eventos

```
Barra inferior → "Mis eventos"
   → sin sesión → Login (MY_EVENTS) → MyEvents
   → con sesión → pestañas Asistiré / Organizados
        → tocar tarjeta → EventDetail (modo asistente o creador)
```

---

## 3. Lo que falta por hacer

Ordenado por importancia para el objetivo de la app.

1. **Check-in (`ui/checkin/` está vacío).** La columna `registrations.checked_in` y el conteo `attendedCount` ya existen y la pantalla del creador muestra la barra "asistentes", pero **nada marca una inscripción como asistida**. Falta una pantalla (escáner o ingreso de código) y un método en `RegistrationRepository`, por ejemplo `checkIn(qrCode)`.
2. **Edición de eventos.** El botón "Editar" abre `EventFormFragment.newEditInstance(eventId)`, pero el formulario **nunca lee ese argumento**: abre un formulario vacío que **crea un evento nuevo** en vez de modificar el existente. `EventRepository.updateEvent` ya está hecho y no se usa. Falta precargar los datos y llamar a `updateEvent` al guardar.
3. **QR real.** El pase muestra solo el texto del código. Hace falta generar una imagen QR (por ejemplo con ZXing) y, para el check-in, un lector.
4. **Botón "¿Tienes un código o invitación?"** del Inicio: sin funcionalidad.
5. **Cancelar una inscripción** y **eliminar/despublicar un evento** (la columna `status` ya permite otros valores además de `PUBLISHED`).

---

## 4. Errores y puntos débiles conocidos

### Datos

| Problema | Detalle |
|---|---|
| **Orden de la lista** | Se ordena por `date` como texto y hay dos formatos de fecha; el resultado no es cronológico (5 de octubre aparece antes que el 20 de septiembre) |
| **Fecha sin formato** | Los eventos creados muestran `2026-09-19 07:00:00` en la tarjeta, mientras que los de ejemplo muestran `20 Sep 2026 · 18:00` |
| **`onUpgrade` destructivo** | Subir la versión de la base de datos borra usuarios, eventos e inscripciones |
| **Aforo sin transacción** | `registerUserInEvent` comprueba el cupo y luego inserta por separado. Con un solo dispositivo no falla, pero no es atómico |

### Seguridad

| Problema | Detalle |
|---|---|
| **Hash de contraseña** | SHA-256 sin sal. Recomendado: PBKDF2 o bcrypt con sal aleatoria |
| **`allowBackup="true"`** | Los datos (incluida la base de usuarios) pueden salir en copias de seguridad. Considera desactivarlo o excluirlos en `backup_rules.xml` |
| **Datos en `SharedPreferences`** | Se guardan nombre y correo además del id. Con guardar solo el id bastaría (el resto se lee de SQLite) |

### Rendimiento y calidad

| Problema | Detalle |
|---|---|
| **Consultas en el hilo principal** | Todas las lecturas y escrituras de SQLite ocurren en la UI. Con pocos datos no se nota; conviene moverlas a un hilo o corrutina |
| **Barra inferior desincronizada** | Al abrir pantallas desde botones internos (por ejemplo, "+ Crear evento" del Inicio), la pestaña resaltada de la barra no cambia |
| **Textos fijos en el código** | Mensajes como "Ingresa tu correo" están en Kotlin, no en `strings.xml` (dificulta traducir o mantener) |
| **Fragments largos** | `EventDetailFragment`, `EventFormFragment`, `HomeFragment` y `RegisterFragment` tienen entre 500 y 730 líneas, en gran parte por el formato de un argumento por línea |
| **Sin pruebas reales** | Solo están los tests de ejemplo. Los repositorios se pueden probar fácilmente |
| **Carpeta `.idea/` versionada** | Suele ser mejor no subirla al repositorio |

---

## 5. Siguiente paso recomendado

Para que la app cumpla su propósito completo ("inscribirse y demostrar asistencia"), este es el orden sugerido:

1. **Arreglar la edición** (rápido; ya existe `updateEvent`).
2. **Unificar las fechas** al formato ISO y formatearlas al mostrarlas (arregla el orden y la presentación).
3. **QR real** en el pase.
4. **Check-in** en `ui/checkin/` + `checkIn()` en `RegistrationRepository`.
5. Endurecer seguridad (hash con sal, backup) y mover la base de datos fuera del hilo principal.
