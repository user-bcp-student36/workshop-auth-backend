AMBITO
Trabaja unicamente en la carpeta workshop-auth-backend/ de reqflow-workspace.
Todas las rutas de este prompt son relativas a esa carpeta.
Esta carpeta es un repositorio Git independiente; ejecuta Git y guarda los
artefactos dentro de ella. Coordina dependencias externas sin ampliar AMBITO. No leas ni
modifiques los otros componentes del workspace.

Analiza este código antes de modificarlo.

CONTEXTO
Consulta primero:
- docs/ENDPOINTS.md: contrato de POST /auth/login a preservar
- docs/GOOD_PRACTICES.md: convenciones a las que debe converger el codigo
- REGLAS.md: reglas del laboratorio
- HALLAZGOS.md: hallazgos confirmados con su estado

OBJETIVO
Tomar de HALLAZGOS.md estos hallazgos marcados "Pendiente":
[IDs confirmados y motivo de seleccion; ejemplo: H-02 por riesgo alto y H-04
por alcance acotado, solo si esos IDs existen en tu matriz] y generar PLAN.md con la propuesta de
correccion para cada uno.

RESTRICCIONES
No cambies el contrato de docs/ENDPOINTS.md. No agregues dependencias sin
justificar. No asumas convenciones que no esten en docs/GOOD_PRACTICES.md. No
modifiques codigo todavia.

CRITERIOS DE ACEPTACION
PLAN.md tiene un apartado por hallazgo, con: que hallazgo de HALLAZGOS.md es,
el problema, el cambio propuesto (parametrizar SQL, hash de contrasenas, token
firmado, configuracion externa o separacion de capas, segun corresponda), los
archivos a tocar, y la prueba de caracterizacion a escribir antes de tocarlos.
El contrato de docs/ENDPOINTS.md se mantiene intacto. El cambio propuesto
tambien respeta las reglas de Clean Code de REGLAS.md (una responsabilidad
por metodo, sin duplicacion, sin anidamiento profundo, sin numeros magicos).

FORMATO DE RESPUESTA
Un archivo PLAN.md con esa estructura. No modifiques codigo todavia.
