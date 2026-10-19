AMBITO
Trabaja unicamente en la carpeta workshop-auth-backend/ de reqflow-workspace.
Todas las rutas de este prompt son relativas a esa carpeta.
Esta carpeta es un repositorio Git independiente; ejecuta Git y guarda los
artefactos dentro de ella. Coordina dependencias externas sin ampliar AMBITO. No leas ni
modifiques los otros componentes del workspace.

CONTEXTO
La evidencia es git diff main desde la raiz del repositorio del componente,
en la rama day1/prompting. Limita la inspeccion al componente indicado en AMBITO.
Revisa tambien git status --short y los archivos nuevos, que no salen en el diff.
No se presupone un PR ni un merge.
El diff local implementa uno o mas hallazgos de HALLAZGOS.md. Consulta los documentos
del repositorio que consideres necesarios para justificar cada observacion (por
ejemplo README.md, RETO.md, docs/ENDPOINTS.md, docs/GOOD_PRACTICES.md,
REGLAS.md) y al final dime cuales usaste.

OBJETIVO
Revisar el diff local como lider tecnico y especialista AppSec, verificando que cada
hallazgo marcado "Resuelto" en HALLAZGOS.md este realmente corregido en el
codigo, y que respeta REGLAS.md.

RESTRICCIONES
No apruebes un diff que reduzca quality gates (CodeQL, Dependency Review, JaCoCo) o
que marque un hallazgo como "Resuelto" sin corregirlo de verdad. No inventes
riesgos que no puedas senalar con archivo y linea.

CRITERIOS DE ACEPTACION
Cada hallazgo "Resuelto" de HALLAZGOS.md queda verificado con evidencia, o se
regresa a "Pendiente" si el diff no lo corrigio del todo. Se revisan ademas:
regresiones funcionales, SQL injection, secretos, manejo de contrasenas,
validacion de entrada, errores verbosos, autenticacion debil, dependencias
vulnerables, cobertura y complejidad.

FORMATO DE RESPUESTA
Observaciones clasificadas en bloqueantes, importantes y sugerencias. Para cada
bloqueante: por que bloquea el cierre y que evidencia lo respalda. Si algun
hallazgo vuelve a "Pendiente", dilo explicitamente. Al final, que documentos
consultaste.
