# Auditoria del flujo de autenticacion: evidencia, cobertura y seguimiento

## 1. Objetivo y limites

Analiza el backend Quarkus de autenticacion y documenta sus hallazgos reales.
Esta es una fase de diagnostico: NO implementes correcciones ni refactorices.

- Trabaja exclusivamente en `workshop-auth-backend`, repositorio Git
  independiente dentro de `reqflow-workspace`.
- Todas las rutas de este prompt son relativas a ese repositorio. En Windows,
  usa separadores adecuados para las operaciones de archivos.
- No leas ni modifiques otros componentes, directorios padre, configuraciones
  personales ni archivos externos. Si una conclusion depende de otro servicio,
  registra la dependencia y el limite de verificacion; no amplifiques el alcance.
- No modifiques codigo, pruebas, dependencias, configuracion, workflows,
  credenciales ni bases de datos. No instales herramientas ni paquetes.
- No hagas commits, cambios de rama, limpieza de Git ni sobrescrituras.
- El laboratorio es intencionalmente vulnerable: no lo publiques, no lo
  despliegues ni ejecutes payloads de explotacion. Usa revision estatica como
  metodo principal. No inicies el servicio ni ejecutes comandos que puedan
  crear o modificar datos.
- Los unicos archivos que puedes crear son el informe seleccionado segun la
  seccion 3 y `docs\ENDPOINTS.md` si este ultimo no existe.
- No modifiques este prompt durante su ejecucion.

## 2. Lectura obligatoria y fuentes de verdad

Antes de emitir conclusiones:

1. Lee `README.md`, `RETO.md`, `REGLAS.md` y `docs\ENDPOINTS.md`, si existe.
2. Identifica las instrucciones locales aplicables dentro de este repositorio.
3. Registra el estado inicial de Git para distinguir cambios previos de los
   archivos creados por esta auditoria. No reviertas cambios ajenos.
4. Lee el informe previo, si existe, solo como antecedente. Sus afirmaciones,
   severidades y referencias NO son evidencia: vuelve a verificarlas.
5. Revisa el codigo fuente actual, no los binarios de `target`, para reconstruir
   comportamiento y obtener numeros de linea.

`RETO.md` es una lista de hipotesis que deben comprobarse, no una prueba de que
los problemas existan. El codigo determina el comportamiento observable;
`docs\ENDPOINTS.md` define el contrato que hay que preservar.

Si `docs\ENDPOINTS.md` no existe, crealo a partir de las rutas, DTO, codigo y
pruebas disponibles. Distingue comportamiento demostrado de comportamiento
desconocido. No inventes respuestas del framework ni ejecutes el servicio para
completar vacios. Si ya existe, no lo edites: informa discrepancias con evidencia.

## 3. Seleccion del informe y versionado sin sobrescrituras

Selecciona el archivo de salida antes de escribir:

- Si no existe `HALLAZGOS.md` ni ninguna version numerada, crea `HALLAZGOS.md`.
- Si existe el informe base o cualquier version, crea
  `HALLAZGOS.vN.md`, donde `N` es el mayor numero de version existente mas uno,
  con un minimo de 2. Ejemplo: si existen el base y `HALLAZGOS.v3.md`, crea
  `HALLAZGOS.v4.md`.
- Comprueba que el destino sigue libre antes de crearlo. Nunca sobrescribas,
  renombres ni copies un informe previo como sustituto de una auditoria nueva.
- Conserva los informes anteriores intactos como historial.

El informe nuevo debe declarar su nombre como archivo de seguimiento activo de
esta ejecucion. Solo ese archivo contendra la matriz vigente; no crees matrices
paralelas, tickets ni archivos auxiliares de seguimiento. La fase posterior
`2-refactor.md` debera actualizar ese archivo explicitamente, no asumir que el
informe activo siempre se llama `HALLAZGOS.md`. No edites esa fase ahora.

## 4. Reconstruccion completa del flujo

Sigue el flujo real, desde la entrada HTTP hasta cada salida, incluyendo las
ramas de error. Empieza por:

- `src\main\java\com\bcp\workshop\auth\AuthResource.java`
- `src\main\java\com\bcp\workshop\auth\LoginRequest.java`
- `src\main\java\com\bcp\workshop\auth\LegacyDatabase.java`
- `src\main\java\com\bcp\workshop\auth\LegacyTokenUtil.java`
- `src\main\java\com\bcp\workshop\auth\UserRecord.java`
- `src\main\resources\application.properties`
- `pom.xml`
- `src\test\java\com\bcp\workshop\auth\AuthResourceTest.java`

Amplia la lectura a otros archivos del mismo repositorio solo si son relevantes:
otras pruebas, configuraciones por perfil, `Dockerfile`, workflows y
`docs\GOOD_PRACTICES.md`, si existen. Busca llamadas y consumidores locales antes
de concluir que una funcion, campo, validacion o control no existe.

Documenta una secuencia con referencias de archivo y linea para:

1. Ruta, metodo HTTP, tipos de contenido y deserializacion del request.
2. Tratamiento de cuerpo nulo, campos ausentes, nulos, vacios o con espacios;
   orden de validacion y diferencias entre validar y normalizar.
3. Uso real de cada campo del DTO, incluido `client`.
4. Inicializacion de SQLite, URL JDBC, esquema, semilla y ciclo de vida.
5. Construccion y ejecucion de la consulta; transformacion de sus resultados.
6. Comportamiento ante credenciales invalidas, fallos SQL e inicializacion
   fallida; diferencia entre ausencia de usuario y error de infraestructura.
7. Construccion del token: entradas, codificacion, secreto, firma, claims y
   calculo del tiempo. Distingue generacion de verificacion y expiracion
   declarada de expiracion efectivamente comprobada.
8. Construccion de las respuestas: estado HTTP, campos, tipos, mensajes,
   transformaciones como escape HTML y datos de depuracion.
9. Configuracion efectiva observable en el repositorio: CORS, secretos,
   variables de entorno, perfiles y valores por defecto.
10. Cobertura existente: que ramas prueban realmente las pruebas y cuales no.

Cuando el flujo termine en un limite externo, indicalo. La ausencia de un
verificador de tokens en este backend no demuestra por si sola que otro
componente acepte tokens falsificados.

## 5. Cobertura minima y busqueda de detalles adicionales

Primero enumera todos los casos de `RETO.md` como `RETO-01`, `RETO-02`, etc.,
en su orden original. Comprueba cada uno, incluidos los subcasos agrupados.
Cada caso debe corresponder al menos a una fila de la matriz, aunque el
resultado sea "No encontrado". Puedes separar problemas independientes de un
mismo caso cuando tengan causa, impacto o correccion distintos.

Despues busca problemas adicionales demostrables, sin limitarte a repetir
los siete casos sembrados. Usa estas preguntas como guia, NO como hallazgos
preaprobados:

- **Entrada y contrato:** validaciones inconsistentes, limites ausentes con
  impacto concreto, nulos, normalizacion que altere credenciales, campos
  ignorados, diferencias entre contrato, implementacion y pruebas.
- **Persistencia:** inyeccion, almacenamiento de credenciales, semilla,
  permisos configurados, recursos JDBC sin cerrar, estado compartido,
  concurrencia, atomicidad y fallos de inicializacion.
- **Token y datos sensibles:** integridad, predictibilidad, contenido
  reversible, exposicion del secreto, delimitadores ambiguos, unidades de
  tiempo, roles, expiracion y filtraciones por respuestas o logs.
- **Configuracion:** valores incrustados, precedencia y alternativas por
  entorno, CORS realmente configurado, exposicion accidental de informacion
  y diferencias entre perfiles documentados.
- **Errores y observabilidad:** errores convertidos en "usuario invalido",
  excepciones suprimidas, detalles internos expuestos, logs sin contexto o
  con informacion sensible, respuestas que aparenten exito tras un fallo.
- **Diseno y mantenibilidad:** responsabilidades mezcladas, duplicacion,
  complejidad, valores magicos y acoplamiento que impida aislar pruebas.
- **Pruebas y automatizacion:** ramas criticas no caracterizadas, assertions
  insuficientes, pruebas dependientes de estado persistente y controles
  declarados pero no configurados. No supongas cobertura por leer un reporte
  antiguo ni confundas cobertura de lineas con calidad de assertions.
- **Dependencias:** versiones directas y transitivas verificables, uso real
  de las API afectadas y evidencia de advisories aplicables.

No conviertas toda preferencia de estilo, ausencia de una buena practica o
control no observado en una vulnerabilidad. Explica el fallo concreto o
clasificalo como refactor/sugerencia con alcance y limites claros. No dupliques
una misma causa para aumentar artificialmente el numero de hallazgos.

### Reglas especificas para dependencias y CVE

- Verifica las versiones declaradas y sus referencias en `pom.xml`.
- No deduzcas vulnerabilidad solamente por antiguedad o por aparecer en
  `RETO.md`; no confundas version de plataforma, extension y artefacto transitivo.
- Para afirmar un CVE, aporta identificador, fuente oficial o advisory del
  mantenedor, rango afectado y condiciones relevantes. Incluye URL y fecha de
  consulta si puedes verificar fuentes publicas.
- No envies codigo, configuraciones privadas ni credenciales a servicios
  externos. Las consultas publicas deben limitarse a identificadores publicos
  de paquetes, versiones y advisories.
- Distingue: dependencia presente, version afectada y ruta vulnerable
  alcanzable. Una llamada a `escapeHtml4` no demuestra uso de interpolacion
  peligrosa, aunque comparta biblioteca con ella.
- No afirmes que un pipeline fallara sin evidencia de su configuracion y de
  un resultado aplicable.
- Si no puedes verificar un advisory o resolver una version transitiva sin
  cambiar el entorno, documenta la limitacion. Registra la investigacion
  pendiente como `Sugerencia`, no como vulnerabilidad confirmada.

## 6. Estandar de evidencia y clasificacion

Para cada hallazgo confirmado:

- Cita rutas relativas completas y lineas actuales, contadas desde 1.
  No uses solo el nombre del archivo cuando pueda resultar ambiguo.
- Cita las lineas exactas de la causa y, cuando sea necesario, del uso o
  consecuencia. Evita rangos extensos que obliguen a buscar el problema.
- Explica el recorrido entrada -> operacion -> consecuencia, las condiciones
  necesarias y lo que puede salir mal. No incluyas payloads de explotacion.
- Separa lo observado de lo inferido. No afirmes impacto fuera del repositorio
  ni configuracion de despliegue que no hayas visto.
- Para afirmar ausencia de un control, indica el alcance y los puntos de
  entrada/configuracion inspeccionados; una busqueda sin resultados no prueba
  ausencia en sistemas externos.
- No transcribas secretos ni credenciales en el informe. Basta referenciar la
  ubicacion y explicar su exposicion.

Escoge un solo tipo, el mas representativo:

- `Vulnerabilidad`: debilidad de seguridad sustentada por evidencia y un
  escenario de impacto, con sus precondiciones.
- `Bug`: comportamiento incorrecto, manejo defectuoso de errores o
  inconsistencia funcional demostrable.
- `Refactor`: problema estructural o de mantenibilidad sin fallo funcional
  o vulnerabilidad independiente demostrados.
- `Sugerencia`: mejora o verificacion pendiente sin defecto confirmado.

Asigna severidad por impacto y condiciones reales, no por el orden de RETO:

- `alta`: compromete de forma sustancial autenticacion, credenciales,
  integridad o confidencialidad bajo condiciones demostradas.
- `media`: impacto limitado o dependiente de condiciones adicionales
  explicitas; errores operativos o exposicion de informacion relevante.
- `baja`: mantenibilidad, observabilidad o mejoras de impacto reducido.

Si un caso de RETO no aparece, no inventes evidencia ni severidad:
usa `No encontrado` en estado, `No evaluada` en severidad y explica donde
buscaste. Esta es la unica excepcion a la escala alta/media/baja. La ausencia
de acceso a una fuente externa no equivale a "No encontrado" en el codigo.
Para casos no confirmados, el tipo `Sugerencia` no implica defecto demostrado.

## 7. Estructura y formato del informe

Escribe el informe en espanol con estas secciones:

1. **Alcance y metodo:** archivo activo, fecha real, revision Git si esta
   disponible, presencia de cambios locales y limites de la revision.
2. **Mapa del flujo:** secuencia normal y ramas de error, con archivo:linea,
   configuracion relevante y fronteras externas.
3. **Matriz de seguimiento:** una sola tabla con estas columnas exactas:

   | id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
   |---|---|---|---|---|---|---|

4. **Cobertura de RETO:** lista breve `RETO-XX -> H-XX` que demuestre que
   ningun caso ni subcaso fue omitido. No crees otra tabla de seguimiento.
5. **Contrato y limitaciones:** discrepancias demostradas, verificaciones no
   realizadas y dudas que requieran informacion externa.

Reglas de la matriz:

- Usa identificadores unicos `H-01`, `H-02`, etc.
- Si existe un informe anterior, conserva los IDs de los mismos hallazgos
  cuando su correspondencia sea inequivoca; asigna a los nuevos IDs mayores
  que cualquier ID previo y no reutilices IDs para problemas distintos.
  No arrastres afirmaciones sin verificarlas.
- Si no hay antecedentes, asigna IDs en orden de severidad.
- Ordena filas por `alta`, `media`, `baja` y al final `No evaluada`. Los IDs
  estables no tienen que quedar numericamente ordenados.
- Identifica el origen dentro de `hallazgo`: `[RETO-XX]` o `[Adicional]`,
  sin agregar columnas.
- Usa `Pendiente` como estado inicial de todos los hallazgos presentes en esta
  auditoria nueva. Esto no modifica el estado historico de informes anteriores.
- Usa `No encontrado` para casos de RETO ausentes, con alcance de busqueda.
- La evidencia debe ser navegable con enlaces relativos y numeros de linea
  visibles. Escapa `|` dentro de celdas para no romper la tabla.
- Manten cada explicacion concisa pero suficiente: causa, impacto y
  precondiciones. Para CVE no confirmados, explicita la incertidumbre.

## 8. Contrato, cierre y comprobaciones de aceptacion

No propongas cambiar endpoints, metodos HTTP, nombres/tipos de campos, estados,
unidades ni comportamientos contractuales. El token debe seguir tratandose
como cadena opaca por el consumidor. La correccion futura debe ser interna.

Si mitigar un hallazgo entra en tension con un comportamiento documentado
actualmente, registra esa tension sin resolverla ni modificar el contrato.
No ocultes un riesgo por estar documentado y no prometas una correccion
compatible que no puedas justificar. En esta fase no generes parches.

Antes de terminar, comprueba:

- [ ] Cada caso y subcaso de RETO tiene una fila o correspondencia explicita.
- [ ] Se buscaron problemas adicionales y solo se incluyeron los sustentados.
- [ ] Toda afirmacion confirmada tiene evidencia local precisa y actual.
- [ ] No se heredaron como hechos los hallazgos del informe anterior.
- [ ] No se confundieron ausencia de evidencia, defecto y vulnerabilidad.
- [ ] CVE y afirmaciones de explotabilidad tienen fuentes y limites claros.
- [ ] El mapa cubre validacion, persistencia, token, configuracion y errores.
- [ ] Se distinguio contrato documentado de comportamiento demostrado.
- [ ] Severidades, tipos, IDs y estados cumplen las reglas anteriores.
- [ ] El informe es una auditoria nueva, no una copia renombrada del anterior.
- [ ] Git muestra solo los archivos nuevos autorizados respecto al estado
      inicial; ningun cambio previo fue alterado.

Esta tarea es documental: no necesitas ejecutar pruebas ni builds. Declara
explicitamente que no se ejecutaron y que las conclusiones provienen de
revision estatica, no de validacion dinamica.

Responde finalmente con el enlace al informe creado, el numero de hallazgos
por severidad y el numero de casos "No encontrado". Indica que no se modifico
codigo ni el contrato existente. No repitas toda la matriz en la respuesta.
