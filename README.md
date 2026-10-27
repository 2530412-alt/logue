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
| **Backend** | [`backend/`](backend) | Ktor Server 3.6, generado con el asistente [start.ktor.io](https://start.ktor.io), motor Netty | API REST local en el puerto **8081**. Valida las credenciales de `users.json` y responde con el rol del usuario |
| **Frontend** | [`frontend/`](frontend) | Kotlin Multiplatform + Compose Multiplatform, Ktor Client | App para Android (Android Studio), web e iOS. Login, Crear cuenta, Recuperar contraseña y una vista por rol |

## Arquitectura local

```
┌──────────────── FRONTEND (Kotlin Multiplatform) ─────────────────┐          ┌───────── BACKEND (Ktor Server) ──────────┐
│                                                                  │          │                                          │
│  LoginScreen ──► validateCredentials() ──► login()  (AuthApi.kt) │   JSON   │  POST /login            (Routing.kt)     │
│                                              │                   │ ───────► │     │                                    │
│                               Ktor Client + ContentNegotiation   │          │  call.receive<LoginRequest>()            │
│                                              │                   │ ◄─────── │     │  compara con                       │
│  App.kt guarda la sesión (username + role)   │                   │ username │  resources/users.json                    │
│      │                                                           │  + role  │     │                                    │
│      └─► when (role)                                             │          │  200 LoginResponse(username, role)       │
│            ├─ CIUDADANO → CiudadanoScreen  "Mis solicitudes"     │          │  401 credenciales incorrectas            │
│            ├─ OPERADOR  → OperadorScreen   "Mis entregas de hoy" │          │  400 solicitud vacía o inválida          │
│            └─ ADMIN     → AdminScreen      "Panel de solicitudes"│          │                                          │
└──────────────────────────────────────────────────────────────────┘          └──────────────────────────────────────────┘
```

1. El usuario escribe su usuario y contraseña en **LoginScreen**.
2. **validateCredentials()** revisa que no estén vacíos y **login()** manda `POST /login`. Ktor Client convierte el objeto `LoginRequest` a JSON.
3. El backend convierte ese JSON en `LoginRequest` y busca al usuario en **users.json**:
   - Si coincide, responde `200` con `LoginResponse(username, role)`.
   - Si no coincide, responde `401`.
   - Si los campos llegan vacíos, responde `400`.
4. **App.kt** guarda la sesión y, con un `when (role)`, muestra la vista que corresponde a ese rol.
5. Al presionar **Cerrar sesión**, la sesión se borra y la app vuelve al login.

La pantalla muestra un mensaje distinto para cada caso: usuario o contraseña incorrectos, error del servidor o sin conexión con el backend.

## Usuarios de prueba (`backend/src/main/resources/users.json`)

| Rol | Usuario | Contraseña | Vista que abre |
|---|---|---|---|
| Ciudadano | `ciudadano` | `1234` | **Mis solicitudes:** solicitudes de agua y su estatus |
| Operador (repartidor) | `operador` | `1234` | **Mis entregas de hoy:** ruta del día y entregas |
| Administrador (delegado) | `admin` | `1234` | **Panel de solicitudes:** pendientes, en camino, entregadas y canceladas |

> En esta etapa las credenciales son predefinidas y están en texto plano dentro del JSON, como lo pide la práctica.
> Los datos que muestra cada vista son de demostración; se conectarán al backend en las siguientes etapas.

## Pantallas demostrativas de acceso

Se agregaron desde el login. Todavía **no se envían al backend**, y cada pantalla lo indica al final.

| Pantalla | Cómo se abre | Qué hace |
|---|---|---|
| **Crear cuenta** | «¿No tienes cuenta? Regístrate» | Registro del ciudadano: nombre completo, teléfono (10 dígitos), domicilio, comprobante de pago predial, usuario y contraseña. Al terminar, la cuenta queda «En revisión» para que el delegado valide el comprobante. Las cuentas de repartidor y administrador las da de alta el delegado |
| **Recuperar contraseña** | «¿Olvidaste tu contraseña?» | Tres pasos: usuario o teléfono → código por SMS → contraseña nueva. El código de demostración es **123456** |

Las reglas de validación están en `AuthValidation.kt` y tienen pruebas en `AuthValidationTest.kt`.

## Estructura de carpetas

```
logue/
├── backend/                          PROYECTO KTOR (asistente start.ktor.io)
│   ├── build.gradle.kts              Dependencias: Ktor Server, Netty, ContentNegotiation, CORS, kotlinx.serialization
│   └── src/
│       ├── main/kotlin/
│       │   ├── main.kt               Arranca Netty en el puerto 8081 (o el de la variable PORT)
│       │   ├── Application.kt        rootModule(): instala CORS, serialización y rutas
│       │   ├── Serialization.kt      install(ContentNegotiation) { json() }
│       │   ├── Routing.kt            Lee users.json y define GET /, POST /login y /pagos-prediales
│       │   ├── PagosPrediales.kt     Guarda en disco las fotos de los pagos prediales
│       │   ├── RemoteCopy.kt         Copia opcional de las fotos a otro servidor por SFTP (SSH)
│       │   └── Models.kt             DTOs @Serializable: User, LoginRequest, LoginResponse, PagoPredial
│       ├── main/resources/
│       │   ├── users.json            Credenciales predefinidas y rol de cada usuario
│       │   └── logback.xml           Formato del registro en consola
│       └── test/kotlin/              ServerTest.kt, PagosPredialesTest.kt y RemoteCopyTest.kt (API con testApplication)
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
        │   ├── RegisterScreen.kt     Crear cuenta (demostrativa)
        │   ├── RecoverPasswordScreen.kt  Recuperar contraseña (demostrativa)
        │   ├── Screens.kt            Vistas por rol: Ciudadano, Operador, Administrador
        │   ├── AuthApi.kt            Ktor Client: POST /login y resultado del login
        │   ├── AuthValidation.kt     Reglas de los formularios
        │   ├── Dtos.kt               DTOs @Serializable y roles
        │   ├── Config.kt             expect val BASE_URL (dirección del backend)
        │   ├── AuthComponents.kt / Components.kt   Piezas de interfaz reutilizables
        │   └── Theme.kt / AquaGoTheme.kt           Colores y tema de AquaGo
        ├── androidMain/ jsMain/ wasmJsMain/ iosMain/   CÓDIGO ESPECÍFICO: actual val BASE_URL
        └── commonTest/               Pruebas de las validaciones
```

## Conexión con el serializador (kotlinx.serialization)

Las clases que viajan por la red llevan `@Serializable`. Así el compilador genera el código que las convierte a JSON y de regreso.

| Dónde | Archivo | Qué hace |
|---|---|---|
| Plugin de Gradle | `org.jetbrains.kotlin.plugin.serialization` en `backend/build.gradle.kts` y `frontend/shared/build.gradle.kts` | Genera el serializador de cada clase `@Serializable` |
| Backend | `Serialization.kt` | `install(ContentNegotiation) { json() }`: `call.receive<LoginRequest>()` lee JSON y `call.respond(LoginResponse(...))` responde JSON |
| Backend | `Routing.kt` | `loadUsers()` lee `users.json` y `Json.decodeFromString(...)` lo convierte en `List<User>` |
| Frontend | `AuthApi.kt` | `install(ContentNegotiation) { json() }` en Ktor Client: `setBody(LoginRequest(...))` envía JSON y `res.body()` lo convierte en `LoginResponse` |
| Ambos | `Models.kt` (backend) y `Dtos.kt` (frontend) | DTOs con los mismos nombres de campo: `LoginRequest(username, password)` y `LoginResponse(username, role)` |

## Dirección del backend por plataforma

`Config.kt` declara `expect val BASE_URL` y cada plataforma define su valor (`actual`):

| Plataforma | Archivo | BASE_URL |
|---|---|---|
| Emulador de Android | `Config.android.kt` | `http://10.0.2.2:8081`: 10.0.2.2 es la computadora vista desde el emulador |
| Web | `Config.js.kt`, `Config.wasmJs.kt` | `http://localhost:8081` |
| iOS (simulador) | `Config.ios.kt` | `http://localhost:8081` |

**Permisos en Android** (`AndroidManifest.xml`):
- `INTERNET` y `usesCleartextTraffic`: para hablar con el backend local por HTTP.
- `ACCESS_LOCAL_NETWORK`: desde **Android 17** hace falta para llegar a `10.0.2.2`. `MainActivity` lo pide al abrir la app; en el aviso de «dispositivos cercanos» hay que tocar **Permitir**.

## API del backend

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/` | — | `Logue backend funcionando` |
| POST | `/login` | `{ "username": "admin", "password": "1234" }` | `200 { "username": "admin", "role": "ADMIN" }` · `401` credenciales incorrectas · `400` campos vacíos |
| POST | `/pagos-prediales` | `multipart/form-data`: `username`, `folio` (opcional), `foto` (JPEG o PNG, máx. 10 MB) | `201 { id, username, folio, fecha, tipo, tamano }` · `400` datos inválidos · `401` usuario desconocido · `413` foto muy grande · `415` no es JPEG ni PNG |
| GET | `/pagos-prediales?username=ciudadano` | — | `200` lista de pagos de ese usuario, del más reciente al más antiguo |
| GET | `/pagos-prediales/{id}/foto` | — | La imagen guardada · `404` si no existe |

## Cámara y huella digital (Android)

El **ciudadano** tiene el botón **Registrar pago predial** en su pantalla. El flujo es:

1. **Tomar foto del recibo:** abre la cámara **dentro de la app** (CameraX), con vista previa en vivo y botón de captura. La primera vez pide el permiso `CAMERA`; si se niega, la pantalla explica cómo activarlo en Ajustes.
2. **Enviar pago:** si el teléfono tiene huella registrada, se muestra el diálogo del sistema (`BiometricPrompt`) para confirmar. Si no tiene, se envía sin confirmar y la pantalla lo avisa.
3. La foto viaja por `POST /pagos-prediales` y el backend la guarda en `backend/uploads/pagos/` (cambia con la variable de entorno `PAGOS_DIR`) como `<id>.jpg` más un `<id>.json` con el folio, la fecha y el usuario.

| Pieza | Archivo |
|---|---|
| Pantalla | `PagoPredialScreen.kt` |
| Cámara (`expect`) / Android (`actual`, CameraX) | `PhotoCapture.kt` / `PhotoCapture.android.kt` |
| Huella (`expect`) / Android (`actual`) | `Biometric.kt` / `Biometric.android.kt` |
| Envío al backend | `PagosApi.kt` |
| iOS y web | `Sensors.<plataforma>.kt`: por ahora indican que la cámara y la huella no están disponibles |
| Android | `AndroidManifest.xml` (permiso `CAMERA`), `MainActivity` extiende `FragmentActivity` (lo exige la huella) |

> El almacenamiento es local y de práctica. Todavía no hay sesión con token: el backend confía en el `username` que manda la app. Antes de producción hay que autenticar las peticiones y mover las fotos a una base de datos o almacenamiento en la nube.

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

## Cómo ejecutarlo

**1. Backend.** Abrir la carpeta `backend` en Android Studio o IntelliJ IDEA y ejecutar `main.kt`. También se puede desde la terminal:

```
cd backend
gradlew.bat run        (Windows)
./gradlew run          (macOS / Linux)
```

Cuando aparezca `Responding at http://...:8081`, el servidor está listo. La barra de Gradle se queda en «EXECUTING» mientras el servidor esté encendido; es normal. Para comprobarlo, abrir http://localhost:8081 en el navegador: debe mostrar «Logue backend funcionando».

**2. Frontend.** Abrir la carpeta `frontend` en Android Studio, elegir **androidApp** y un emulador, y presionar Run.
- **Emulador:** ya viene configurado con `10.0.2.2:8081`. En Android 17 hay que permitir el acceso a «dispositivos cercanos».
- **Celular físico (USB):** ejecutar `adb reverse tcp:8081 tcp:8081` y cambiar `Config.android.kt` a `http://localhost:8081`.
- **Versión web:** `gradlew.bat :webApp:wasmJsBrowserDevelopmentRun` dentro de `frontend`.

**3. Pruebas**
- `backend`: `gradlew.bat test`
- `frontend`: `gradlew.bat :shared:testAndroidHostTest` (incluye las pruebas de Crear cuenta y Recuperar contraseña)

## Siguientes etapas (según la propuesta de AquaGo)

- **Ciudadano:** registro real con comprobante de pago predial, solicitud del servicio, seguimiento del estatus (recibida, en proceso, programada, entregada) y notificaciones de fecha y horario.
- **Repartidor:** ruta y citas del día, confirmación de la entrega con folio y registro de incidencias.
- **Delegado (web):** panel de solicitudes, validación del pago predial, asignación de citas con folio y reportes.
- Una sola base de datos compartida por las aplicaciones de escritorio, web y móvil.

## Repositorio

https://github.com/2530412-alt/logue
