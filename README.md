# OpenCode para Android

App nativa Android (Kotlin + Jetpack Compose) que usa todas las funciones de
opencode desde el teléfono: chat en streaming estilo DeepSeek, sesiones,
modelos, proyectos y exploración/edición de archivos.

Se conecta al servidor `opencode serve` que corre en Termux
(`http://127.0.0.1:4096`).

## Funciones

- **Chat** con streaming en vivo (Server-Sent Events), historial de sesiones,
  nueva conversación, borrar/renombrar, detener respuesta a mitad.
- **Archivos**: exploración del almacenamiento del dispositivo (requiere
  permiso «Todos los archivos» en Android 11+ o SAF) y modo **Proyecto** que
  lista los archivos del proyecto abierto en el servidor (`GET /file`).
  Editor de texto integrado con guardado en dispositivo.
- **Proyectos**: proyecto actual, lista de proyectos, modelo por defecto y
  agentes del servidor.
- **Ajustes**: URL del servidor, contraseña (auth básica), aceptación
  automática de permisos de herramientas, tema oscuro.

## Cómo funciona

El servidor `opencode serve` expone una API HTTP + SSE:

- `POST /session` crear sesión, `GET /session` listar
- `POST /session/:id/prompt` (fallback `POST /session/:id/message`) enviar mensaje
- `GET /session/:id/messages` (fallback `GET /session/:id/message`) historial
- `GET /event` stream de eventos (`message.part.updated`, `message.updated`,
  `permission.requested`)
- `POST /session/:id/abort` detener generación
- `POST /session/:id/permissions/:permissionID` responder permisos
- `GET /file`, `GET /file/content`, `GET /find/file`, `GET /project`,
  `GET /project/current`, `GET /agent`, `GET /config/providers`, `GET /health`

Si defines `OPENCODE_SERVER_PASSWORD=s`, el servidor pide auth básica
(`opencode:s`) y la app la reenvía.

## 1. Instalar y arrancar el servidor en Termux

```bash
pkg update && pkg install nodejs
npm install -g opencode-ai
```

Arrancar (misma red Wi-Fi si quieres acceso desde otra máquina):

```bash
bash scripts/servidor.sh            # solo local
bash scripts/servidor.sh --host 0.0.0.0   # accesible desde la red
```

Para que opencode acepte los permisos de herramientas sin preguntar, crea
`~/.config/opencode/opencode.json`:

```json
{
  "permission": {
    "defaultMode": "allow"
  }
}
```

## 2. Compilar el APK con GitHub Actions

Este repositorio incluye un workflow en `.github/workflows/build.yml` que:

1. Compila `debug` y `release` con `./gradlew assembleDebug assembleRelease`.
2. Sube ambos APK como artefactos del workflow.
3. Si se crea un tag `v*`, publica una Release de GitHub con los APK.

Pasos:

```bash
git init && git add . && git commit -m "OpenCode Android"
gh repo create opencode-android --public --source=. --push
git tag v1.0.0 && git push --tags
```

Luego en GitHub → **Actions** espera a que termine `Build`, descarga el
artefacto, e instala `app-debug.apk` en el teléfono.

> También puedes compilar localmente con Android Studio (JDK 17) o en Termux
> instalando `openjdk-17` (muy lento: gradle necesita descargar todo).

## 3. Configurar la app

1. Abre la app → pestaña **Ajustes**.
2. URL: `http://127.0.0.1:4096` (o la IP del teléfono si es remoto).
3. Pulsa **Guardar y conectar** → verás la versión del servidor.
4. Para explorar el dispositivo: pestaña **Archivos** → **Conceder permiso**
   (Android abre «Todos los archivos»; en versiones viejas pide lectura).
5. En **Archivos → Proyecto** verás el árbol del proyecto activo del servidor.

## Estructura

```
app/src/main/java/dev/opencode/android/
  MainActivity.kt                 navegación por pestañas
  OpenCodeApplication.kt          singleton del repositorio
  data/
    OpenCodeClient.kt             cliente HTTP (OkHttp) con fallbacks 1.x/2.x
    SseClient.kt                  stream SSE de eventos
    OpenCodeRepository.kt         estado compartido + ajustes (SharedPreferences)
    model/Models.kt               DTOs
  ui/
    theme/Theme.kt                tema estilo DeepSeek (fondo #0F1115, azul #4D6BFE)
    chat/                         ChatScreen, ChatViewModel, SessionsDrawer
    files/                        explorador + editor, FilesViewModel
    projects/                     ProjectsScreen, ProjectsViewModel
    settings/                     SettingsScreen, SettingsViewModel
  util/Permissions.kt             All Files Access + SAF
```

## Solución de problemas

- **No conecta**: comprueba que `opencode serve` está corriendo y que la URL es
  correcta. El emulador usa `10.0.2.2` en vez de `127.0.0.1`.
- **El servidor pide contraseña**: añádela en Ajustes; el servidor debe lanzarse
  con `OPENCODE_SERVER_PASSWORD`.
- **No veo archivos**: concede «Todos los archivos» (Android 11+) o el permiso
  de lectura (Android 10-). Reinicia la pestaña Archivos.