# OpenCode Android

Cliente de agente de código (estilo OpenCode) completamente nativo para Android:
chat + servidor HTTP local + motor de agente con herramientas de archivos.
Sin dependencia de Termux ni de cualquier otra app externa.

## Cómo compilar

El build se hace **exclusivamente en GitHub Actions** (el repo no incluye Gradle
wrapper). Los pasos automáticos del workflow `.github/workflows/build.yml`:

1. Clona el repo.
2. Instala JDK 17 (temurin), Android SDK y Gradle 8.11.1.
3. Ejecuta `gradle assembleDebug`.
4. Publica el APK como artifact (`app-debug.apk`).

Cualquier `push` a `main` o ejecución manual (`workflow_dispatch`) genera el APK
en "Actions" → `app-debug-apk`.

## Cómo usar

1. Instala el APK en el dispositivo.
2. Abre la app: el servidor local se inicia como servicio en primer plano
   (puerto por defecto **8787**).
3. Escribe un pedido en el chat (ej.: "crea una carpeta con un README").
4. Ajusta proveedor/modelo/API key en el icono de engranaje si quieres usar
   Anthropic, Gemini o "custom" en lugar de OpenCode Zen (el default).

La URL por defecto (`https://opencode.ai/zen/v1`) es el endpoint de
**OpenCode Zen**, compatible con la API de OpenAI (modelo `big-pickle`).
Para llamadas exitosas se necesita una API key válida en Ajustes.

## Servidor local

Expuesto únicamente en `127.0.0.1`, sirve los endpoints:

| Endpoint       | Método | Descripción                              |
|----------------|--------|------------------------------------------|
| `/api/health`  | GET    | `{ok:true}`                              |
| `/api/status`  | GET    | Estado, provider, modelo                 |
| `/api/chat`    | POST   | `{message: "..."}` → lanza el agente     |
| `/api/stop`    | POST   | Detiene la ejecución en curso            |
| `/api/messages`| GET    | Historial persistido (JSON)              |
| `/api/stream`  | GET    | SSE con eventos del agente en tiempo real|

## Herramientas del agente

Ejecutadas dentro de un sandbox en `filesDir/projects` (sin acceso al resto del
almacenamiento): `list`, `read`, `write`, `edit`, `patch`, `glob`, `grep` y
`bash`.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- Room (persistencia de la conversación)
- NanoHTTPD (servidor embebido) + OkHttp (cliente LLM)
- Gradle 8.11.1 / AGP 8.7.3 / minSdk 26 / targetSdk 35