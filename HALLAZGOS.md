# Hallazgos — seguimiento

Fuente: [RETO.md](RETO.md). Los hallazgos originales se confirmaron sobre el
commit `2e7f1a69f62c2eae4bb8f825e7102c376672c8ca`, anterior al refactor.
La columna de evidencia original describe esa revision, no afirma que los
defectos sigan presentes. La evidencia vigente de codigo, plan y pruebas se
detalla por ID en la seccion «Trazabilidad actual de las resoluciones».
Orden: severidad de mayor a menor.

id | hallazgo | tipo | explicacion original | evidencia original (archivo:linea) | severidad | estado | nota de resolucion
--- | --- | --- | --- | --- | --- | --- | ---
H-01 | Concatenación de credenciales en SQL | Vulnerabilidad | username y password se insertan directamente en la sentencia. Un valor con comillas altera la consulta y puede devolver un usuario sin conocer su contraseña. Además, cualquier excepción SQL se registra y termina como 401, ocultando el fallo real. | LegacyDatabase.java:32, LegacyDatabase.java:39-42 | alta | Resuelto | UserRepository usa parametros y errores tipados; se conserva 401 por contrato, con logging explicito. Inyeccion rechazada y usuario con apostrofo probado.
H-02 | Contraseñas en texto plano | Vulnerabilidad | Las contraseñas se guardan y se comparan sin hash. Si la base SQLite se filtra, las credenciales quedan utilizables, y las de la semilla están además en el código fuente. | LegacyDatabase.java:23-24, LegacyDatabase.java:32 | alta | Resuelto | PBKDF2-HMAC-SHA256 con salt aleatorio, semillas por hashes externos y migracion opt-in transaccional/idempotente. Pruebas de contrasenas largas y rollback; no se migro la base habitual.
H-03 | Token Base64 con secreto embebido | Vulnerabilidad | Base64 es codificación, no firma. Quien decodifique el token ve el secreto y puede fabricar tokens para otro usuario o rol. No existe ningún punto que verifique firma o expiración. | LegacyTokenUtil.java:8, LegacyTokenUtil.java:14-15 | alta | Resuelto | TokenService usa Nimbus JOSE JWT/HS256 y claims explicitos; firma, algoritmo, clave y expiracion comprobados localmente. Consumidores externos requieren coordinacion, no fueron modificados.
H-04 | Secreto y CORS hardcodeados | Vulnerabilidad | El secreto vive en el código fuente versionado: no se rota sin recompilar y cualquiera con acceso al repositorio lo conoce. CORS acepta cualquier origen (/.*/), lo que permite que cualquier sitio lea las respuestas del login desde el navegador. | LegacyTokenUtil.java:8, application.properties:4-5 | alta | Resuelto | Clave externa sin fallback y origenes explicitos obligatorios con validacion al arrancar; pruebas HTTP CORS y del paquete ante configuracion ausente/invalida.
H-05 | Respuesta de error expone detalles internos | Vulnerabilidad | El 500 devuelve al cliente el mensaje de la excepción y su clase, lo que revela detalles de implementación. El 401 además refleja el username tal cual. Los errores se imprimen con printStackTrace en lugar de un logger controlado. | AuthResource.java:37, AuthResource.java:55-57, LegacyDatabase.java:27, LegacyDatabase.java:40 | media | Resuelto | Cambio contractual autorizado el 2026-10-19: errores 401/500 genericos sin usuario ni detalle/clase de excepcion. Logger de servidor conserva excepcion/stacktrace sin anadir credenciales; pruebas de ambos caminos y contrato actualizado.
H-06 | Dependencias con posibles CVE (Quarkus 3.27.4 y Commons Text 1.9) | Vulnerabilidad | Commons Text 1.9 es anterior a 1.10.0, versión que corrige CVE-2022-42889 (Text4Shell) según el advisory público. El código solo usa escapeHtml4, que no es el vector de ese CVE, pero la dependencia queda en el classpath y Dependency Review la marcará. Quarkus 3.27.4 requiere revisar advisories vigentes antes de decidir. | pom.xml:13, pom.xml:49-50 | media | Resuelto | Commons Text 1.15.0 y BOM/plugin Quarkus 3.27.6, tras consultar advisories oficiales. Versiones resueltas y contrato probados; controles remotos CodeQL/Dependency Review pendientes, no se afirman aprobados.
H-07 | Resource con validación, negocio, persistencia y respuesta en un solo método | Refactor | AuthResource.login mezcla validación, llamada a persistencia, generación de token y armado de respuesta, con if/else anidados hasta cuatro niveles. Eso impide probar cada capa por separado. Incluye números mágicos (3600) sin constante. | AuthResource.java:24-53, AuthResource.java:42, LegacyTokenUtil.java:13 | baja | Resuelto | Resource -> AuthService -> UserRepository, seguridad separada, DTOs propios, guard clauses y vigencia centralizada; pruebas independientes y contrato HTTP preservado.

## Revisiones y evidencia reproducible

- **Origen de los defectos:** `2e7f1a69f62c2eae4bb8f825e7102c376672c8ca`.
  Las referencias a `LegacyDatabase.java` y `LegacyTokenUtil.java` son
  historicas: esas clases se retiraron. Por ejemplo, se puede recuperar la
  fuente original sin modificar el arbol de trabajo con:

  ```powershell
  git show 2e7f1a6:src/main/java/com/bcp/workshop/auth/LegacyDatabase.java
  git show 2e7f1a6:src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java
  ```

- **Revision de codigo y pruebas referenciada abajo:**
  tag `day1-done` (`day1: prompts, correcciones, pruebas y documentación`).
  Los numeros `archivo:linea` de las fuentes corresponden a esa revision.
  Los enlaces llevan al archivo actual; ante cambios posteriores, consultar
  la version fijada mediante `git show day1-done:<ruta>`.
- **Diff reproducible del componente:**

  ```powershell
  git diff 2e7f1a6 day1-done -- src pom.xml .github
  ```

- **Validacion registrada:** `mvn --batch-mode clean verify
  '-Dquarkus.http.test-port=0'`, con JDK 21, salida **0**.
  [testing-verify.log:547](docs/validation/testing-verify.log#L547) registra
  **56 pruebas, 0 fallos, 0 errores, 0 omitidas**;
  [testing-verify.log:560](docs/validation/testing-verify.log#L560) confirma el
  gate de cobertura y
  [testing-verify.log:562](docs/validation/testing-verify.log#L562), BUILD SUCCESS.
- **Cobertura de lineas de la aplicacion:** 259/265 = **97,74%**, segun
  [testing-coverage.csv](docs/validation/testing-coverage.csv). El metodo,
  alcance, perfiles y comandos completos estan en
  [docs/TESTING.md](docs/TESTING.md).
  Este cambio documental no implica una nueva ejecucion de las pruebas.

## Trazabilidad actual de las resoluciones

### H-01

- **Plan:** [PLAN.md:58](PLAN.md#L58).
- **Correccion:** consulta con `PreparedStatement` y filtro `username=?` en
  [UserRepository.java:69](src/main/java/com/bcp/workshop/auth/UserRepository.java#L69);
  enlace del parametro en
  [UserRepository.java:73](src/main/java/com/bcp/workshop/auth/UserRepository.java#L73).
  Las semillas tambien se parametrizan en
  [UserRepository.java:89](src/main/java/com/bcp/workshop/auth/UserRepository.java#L89).
- **Pruebas:** rechazo de alteracion del predicado SQL en
  [AuthCorrectionTest.java:17](src/test/java/com/bcp/workshop/auth/AuthCorrectionTest.java#L17)
  y lectura literal de usuario con apostrofo en
  [UserRepositoryTest.java:11](src/test/java/com/bcp/workshop/auth/UserRepositoryTest.java#L11).
- **Resultado:** ambas clases aprobadas en
  [testing-verify.log:113](docs/validation/testing-verify.log#L113) y
  [testing-verify.log:540](docs/validation/testing-verify.log#L540).
  El 401 ante fallo SQL se conserva de forma explicita; no se oculta el fallo
  al servidor, que lo registra en
  [AuthResource.java:42](src/main/java/com/bcp/workshop/auth/AuthResource.java#L42).

### H-02

- **Plan:** [PLAN.md:90](PLAN.md#L90); la alternativa a BCrypt para no truncar
  contrasenas largas esta autorizada en la seccion inicial del plan.
- **Correccion:** coste minimo, salt y derivacion PBKDF2 definidos en
  [PasswordHasher.java:17](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L17);
  verificacion del hash con comparacion de bytes en
  [PasswordHasher.java:39](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L39).
  Hashes de semillas externos en
  [application.properties:11](src/main/resources/application.properties#L11).
  Migracion del esquema en
  [PasswordMigration.java:57](src/main/java/com/bcp/workshop/auth/PasswordMigration.java#L57),
  dentro de transaccion con rollback en
  [UserRepository.java:50](src/main/java/com/bcp/workshop/auth/UserRepository.java#L50).
- **Pruebas:** salt y contrasenas largas en
  [PasswordHasherTest.java:11](src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java#L11);
  ausencia de columna en claro en
  [AuthCorrectionTest.java:24](src/test/java/com/bcp/workshop/auth/AuthCorrectionTest.java#L24);
  opt-in/idempotencia en
  [PasswordMigrationTest.java:9](src/test/java/com/bcp/workshop/auth/PasswordMigrationTest.java#L9)
  y rollback en
  [PasswordMigrationTest.java:37](src/test/java/com/bcp/workshop/auth/PasswordMigrationTest.java#L37).
- **Resultado:** clases aprobadas en
  [testing-verify.log:530](docs/validation/testing-verify.log#L530),
  [testing-verify.log:113](docs/validation/testing-verify.log#L113) y
  [testing-verify.log:532](docs/validation/testing-verify.log#L532).
  No acredita migracion ni borrado fisico de datos del despliegue.

### H-03

- **Plan:** [PLAN.md:129](PLAN.md#L129).
- **Correccion:** claims y firma HS256 en
  [TokenService.java:48](src/main/java/com/bcp/workshop/auth/TokenService.java#L48);
  algoritmo y firma comprobados en
  [TokenService.java:63](src/main/java/com/bcp/workshop/auth/TokenService.java#L63);
  rechazo de expiracion en
  [TokenService.java:86](src/main/java/com/bcp/workshop/auth/TokenService.java#L86).
  Vigencia centralizada en
  [TokenService.java:25](src/main/java/com/bcp/workshop/auth/TokenService.java#L25).
- **Pruebas:** claims y limite exacto de expiracion en
  [TokenServiceTest.java:26](src/test/java/com/bcp/workshop/auth/TokenServiceTest.java#L26),
  alteracion/clave incorrecta en
  [TokenServiceTest.java:40](src/test/java/com/bcp/workshop/auth/TokenServiceTest.java#L40)
  y algoritmo/claims invalidos en
  [TokenServiceTest.java:60](src/test/java/com/bcp/workshop/auth/TokenServiceTest.java#L60).
- **Resultado:** 3 pruebas aprobadas en
  [testing-verify.log:538](docs/validation/testing-verify.log#L538).
  La verificacion local no acredita que consumidores externos validen el token.

### H-04

- **Plan:** [PLAN.md:160](PLAN.md#L160).
- **Correccion:** clave y origenes externos sin fallback en
  [application.properties:14](src/main/resources/application.properties#L14);
  validacion de la clave en
  [TokenService.java:36](src/main/java/com/bcp/workshop/auth/TokenService.java#L36)
  y de origenes explicitos en
  [CorsConfiguration.java:16](src/main/java/com/bcp/workshop/auth/CorsConfiguration.java#L16).
- **Pruebas:** origen permitido, ausencia de Origin y rechazo 403 en
  [CorsTest.java:14](src/test/java/com/bcp/workshop/auth/CorsTest.java#L14),
  [CorsTest.java:24](src/test/java/com/bcp/workshop/auth/CorsTest.java#L24) y
  [CorsTest.java:31](src/test/java/com/bcp/workshop/auth/CorsTest.java#L31);
  rechazo de comodines/configuracion invalida en
  [CorsConfigurationTest.java:9](src/test/java/com/bcp/workshop/auth/CorsConfigurationTest.java#L9).
- **Resultado:** clases aprobadas en
  [testing-verify.log:117](docs/validation/testing-verify.log#L117) y
  [testing-verify.log:125](docs/validation/testing-verify.log#L125).
  Las sondas adicionales del paquete y sus resultados estan en
  [Check-Startup.ps1](docs/validation/Check-Startup.ps1) y
  [startup-results.txt](docs/validation/startup-results.txt).

### H-05

- **Plan y autorizacion:** [PLAN.md:184](PLAN.md#L184) y la actualizacion
  posterior de alcance en [PLAN.md:5](PLAN.md#L5), que autoriza errores genericos
  y sustituye el bloqueo contractual inicial.
- **Correccion:** respuestas genericas y logging de excepciones en
  [AuthResource.java:41](src/main/java/com/bcp/workshop/auth/AuthResource.java#L41);
  401 sin usuario reflejado en
  [AuthResource.java:64](src/main/java/com/bcp/workshop/auth/AuthResource.java#L64).
  El contrato vigente esta en [docs/ENDPOINTS.md](docs/ENDPOINTS.md).
- **Pruebas:** respuesta sin detalle interno en
  [ErrorHandlingTest.java:18](src/test/java/com/bcp/workshop/auth/ErrorHandlingTest.java#L18)
  y logging de ambos tipos de fallo en
  [ErrorHandlingTest.java:58](src/test/java/com/bcp/workshop/auth/ErrorHandlingTest.java#L58).
  Verificacion HTTP real de 401/500 con fallos inducidos en
  [AuthFailureHttpTest.java:23](src/test/java/com/bcp/workshop/auth/AuthFailureHttpTest.java#L23),
  [AuthFailureHttpTest.java:28](src/test/java/com/bcp/workshop/auth/AuthFailureHttpTest.java#L28)
  y [AuthFailureHttpTest.java:33](src/test/java/com/bcp/workshop/auth/AuthFailureHttpTest.java#L33).
- **Resultado:** clases aprobadas en
  [testing-verify.log:528](docs/validation/testing-verify.log#L528) y
  [testing-verify.log:98](docs/validation/testing-verify.log#L98).

### H-06

- **Plan:** [PLAN.md:209](PLAN.md#L209).
- **Correccion:** version compartida del BOM/plugin Quarkus en
  [pom.xml:13](pom.xml#L13) y Commons Text en [pom.xml:55](pom.xml#L55).
  El arbol resuelto se conserva en
  [dependencies-after.txt](docs/validation/dependencies-after.txt).
- **Pruebas:** versiones efectivas de Commons Text y Quarkus en
  [DependencyPolicyTest.java:14](src/test/java/com/bcp/workshop/auth/DependencyPolicyTest.java#L14)
  y [DependencyPolicyTest.java:19](src/test/java/com/bcp/workshop/auth/DependencyPolicyTest.java#L19).
- **Resultado:** 2 pruebas aprobadas en
  [testing-verify.log:127](docs/validation/testing-verify.log#L127).
  La investigacion de advisories esta documentada en el apartado H-06 de
  [RESULTADOS.md](docs/validation/RESULTADOS.md).
  Esto verifica la correccion de las dependencias senaladas, no certifica
  ausencia de CVE en toda la cadena; CodeQL/Dependency Review remotos siguen
  sin resultado aprobado registrado.

### H-07

- **Plan:** [PLAN.md:230](PLAN.md#L230).
- **Correccion:** adaptacion HTTP y guard clauses en
  [AuthResource.java:29](src/main/java/com/bcp/workshop/auth/AuthResource.java#L29),
  autenticacion en
  [AuthService.java:21](src/main/java/com/bcp/workshop/auth/AuthService.java#L21),
  persistencia en
  [UserRepository.java:67](src/main/java/com/bcp/workshop/auth/UserRepository.java#L67)
  y DTOs en [LoginResponse.java:3](src/main/java/com/bcp/workshop/auth/LoginResponse.java#L3)
  y [UserResponse.java:5](src/main/java/com/bcp/workshop/auth/UserResponse.java#L5).
  La vigencia se comparte mediante
  [TokenService.java:25](src/main/java/com/bcp/workshop/auth/TokenService.java#L25).
- **Pruebas:** servicio independiente en
  [AuthServiceTest.java:9](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java#L9),
  mapeo/escape HTML en
  [UserResponseTest.java:9](src/test/java/com/bcp/workshop/auth/UserResponseTest.java#L9)
  y forma exacta de respuesta/compatibilidad en
  [AuthCharacterizationTest.java:15](src/test/java/com/bcp/workshop/auth/AuthCharacterizationTest.java#L15).
- **Resultado:** clases aprobadas en
  [testing-verify.log:123](docs/validation/testing-verify.log#L123),
  [testing-verify.log:542](docs/validation/testing-verify.log#L542) y
  [testing-verify.log:111](docs/validation/testing-verify.log#L111).
  El gate de cobertura >=85% se declara en [pom.xml:118](pom.xml#L118).

## Criterio humano y veredicto del review

### Identificacion y evidencia de la decision

- **Fecha:** 2026-10-19.
- **Persona que confirma:** usuario de esta sesion. La identidad Git indicada
  por el usuario para la entrega es Andres Koga
  (`andreskoga@bcp.com.pe`); este registro es una declaracion en la conversacion,
  no una firma digital ni una revision externa independiente.
- **Revision sometida a confirmacion:**
  `e15950e004fd109f3ac7da50cfb46eb2748aa664`, publicada en `day1/prompting`
  y apuntada por `day1-done` al momento de la confirmacion.
  Se fija el hash para que el alcance no cambie si el tag se mueve despues.
- **Base del review tecnico:** diff del componente contra `main`, estado de
  Git, fuentes, documentos y pruebas. El analisis tecnico lo realizo el
  asistente; la confirmacion siguiente procede del usuario.
- **Declaracion humana literal**, recibida mediante el formulario de
  confirmacion de esta sesion:

  > Confirmo el cierre de H-01–H-07 con las observaciones pendientes del review; he revisado y comprendido los cambios

### Decisiones humanas previas y como afectaron la implementacion

| Decision expresada en la sesion | Consecuencia y evidencia |
|---|---|
| Aprobar el plan y resolver sus decisiones antes de implementar. | Aprobacion registrada en [PLAN.md:14](PLAN.md#L14); no se trato la aprobacion inicial como permiso para asumir configuraciones o cambiar el contrato. |
| Ampliar el plan a todos los hallazgos, no solo H-01/H-02: "you should do a plan for all the findings". | El plan contempla H-01 a H-07; las secciones de trazabilidad anteriores enlazan cada ID con su correccion y pruebas. |
| Autorizar una alternativa de hash lento con salt que preserve contrasenas largas, en lugar de BCrypt. | Se eligio PBKDF2 del JDK; justificacion en [PLAN.md:28](PLAN.md#L28) y pruebas de contrasenas largas en [PasswordHasherTest.java:11](src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java#L11). |
| Exigir origenes CORS externos sin valor por defecto y migracion explicita opt-in, probada solo sobre bases temporales. | Decisiones registradas en [PLAN.md:19](PLAN.md#L19); configuracion y migracion verificadas en H-02/H-04. No se autorizo migrar la base habitual durante las pruebas. |
| Corregir la discrepancia de JSON malformado preservando el contrato documentado. | Autorizacion en [PLAN.md:25](PLAN.md#L25); se agregaron mappers acotados y la prueba [AuthCharacterizationTest.java:111](src/test/java/com/bcp/workshop/auth/AuthCharacterizationTest.java#L111). |
| Autorizar expresamente terminar H-05 con errores HTTP genericos y actualizar contrato/pruebas. | Excepcion a la restriccion contractual inicial, registrada en [PLAN.md:5](PLAN.md#L5). Las pruebas HTTP de H-05 confirman el nuevo cuerpo sin detalle interno. |
| Tras el review, elegir "Solo entregar la revision, sin modificar codigo". | La observacion temporal no se corrigio silenciosamente ni se presento como resuelta; permanece pendiente como se describe abajo. |

### Veredicto confirmado

**Cierre de los hallazgos originales H-01 a H-07 confirmado por el usuario,
con observaciones pendientes. No se reabre ninguno de esos siete IDs en esta
decision.** El cierre se refiere a las causas originales y al alcance local
comprobado, no a ausencia total de riesgos ni a aprobacion para produccion.

La confirmacion se basa en las correcciones y pruebas enlazadas por ID y en
el resultado registrado de **56 pruebas aprobadas y 97,74% de cobertura de
lineas de la aplicacion**. No se afirma una nueva ejecucion por escribir
esta seccion.

### Observaciones que la confirmacion no da por resueltas

| Observacion del review / entrega | Estado y siguiente accion |
|---|---|
| Diferencia temporal entre usuario existente e inexistente: [AuthService.java:22](src/main/java/com/bcp/workshop/auth/AuthService.java#L22) solo ejecuta la comprobacion del hash si encuentra la cuenta. | **Pendiente.** Registrar seguimiento con ID propio y HU de D2; proponer verificacion de un hash ficticio con coste equivalente y una prueba del trabajo realizado en ambos caminos. No equivale a reabrir el almacenamiento en claro de H-02. |
| Enlaces de parches por hallazgo declarados en [RESULTADOS.md](docs/validation/RESULTADOS.md) sin todos sus archivos de destino. | **Pendiente documental.** Generar los parches o sustituir los enlaces por referencias reproducibles; el comando de diff fijado en este archivo permite revisar el cambio, pero no convierte enlaces ausentes en evidencia existente. |
| CodeQL, Dependency Review y checker autorizado de handoff sin resultado verde acreditado en esta sesion. | **Pendiente de validacion.** Ejecutar los controles en su contexto autorizado y adjuntar resultados. No reducir gates ni presentar el tag publicado como sustituto de esas ejecuciones. |
| Requisito de client en [HU-01.md](HU-01.md), mientras el contrato actual lo ignora. | **Pendiente de D2/decision de alcance.** Reconciliar la historia con el contrato antes de agregar validacion o claims nuevos. No se autoriza cambiar ese comportamiento con este veredicto. |

## Limites del seguimiento

Los estados indican correccion de los hallazgos originales dentro de este
repositorio. Aplicar migracion/saneamiento a datos existentes y coordinar
consumidores/origenes sigue siendo una tarea de despliegue.
La confirmacion humana registrada arriba tiene el alcance y las reservas
expresados; no convierte observaciones nuevas del review en resueltas ni
acredita controles externos pendientes. No se usaron secretos reales ni se
modificaron otros componentes.
