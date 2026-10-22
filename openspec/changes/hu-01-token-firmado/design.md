# Design

## Context

El login vive en `AuthResource.login`, que valida la solicitud, consulta `LegacyDatabase.findByCredentials` y emite el token con `LegacyTokenUtil.create(username, role)`. `LegacyTokenUtil` construye un Base64 de `username|role|expiry|SECRET` con el secreto literal y sin firma. `expiresIn` (3600) está escrito a mano en `AuthResource` y la expiración de 3600 s en `LegacyTokenUtil`, es decir, la duración está duplicada. El campo `client` se deserializa en `LoginRequest` pero no se usa. No existe ninguna dependencia JWT en `pom.xml`; las únicas relevantes son `quarkus-rest-jackson`, `commons-text` y `sqlite-jdbc`. La configuración está en `application.properties` sin secretos.

El único test existente, `AuthResourceTest`, comprueba solo el 200 con credenciales válidas. No hay pruebas de 400, 401, 500, de expiración ni de firma.

Ver [proposal.md](proposal.md) para el motivo y [specs/auth-token/spec.md](specs/auth-token/spec.md) y [specs/auth-login/spec.md](specs/auth-login/spec.md) para los requisitos.

## Goals / Non-Goals

**Goals:**
- Un único punto que emite y valida tokens firmados (`sub`, `client`, `iat`, `exp`).
- Secreto solo desde `AUTH_TOKEN_SECRET`; si falta, no se emite token.
- Una única duración con nombre que alimenta `exp` y `expiresIn`.
- Mantener la forma de las respuestas 200, 400, 401 y 500 descrita en `docs/ENDPOINTS.md`.

**Non-Goals:**
- No se corrige la inyección SQL de `LegacyDatabase.findByCredentials` ni el almacenamiento de contraseñas en claro: son hallazgos de otra HU y no afectan al formato del token.
- No se añaden refresh tokens, revocación, rotación de claves ni autorización por rol en otros endpoints.
- No se elimina el campo `debug` de la respuesta 200 (se preserva tal cual).
- No se rediseña `LegacyDatabase` ni el resto de la aplicación.

## Decisions

### D1. Formato y librería del token

Opciones:
- **A. HMAC-SHA256 propio con `javax.crypto`** sobre header y payload Base64URL. Sin dependencias nuevas, pero implementamos parseo, comparación en tiempo constante y validación de `alg` nosotros.
- **B. Librería SmallRye JWT** (`quarkus-smallrye-jwt` y `smallrye-jwt-build`). Es el estándar del ecosistema Quarkus, valida firma, `exp` y `iat` y evita errores de implementación, a costa de una dependencia nueva en `pom.xml`.
- **C. `jjwt` directamente.** Equivalente a B sin la integración con Quarkus.

**Decisión: B.** Reducir el riesgo criptográfico pesa más que evitar una dependencia; la HU ya contempla la decisión HMAC frente a librería JWT y esta es la recomendada. Si el usuario prefiere A, el resto del diseño no cambia.

### D2. Punto único de emisión y verificación

Se mantiene una clase de token (sustituye o renombra a `LegacyTokenUtil`) con dos responsabilidades: `issue(username, client)` y `verify(token)`. `AuthResource` no conoce claims ni firma. La verificación queda aislada para que los tests de firma/expiración apunten a un único componente.

### D3. Secreto y configuración

- El secreto se lee de `AUTH_TOKEN_SECRET` (vía propiedad de configuración que resuelve esa variable de entorno, sin valor por defecto).
- Si la variable falta o está vacía, la emisión falla con error de configuración y se responde 500 con el formato actual; no se emite token. Esto se resuelve en arranque o en el primer login según la preferencia del equipo.
- El secreto de `LegacyTokenUtil` se elimina. No se guarda secreto en `application.properties`.

### D4. Duración única

Una propiedad `auth.token.ttl-seconds` con valor por defecto `3600` (no secreto, sí configurable). `exp = iat + ttl` y `expiresIn = ttl`. Así la coherencia `expiresIn == exp - iat` es estructural y la tolerancia de la HU puede ser 0 en pruebas.

### D5. Validación de `client`

El spec deja pendiente la lista de clientes permitidos. Propuesta: propiedad `auth.clients.allowed` con valor inicial `angular-workshop` (el único cliente del contrato y de las pruebas). Un `client` ausente o fuera de la lista devuelve 400.

**Pendiente de confirmar con el usuario:** si `client` es obligatorio (hoy es opcional en `docs/ENDPOINTS.md`). Esta decisión cambia el contrato, por lo que no se resuelve aquí.

### D6. Logs

No se añade ningún log del token, del secreto ni de la contraseña. El `printStackTrace` existente en `LegacyDatabase` puede volcar el SQL (que contiene la contraseña) en la excepción; se revisará en las pruebas de logs.

## Pruebas de caracterización (antes de modificar)

Antes de tocar el token, añadir pruebas que fijen el comportamiento actual de `POST /auth/login`:

1. 200 con `token`, `expiresIn == 3600`, `user.username`, `user.displayName`, `user.role` y `debug`.
2. `displayName` escapado en HTML (usar un usuario de prueba o verificar la función de escape).
3. 400 con `Request is null`, `Username is required` y `Password is required`, incluidos valores con solo espacios.
4. 401 con `Invalid user <username> or password` para contraseña incorrecta y usuario inexistente.
5. Campos extra en la solicitud ignorados.

Estas pruebas deben pasar sin cambios de código. Después se añaden pruebas nuevas para `auth-token` (claims, firma alterada, secreto distinto, token expirado, token en formato Base64 anterior rechazado, ausencia de `AUTH_TOKEN_SECRET`) y para logs redactados.

## Estrategia incremental

1. Añadir pruebas de caracterización (sección anterior) y comprobar que pasan sobre el código actual, antes de cualquier cambio.
2. Introducir la configuración de `auth.token.ttl-seconds` y `auth.clients.allowed` sin cambiar el token; comprobar que la 200 sigue igual.
3. Añadir la dependencia de firma (D1-B) y el componente de emisión/verificación detrás de `LegacyTokenUtil` (o su sustituto), sin usarlo aún.
4. Cambiar `AuthResource` para emitir el token nuevo y leer `expiresIn` desde la duración; actualizar pruebas de token.
5. Eliminar el secreto literal y el formato Base64; comprobar que ninguna prueba depende del formato anterior.
6. Revisar logs y documentar `AUTH_TOKEN_SECRET` en `README.md` o la documentación de despliegue.

Cada paso debe dejar el proyecto compilable y con pruebas en verde.

## Riesgos / Trade-offs

- **[Ruptura de clientes con tokens anteriores]** → Es BREAKING por diseño; comunicar la fecha de corte. Sin período de convivencia para no mantener el formato inseguro.
- **[Despliegue sin `AUTH_TOKEN_SECRET`]** → El servicio no emite tokens. Mitigar con mensaje de error claro y documentación; validar en arranque si el equipo lo prefiere.
- **[Nueva dependencia SmallRye JWT]** → Aumenta superficie de dependencias (H-05 está fuera de alcance). Mitigar fijando versión con la plataforma Quarkus existente.
- **[Cambiar `client` a obligatorio]** → Rompería clientes que hoy omiten el campo. Por eso se mantiene opcional hasta confirmar.
- **[Pruebas de expiración lentas o frágiles]** → Usar `ttl` configurable en pruebas en lugar de esperar 3600 s.

## Rollback

- Los pasos 2 a 4 son aditivos y se revierten con un commit.
- El paso 5 (eliminar el formato Base64) es el punto de no retorno para los tokens emitidos; el rollback es revertir el commit completo, que vuelve a emitir tokens del formato anterior.
- Mantener las pruebas de caracterización como red de seguridad durante todo el cambio.

## Open Questions

- ¿Es `client` obligatorio a partir de este cambio, o se mantiene opcional como hoy? (Afecta a D5 y a `auth-login`.)
- ¿Emisión sin secreto falla en arranque o en el primer login? (D3; no cambia los specs.)
