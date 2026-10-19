# PLAN - Correccion de H-01 a H-07

## Aprobacion humana registrada

- Actualizacion posterior, 2026-10-19: el usuario autorizo explicitamente
  "Finish H-05: authorize generic HTTP errors and update the endpoint contract
  and tests". Sustituye el bloqueo de H-05 descrito en las secciones historicas
  inferiores: 401 conserva su estado con `Invalid credentials`; 500 devuelve
  solo `Authentication failed`, sin clase ni mensaje de excepcion.
  Los detalles y stacktrace siguen en el logger del servidor; no se agregan
  credenciales de la solicitud. Se actualizan contrato, pruebas y seguimiento.

- Fecha: 2026-10-19.
- Aprobacion explicita del usuario en esta conversacion:
  "Apruebo PLAN.md; registrar esta aprobacion y resolver decisiones pendientes
  antes de implementar".
- Esta aprobacion habilita resolver las decisiones pendientes; no las da por
  resueltas. No autoriza cambiar el contrato ni cerrar H-05 por completo.
- Decisiones posteriores aprobadas en la misma conversacion: seleccionar y
  justificar bibliotecas mantenidas; usar una alternativa de hash lento con
  salt en lugar de BCrypt para preservar contrasenas largas; exigir
  `AUTH_CORS_ORIGINS` sin valor por defecto; migracion explicita opt-in,
  transaccional e idempotente con semillas mediante hashes externos, probada
  solo en bases temporales; implementar lo viable y documentar bloqueos.
- Desviacion contractual previa detectada y aprobada: JSON malformado devolvia
  400 sin cuerpo. El usuario autorizo mappers acotados de deserializacion para
  devolver el JSON ya documentado, sin editar `docs/ENDPOINTS.md`.
- Seleccion tecnica justificada bajo la autorizacion: PBKDF2-HMAC-SHA256 del
  JDK (salt de 16 bytes, clave derivada de 32 bytes, minimo 600000 iteraciones)
  y Nimbus JOSE JWT 10.10 con HS256. La clave externa contiene al menos
  32 bytes aleatorios codificados en Base64. BCrypt queda sustituido, no se
  agrega un limite nuevo de longitud de contrasena.
- Configuracion CORS: validacion explicita al arrancar mediante
  `CorsConfiguration`; la sustitucion de propiedades de Quarkus por si sola
  no garantizo fallo de arranque al omitir origenes en la prueba del paquete.

## 1. Summary

Propuesta basada en [HALLAZGOS.md](HALLAZGOS.md), el
[contrato](docs/ENDPOINTS.md), las [convenciones](docs/GOOD_PRACTICES.md)
y [REGLAS.md](REGLAS.md), tras analizar el flujo completo y su prueba actual.
Por indicacion del usuario se incluyen todos los hallazgos **Pendiente**:
H-01 a H-07. Se propone priorizar los de severidad alta y resolver las
dependencias entre correcciones en cambios revisables. Este documento no
implementa las correcciones ni cambia estados en la matriz.

## 2. Scope

- **Dentro del alcance:** planificar SQL parametrizado, hash de contrasenas,
  token firmado, configuracion externa, tratamiento de errores, dependencias
  y separacion de capas, con caracterizacion previa para cada hallazgo.
- **Fuera del alcance:** implementar codigo, modificar el contrato, actualizar
  dependencias sin justificacion o leer/modificar otros componentes.
- **Rutas comunes:** los nombres Java de produccion indicados abajo pertenecen
  a `src/main/java/com/bcp/workshop/auth/`; las pruebas, a
  `src/test/java/com/bcp/workshop/auth/`, salvo ruta explicita.

### H-01 - Concatenacion de credenciales en SQL

- **Referencia y problema:** H-01 de la matriz; `LegacyDatabase.findByCredentials`
  concatena entradas en el filtro SQL. Las excepciones tambien terminan como
  ausencia de usuario y respuesta 401.
- **Cambio propuesto:** primero sustituir la consulta por `PreparedStatement`
  con parametros para usuario y contrasena; conservar seleccion y mapeo de
  columnas y cierre mediante try-with-resources. Parametrizar tambien los
  valores de insercion de la semilla. Al implementar H-02, reemplazar la
  busqueda por credenciales por una consulta parametrizada por usuario:
  la comprobacion del hash no corresponde al SQL.
- **Capas y errores:** introducir `UserRepository` para JDBC y `AuthService`
  para autenticar; `AuthResource` conserva solo adaptacion HTTP. Distinguir
  internamente fallo de persistencia y credenciales invalidas mediante un
  error especifico y registro del servidor sin credenciales. Por compatibilidad
  con el flujo documentado, el adaptador mantiene 401 ante fallo de consulta;
  no cambiarlo silenciosamente a 500. Revisar esa politica requiere otro acuerdo.
- **Archivos a tocar:** `LegacyDatabase.java` (mover persistencia y retirar
  codigo sustituido), nuevos `UserRepository.java` y `AuthService.java`,
  `AuthResource.java`; `UserRecord.java` solo si necesita un modelo interno
  separado. Todos bajo `src/main/java/com/bcp/workshop/auth/`.
  Ampliar `AuthResourceTest.java` y crear `UserRepositoryTest.java` bajo
  `src/test/java/com/bcp/workshop/auth/`.
- **Caracterizacion ANTES de tocar produccion:** registrar login valido,
  usuario inexistente y contrasena incorrecta con su cuerpo exacto; comprobar
  el comportamiento actual de un usuario con apostrofo almacenado mediante
  fixture parametrizado, sin exigir que el defecto actual pase como caso valido;
  caracterizar
  el 401 ante error SQL en SQLite aislado. No convertir un bypass en conducta
  contractual. Despues, agregar regresiones que exijan 401 para entradas que
  intentan alterar la consulta y comprueben parametros como valores literales.

### H-02 - Contrasenas en texto plano

- **Referencia y problema:** H-02 de la matriz; tabla y semilla contienen
  contrasenas recuperables y la autenticacion compara texto plano.
- **Cambio propuesto:** encapsular PBKDF2-HMAC-SHA256 del JDK en
  `PasswordHasher`, con salt aleatorio y coste definido mediante configuracion
  con nombre. Esta alternativa a BCrypt fue autorizada para preservar
  contrasenas largas y no necesita dependencias adicionales. No implementar
  primitivas criptograficas propias ni usar un hash rapido.
- **Persistencia y migracion:** `UserRepository` obtiene el hash por usuario;
  `AuthService` lo verifica y devuelve un resultado sin hash. No agregar
  contrasena/hash a `UserRecord` ni a DTOs HTTP. Migrar SQLite local mediante
  una operacion explicita, transaccional e idempotente: distinguir el esquema
  antiguo, convertir todas las filas, validar y retirar la columna en claro
  antes del commit. Un fallo aborta y se informa; no dejar fallback de login
  en texto plano. Probar el procedimiento sobre una copia aislada antes de
  aplicarlo a datos existentes. La eliminacion logica no borra necesariamente
  paginas antiguas, WAL o respaldos: prever saneamiento y rotacion de
  credenciales antes de declarar eliminado el riesgo.
- **Semilla:** retirar contrasenas literales del codigo; provisionar hashes
  desde configuracion externa, sin valores productivos por defecto. Mantener
  los dos usuarios y su comportamiento cuando se provea la configuracion
  requerida. Confirmar el mecanismo antes de implementar; una configuracion
  requerida ausente debe producir error explicito, no omitir usuarios.
- **Archivos a tocar:** `UserRepository.java`, `AuthService.java`,
  nuevo `PasswordHasher.java` y migrador especifico bajo el paquete de auth;
  `LegacyDatabase.java` mientras exista inicializacion heredada;
  `src/main/resources/application.properties`, `pom.xml` solo para la
  dependencia aprobada y `README.md` para provisionamiento/migracion.
  Pruebas nuevas `PasswordHasherTest.java`, `PasswordMigrationTest.java` y
  `AuthServiceTest.java`, mas las pruebas HTTP y de repositorio anteriores.
- **Caracterizacion ANTES de tocar produccion:** fijar login de ambos usuarios,
  rechazo de contrasena incorrecta, sensibilidad a mayusculas y tratamiento
  literal de espacios no vacios; inicializacion repetida sin duplicacion ni
  sobrescritura. Usar fixtures sinteticos, no datos reales. Despues, comprobar
  hashes distintos para la misma contrasena, verificacion correcta/incorrecta,
  ausencia de texto plano en esquema activo, migracion repetible, rollback y
  ausencia de hash o contrasena en respuesta y logs.

### H-03 - Token Base64 con secreto embebido

- **Referencia y problema:** H-03 de la matriz; `LegacyTokenUtil` codifica
  datos y secreto sin firma ni verificacion de expiracion.
- **Cambio propuesto:** sustituirlo por `TokenService`, usando una biblioteca
  mantenida para tokens firmados, compatible con Quarkus y justificada antes
  de agregarla. Definir algoritmo permitido y claims `sub`, `role`, `iat` y
  `exp`; verificar firma y expiracion con reloj inyectable, sin aceptar
  algoritmos elegidos libremente por la entrada. Centralizar vigencia de
  3600 segundos y usarla tambien para `expiresIn`. No crear criptografia casera.
- **Compatibilidad:** conservar token como cadena opaca y todos los campos
  HTTP. No existe endpoint consumidor en este repositorio: probar el
  verificador local y coordinar compatibilidad/rotacion con consumidores sin
  leerlos ni modificarlos. No afirmar que su validacion queda implementada.
  La clave externa pertenece a H-04, requisito de este cambio.
- **Archivos a tocar:** `LegacyTokenUtil.java` (retirar al sustituirlo),
  nuevo `TokenService.java`, `AuthService.java`, `AuthResource.java`,
  `pom.xml` solo para biblioteca aprobada, `README.md`;
  nuevas `TokenServiceTest.java` y ampliacion de `AuthResourceTest.java`.
- **Caracterizacion ANTES de tocar produccion:** comprobar token no vacio,
  datos del usuario de prueba y vigencia actual de 3600 segundos con tolerancia
  explicita de reloj. Documentar formato Base64 como detalle interno que se
  reemplaza, no como contrato a perpetuar. Despues, probar claims, firma
  alterada, clave incorrecta y rechazo exactamente en `now >= exp` con reloj
  fijo; asegurar coherencia exacta entre `exp - iat` y `expiresIn`.
- **Decision pendiente:** [HU-01.md](HU-01.md) describe HMAC ya implementado,
  pero el codigo analizado no lo tiene. Ademas exige validar `client`, hoy
  opcional e ignorado segun el contrato. No asumir esa historia implementada
  ni hacer `client` obligatorio; reconciliarla por separado antes de ampliar
  claims o validaciones de entrada.

### H-04 - Secreto y CORS hardcodeados

- **Referencia y problema:** H-04 de la matriz; clave embebida en Java y
  origen CORS universal en `application.properties`.
- **Cambio propuesto:** configurar la clave de H-03 mediante
  `AUTH_TOKEN_SECRET` sin secreto por defecto, validar requisitos del algoritmo
  al iniciar y fallar explicitamente si falta o es invalida. Externalizar
  origenes mediante `AUTH_CORS_ORIGINS` con lista explicita, sin comodin de
  respaldo. No cambiar metodos/cabeceras CORS innecesariamente; no registrar
  claves ni tokens. Coordinar y documentar rotacion.
- **Archivos a tocar:** `LegacyTokenUtil.java` al sustituirlo,
  `TokenService.java`, `src/main/resources/application.properties`,
  `README.md`; nuevas `TokenConfigurationTest.java` y `CorsTest.java`,
  mas `TokenServiceTest.java`. Valores sinteticos solo en configuracion de
  pruebas aislada.
- **Caracterizacion ANTES de tocar produccion:** registrar respuestas y
  cabeceras actuales para origen permitido y arbitrario, preflight y solicitud
  sin `Origin`, sin congelar el comodin como requisito. Despues, comprobar
  acceso desde origen configurado, ausencia de autorizacion CORS para otro y
  error de arranque por configuracion invalida. CORS no reemplaza autenticacion.
- **Decision pendiente:** obtener los origenes autorizados sin inspeccionar
  otros componentes. El contrato JSON se conserva; restringir navegadores es
  un cambio intencional de politica, que requiere aprobar lista y despliegue.

### H-05 - Respuesta de error expone detalles internos

- **Referencia y problema:** H-05 de la matriz; respuestas 500 exponen mensaje
  y clase, 401 refleja usuario y persistencia usa `printStackTrace`.
- **Cambio propuesto compatible ahora:** sustituir `printStackTrace` por
  logging del servidor con excepcion y contexto no sensible; capturar fallos
  esperados especificamente y mantener un limite HTTP para imprevistos,
  nunca ocultarlos como exito. Revisar que parametros, tokens, hashes y secretos
  no se incorporen a los registros.
- **Bloqueo contractual:** mensajes genericos y eliminar la clase del 500
  contradicen los cuerpos 401/500 fijados en `docs/ENDPOINTS.md`. Documentar
  esta correccion propuesta, pero no aplicarla bajo la restriccion actual.
  Mantener esos cuerpos deja riesgo residual: H-05 no puede cerrarse completo
  sin autorizacion de revision/versionado del contrato. No presentar logging
  como solucion de la filtracion HTTP.
- **Archivos a tocar:** `LegacyDatabase.java`/`UserRepository.java`,
  `AuthService.java`, `AuthResource.java`, `AuthResourceTest.java`;
  nueva `ErrorHandlingTest.java`. La rama de errores genericos queda bloqueada;
  este plan no propone editar `docs/ENDPOINTS.md` dentro del alcance vigente.
- **Caracterizacion ANTES de tocar produccion:** fijar mensajes exactos 401,
  500 con mensaje y con mensaje nulo, clase de excepcion y 401 por fallo SQL,
  mediante fallos sinteticos. Despues, verificar logging controlado sin datos
  sensibles y mismo contrato. Solo tras autorizacion, definir nuevas pruebas
  de errores genericos y actualizar expectativas de forma explicita.

### H-06 - Dependencias con posibles CVE

- **Referencia y problema:** H-06 de la matriz; Commons Text 1.9 tiene el
  advisory citado y Quarkus 3.27.4 requiere investigar avisos vigentes.
  No asumir nuevos CVE confirmados ni explotabilidad por `escapeHtml4`.
- **Cambio propuesto:** contrastar advisories oficiales y salida de los
  controles existentes en el momento de implementar; actualizar Commons Text
  a una version mantenida que corrija los avisos aplicables (1.10.0 es el
  minimo citado, no una recomendacion de version vigente). Si Quarkus resulta
  afectado, actualizar conjuntamente BOM y plugin a una version corregida
  compatible con Java 21. No silenciar alertas ni agregar otro escaper.
- **Archivos a tocar:** `pom.xml`, `README.md` solo si cambian requisitos;
  `AuthResourceTest.java` y pruebas de escape/serializacion. No cambiar
  archivos de CI sin identificar una necesidad concreta.
- **Caracterizacion ANTES de tocar produccion:** fijar escape HTML de
  `displayName` con caracteres especiales, serializacion y matriz HTTP; guardar
  arbol Maven y avisos actuales como evidencia. Despues, repetir pruebas,
  comparar dependencias resueltas y adjuntar resultados de CodeQL/Dependency
  Review disponibles. Si los controles remotos no se pueden ejecutar,
  declararlos pendientes, no aprobados.

### H-07 - Resource con responsabilidades mezcladas

- **Referencia y problema:** H-07 de la matriz; `AuthResource.login` valida,
  autentica, crea token y construye respuesta con anidamiento y literales.
- **Cambio propuesto:** completar Resource -> Service -> Repository con
  seguridad en `PasswordHasher`/`TokenService`, reutilizando los componentes
  de H-01 a H-04. Validacion HTTP mediante guard clauses, DTOs explicitos de
  respuesta sin entidad de persistencia y mapeo unico de usuario/escape HTML.
  Una responsabilidad por metodo; extraer solo duplicacion real y centralizar
  vigencia/valores de protocolo con nombres. Mantener `debug` y orden de
  validacion actual; no confundir refactor con cambio funcional.
- **Archivos a tocar:** `AuthResource.java`, `AuthService.java`,
  `UserRepository.java`, `LoginRequest.java` solo si lo exige la extraccion,
  nuevos `LoginResponse.java` y `UserResponse.java`, componentes de seguridad
  ya propuestos; retirar clases heredadas solo despues de migrar referencias.
  Ampliar `AuthResourceTest.java`, `AuthServiceTest.java` y pruebas de mapeo.
- **Caracterizacion ANTES de tocar produccion:** matriz completa 200/400/401/500,
  precedencia de errores cuando faltan ambos campos, campos JSON exactos,
  `client` ausente/presente y propiedades desconocidas. Despues, repetirla y
  probar servicio/repositorio independientemente, sin cambiar errores H-05.

## 3. Supuestos

- Se conserva SQLite y no se necesita leer consumidores externos.
- El token sigue siendo opaco, aunque se propone cambiar su implementacion.
- No se ejecutan migraciones ni se modifica la base durante esta planificacion.

## 4. Decisiones tecnicas por tomar

- Resuelto: algoritmo/coste de hash, biblioteca/algoritmo de tokens,
  provisionamiento por hashes externos y migracion explicita opt-in.
- Pendiente de despliegue: lista real de origenes CORS, coordinacion con
  consumidores, ventana de migracion, recuperacion y saneamiento de copias.
- Bloqueado por contrato: errores genericos de H-05 y validacion de `client`
  de HU-01; se conserva el comportamiento documentado.
- Aplicar cambios revisables: caracterizacion comun; H-01 y H-02 con extraccion
  minima de H-07; H-03 junto con la clave externa de H-04; politica CORS H-04;
  logging compatible H-05; completar H-07. H-06 puede realizarse por separado
  una vez fijada su caracterizacion, sin mezclar actualizacion y refactor.
  Cada metodo
  tendra una responsabilidad, helpers sin duplicacion, guard clauses y
  constantes/configuracion con nombre; evitar anidamiento profundo.

## 5. Riesgos y mitigaciones

- **Perdida de acceso al migrar:** transaccion, validacion de todas las filas
  y ensayo de recuperacion aislado; no sobrescribir cuentas existentes.
- **Regresion HTTP:** pruebas de cuerpos/estados y DTO de salida explicito,
  sin exponer el modelo con hash.
- **Coste del hash y formatos incompatibles:** medir coste antes de desplegar
  y probar entradas limite; no inventar umbrales de rendimiento.
- **Consumidores del token y navegadores:** coordinar formato firmado,
  rotacion y origenes antes del despliegue sin ampliar el ambito de archivos.
- **Filtracion HTTP residual:** mantener H-05 abierto hasta resolver el
  conflicto contractual; no certificarlo como corregido.

## 6. Criterios de aceptacion medibles

1. Cero cambios en `docs/ENDPOINTS.md`; mantener JSON, campos
   `token`, `expiresIn=3600`, `user`, `debug`, escape HTML de `displayName`,
   `client` opcional, propiedades desconocidas ignoradas y cuerpos 400/401/500.
2. Cero valores de credenciales concatenados en SQL; todas las regresiones
   de inyeccion rechazan autenticacion indebida.
3. Cero contrasenas en claro en el esquema activo migrado o semillas Java;
   hashes con salt verificables y migracion idempotente con rollback probado.
4. Todas las pruebas pasan y la cobertura del codigo modificado es >=85%,
   sin silenciar JaCoCo, CodeQL ni Dependency Review.
5. Tokens firmados con vigencia de 3600 segundos, sin clave embebida;
   pruebas de firma alterada y expiracion aprobadas. CORS sin origen universal
   y con pruebas de origen autorizado/no autorizado.
6. Ningun `printStackTrace` en el flujo modificado; H-05 permanece abierto
   por filtracion HTTP mientras no se autorice cambiar el contrato.
7. H-06 tiene evidencia de advisories, versiones resueltas y controles;
   ningun hallazgo se marca corregido solo por haber escrito este plan.
8. Resource sin JDBC ni decisiones criptograficas; DTOs sin campos sensibles
   y metodos con una responsabilidad, sin duplicacion ni anidamiento profundo.

## 7. Plan de validacion

- **Unitarias:** repositorio SQLite temporal, servicio, hasher, migrador,
  token con reloj fijo y mapeo de DTOs;
  ninguna prueba debe usar la base local habitual.
- **Funcionales:** ampliar la prueba HTTP actual antes del refactor para
  cubrir 200/400/401/500, cuerpos, campos opcionales y escape HTML. Usar fallos
  controlados por dobles, sin agregar dependencias de mocking sin justificar.
  Caracterizar solicitudes nulas/no deserializables y detenerse si la conducta
  real difiere del contrato; no modificarlo para acomodar la prueba.
- **Seguridad minima:** entradas SQL literales, hashes con salts distintos,
  firma/expiracion, CORS y errores observables sin credenciales en logs;
  ejecutar los controles existentes y verificar dependencias sin silenciarlos.
- **Evidencia requerida:** salida de `mvn --batch-mode test
  -Dquarkus.http.test-port=0` y `mvn --batch-mode verify
  -Dquarkus.http.test-port=0` con JDK 21, reporte JaCoCo sobre codigo cambiado
  y evidencia sintetica de migracion/rollback. El puerto aleatorio evita el
  conflicto local detectado en 8081. Estas validaciones corresponden a la
  implementacion futura; no se ejecutan como evidencia de una correccion hoy.

## 8. Tareas sugeridas por rol

- **Tech Lead:** aprobar bibliotecas/coste/algoritmo; resolver migracion y
  compatibilidad token/CORS; revisar capas y bloqueo contractual de H-05.
- **Backend:** escribir caracterizacion; parametrizar y separar autenticacion;
  implementar hash/migracion y token/configuracion con errores explicitos;
  actualizar dependencias justificadas y completar DTOs.
- **QA:** verificar matriz HTTP; probar migracion/rollback e idempotencia;
  probar firma, expiracion y CORS; adjuntar resultados y cobertura >=85%.
- **Chapter/PO:** confirmar decisiones pendientes; no hay cambio funcional pedido.
- **Frontend:** no aplica implementacion; coordinar dudas de contrato sin leer
  ni modificar su componente.
- **Cyber:** validar bibliotecas, hash/firma, saneamiento y advisories;
  documentar riesgo residual de H-05.

## 9. Preguntas abiertas

- Algoritmos, coste minimo y mecanismo de semilla quedaron resueltos arriba.
- Que procedimiento de recuperacion y saneamiento se permite para SQLite?
- Que lista de origenes CORS se aprovisionara en el despliegue?
- Como se coordinara el formato firmado con consumidores externos sin
  ampliar el ambito? Como se reconciliara HU-01 sin cambiar `client`?
- La compatibilidad obliga a conservar errores 401/500 inseguros: su cambio
  queda bloqueado dentro de H-05 y requiere autorizar una revision del contrato.
