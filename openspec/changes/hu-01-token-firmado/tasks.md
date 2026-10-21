# Tasks

Decisiones confirmadas antes de este checklist (ver [design.md](design.md#open-questions)):
- `client` opcional; si falta se usa `angular-workshop`; si viene y no está permitido, 400.
- Secreto ausente: el arranque no falla; el primer login responde 500 con el formato actual y no emite token.
- Firma con SmallRye JWT (D1-B).

## 1. Pruebas de caracterización (sin cambios de código)

- [ ] 1.1 Añadir prueba de 200 con `token`, `expiresIn == 3600`, `user.username`, `user.displayName`, `user.role` y `debug`; verificar con `mvn test -Dtest=AuthCharacterizationTest` que pasa sobre el código actual.
- [ ] 1.2 Añadir prueba de `displayName` escapado en HTML (usuario de prueba o test directo de la función de escape); verificar que pasa sobre el código actual.
- [ ] 1.3 Añadir pruebas de 400 con `Request is null`, `Username is required` y `Password is required`, incluidos valores formados solo por espacios; verificar que pasan.
- [ ] 1.4 Añadir pruebas de 401 con `Invalid user <username> or password` para contraseña incorrecta y usuario inexistente; verificar que pasan.
- [ ] 1.5 Añadir prueba de que los campos extra de la solicitud se ignoran; verificar que pasa.

## 2. Especificación de login pendiente

- [ ] 2.1 Añadir al spec `auth-login` el escenario de `client` ausente (se usa `angular-workshop`) y el de `client` no permitido ya existente; verificar con `openspec validate "hu-01-token-firmado"`.
- [ ] 2.2 Añadir al spec `auth-token` el escenario de error de configuración con código 500 y formato `{"message", "exception"}`; verificar con `openspec validate "hu-01-token-firmado"`.

## 3. Configuración sin cambiar el token

- [ ] 3.1 Añadir `auth.token.ttl-seconds` (por defecto `3600`) y `auth.clients.allowed` (por defecto `angular-workshop`) a `application.properties`, sin secretos; verificar con `mvn test` que la prueba 1.1 sigue en verde.
- [ ] 3.2 Hacer que `AuthResource` lea `expiresIn` desde `auth.token.ttl-seconds` en lugar del literal `3600`; verificar que la prueba 1.1 sigue en verde.

## 4. Dependencia y componente de token

- [ ] 4.1 Añadir la dependencia SmallRye JWT a `pom.xml` con la versión gestionada por la plataforma Quarkus; verificar con `mvn -q dependency:tree` que aparece y que `mvn test` compila.
- [ ] 4.2 Crear el componente de emisión y verificación (`issue(username, client)` y `verify(token)`) junto a `LegacyTokenUtil`, sin usarlo aún; verificar con pruebas unitarias de claims `sub`, `client`, `iat`, `exp` y `exp - iat == ttl`.
- [ ] 4.3 Leer `AUTH_TOKEN_SECRET` por propiedad de configuración sin valor por defecto; verificar con prueba de que, con la variable vacía o ausente, `issue` lanza error de configuración.
- [ ] 4.4 Añadir pruebas de `verify` para firma alterada, secreto distinto, token expirado y token en formato Base64 anterior; verificar que todas son rechazadas.

## 5. Emisión de tokens firmados en login

- [ ] 5.1 Cambiar `AuthResource` para emitir el token con el componente nuevo y responder 500 de configuración si falta `AUTH_TOKEN_SECRET`, sin emitir token; verificar con prueba de login sin la variable (500, sin `token`).
- [ ] 5.2 Implementar validación de `client`: ausente usa `angular-workshop`; presente y fuera de `auth.clients.allowed` responde 400 con `message` que menciona `client`; verificar con pruebas de ambos casos.
- [ ] 5.3 Verificar en prueba de login exitoso que el claim `client` coincide con el enviado y que `expiresIn == exp - iat`.
- [ ] 5.4 Actualizar `AuthResourceTest` y las pruebas de caracterización si cambió alguna forma de respuesta; verificar que solo cambian las aserciones de token y que 400, 401 y 500 siguen iguales.

## 6. Eliminar el formato anterior y el secreto literal

- [ ] 6.1 Eliminar el secreto `WORKSHOP_SECRET_2026` y la emisión Base64 de `LegacyTokenUtil`; verificar con `Select-String -Path src/main -Pattern WORKSHOP_SECRET` sin resultados.
- [ ] 6.2 Eliminar cualquier uso restante del formato Base64 en `src/main` y `src/test`; verificar con `mvn test` en verde.

## 7. Logs y redacción

- [ ] 7.1 Revisar `printStackTrace` en `LegacyDatabase` y sustituirlo por un log sin SQL ni contraseña; verificar con prueba de login fallido que ningún log contiene la contraseña enviada.
- [ ] 7.2 Verificar en prueba de login exitoso que ningún log contiene el token ni el secreto; verificar con `mvn test`.

## 8. Documentación de despliegue

- [ ] 8.1 Documentar `AUTH_TOKEN_SECRET`, `auth.token.ttl-seconds` y `auth.clients.allowed` en `README.md`, incluyendo el corte para tokens anteriores (BREAKING); verificar que el texto existe con `Select-String -Path README.md -Pattern AUTH_TOKEN_SECRET`.

## 9. Verificación de integración

- [ ] 9.1 Ejecutar `mvn test` completo y comprobar que todas las pruebas de `auth-token` y `auth-login` pasan; verificar con el resumen de Surefire sin fallos.
- [ ] 9.2 Comprobar manualmente con `curl` que un login válido devuelve un token de tres segmentos, que el token anterior en Base64 es rechazado y que `docs/ENDPOINTS.md` sigue coincidiendo con las respuestas 200, 400, 401 y 500.

## Workflow follow-up

- Actualizar `docs/ENDPOINTS.md` si el contrato de `client` cambia (no previsto con la decisión actual).
- Sincronizar specs y archivar el cambio cuando el Issue #4 esté completo.
