# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Configuración de tokens

- `AUTH_TOKEN_SECRET` (obligatoria): secreto de firma HMAC. No tiene valor por defecto. Si falta o está vacía, `POST /auth/login` responde 500 y no emite token.
- `auth.token.ttl-seconds` (por defecto `3600`): duración del token; alimenta `exp` y `expiresIn`.
- `auth.clients.allowed` (por defecto `angular-workshop`): lista de valores permitidos para `client`. Un `client` ausente usa `angular-workshop`; uno no permitido responde 400.

```bash
# Linux / macOS
export AUTH_TOKEN_SECRET="<valor-largo-y-aleatorio>"
# Windows PowerShell
$env:AUTH_TOKEN_SECRET = "<valor-largo-y-aleatorio>"
```

**BREAKING:** los tokens emitidos por la versión anterior (Base64 sin firma) son rechazados. Los clientes deben iniciar sesión de nuevo tras el despliegue. La fecha de corte está pendiente de confirmar.

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
