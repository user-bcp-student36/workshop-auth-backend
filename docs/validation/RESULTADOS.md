# Implementacion de PLAN.md - evidencia

## Actualizacion posterior: H-05 completado

El 2026-10-19 el usuario autorizo cambiar el contrato y las pruebas para
eliminar la filtracion HTTP de H-05. Esta seccion sustituye las referencias
historicas inferiores a H-05 pendiente y al contrato de errores inalterado.

- 401: `{"message":"Invalid credentials"}`, tambien ante fallo SQL por
  compatibilidad del estado. No refleja el usuario.
- 500: `{"message":"Authentication failed"}`. No incluye clase ni mensaje
  de excepcion, incluso si el mensaje interno es nulo.
- Logger del servidor: un evento ERROR por fallo, con la excepcion y sus
  causas/stacktrace, sin agregar usuario ni contrasena de la solicitud.
  Se comprueban ambos caminos, persistencia e imprevistos.
- Pruebas antes del cambio: `mvn --batch-mode test
  -Dquarkus.http.test-port=0 -Dtest=ErrorHandlingTest,AuthCharacterizationTest`
  devuelve **13 pruebas, 4 fallos esperados, 0 errores**.
  [Evidencia roja](h05-before.log).
- Despues: `mvn --batch-mode clean verify quarkus:build
  -Dquarkus.http.test-port=0`, con JDK 21, **BUILD SUCCESS**,
  **41 pruebas, 0 fallos, 0 errores, 0 omitidas**.
  [Evidencia final](h05-after.log).
- Cobertura limpia: **259/265 = 97,74%** global;
  **257/263 = 97,72%** en clases modificadas/nuevas. Gate >=85% aprobado.
  [Contadores actualizados](coverage.csv).
- Paquete real: `.\docs\validation\Check-Startup.ps1 -JavaHome $env:JAVA_HOME`
  verifica salud UP, login valido 200 con token firmado y login invalido 401
  con un unico campo generico. Tambien aprueba seis rechazos de configuracion.
  [Sondas](startup-results.txt). Procesos detenidos y bases temporales retiradas.
- Actualizados contrato, README, PLAN y HALLAZGOS; H-05 marcado **Resuelto**.
  Desviacion expresamente aprobada: cambiar los cuerpos 401/500, sin alterar
  estados ni las respuestas de exito y validacion.

## Registro historico anterior al cierre de H-05

Fecha: 2026-10-19. Ambito exclusivo: este repositorio. Aprobacion y decisiones
humanas registradas en [PLAN.md](../../PLAN.md).

## Resultado final

- **41 pruebas, 0 fallos, 0 errores, 0 omitidas** con JDK 21.
- Cobertura limpia de lineas: **261/267 = 97,75%** global;
  **259/265 = 97,74%** sobre clases modificadas/nuevas, excluyendo solamente
  `LoginRequest` y `UserRecord`, que no cambiaron. Todas las clases modificadas
  tienen al menos 85% de lineas cubiertas. [Contadores CSV](coverage.csv).
- Se agrego comprobacion JaCoCo >=85% de lineas al `verify` existente. No se
  redujeron controles ni se modificaron workflows CodeQL/Dependency Review.
- `docs/ENDPOINTS.md` no se edito. Se preservan JSON, campos, metadatos, escape
  HTML, estados, errores y `client` opcional/ignorado. La correccion autorizada
  de JSON malformado alinea el comportamiento real con ese contrato.
- H-01/H-02/H-03/H-04/H-06/H-07: **Resuelto** en
  [HALLAZGOS.md](../../HALLAZGOS.md). H-05: **Pendiente**, avance parcial.

## Ejecuciones y evidencia

Todos los comandos Maven se ejecutaron con `JAVA_HOME` apuntando a JDK 21.
Los comandos son relativos a la raiz de este repositorio.

| Fase | Comando | Resultado observado |
|---|---|---|
| Caracterizacion HTTP inicial | `mvn --batch-mode test -Dquarkus.http.test-port=0 -Dtest=AuthResourceTest,AuthCharacterizationTest` | 8 pruebas: 7 pasan, 1 error: JSON malformado devuelve 400 vacio, sin Content-Type. |
| Correcciones iniciales y errores heredados | `mvn --batch-mode test -Dquarkus.http.test-port=0 -Dtest=AuthCorrectionTest,ErrorHandlingTest` | 6 pruebas: 4 fallan (SQL, esquema, token, CORS), 2 de errores 500 pasan. |
| Persistencia/token iniciales | `mvn --batch-mode test -Dtest=PersistenceCharacterizationTest,TokenCharacterizationTest` | 2 pruebas pasan. |
| SQL parametrizado | `mvn --batch-mode test -Dquarkus.http.test-port=0 -Dtest=AuthResourceTest,PersistenceCharacterizationTest,AuthCorrectionTest#credentialsCannotChangeTheSqlPredicate` | 3 pruebas pasan. |
| Hash antes de implementarlo | `mvn --batch-mode test -Dtest=PasswordHasherTest` | Rojo de compilacion: `PasswordHasher` todavia no existia. No es un fallo de algoritmo observado. |
| Hash/migracion despues | `mvn --batch-mode test -Dquarkus.http.test-port=0 -Dtest=AuthResourceTest,AuthCorrectionTest#credentialsCannotChangeTheSqlPredicate+activeSchemaContainsOnlyPasswordHashes,PasswordHasherTest,PasswordMigrationTest,PersistenceCharacterizationTest` | 8 pruebas pasan. |
| Token/CORS despues | `mvn --batch-mode test -Dquarkus.http.test-port=0 -Dtest=AuthResourceTest,AuthCorrectionTest,TokenServiceTest,TokenCharacterizationTest` | 8 pruebas pasan. |
| Limite HTTP/DTO antes del ultimo refactor | `mvn --batch-mode test -Dtest=ErrorHandlingTest` | 4 pruebas: 2 pasan, 2 fallan (DTO aun HashMap y regresion intermedia de persistencia 500 en lugar de 401). |
| Caracterizacion antes de actualizar dependencias | `mvn --batch-mode test -Dquarkus.http.test-port=0 -Dtest=AuthCharacterizationTest,AuthResourceTest,UserResponseTest,ErrorHandlingTest,CorsTest` | 17 pruebas pasan con Commons Text 1.9 y Quarkus 3.27.4. |
| Versiones corregidas antes de actualizar | `mvn --batch-mode test -Dtest=DependencyPolicyTest` | 2 fallos: se resuelven 1.9 y 3.27.4 en lugar de las versiones corregidas. |
| Verificacion final desde limpio | `mvn --batch-mode clean verify quarkus:build -Dquarkus.http.test-port=0` | **BUILD SUCCESS**, 41 pruebas pasan, gate JaCoCo aprobado y paquete ejecutable construido. |
| Paquete real/configuracion | `.\docs\validation\Check-Startup.ps1 -JavaHome $env:JAVA_HOME` | 6 rechazos explicitos de configuracion y 1 arranque valido con salud UP y login 200. |

Las primeras tres ejecuciones y la correccion SQL usaron exclusivamente
`AUTH_DB_URL=jdbc:sqlite:target\characterization-before.db`; la inicializacion
heredada no aceptaba sobreescritura por propiedad de prueba. Tras extraer el
repositorio, `AuthTestResource` genera SQLite temporal en `target`, hashes y
claves sinteticas automaticamente. Las pruebas limpias y las sondas eliminan
esas bases; no se migro ni inspecciono la base habitual.

Evidencia conservada:
[historial resumido](test-history.txt),
[log final](verify.log),
[sondas de arranque](startup-results.txt),
[script reproducible](Check-Startup.ps1),
[dependencias antes](dependencies-before.txt) y
[despues](dependencies-after.txt).
Los arboles se generaron con `mvn --batch-mode dependency:tree -DoutputFile=...`;
el anterior usa el `pom.xml` del commit inicial mediante una copia temporal
en `target`, ya eliminada por `clean`.

## Por hallazgo: caracterizacion, correccion y diff

Los diffs por hallazgo muestran el estado final frente a Git HEAD e incluyen
archivos compartidos: **no son parches independientes para aplicar en serie**.
La secuencia real se evidencia por las ejecuciones anteriores; no se crearon
commits. Las fuentes de pruebas anteriores permanecen identificadas por nombre;
al retirar clases heredadas se adapto el cableado de sus pruebas, no las
expectativas HTTP. La prueba de token se adapto al nuevo formato opaco.

### H-01 - SQL

- Caracterizacion antes: login valido, credenciales incorrectas y semilla
  idempotente pasan. Despues: siguen pasando.
- Correccion antes: `credentialsCannotChangeTheSqlPredicate` esperaba 401 y
  obtenia **200**. Despues: **401**, prueba aprobada.
- Adicional despues: busqueda parametrizada acepta usuario con apostrofo;
  errores SQL se convierten en `PersistenceFailure`, se registran y conservan
  el 401 documentado. `UserRepositoryTest` y `PasswordMigrationTest` pasan.
- Diff inicial aislado: [H-01-sql.patch](H-01-sql.patch).
  Diff final asociado: [H-01.patch](H-01.patch).

### H-02 - Contrasenas

- Caracterizacion antes: ambos usuarios, rechazo de credenciales incorrectas,
  espacios literales e inicializacion repetida pasan. Despues: pasan, con
  verificacion del hash en el servicio.
- Correccion antes: `activeSchemaContainsOnlyPasswordHashes` falla; existe
  `password` y falta `password_hash`. Despues: pasa; no existe columna en claro.
- `PasswordHasherTest` se escribio antes del hasher: rojo por API inexistente;
  despues pasa con salt distinto, contrasena larga Unicode sin truncamiento,
  verificacion correcta/incorrecta y errores de formato/coste explicitos.
- `PasswordMigrationTest` agrega opt-in, conservacion de usuarios, espacios,
  idempotencia y rollback: aprobado. No existia migrador contra el que ejecutar
  esos escenarios antes; no se afirma que se probaran sobre datos reales.
- Diff: [H-02.patch](H-02.patch).

### H-03 - Token

- Caracterizacion antes/despues: identidad, rol y vencimiento de una hora
  pasan. Antes se decodificaba Base64 solo para inspeccionar el detalle interno;
  despues se verifica el token firmado. Ningun consumidor debe depender de
  esa representacion anterior.
- Correccion antes: `tokenUsesSignedCompactFormat` falla (1 segmento en lugar
  de 3). Despues pasa; `TokenServiceTest` comprueba firma alterada, clave
  incorrecta, algoritmo distinto, claims ausentes/invalidos y vencimiento
  exacto en `now >= exp`. El verificador antes no existia.
- Diff: [H-03.patch](H-03.patch). No se modifica consumidor externo ni se
  afirma que este ya verifique tokens.

### H-04 - Configuracion/CORS

- Caracterizacion antes: origen arbitrario obtiene autorizacion CORS.
  Despues: origen configurado autorizado, otro no autorizado; preflight y
  login sin `Origin` pasan en `CorsTest`.
- Correccion antes: `unapprovedOriginReceivesNoCorsAuthorization` falla;
  recibe el origen no aprobado. Despues pasa.
- Una primera sonda de paquete sin CORS no termino en fallo dentro de 30 s;
  se detuvo su PID y se agrego validacion de arranque dedicada. Despues:
  falta de clave/CORS/semilla, Base64 invalido, clave corta y comodin producen
  salida 1. La configuracion valida responde en loopback y luego se detiene.
- Diff: [H-04.patch](H-04.patch).

### H-05 - Errores (parcial)

- Caracterizacion antes/despues: los cuerpos 500 con detalle y con mensaje
  nulo pasan. 401 conserva el usuario reflejado y fallo SQL conserva 401.
- Correccion implementable: logging con excepcion sin anadir credenciales,
  sin `printStackTrace`. `logsFailuresWithoutAddingRequestCredentials` pasa
  despues. No se ejecuto esta prueba de logging contra el codigo inicial;
  no se inventa un resultado rojo previo.
- La prueba del error SQL detecto una regresion **intermedia** durante la
  extraccion (500); luego vuelve a pasar con 401. No era el resultado inicial
  del codigo heredado y no se presenta como tal.
- Correccion completa de filtracion HTTP: **bloqueada**, sin cambio de cuerpo
  ni prueba de errores genericos habilitada. Estado sigue **Pendiente**.
- Diff: [H-05.patch](H-05.patch).

### H-06 - Dependencias

- Caracterizacion antes/despues de actualizar: escape HTML, JSON y matriz
  HTTP pasan; 17 pruebas en el control previo, incluidas en las 41 finales.
- Correccion antes: 2 fallos de `DependencyPolicyTest` por versiones efectivas
  1.9/3.27.4. Despues: ambas pruebas pasan con 1.15.0/3.27.6.
- [Apache Commons Text](https://commons.apache.org/proper/commons-text/security.html):
  CVE-2022-42889 afecta versiones anteriores a 1.10.0 mediante interpolacion;
  `escapeHtml4` no es ese vector. Se eligio 1.15.0, version publicada mantenida.
- [Advisory oficial Quarkus GHSA-xrmg-9xc7-83fp](https://github.com/quarkusio/quarkus/security/advisories/GHSA-xrmg-9xc7-83fp):
  parche LTS en 3.27.5.1 para REST multipart. Se selecciono BOM/plugin 3.27.6,
  sin afirmar explotabilidad por el login que consume JSON.
- Nimbus 10.10 se agrega para firma/verificacion JOSE, no para hash. Se
  comprobo publicacion en Maven Central y la consulta de advisories GitHub
  para esa coordenada/version no devolvio avisos en esta revision.
- CodeQL/Dependency Review **no ejecutados remotamente**: requieren su
  contexto de GitHub. Los workflows originales se conservan; resolucion de
  estos avisos concretos no equivale a certificar toda la cadena sin CVE.
- Diff: [H-06.patch](H-06.patch).

### H-07 - Capas/Clean Code

- Caracterizacion antes/despues: mismos estados/cuerpos, validacion en el
  mismo orden, `client` ignorado, campos desconocidos permitidos y escape HTML.
- Correccion antes del ultimo paso: `successUsesAnExplicitOutputDto` obtiene
  `HashMap`, falla. Despues obtiene `LoginResponse`, pasa. La extraccion minima
  de servicio/repositorio ya se habia iniciado junto a H-01/H-02, segun el plan.
- `AuthServiceTest`, `UserRepositoryTest` y `UserResponseTest` prueban capas
  independientemente; Resource solo adapta HTTP, no ejecuta SQL ni firma.
  Guard clauses, mapeo unico y vigencia centralizada sustituyen anidamiento y
  duplicacion de valores. `StoredUser.toString()` no imprime el hash.
- Diff: [H-07.patch](H-07.patch).

## Desviaciones y limitaciones

1. **Autorizada:** PBKDF2 del JDK reemplaza BCrypt para preservar contrasenas
   largas. No se agrego dependencia para el hash ni se trunco la entrada.
2. **Autorizada:** dos mappers acotados cubren las excepciones que Quarkus
   distingue (JSON malformado envuelto y tipo incompatible). Antes: 400 vacio;
   despues: 400 con el cuerpo documentado. Errores HTTP ajenos no se reescriben.
   [Diff adicional](deserializacion.patch).
3. **Necesaria para H-04:** clase `CorsConfiguration` valida configuracion
   explicita al arrancar; la prueba del paquete detecto que dejar solo el
   placeholder no garantizaba el rechazo requerido. No se amplian origenes.
4. **Calidad:** se refuerza JaCoCo con el gate aprobado de 85%; la ejecucion
   limpia evita clases heredadas residuales y datos de cobertura acumulados.
5. **Verificacion:** se agrega el script de sondas bajo esta carpeta como
   evidencia reproducible, sin dependencias instaladas ni procesos residentes.
6. **Pendiente por diseno:** H-05 no se cierra completamente. Origenes reales,
   rotacion, verificacion en consumidores, migracion/saneamiento de bases
   existentes y conciliacion de HU-01 requieren coordinacion de despliegue.
