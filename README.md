# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+. Usa `JAVA_HOME` de Java 21; JaCoCo 0.8.12
no es compatible con el Java 25 predeterminado de algunos entornos.

Configura antes de iniciar (variables de entorno o propiedades equivalentes):

| Variable | Uso |
|---|---|
| `AUTH_DB_URL` | SQLite; por defecto `jdbc:sqlite:./data/workshop.db`. |
| `AUTH_STUDENT_PASSWORD_HASH` | Hash PBKDF2 de la semilla `student`; obligatorio, sin contraseña por defecto. |
| `AUTH_TRAINER_PASSWORD_HASH` | Hash PBKDF2 de la semilla `trainer`; obligatorio, sin contraseña por defecto. |
| `AUTH_TOKEN_SECRET` | Al menos 32 bytes aleatorios en Base64, obligatorios, sin valor por defecto. |
| `AUTH_CORS_ORIGINS` | Lista separada por comas de orígenes HTTP(S) explícitos; obligatoria. Sin `*`, regex, rutas ni credenciales. |
| `AUTH_PASSWORD_ITERATIONS` | PBKDF2-HMAC-SHA256: 600000 por defecto, rango 600000–2000000. |
| `AUTH_DB_MIGRATE_PLAINTEXT` | `false` por defecto; `true` autoriza explícitamente la migración del esquema antiguo. |

No guardes valores reales en Git, comandos versionados ni logs. Los hashes
también deben protegerse. Se valida configuración al arrancar; no se omiten
semillas ante un error. Las cuentas existentes no se sobrescriben.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Las cuentas de laboratorio siguen siendo `student` y `trainer`, con sus
nombres/roles anteriores; sus contraseñas las determina el provisionamiento
externo. Los valores conocidos del laboratorio solo se usan en pruebas
aisladas, no como fallback de producción.

### Provisionar hashes

Se usa la API estándar del JDK, no una implementación criptográfica propia:
PBKDF2-HMAC-SHA256 con salt aleatorio de 16 bytes y derivación de 32 bytes.
El formato es `pbkdf2-sha256$iteraciones$saltBase64$hashBase64`.
Se eligió en lugar de BCrypt para no truncar contraseñas largas ni cambiar
el contrato. Referencia: [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Después de compilar, inicia `jshell --class-path target\classes` con JDK 21.
Con la contraseña provista temporalmente mediante `AUTH_SEED_PASSWORD`:

```java
import com.bcp.workshop.auth.PasswordHasher;
new PasswordHasher(600_000).hash(System.getenv("AUTH_SEED_PASSWORD"));
```

Provisiona el hash resultante de forma segura y elimina la variable temporal.
No pegues contraseñas reales en el historial ni publiques la salida. Para
la clave del token, usa un generador criptográfico y codifica sus bytes en
Base64; no uses una contraseña humana. Rotar la clave invalida los tokens
anteriores y exige coordinación con consumidores.

### Migrar una base existente

1. Detén escritores y ensaya en una copia aislada con acceso restringido.
   No actives la migración sobre tu base habitual durante las pruebas.
2. Provisiona configuración y hashes externos. Con el servicio detenido,
   establece `AUTH_DB_MIGRATE_PLAINTEXT=true` para el arranque controlado.
3. Se preservan identificadores y datos de usuario; se derivan hashes,
   se validan y se retira la columna en claro en una transacción. Cualquier
   fallo revierte el esquema y los datos; el arranque falla explícitamente.
4. Verifica autenticación e idempotencia y vuelve a desactivar la opción.
   No existe fallback de autenticación en texto plano.
5. La retirada de la columna **no es borrado físico garantizado**: acuerda
   saneamiento seguro de páginas SQLite, WAL, journal y respaldos, además de
   rotación de credenciales. No se borran copias automáticamente.

### Pruebas y cobertura

```powershell
mvn --batch-mode clean verify '-Dquarkus.http.test-port=0'
mvn --batch-mode quarkus:build '-DskipTests'
.\docs\validation\Check-Startup.ps1 -JavaHome $env:JAVA_HOME
```

La primera orden ejecuta todas las pruebas sobre SQLite temporal dentro de
`target`, con claves sintéticas, y exige al menos 85% de cobertura de líneas.
No requiere secretos externos ni migra la base habitual. El puerto aleatorio
evita conflictos con 8081. La segunda solo empaqueta el código ya comprobado;
no reemplaza la validación anterior. El script de PowerShell 7 verifica el
paquete, casos de configuración inválida, salud y login; cierra sus procesos
y elimina sus bases temporales.

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

El estado de las correcciones y la evidencia están en
[HALLAZGOS.md](HALLAZGOS.md) y [los resultados](docs/validation/RESULTADOS.md).
Resource, servicio, repositorio y seguridad están separados; el token usa
Nimbus JOSE JWT 10.10 con HS256, claims `sub`, `role`, `iat`, `exp` y vigencia
de 3600 segundos. El verificador local rechaza firma inválida y expiración;
no hay endpoint consumidor protegido en este repositorio. `client` sigue
siendo opcional e ignorado: [HU-01.md](HU-01.md) requiere reconciliación.

El contrato HTTP está en [docs/ENDPOINTS.md](docs/ENDPOINTS.md). Para cerrar
H-05, el usuario autorizó errores genéricos: 401 devuelve únicamente
`{"message":"Invalid credentials"}` y 500 únicamente
`{"message":"Authentication failed"}`. Los detalles de excepción y el
stacktrace se registran solo en el servidor, sin añadir credenciales de la
solicitud. No se usa `printStackTrace`. Mantén este laboratorio aislado.
