# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

## Configuracion del token firmado

El login emite JWT RS256. Antes de ejecutarlo, configurar
`AUTH_TOKEN_KEY_LOCATION` con la ubicacion de una clave privada RSA de al menos
2048 bits, `AUTH_TOKEN_ISSUER` y `AUTH_TOKEN_AUDIENCE`. No existe clave de respaldo
ni emision legacy; sin configuracion valida no se emiten tokens utilizables.
La clave privada solo pertenece al emisor y no debe versionarse ni registrarse.

FastAPI debe recibir la clave publica correspondiente y los mismos valores de
emisor y audiencia. El corte requiere desplegar ambos cambios coordinadamente;
las sesiones con tokens legacy requieren nuevo login. Contrato y evidencia del
cambio: [expediente del PR](../workshop-requirements-api-template/docs/PR-ISSUE-1.md).

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
