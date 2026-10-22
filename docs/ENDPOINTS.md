# Endpoints — Backend Auth

> Documento generado a partir del código (`AuthResource`, `LoginRequest`,
> `application.properties`). Describe el contrato que debe preservarse.

Base URL local: `http://127.0.0.1:8080` (ver `quarkus.http.host` en `application.properties`).

## POST /auth/login

- Consume: `application/json`
- Produce: `application/json`
- Implementación: `src/main/java/com/bcp/workshop/auth/AuthResource.java`

### Request

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

| Campo | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `username` | string | Sí | No puede ser vacío ni solo espacios. |
| `password` | string | Sí | No puede ser vacío ni solo espacios. |
| `client` | string | No | Si falta se usa `angular-workshop`. Si viene y no está en `auth.clients.allowed`, responde 400. Se refleja en el claim `client` del token. |

Campos desconocidos se ignoran (`quarkus.jackson.fail-on-unknown-properties=false`).

### Respuestas

| Código | Cuándo | Cuerpo |
|---|---|---|
| 200 | Credenciales válidas | `{"token": "...", "expiresIn": 3600, "user": {"username": "...", "displayName": "...", "role": "..."}, "debug": "authenticated-with-legacy-flow"}` |
| 400 | Cuerpo nulo | `{"message": "Request is null"}` |
| 400 | Falta `username` | `{"message": "Username is required"}` |
| 400 | Falta `password` | `{"message": "Password is required"}` |
| 400 | `client` no permitido (tras autenticar) | `{"message": "Invalid client"}` |
| 401 | Usuario o contraseña incorrectos | `{"message": "Invalid user <username> or password"}` |
| 500 | Error no controlado | `{"message": "<detalle>", "exception": "<clase>"}` |

Notas del contrato:

- `user.displayName` se devuelve con escape HTML (`StringEscapeUtils.escapeHtml4`).
- `expiresIn` es un número de segundos (hoy fijo en `3600`).
- El formato de error 500 es el actual; el refactor debe corregir su contenido por dentro, no cambiar la forma del contrato.

## GET /q/health

Endpoint de salud provisto por `quarkus-smallrye-health` (ver `pom.xml`). No tiene código propio en el repositorio.
