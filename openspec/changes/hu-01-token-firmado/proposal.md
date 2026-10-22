# Proposal

Referencia: Issue [#4](https://github.com/user-bcp-student36/workshop-auth-backend/issues/4) (HU-01 - Token firmado con expiracion y claims minimos). Fuente de alcance: [HU-01.md](../../../HU-01.md) y contrato de referencia [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

## Why

`POST /auth/login` emite hoy un token que es solo el Base64 de `username|role|expiry|SECRET` (`LegacyTokenUtil.create`). El token no tiene firma, el secreto va embebido en el codigo y dentro de cada token, y no tiene claims estandar (`sub`, `client`, `iat`, `exp`). Cualquiera que decodifique el token obtiene el secreto y puede fabricar tokens validos. La HU exige un token firmado con expiracion verificable y claims minimos.

Discrepancia a resolver: HU-01 describe el estado actual como "firma HMAC y lee `AUTH_TOKEN_SECRET`", pero el codigo en `src/main` no firma ni lee esa variable. Esta propuesta toma el codigo como estado actual.

## What Changes

- Emitir un token firmado que contenga los claims `sub`, `client`, `iat` y `exp`.
- Validar firma y expiracion del token en un unico punto de verificacion.
- Leer el secreto de la variable de entorno `AUTH_TOKEN_SECRET`; sin esa variable el servicio no emite tokens.
- Eliminar el secreto literal del codigo fuente de `src/main`.
- Derivar `expiresIn` y `exp` de una unica duracion con nombre, para que sean coherentes.
- Obtener `client` de la solicitud, validarlo y reflejarlo en el claim `client`.
- Impedir que tokens, secretos o valores sensibles aparezcan en logs.
- **BREAKING**: los tokens emitidos con el formato Base64 anterior dejan de ser aceptados.

## Capabilities

### New Capabilities

- `auth-token`: emision y validacion de tokens firmados con claims minimos, expiracion y secreto configurado por entorno.
- `auth-login`: comportamiento observable de `POST /auth/login` (respuestas, validacion de entrada, uso del campo `client`).

### Modified Capabilities

Ninguna. `openspec/specs/` no contiene specs previas (solo `.gitkeep`).

## Comportamiento a preservar

- `POST /auth/login` mantiene consumo y produccion `application/json`.
- Respuesta 200 con `token`, `expiresIn` (segundos) y `user` (`username`, `displayName`, `role`).
- `user.displayName` sigue escapado en HTML.
- Respuestas 400 (`Request is null`, `Username is required`, `Password is required`) y 401 (`Invalid user <username> or password`) mantienen la forma `{"message": ...}`.
- El formato de error 500 (`message` y `exception`) no cambia de forma.
- Los campos desconocidos en la solicitud se ignoran.

Si la implementacion cambia algun punto anterior (por ejemplo, el campo `debug` de la respuesta 200), el spec debe marcarlo como cambio intencional.

## Scope

- Emision y validacion del token devuelto por `/auth/login`.
- Configuracion del secreto por variable de entorno.
- Validacion y uso del campo `client`.
- Comportamiento observable ante login valido, credenciales invalidas, token expirado y token alterado.
- Ausencia de tokens y secretos en logs.

## Fuera de alcance

- Cambiar el contrato HTTP de `/auth/login` mas alla de lo descrito arriba.
- Autorizacion por rol sobre otros endpoints.
- Refresh tokens, revocacion y rotacion de claves.
- Decision tecnica entre HMAC propio y libreria JWT: se resuelve en la fase de diseno.
- Cobertura de pruebas y evidencia documental: se tratan en la fase de implementacion.

## Impact

- Codigo: `LegacyTokenUtil`, `AuthResource`, `LoginRequest` y la capa de token en `src/main/java/com/bcp/workshop/auth/`.
- Configuracion: nueva variable de entorno `AUTH_TOKEN_SECRET`; actualizar `application.properties` y la documentacion de despliegue.
- Clientes: los tokens previos dejan de validarse (BREAKING).
- Sin cambios previstos en la estructura de las respuestas HTTP del login.
