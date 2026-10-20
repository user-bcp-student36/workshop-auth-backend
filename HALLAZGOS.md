# Hallazgos — Backend Auth (matriz de seguimiento)

Fuente: [RETO.md](RETO.md). Cada fila fue verificada en el código actual con archivo y línea.
Ruta base de los archivos de código: `src/main/java/com/bcp/workshop/auth/` (salvo `pom.xml` y `src/main/resources/application.properties`).
Estado inicial de todas las filas: **Pendiente**. Este documento es el único lugar de seguimiento; `2-refactor.md` lo actualizará hallazgo por hallazgo.

## Flujo de login analizado

1. **Entrada HTTP**: `AuthResource.login` (`AuthResource.java:24`) recibe `LoginRequest` (`LoginRequest.java:3-6`).
2. **Validación**: nulo, `username` vacío y `password` vacío devuelven 400 (`AuthResource.java:26-33`).
3. **Persistencia**: `LegacyDatabase.findByCredentials` (`LegacyDatabase.java:31-43`) arma el SQL por concatenación y compara la contraseña en claro. Las semillas se crean en `LegacyDatabase.java:20-24`.
4. **Token**: `LegacyTokenUtil.create` (`LegacyTokenUtil.java:12-16`) produce Base64 de `username|role|exp|SECRET`.
5. **Respuesta**: `AuthResource.java:40-48` arma `token`, `expiresIn`, `user` y `debug`. Los errores van al `catch` de `AuthResource.java:54-58`.
6. **Configuración**: `application.properties` (CORS y host) y `pom.xml` (versiones). La URL de BD sale de la variable `AUTH_DB_URL` con valor por defecto en `LegacyDatabase.java:15`.

Contrato a preservar: [docs/ENDPOINTS.md](docs/ENDPOINTS.md).

## Matriz de hallazgos de RETO.md

id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado
---|---|---|---|---|---|---
H-01 | Concatenación de credenciales en SQL (RETO: SQL) | Vulnerabilidad | `findByCredentials` une `username` y `password` directamente en la sentencia. Un valor con comillas altera la consulta (inyección SQL) y puede evitar la validación de contraseña o exponer datos de `legacy_users`. | `LegacyDatabase.java:32` | alta | Pendiente
H-02 | Token Base64 con secreto embebido (RETO: Token) | Vulnerabilidad | El token es `username\|role\|exp\|SECRET` codificado en Base64. Base64 no firma nada: quien decodifique el token obtiene el secreto y puede fabricar tokens para otro usuario o rol. No hay firma, ni claims estándar (`sub`, `iat`, `client`), ni validación de `exp` en el repositorio. | `LegacyTokenUtil.java:8,13-15`; `AuthResource.java:39` | alta | Pendiente
H-03 | Contraseñas en texto plano (RETO: Datos) | Vulnerabilidad | Las contraseñas se guardan y se comparan en claro. Una fuga de `data/workshop.db` entrega credenciales reutilizables en otros sistemas. Las semillas de `student` y `trainer` también están en claro dentro del código. | `LegacyDatabase.java:23-24` (semillas), `LegacyDatabase.java:32` (comparación) | alta | Pendiente
H-04 | Secreto y CORS hardcodeados (RETO: Configuración) | Vulnerabilidad | El secreto de firma es una constante Java: no se puede rotar sin recompilar y está en el control de versiones. CORS acepta cualquier origen (`/.*/`) sin distinguir entorno, por lo que un sitio ajeno puede leer las respuestas de `/auth/login` desde el navegador, incluido el token. | `LegacyTokenUtil.java:8`; `src/main/resources/application.properties:4-7` | alta | Pendiente
H-05 | Dependencias con CVE: Commons Text 1.9 y Quarkus 3.27.4 (RETO: SCA) | Vulnerabilidad | `commons-text` 1.9 tiene CVE-2022-42889 (Text4Shell, CVSS 9.8, corregido en 1.10.0). El código solo usa `StringEscapeUtils`, no el interpolador afectado, así que el riesgo práctico aquí es bajo, pero CodeQL y Dependency Review lo reportan. Quarkus 3.27.4 tiene CVE-2026-50559 (bypass de autorización por path con `%3B`, `%2F`, `%5C`, corregido en 3.27.4.1); la app no define políticas de autorización por path, así que el impacto es limitado. | `pom.xml:13` (Quarkus 3.27.4); `pom.xml:49-50` (commons-text 1.9); `AuthResource.java:10,45` (uso de la librería) | media | Pendiente
H-06 | Respuesta de error expone detalles internos (RETO: Errores) | Vulnerabilidad | El `catch` genérico devuelve al cliente `error.getMessage()` y el nombre de la clase de la excepción. El 401 además refleja el `username` en el mensaje. Los errores de BD se registran con `printStackTrace` a stderr, sin logger ni redacción. | `AuthResource.java:54-58` (500); `AuthResource.java:37` (eco del username); `LegacyDatabase.java:27,40` (printStackTrace) | media | Pendiente
H-07 | Resource con validación, negocio, persistencia y respuesta mezclados (RETO: Clean code) | Refactor | `login()` valida, consulta la BD, genera el token y arma la respuesta en un solo método con cuatro niveles de anidamiento (regla 8, 11 de REGLAS.md). El literal `3600` aparece duplicado en dos archivos (regla 12). Esto impide probar cada capa por separado y cambiar el token sin tocar HTTP. | `AuthResource.java:24-60`; `AuthResource.java:42`; `LegacyTokenUtil.java:13` | baja | Pendiente

## Hallazgos adicionales (observados, fuera de RETO.md)

Se listan aparte para no mezclar con la matriz del reto. Ninguno cambia el contrato de `/auth/login`.

id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado
---|---|---|---|---|---|---
A-01 | Errores de BD convertidos en "credenciales inválidas" | Bug | `findByCredentials` captura cualquier excepción, la imprime y devuelve `null`. `AuthResource` lo interpreta como credenciales incorrectas, así que una caída de la BD responde 401 en lugar de 500 y oculta la causa real. | `LegacyDatabase.java:39-42`; `AuthResource.java:36-37` | media | Pendiente
A-02 | Campo `debug` en la respuesta exitosa | Vulnerabilidad | La respuesta incluye `"debug":"authenticated-with-legacy-flow"`, que revela detalles del flujo interno. Es parte del contrato actual, así que la corrección debe ser interna (por ejemplo, no exponer detalles de implementación en ese valor), no quitar el campo. | `AuthResource.java:48` | baja | Pendiente
A-03 | Campo `client` recibido y no usado | Refactor | `LoginRequest.client` se deserializa pero nunca se lee ni se valida. HU-01 pide validarlo y reflejarlo en el token. | `LoginRequest.java:6` (sin uso en `AuthResource.java`) | baja | Pendiente

## Notas de verificación

- Revisión estática del código fuente y de `pom.xml`, `application.properties`, `Dockerfile`, `ci.yml` y `docs/GOOD_PRACTICES.md`.
- Advisories de dependencias consultados en línea (cvefeed.io para CVE-2026-50559; resumen de NVD/Apache para CVE-2022-42889).
- No se ejecutaron pruebas: esta fase es solo de análisis y no toca código.
- `docs/ENDPOINTS.md` no existía; se generó a partir del código.
