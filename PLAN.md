# Plan de correccion del backend

## Alcance y decisiones

Se seleccionan **H-01, H-02 y H-03**, los tres primeros hallazgos de severidad
alta y estado Pendiente de [HALLAZGOS.md](HALLAZGOS.md). Este documento es una
propuesta: no implementa cambios ni actualiza estados. El seguimiento permanece
exclusivamente en esa matriz. H-04, H-05, H-06 y H-07 no se consideran resueltos
por este plan, aunque algunas correcciones reduzcan parte de su riesgo.

Bases: [docs/ENDPOINTS.md](docs/ENDPOINTS.md),
[docs/GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) y [REGLAS.md](REGLAS.md).
Orden propuesto: caracterizacion comun, H-01, H-02 y H-03, con cambios revisables
y verificacion independiente en cada paso.

## Contrato y caracterizacion comun

Antes de modificar cualquier clase, ampliar
[src/test/java/com/bcp/workshop/auth/AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
Hoy solo comprueba 200, username y presencia de token. Usar JUnit 5 y
RestAssured ya declarados, con SQLite temporal bajo target y AUTH_DB_URL aislada
antes de cargar LegacyDatabase; nunca ejecutar estas pruebas contra data/workshop.db.

Pruebas que deben pasar antes y despues de las correcciones:

- Student y trainer autentican con sus credenciales de laboratorio. Verificar
  Content-Type JSON, campos exactos token, expiresIn, user y debug; token string
  no vacio, expiresIn=3600, username y role correctos y debug literal actual.
- Verificar user con username, displayName y role, sin id ni password. Un usuario
  de prueba con caracteres HTML confirma escapeHtml4 en displayName.
- Client ausente o presente y campos desconocidos no cambian la autenticacion.
- Request null, username null/vacio/blanco y password null/vacio/blanco producen
  los 400 y mensajes documentados, respetando el orden de validacion. Para
  request null probar tambien el metodo directamente, sin asumir como Quarkus
  deserializa un cuerpo HTTP ausente.
- Usuario inexistente o password incorrecto devuelve el 401 y mensaje exactos.
  Los espacios de una credencial no vacia no se eliminan para autenticar.
- Caracterizar que un error de consulta capturado produce null y luego 401.
  Forzar en una prueba directa una excepcion que alcance el Resource usando un
  doble local de LegacyDatabase; verificar el 500 y sus campos actuales, tanto
  con mensaje como sin mensaje. No agregar una biblioteca de mocks solo para esto.

Conservar ruta, metodo, entrada, codigos, mensajes y estructura JSON descritos
en el contrato. No quitar debug, modificar el escape de displayName, agregar
restricciones de longitud ni corregir la exposicion del 500 en este alcance.
No editar [docs/ENDPOINTS.md](docs/ENDPOINTS.md). El documento permite cambiar
el mecanismo interno del token; no obliga a conservar Base64 inseguro.

## H-01: SQL concatenado

### Hallazgo y problema

H-01 de [HALLAZGOS.md](HALLAZGOS.md). Las dos credenciales se concatenan en el
WHERE y se ejecutan mediante Statement, permitiendo alterar la consulta
([src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32),
[src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:35](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L35)).

### Cambio propuesto

Reemplazar la consulta por SQL fijo con dos placeholders y PreparedStatement.
Asignar username y password con setString, sin trim, escape manual ni construir
fragmentos SQL con entradas. Mantener try-with-resources, el mapeo de UserRecord
y el resultado null cuando no hay coincidencia o falla la consulta. Separar
consulta y mapeo en metodos de una responsabilidad. Este primer paso no cambia
el almacenamiento de passwords; H-02 sustituira luego la comparacion SQL.
No requiere dependencias nuevas: JDBC y SQLite ya estan disponibles.

### Archivos a tocar

- [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java).
- [src/test/java/com/bcp/workshop/auth/AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
- Nuevo test de persistencia, reutilizado tambien por H-02:

```text
src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java
```

### Prueba de caracterizacion previa

En una base temporal, comprobar ambos usuarios conocidos, password incorrecto,
usuario ausente, espacios significativos y el mapeo completo del primer registro.
Caracterizar el fallo de consulta mediante una base aislada sin la tabla esperada:
findByCredentials devuelve null. Estas pruebas deben pasar en el codigo actual.

### Verificacion de la correccion

Agregar regresiones que antes fallen: un usuario de fixture con apostrofo debe
autenticar solo con su password correcto; entradas con caracteres SQL deben
tratarse como datos y no autenticar a otro usuario. No fijar el bypass vulnerable
como comportamiento a conservar ni incluir payloads de explotacion en el plan.
Repetir la caracterizacion HTTP sin cambios de mensajes ni codigos.

## H-02: contrasenas en texto plano

### Hallazgo y problema

H-02 de [HALLAZGOS.md](HALLAZGOS.md). Los seeds guardan passwords literales y la
autenticacion compara el recibido con la columna password
([src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:23](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L23),
[src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:24](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L24),
[src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32)).
Cambiar solo los seeds no corrige bases existentes porque INSERT OR IGNORE
conserva las filas antiguas.

### Cambio propuesto

Consultar por username con PreparedStatement y verificar el password en una capa
de seguridad, fuera del repositorio y del Resource. Usar Argon2id con salt
aleatorio por password y formato codificado que conserve algoritmo y parametros.
No implementar el algoritmo manualmente. Proponer Password4j como biblioteca:
el JDK y las dependencias directas actuales no ofrecen el hash lento requerido;
Argon2id evita el limite de entrada de BCrypt que podria truncar credenciales
largas admitidas por el contrato. Antes de agregarla, fijar una version mantenida,
revisar advisories, licencia, transitivas y compatibilidad con JDK 21/Quarkus.
No se agrega ninguna dependencia en esta etapa.

Definir parametros de coste con nombres explicitos y validarlos; ajustar el coste
con mediciones locales antes de fijar valores. No agregar limites o normalizacion
de passwords a la API. La base nunca recibe el password de entrada para buscarlo.
Mantener el hash en un modelo interno de credenciales, separado de UserRecord y
de los DTO de respuesta; nunca devolverlo ni registrarlo en logs.

Migrar SQLite antes de servir autenticacion: crear una tabla de reemplazo con
password_hash, convertir todas las filas existentes, conservar id, username,
display_name y role, y sustituir la tabla dentro de una transaccion. Registrar
version de esquema para que el proceso sea idempotente y no vuelva a hashear un
hash. Ante fallo, rollback y no declarar la migracion completa. La base nueva
debe sembrarse directamente con hashes; no conservar una columna de texto plano
ni un fallback de autenticacion contra passwords antiguos.

Coordinar una ventana local sin escrituras y una copia de recuperacion aislada
antes de migrar. Borrar la columna no garantiza eliminar secretos de paginas
libres, WAL, journals o copias: planificar mantenimiento SQLite fuera de la
transaccion (checkpoint/VACUUM cuando corresponda) y retiro seguro de copias
antiguas. No anunciar eliminacion de texto plano si quedan estos artefactos.
Las credenciales de seed siguen siendo datos de laboratorio, no secretos reales.

Extraer la orquestacion necesaria a un servicio de autenticacion. El Resource
valida entrada y mapea HTTP mediante DTO propios; el servicio coordina consulta,
verificacion y resultado; LegacyDatabase conserva solo persistencia. Usar guard
clauses y mantener el adaptador de errores actual para no abordar H-05 aqui.

### Archivos a tocar

- [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java): esquema, migracion, seeds y consulta por username.
- [src/main/java/com/bcp/workshop/auth/AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java): delegacion y mapeo del contrato.
- [pom.xml](pom.xml): biblioteca de hash, solo despues de la comprobacion anterior.
- [src/test/java/com/bcp/workshop/auth/AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) y el test de persistencia propuesto para H-01.
- Nuevas clases y pruebas minimas para separar negocio, seguridad y salida:

```text
src/main/java/com/bcp/workshop/auth/AuthService.java
src/main/java/com/bcp/workshop/auth/PasswordHasher.java
src/main/java/com/bcp/workshop/auth/StoredCredentials.java
src/main/java/com/bcp/workshop/auth/LoginResponse.java
src/main/java/com/bcp/workshop/auth/UserResponse.java
src/test/java/com/bcp/workshop/auth/AuthServiceTest.java
src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java
```

### Prueba de caracterizacion previa

Antes de tocar persistencia, crear fixtures de base nueva y base existente con
usuarios adicionales. Verificar autenticacion correcta/incorrecta y que un segundo
initialize conserva usuarios y metadatos. Reutilizar la caracterizacion HTTP
comun, incluyendo passwords largos y Unicode no normalizados en fixtures locales.
No exigir que siga existiendo texto plano: eso es la vulnerabilidad, no el contrato.

### Verificacion de la correccion

Comprobar migracion sin perdida de usuarios y credenciales funcionales, ausencia
de la columna antigua, hashes verificables y diferentes para passwords iguales
por salts distintos. Probar reinicializacion idempotente, rollback ante fallo,
password incorrecto, hashes corruptos sin autenticacion y passwords largos que
difieran despues de los primeros 72 bytes. Revisar que consulta, DTO y logs no
exponen password ni hash. Repetir HTTP y pruebas antiinyeccion de H-01.

## H-03: token Base64 sin firma

### Hallazgo y problema

H-03 de [HALLAZGOS.md](HALLAZGOS.md). El token contiene username, role, expiry y
un secreto fijo codificados en Base64 reversible, sin integridad criptografica
([src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8),
[src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13),
[src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:15](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15)).
Ya existe una expiracion a una hora; el defecto no es la ausencia del timestamp.

### Cambio propuesto

Emitir JWT firmado mediante la extension quarkus-smallrye-jwt-build, gestionada
por el BOM existente. Justificacion: usar el soporte Quarkus/SmallRye para claims
y firma JWS evita implementar criptografia y serializacion JWT caseras. Verificar
la version resuelta, advisories y transitivas antes de agregar la extension; no
actualizar el BOM ni abordar todo H-06 como efecto lateral. El backend emite
tokens: no agregar endpoints de validacion ni proteccion de login.

Proponer RS256 con clave privada externa, sin valor real ni fallback hardcodeado
en Java, properties o repositorio. Configurar la ubicacion de clave e issuer con
variables de entorno/configuracion Quarkus. En pruebas usar exclusivamente un
par de claves de laboratorio aislado. Validar la configuracion al arrancar y
fallar de forma controlada si falta o es invalida, sin revelar material de clave.

Definir claims explicitos sub=username, groups con el role del usuario, iat,
exp y un issuer configurado. Mantener vida de una hora y expiresIn=3600 mediante
una unica constante con nombre compartida por emision y respuesta. Separar
TokenService del servicio de autenticacion; usar Clock inyectable para pruebas
deterministas. Los claims son legibles: no incluir passwords, hashes ni claves.

El contrato HTTP mantiene token string y los demas campos sin variaciones.
El formato interno puede cambiar segun el contrato, pero la compatibilidad real
con consumidores externos no esta verificada en este ambito. Antes de activar
la nueva emision, obtener evidencia de que esos consumidores verifican RS256,
clave publica, issuer, expiracion y el mapeo del rol; no inspeccionar ni modificar
otros repositorios en esta tarea. Si falta esa evidencia, dejar la activacion
bloqueada y no afirmar resolucion integral. No aceptar tokens legacy como fallback
ni afirmar que emitir exp obliga por si solo a un consumidor a validarlo.

### Archivos a tocar

- [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java): retirar el emisor inseguro cuando todas las llamadas migren.
- [src/main/java/com/bcp/workshop/auth/AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) y AuthService propuesto en H-02: delegacion y expiresIn compartido.
- [src/main/resources/application.properties](src/main/resources/application.properties): referencias a configuracion externa de firma, sin cambiar CORS en este paso.
- [pom.xml](pom.xml): extension de emision justificada, con version del BOM.
- [src/test/java/com/bcp/workshop/auth/AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) y AuthServiceTest propuesto en H-02.
- Nuevas clases y pruebas de seguridad:

```text
src/main/java/com/bcp/workshop/auth/TokenService.java
src/main/java/com/bcp/workshop/auth/TokenPolicy.java
src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java
src/test/java/com/bcp/workshop/auth/TokenServiceTest.java
```

### Prueba de caracterizacion previa

Antes de reemplazar el emisor, registrar en LegacyTokenUtilTest el comportamiento
actual: salida string decodificable, identidad y rol recibidos, timestamp dentro
del intervalo entre los instantes anterior/posterior a la llamada mas 3600.
Usar solo el secreto de laboratorio actual. Es una caracterizacion temporal del
mecanismo que se retira, no una prueba de aceptacion perpetua de Base64.
La caracterizacion HTTP comun permanece estable y no decodifica el token legacy.

### Verificacion de la correccion

Al sustituir el emisor, retirar la prueba especifica del formato legacy junto
con la clase; mantener las pruebas HTTP. Con reloj fijo y claves de prueba,
verificar firma con la clave publica, algoritmo permitido, issuer, identidad,
grupos e intervalo exp-iat=3600. Usar un verificador de la biblioteca, no uno
casero; demostrar rechazo de firma alterada, clave incorrecta, algoritmo no
permitido y token vencido con reloj controlado. Estas pruebas validan el formato
emitido, no prueban el comportamiento de consumidores no inspeccionados.
Comprobar que el token no contiene el antiguo secreto y que configuracion
ausente/invalida impide emitirlo. No cambiar mensajes HTTP para resolver H-05.

## Reglas de implementacion y cierre futuro

- Metodos de una responsabilidad: validacion HTTP, consulta, mapeo, verificacion
  de password, migracion y emision separados. No trasladar el metodo largo intacto
  a un servicio ni devolver StoredCredentials como respuesta HTTP.
- Compartir mapeos, helpers de fixtures y politica de duracion para evitar
  duplicacion. Usar nombres que expresen finalidad y retornos anticipados.
- SQL, duracion, nombres de configuracion y parametros de hash deben tener
  constantes o propiedades con nombres explicitos, no literales repetidos.
- Escribir y ejecutar primero las caracterizaciones contra la implementacion
  actual. Las regresiones de seguridad se agregan despues y deben demostrar el
  cambio intencional sin perpetuar vulnerabilidades como contrato.
- Ejecutar `mvn -Dtest=AuthResourceTest,LegacyDatabaseTest test` para H-01;
  sumar AuthServiceTest y PasswordHasherTest para H-02, y TokenServiceTest para
  H-03. Estos comandos son futuros: aun no existen todos esos tests.
- Tras cada paso ejecutar `mvn verify`, revisar el informe JaCoCo y demostrar
  cobertura del codigo modificado de al menos 85%. El POM configura informe,
  no un umbral automatico de cobertura: el exito de Maven solo no prueba la meta.
  No silenciar CodeQL, Dependency Review ni JaCoCo.
- Revisar diff y contrato en cada paso. Solo actualizar [HALLAZGOS.md](HALLAZGOS.md)
  con evidencia de pruebas y correccion efectiva; no cerrar H-04 por externalizar
  la clave mientras CORS siga permisivo, ni H-07 sin revisar toda su separacion.

En esta etapa solo se crea PLAN.md. No se crean pruebas, clases, migraciones ni
dependencias y no se ejecuta Maven para evitar inicializar o modificar datos.