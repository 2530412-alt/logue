# AquaGo · App móvil con inicio de sesión por roles

**AquaGo** facilita la solicitud de agua potable por pipa en colonias sin toma propia. El caso de estudio es la colonia
Las Camelinas, en Santiago Tulantepec de Lugo Guerrero, Hidalgo. Ahí los ciudadanos hoy piden el servicio en persona
con el delegado o por WhatsApp, sin saber cuándo llegará el agua.

Esta etapa corresponde a la **aplicación móvil**:
- Pantalla de inicio de sesión.
- Arquitectura local con un backend **Ktor** y un frontend **Kotlin Multiplatform**.
- Credenciales predefinidas en un archivo JSON.
- Navegación a una vista distinta según el rol de cada usuario.

**Equipo:** Sebastián Muñoz Ríos · Valeria Rangel Granados · Brenda Montes Lázaro

**Universidad Politécnica de Tulancingo** · Octubre 2026

El proyecto está dividido en dos partes que se ejecutan por separado y se comunican por HTTP con JSON:

| Parte | Carpeta | Tecnología | Qué hace |
|---|---|---|---|
| **Backend** | [`backend/`](backend) | Ktor Server 3.6, generado con el asistente [start.ktor.io](https://start.ktor.io), motor Netty | API REST en el puerto **8081** (local) o en la nube. Valida las credenciales de `users.json` con BCrypt y responde con el rol y un token |
| **Frontend** | [`frontend/`](frontend) | Kotlin Multiplatform + Compose Multiplatform, Ktor Client | App para Android (Android Studio), web e iOS. Login, Crear cuenta, Recuperar contraseña y una vista por rol |

## Arquitectura local

```
┌──────────────── FRONTEND (Kotlin Multiplatform) ─────────────────┐          ┌───────── BACKEND (Ktor Server) ──────────┐
│                                                                  │          │                                          │
│  LoginScreen ──► validateCredentials() ──► login()  (AuthApi.kt) │   JSON   │  POST /login            (Routing.kt)     │
│                                              │                   │ ───────► │     │                                    │
│                               Ktor Client + ContentNegotiation   │          │  call.receive<LoginRequest>()            │
│                                              │                   │ ◄─────── │     │  compara con                       │
│  App.kt guarda la sesión (username, role,    │                   │ username │  resources/users.json (hash BCrypt)      │
│        token)                                                    │  + role  │     │                                    │
│      └─► when (role)                                             │ + token  │  200 LoginResponse(username, role, token)│
│            ├─ CIUDADANO → CiudadanoScreen  "Mis solicitudes"     │          │  401 credenciales incorrectas            │
│            ├─ OPERADOR  → OperadorScreen   "Mis entregas de hoy" │          │  429 cuenta bloqueada · 400 inválida     │
│            └─ ADMIN     → AdminScreen      "Panel de solicitudes"│          │                                          │
└──────────────────────────────────────────────────────────────────┘          └──────────────────────────────────────────┘
```

1. El usuario escribe su usuario y contraseña en **LoginScreen**. Mientras escribe, la app ya despertó al servidor con `GET /salud`.
2. **validateCredentials()** revisa que no estén vacíos y **login()** manda `POST /login`. Ktor Client convierte el objeto `LoginRequest` a JSON.
3. El backend convierte ese JSON en `LoginRequest`, busca al usuario en **users.json** y compara la contraseña con su hash BCrypt:
   - Si coincide, responde `200` con `LoginResponse(username, role, token, expiresIn)`.
   - Si no coincide, responde `401`. Al tercer intento fallido, responde `429` y bloquea la cuenta 5 minutos.
   - Si los campos llegan vacíos, responde `400`.
4. **App.kt** guarda la sesión (con el token) y, con un `when (role)`, muestra la vista que corresponde a ese rol. Cada petición siguiente manda `Authorization: Bearer <token>`.
5. Al presionar **Cerrar sesión**, la sesión se borra y la app vuelve al login. Si el token vence (12 h), la app regresa sola al login con el aviso «Tu sesión expiró».

La pantalla muestra un mensaje distinto para cada caso: usuario o contraseña incorrectos, cuenta bloqueada, error del servidor, sin conexión o «Despertando el servidor...» cuando la primera petición tarda.

## Usuarios de prueba (`backend/src/main/resources/users.json`)

| Rol | Usuario | Contraseña | Vista que abre |
|---|---|---|---|
| Ciudadano | `ciudadano` | `1234` | **Mis solicitudes:** solicitudes de agua y su estatus |
| Operador (repartidor) | `operador` | `1234` | **Mis entregas de hoy:** ruta del día y entregas |
| Administrador (delegado) | `admin` | `1234` | **Panel de solicitudes:** pendientes, en camino, entregadas y canceladas |

> Las credenciales siguen predefinidas en el JSON, como lo pide la práctica, pero desde la Fase 1 el archivo guarda solo el
> **hash BCrypt** de cada contraseña (`passwordHash`), nunca el texto. Para agregar un usuario, genera su hash con
> `gradlew run --args="hash MiContraseña"` y pégalo en `users.json`. En la Fase 2 los usuarios pasan a MySQL.
> Antes del piloto cambien las contraseñas `1234`: el repositorio es público.
> Las vistas de cada rol ya leen sus datos del backend (`GET /solicitudes`, `GET /entregas`, `GET /pagos-prediales`). Las listas de
> solicitudes empiezan vacías: todavía no hay forma de crear una solicitud desde la app (SOL-03), así que se cargan a mano en `solicitudes.json`.

## Pantallas de acceso a la cuenta

Se abren desde el login y ya hablan con el backend.

| Pantalla | Cómo se abre | Qué hace |
|---|---|---|
| **Crear cuenta** | «¿No tienes cuenta? Regístrate» | Registro del ciudadano: nombre completo, teléfono (10 dígitos), domicilio, foto del comprobante de pago predial (con la cámara, solo Android), usuario y contraseña. `POST /registro` guarda la cuenta como «En revisión»; no puede iniciar sesión hasta que el delegado la active. Rechaza usuarios y teléfonos repetidos. Las cuentas de repartidor y administrador las da de alta el delegado |
| **Recuperar contraseña** | «¿Olvidaste tu contraseña?» | Tres pasos: usuario o teléfono → código de 6 dígitos → contraseña nueva. El servidor genera el código (vigente 10 minutos, 5 intentos); mientras no exista el envío por SMS (NOT-05), el código solo aparece en el registro del servidor |
| **Perfil** | Encabezado de cada vista | Consulta y edita nombre, teléfono y domicilio (`GET` y `PATCH /usuarios/yo`) |
| **Mis pagos prediales** | Inicio del ciudadano | Historial de pagos y visor de la foto de cada comprobante |

Las reglas de validación están en `AuthValidation.kt` y tienen pruebas en `AuthValidationTest.kt`.

## Estructura de carpetas

```
logue/
├── backend/                          PROYECTO KTOR (asistente start.ktor.io)
│   ├── build.gradle.kts              Dependencias: Ktor Server, Netty, ContentNegotiation, CORS, kotlinx.serialization
│   └── src/
│       ├── main/kotlin/
│       │   ├── main.kt               Arranca Netty en el puerto 8081 (o PORT); comandos «hash» y «probar»
│       │   ├── Application.kt        aquaGoModule(): registro de peticiones, CORS, serialización, errores, seguridad y rutas
│       │   ├── Security.kt           Token JWT, permisos por rol, bloqueo por intentos y límite por IP
│       │   ├── Users.kt              Hash BCrypt, UserRepository (buscar, agregar, actualizar) y users.json bajo DATA_DIR
│       │   ├── Cuentas.kt            Registro, recuperación de contraseña, perfil y cambio de estado de cuenta
│       │   ├── Solicitudes.kt        Solicitudes en solicitudes.json, GET /solicitudes y GET /entregas
│       │   ├── Serialization.kt      install(ContentNegotiation) { json() }
│       │   ├── Routing.kt            GET /, GET /salud, POST /login y /pagos-prediales (protegidas con token); registra además las rutas de cuentas y solicitudes
│       │   ├── PagosPrediales.kt     Guarda en disco las fotos de los pagos prediales
│       │   ├── RemoteCopy.kt         Copia opcional de las fotos a otro servidor por SFTP (SSH)
│       │   ├── Probador.kt           Pruebas funcionales y de rendimiento contra el servidor desplegado
│       │   └── Models.kt             DTOs @Serializable: User, EstadoCuenta, LoginRequest, LoginResponse, Salud, PagoPredial, Perfil, Registro y Recuperación
│       ├── main/resources/
│       │   ├── users.json            Usuarios predefinidos con el hash BCrypt de su contraseña y su rol
│       │   └── logback.xml           Formato del registro en consola
│       └── test/kotlin/              AuthTest, PagosPredialesTest, RemoteCopyTest, ServerTest, CuentasTest y SolicitudesTest (67 pruebas con testApplication)
│
└── frontend/                         PROYECTO KOTLIN MULTIPLATFORM (Android Studio)
    ├── androidApp/                   App Android: MainActivity.kt y AndroidManifest.xml (permisos de red)
    ├── webApp/                       Versión web (Wasm / JS)
    ├── iosApp/                       Proyecto de Xcode (se abre en una Mac)
    ├── gradle/libs.versions.toml     Versiones de las librerías
    └── shared/src/
        ├── commonMain/               CÓDIGO COMPARTIDO por todas las plataformas
        │   ├── App.kt                Raíz: guarda la sesión y elige la pantalla según el rol
        │   ├── LoginScreen.kt        Pantalla de inicio de sesión
        │   ├── RegisterScreen.kt     Crear cuenta: formulario y envío del registro (POST /registro)
        │   ├── RecoverPasswordScreen.kt  Recuperar contraseña: solicitar y confirmar el código
        │   ├── Screens.kt            Vistas por rol (Ciudadano, Operador, Administrador) con datos del servidor
        │   ├── PerfilScreen.kt       Perfil: ver y editar nombre, teléfono y domicilio
        │   ├── PagosPredialesScreen.kt  Historial de pagos y visor de fotos
        │   ├── SolicitudesVista.kt   Etiquetas, colores y contadores de las listas de solicitudes
        │   ├── CuentasApi.kt         Llamadas a registro, recuperación, perfil, solicitudes, entregas y pagos
        │   ├── AuthApi.kt            Ktor Client: POST /login, resultado del login y despertar al servidor
        │   ├── PagosApi.kt           POST /pagos-prediales con el token
        │   ├── AuthValidation.kt     Reglas de los formularios
        │   ├── Dtos.kt               DTOs @Serializable y roles
        │   ├── Config.kt             SERVIDOR_NUBE, expect val SERVIDOR_LOCAL y BASE_URL según SERVIDOR_URL
        │   ├── AuthComponents.kt / Components.kt   Piezas de interfaz reutilizables
        │   └── Theme.kt / AquaGoTheme.kt           Colores y tema de AquaGo
        ├── androidMain/ jsMain/ wasmJsMain/ iosMain/   CÓDIGO ESPECÍFICO: actual val SERVIDOR_LOCAL y decodeImage (ImageDecoder)
        └── commonTest/               Pruebas de las validaciones, de las vistas de solicitudes y de la dirección del servidor
```

## Conexión con el serializador (kotlinx.serialization)

Las clases que viajan por la red llevan `@Serializable`. Así el compilador genera el código que las convierte a JSON y de regreso.

| Dónde | Archivo | Qué hace |
|---|---|---|
| Plugin de Gradle | `org.jetbrains.kotlin.plugin.serialization` en `backend/build.gradle.kts` y `frontend/shared/build.gradle.kts` | Genera el serializador de cada clase `@Serializable` |
| Backend | `Serialization.kt` | `install(ContentNegotiation) { json() }`: `call.receive<LoginRequest>()` lee JSON y `call.respond(LoginResponse(...))` responde JSON |
| Backend | `Users.kt` | `loadUsers()` lee `users.json` y `Json.decodeFromString(...)` lo convierte en `List<User>` |
| Frontend | `AuthApi.kt` | `install(ContentNegotiation) { json() }` en Ktor Client: `setBody(LoginRequest(...))` envía JSON y `res.body()` lo convierte en `LoginResponse` |
| Ambos | `Models.kt` (backend) y `Dtos.kt` (frontend) | DTOs con los mismos nombres de campo: `LoginRequest(username, password)` y `LoginResponse(username, role, token, expiresIn)` |

## Dirección del backend por plataforma

Por defecto **todas las plataformas usan el servidor en la nube** (`SERVIDOR_NUBE` en `Config.kt`, con HTTPS), así la app
funciona en cualquier teléfono y con cualquier red. Para trabajar con el backend de tu computadora no se edita código:
se usa la variable `SERVIDOR_URL` en `frontend/local.properties`, que no se sube a GitHub. Hay un ejemplo en
`frontend/local.properties.ejemplo`.

| `SERVIDOR_URL` en local.properties | A dónde se conecta la app de Android |
|---|---|
| (sin definir) | Servidor en la nube: `https://logue-backend.onrender.com` |
| `local` | Backend de tu PC visto desde el emulador: `http://10.0.2.2:8081` (`SERVIDOR_LOCAL` de `Config.android.kt`) |
| `http://localhost:8081` | Celular por cable con `adb reverse tcp:8081 tcp:8081` |
| `http://192.168.1.70:8081` | Celular en la misma red Wi-Fi (IP de tu PC, comando `ipconfig`) |
| `https://tu-servicio.up.railway.app` | Otro servidor en la nube |

`Config.kt` conserva `expect`/`actual` para lo que sí cambia por plataforma, la dirección local: `10.0.2.2` en el emulador
de Android y `localhost` en iOS y la web. `androidApp/build.gradle.kts` lee `SERVIDOR_URL` y la entrega como
`BuildConfig.SERVIDOR_URL`; `MainActivity` se la pasa a `configurarServidor()`. Si el backend se muda a Railway,
solo cambia la constante `SERVIDOR_NUBE`.

**HTTPS en Android** (`androidApp/src/*/res/xml/network_security_config.xml`):
- La versión **release** solo permite HTTPS. Antes, `usesCleartextTraffic="true"` dejaba viajar sin cifrar cualquier petición, incluida la contraseña.
- La versión **debug** (la que corre desde Android Studio) permite HTTP para llegar al backend local.
- `ACCESS_LOCAL_NETWORK`: desde **Android 17** hace falta para llegar a `10.0.2.2`. `MainActivity` lo pide al abrir la app; solo importa con el backend local.

**Servidor dormido.** Render gratuito apaga el servicio tras 15 minutos sin uso y tarda cerca de un minuto en
despertar. La app lo despierta con `GET /salud` en cuanto se abre el login, espera hasta 75 s al iniciar sesión y, si
tarda más de 4 s, muestra «Despertando el servidor...» en lugar de un error.

**Sesión que no se pierde.** La sesión y los campos de los formularios usan `rememberSaveable`: girar el teléfono o
cambiar el tamaño de letra ya no cierra la sesión. La contraseña y la foto se quedan fuera a propósito: la contraseña
para no guardarla en el estado del sistema, y la foto porque varios MB no caben ahí.

## Seguridad (Fase 1 del inventario)

| Función | Cómo quedó | Dónde |
|---|---|---|
| USR-15 Contraseñas con hash | `users.json` guarda `passwordHash` (BCrypt, costo 10). Si el usuario no existe se compara contra un hash ficticio, para que la respuesta tarde lo mismo y no revele qué cuentas hay | `Users.kt` |
| USR-16 Token en cada petición | El login entrega un JWT firmado con `JWT_SECRET` que dura 12 h. Las rutas de pagos exigen `Authorization: Bearer <token>`; sin él responden `401` | `Security.kt`, `Routing.kt` |
| USR-17 Permisos por rol | Solo el ciudadano sube pagos; el repartidor no ve pagos; el delegado ve todos. Fuera de su rol, `403` | `requireRole()` |
| PRE-14 Privacidad de los pagos | El usuario sale del token, no del formulario. El ciudadano ve solo sus pagos y sus fotos; la foto de otra persona responde `404` para no confirmar que existe | `Routing.kt` |
| USR-20 Bloqueo por intentos | 3 contraseñas incorrectas en 15 min bloquean la cuenta 5 min (`429` + `Retry-After`), como piden RFM-NN-01 y RFW-NN-01 del SRS | `LoginAttempts` |
| SRV-14 Límite de peticiones | 20 logins por minuto por IP. Lee `X-Forwarded-For` porque Render y Railway ponen un proxy delante; sin eso, todos compartirían la IP del proxy | `configureRateLimit()` |
| SRV-07 CORS | Solo los dominios de `CORS_ORIGINS`, y permite el encabezado `Authorization` | `configureCors()` |
| Límite de memoria | Ktor deja de leer una foto al pasar 10 MB aunque no venga `Content-Length` (antes aceptaba hasta 50 MB en memoria) | `receiveMultipart(formFieldLimit)` |
| SRV-12 Registro de peticiones | Una línea por petición: método, ruta, código, duración e IP. Nunca el token ni la contraseña | `configureLogging()` |
| SRV-13 Chequeo de salud | `GET /salud`: estado, plataforma (`local`, `render` o `railway`) y commit desplegado | `Routing.kt` |

### Variables de entorno del backend

| Variable | ¿Obligatoria en la nube? | Para qué sirve |
|---|---|---|
| `PORT` | La pone la plataforma | Puerto de Netty (8081 en la computadora) |
| `JWT_SECRET` | **Sí** | Clave para firmar los tokens, mínimo 32 caracteres. Sin ella se genera una al azar y todas las sesiones se cierran cada vez que el servidor se reinicia o se duerme. Para crear una: `openssl rand -base64 48` |
| `CORS_ORIGINS` | Sí, si hay versión web | Dominios del panel web separados por comas, por ejemplo `https://2530412-alt.github.io` |
| `PAGOS_DIR` | No | Carpeta de las fotos (por defecto `uploads/pagos`, o sea `/app/uploads/pagos` en el contenedor) |
| `DATA_DIR` | No | Carpeta de las cuentas (`users.json`) y las solicitudes (`solicitudes.json`); por defecto `uploads`. Si ya existe `users.json` aquí, se usa en lugar del de `resources`. Para que nada se borre al reiniciar, monta el volumen en `/app/uploads` |
| `APP_TZ` | No | Zona horaria para «hoy» en `/entregas` (por defecto `America/Mexico_City`) |
| `SFTP_*` | No | Copia de las fotos a otro servidor por SFTP (ver abajo) |

## API del backend

Las rutas marcadas con 🔒 exigen el encabezado `Authorization: Bearer <token>` que entrega `POST /login`.

| Método | Ruta | Quién | Cuerpo | Respuesta |
|---|---|---|---|---|
| GET | `/` | Público | — | `Logue backend funcionando` |
| GET | `/salud` | Público | — | `200 { estado, servicio, plataforma, commit, segundosActivo }` |
| POST | `/login` | Público | `{ "username": "admin", "password": "1234" }` | `200 { username, role, token, expiresIn }` · `401` credenciales incorrectas · `400` campos vacíos · `429` cuenta bloqueada o demasiados intentos (con `Retry-After`) |
| POST | `/pagos-prediales` 🔒 | Ciudadano | `multipart/form-data`: `folio` (opcional), `foto` (JPEG o PNG, máx. 10 MB). El usuario sale del token | `201 { id, username, folio, fecha, tipo, tamano }` · `400` datos inválidos · `401` sin sesión · `403` no es ciudadano · `413` foto muy grande · `415` no es JPEG ni PNG |
| GET | `/pagos-prediales` 🔒 | Ciudadano, delegado | — (el delegado puede agregar `?username=ciudadano`) | `200` pagos del ciudadano del token, o todos si es el delegado · `403` si un ciudadano pide los de otro |
| GET | `/pagos-prediales/{id}/foto` 🔒 | Dueño, delegado | — | La imagen · `404` si no existe o es de otra persona |
| POST | `/registro` | Público | `multipart/form-data`: `nombre`, `telefono` (10 dígitos), `calle`, `colonia`, `usuario`, `contrasena`, `folio` (opcional), `foto` del comprobante (JPEG o PNG, máx. 10 MB) | `201 { username, estado: "EN_REVISION", pagoId }` · `400` dato inválido · `409` usuario o teléfono ya registrado · `413` · `415`. La cuenta no puede entrar hasta que el delegado la active |
| POST | `/recuperacion/solicitar` | Público | `{ "cuenta": "usuario o teléfono" }` | `202` siempre (no revela si la cuenta existe) · `400` dato inválido. El código de 6 dígitos sale por `CodeSender` (hoy, el registro del servidor; SMS es NOT-05) |
| POST | `/recuperacion/confirmar` | Público | `{ "cuenta", "codigo", "contrasena" }` | `204` contraseña cambiada · `400` código incorrecto, vencido (10 min) o agotado (5 intentos) |
| GET | `/usuarios/yo` 🔒 | Todos | — | `200 { username, role, nombre, telefono, calle, colonia, estado }` |
| PATCH | `/usuarios/yo` 🔒 | Todos | `{ nombre?, telefono?, calle?, colonia? }` (solo lo que cambia) | `200` perfil · `400` dato inválido · `409` teléfono de otra cuenta |
| PATCH | `/usuarios/{username}/estado` 🔒 | Delegado | `{ "estado": "ACTIVA" }` (`ACTIVA`, `EN_REVISION` o `INACTIVA`) | `200` perfil · `400` · `403` no es delegado · `404` |
| GET | `/solicitudes` 🔒 | Ciudadano, delegado | — | `200 [ { id, username, dia, hora, capacidadLitros, domicilio, colonia, estado, repartidor } ]`: las del ciudadano, o todas si es el delegado · `403` repartidor |
| GET | `/entregas` 🔒 | Repartidor | — (`?dia=AAAA-MM-DD`, por omisión hoy) | `200` su ruta del día, ordenada por hora · `400` día inválido · `403` otro rol |

## Cámara y huella digital (Android)

El **ciudadano** tiene el botón **Registrar pago predial** en su pantalla. El flujo es:

1. **Tomar foto del recibo:** abre la cámara **dentro de la app** (CameraX), con vista previa en vivo y botón de captura. La primera vez pide el permiso `CAMERA`; si se niega, la pantalla explica cómo activarlo en Ajustes.
2. **Enviar pago:** si el teléfono tiene huella registrada, se muestra el diálogo del sistema (`BiometricPrompt`) para confirmar. Si no tiene, se envía sin confirmar y la pantalla lo avisa.
3. La foto viaja por `POST /pagos-prediales` con el token de la sesión, y el backend la guarda en `backend/uploads/pagos/` (cambia con la variable de entorno `PAGOS_DIR`) como `<id>.jpg` más un `<id>.json` con el folio, la fecha y el usuario del token.

| Pieza | Archivo |
|---|---|
| Pantalla | `PagoPredialScreen.kt` |
| Cámara (`expect`) / Android (`actual`, CameraX) | `PhotoCapture.kt` / `PhotoCapture.android.kt` |
| Huella (`expect`) / Android (`actual`) | `Biometric.kt` / `Biometric.android.kt` |
| Envío al backend | `PagosApi.kt` |
| iOS y web | `Sensors.<plataforma>.kt`: por ahora indican que la cámara y la huella no están disponibles |
| Android | `AndroidManifest.xml` (permiso `CAMERA`), `MainActivity` extiende `FragmentActivity` (lo exige la huella) |

> Desde la Fase 1 cada petición lleva el token y el servidor toma el usuario de ahí (ver «Seguridad»). Las fotos siguen en el
> disco del servidor: en Render gratuito se borran cada vez que el servicio se duerme o se redespliega. En Railway se
> conservan si se monta un volumen en `/app/uploads` (ver «Despliegue en la nube y conexión por SSH»). Fase 2: base de datos.

## Copia de las fotos por SSH (SFTP)

Además de guardar la foto en su disco, el backend puede **copiarla a otro servidor por SFTP**, que viaja dentro de una conexión SSH cifrada (librería SSHJ). Está **apagada por defecto**: solo se activa si defines `SFTP_HOST`.

| Variable de entorno | Para qué sirve |
|---|---|
| `SFTP_HOST` | Dirección del servidor. Si no existe, no se copia nada |
| `SFTP_PORT` | Puerto (por defecto `22`) |
| `SFTP_USER` | Usuario en el servidor |
| `SFTP_KEY_PATH` | Ruta de la llave privada (recomendado). Alternativa: `SFTP_PASSWORD` |
| `SFTP_REMOTE_DIR` | Carpeta de destino (por defecto `uploads/pagos`) |
| `SFTP_HOST_FINGERPRINT` | **Obligatoria.** Huella de la llave del servidor, por ejemplo `SHA256:...`. Evita conectarse a un servidor falso |

Para obtener la huella del servidor: `ssh-keyscan -t ed25519 TU_SERVIDOR | ssh-keygen -lf -`. Compárala con la que te dé quien administra el servidor antes de usarla.

Ejemplo en PowerShell, antes de encender el backend:

```
$env:SFTP_HOST = "servidor.ejemplo.mx"
$env:SFTP_USER = "aquago"
$env:SFTP_KEY_PATH = "C:\Users\tuusuario\.ssh\id_ed25519"
$env:SFTP_HOST_FINGERPRINT = "SHA256:..."
.\gradlew.bat run
```

- La copia se hace **en segundo plano**: el disco local es la copia principal, y si el servidor remoto no responde el pago se guarda igual y el error queda en el registro del backend.
- Las credenciales **nunca** van en el código ni en la app del teléfono: solo en variables de entorno del backend.
- Archivos: `RemoteCopy.kt` (conexión y variables) y el uso en `Routing.kt`. Pruebas en `RemoteCopyTest.kt`.

## Despliegue en la nube y conexión por SSH

El backend se despliega con el `Dockerfile` de la raíz, que sirve igual para **Render** (donde está hoy) y para
**Railway**. Cada `git push` a `main` dispara un despliegue nuevo, y `GET /salud` muestra el commit que quedó
corriendo, así se comprueba que el servidor tiene la última versión.

| | Render (hoy) | Railway |
|---|---|---|
| Entrar al servidor por SSH | No en el plan gratuito | Sí: `railway ssh` |
| Fotos y datos al reiniciar | Se borran (no hay disco persistente gratis) | Se conservan con un volumen en `/app/uploads` |
| MySQL para la Fase 2 | No (solo ofrece Postgres) | Sí, como servicio del mismo proyecto |
| Servidor dormido | Tras 15 min sin uso, ~1 min en despertar | Solo si se activa el modo Serverless |

Por eso, para la práctica de SSH y para la Fase 2 conviene mudar el backend a Railway. Revisen los créditos y
límites del plan en railway.com/pricing antes de crear el proyecto.

### 1. Subir el código por SSH (`git push`)

En la computadora, una sola vez:

```
ssh-keygen -t ed25519 -C "tu-correo@upt.edu.mx"      (Enter en todo; crea ~/.ssh/id_ed25519 y id_ed25519.pub)
```

Copia el contenido de `id_ed25519.pub` en GitHub → **Settings → SSH and GPG keys → New SSH key**. Después:

```
ssh -T git@github.com                                         debe responder «You've successfully authenticated»
git remote set-url origin git@github.com:2530412-alt/logue.git
git remote -v                                                 las dos líneas deben iniciar con git@github.com:
git push                                                      sube el código por SSH y dispara el despliegue
```

### 2. Crear el servicio en Railway

1. **New Project → Deploy from GitHub repo → logue.** No hace falta Root Directory: el `Dockerfile` está en la raíz y
   compila la carpeta `backend/`. Si Railway no lo detecta, agrega la variable `RAILWAY_DOCKERFILE_PATH=Dockerfile`.
2. **Variables:** `JWT_SECRET` (resultado de `openssl rand -base64 48`) y `CORS_ORIGINS=https://2530412-alt.github.io`.
   `PORT` la pone Railway.
3. **Volumen** montado en `/app/uploads`, para que las fotos de los pagos no se borren con cada despliegue.
4. **Settings → Networking → Generate Domain** da la dirección pública (`https://…up.railway.app`). Ponla en
   `SERVIDOR_NUBE` de `frontend/shared/src/commonMain/kotlin/com/logue/frontend/Config.kt` y en
   `Config.js.kt`/`Config.wasmJs.kt` si la web también se muda.
5. Comprueba en el navegador `https://…up.railway.app/salud`: debe decir `"plataforma":"railway"` y el commit.

### 3. Entrar al servidor por SSH (`railway ssh`)

```
npm i -g @railway/cli          en Windows usa npm.cmd y railway.cmd si PowerShell bloquea los scripts
railway login
railway link                   elige el proyecto, el entorno y el servicio del backend
railway ssh                    la primera vez registra tu llave y pide aceptar la huella de ssh.railway.com
```

Si la sesión se cierra en cuanto se abre, el contenedor estaba dormido: abre `/salud` en el navegador y vuelve a
intentar. Dentro del contenedor (`root@…:/app`):

```
ls -la                                   app.jar y la carpeta uploads
ls uploads/pagos                         fotos de los pagos (<id>.jpg y <id>.json)
java -version                            Temurin 21, el de la imagen, no el de tu computadora
cat /proc/1/cmdline | tr '\0' ' '        java -XX:MaxRAMPercentage=75 -jar app.jar
curl -s localhost:$PORT/salud            el servidor respondiendo desde la misma máquina
java -Xmx64m -jar app.jar probar http://localhost:$PORT     pruebas automáticas desde dentro del servidor
exit
```

Desde la computadora, `railway logs` muestra una línea por petición (método, ruta, código, duración e IP), sin tokens
ni contraseñas.

### 4. Pruebas sobre el servidor alojado

`Probador.kt` manda 19 casos de prueba al servidor ya desplegado (login de los tres roles, bloqueo, token, permisos,
subida de foto, CORS) y tres series de rendimiento, y guarda la tabla en un CSV para el reporte:

```
cd backend
gradlew.bat run --args="probar https://tu-servicio.up.railway.app"
```

Usa los usuarios de prueba (contraseña `1234`, o la que se pase como tercer argumento), sube una foto de prueba con el
folio `PRUEBA-AUTOMATICA` y el caso de bloqueo usa un usuario inventado, así que no bloquea ninguna cuenta real. Hace
menos de 20 logins: si se corre dos veces seguidas, espera un minuto por el límite por IP. Si falla PS-19, falta
definir `CORS_ORIGINS` en el servidor.

## Cómo ejecutarlo

**1. Backend.** Abrir la carpeta `backend` en Android Studio o IntelliJ IDEA y ejecutar `main.kt`. También se puede desde la terminal:

```
cd backend
gradlew.bat run        (Windows)
./gradlew run          (macOS / Linux)
```

Cuando aparezca `Responding at http://...:8081`, el servidor está listo. La barra de Gradle se queda en «EXECUTING» mientras el servidor esté encendido; es normal. Para comprobarlo, abrir http://localhost:8081/salud en el navegador. En local el aviso «JWT_SECRET no está definido» es normal.

**2. Frontend.** Abrir la carpeta `frontend` en Android Studio, elegir **androidApp** y un emulador, y presionar Run.
- **Sin configurar nada**, la app usa el servidor en la nube.
- **Backend local con el emulador:** en `frontend/local.properties` agrega `SERVIDOR_URL=local`. En Android 17 hay que permitir el acceso a «dispositivos cercanos».
- **Backend local con celular por USB:** ejecuta `adb reverse tcp:8081 tcp:8081` y usa `SERVIDOR_URL=http://localhost:8081`.
- **Versión web:** `gradlew.bat :webApp:wasmJsBrowserDevelopmentRun` dentro de `frontend`.

**3. Pruebas**
- `backend`: `gradlew.bat test` (67 pruebas: login, token, permisos, bloqueo, fotos, SFTP, CORS, salud, cuentas, recuperación, perfil y solicitudes)
- `frontend`: `gradlew.bat :shared:testAndroidHostTest` (35 pruebas: Crear cuenta, Recuperar contraseña, perfil, solicitudes y dirección del servidor)
- Servidor desplegado: `gradlew.bat run --args="probar https://…"` (ver «Pruebas sobre el servidor alojado»)

## Siguientes etapas (según la propuesta de AquaGo)

- **Ciudadano:** registro real con comprobante de pago predial, solicitud del servicio, seguimiento del estatus (recibida, en proceso, programada, entregada) y notificaciones de fecha y horario.
- **Repartidor:** ruta y citas del día, confirmación de la entrega con folio y registro de incidencias.
- **Delegado (web):** panel de solicitudes, validación del pago predial, asignación de citas con folio y reportes.
- Una sola base de datos compartida por las aplicaciones de escritorio, web y móvil.

## Repositorio

https://github.com/2530412-alt/logue
