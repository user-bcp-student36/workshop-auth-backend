# Spec Delta

## Purpose
Define el comportamiento observable de `POST /auth/login`: validacion de entrada, respuestas de exito y error, y uso del campo `client`, preservando el contrato documentado en docs/ENDPOINTS.md.

## ADDED Requirements

### Requirement: Respuesta de login exitoso
El sistema SHALL responder 200 a un login con credenciales validas con un cuerpo JSON que contenga `token`, `expiresIn` y `user`, donde `user` incluye `username`, `displayName` y `role`.

#### Scenario: Login valido
- **WHEN** se envia un cuerpo JSON con `username`, `password` y `client` correctos
- **THEN** la respuesta es 200 con `application/json` y contiene `token`, `expiresIn` y `user`

#### Scenario: displayName escapado en HTML
- **WHEN** el `displayName` del usuario contiene caracteres HTML especiales
- **THEN** el valor devuelto en `user.displayName` esta escapado en HTML

### Requirement: Validacion de entrada
El sistema SHALL responder 400 con `{"message": ...}` cuando el cuerpo sea nulo, o cuando `username` o `password` esten ausentes o contengan solo espacios.

#### Scenario: Cuerpo nulo
- **WHEN** se envia una solicitud sin cuerpo
- **THEN** la respuesta es 400 con `message` igual a `Request is null`

#### Scenario: Username ausente o vacio
- **WHEN** falta `username` o solo contiene espacios
- **THEN** la respuesta es 400 con `message` igual a `Username is required`

#### Scenario: Password ausente o vacio
- **WHEN** falta `password` o solo contiene espacios
- **THEN** la respuesta es 400 con `message` igual a `Password is required`

### Requirement: Credenciales invalidas
El sistema SHALL responder 401 con `{"message": "Invalid user <username> or password"}` cuando las credenciales no sean correctas.

#### Scenario: Contrasena incorrecta
- **WHEN** el usuario existe pero la contrasena no coincide
- **THEN** la respuesta es 401 con el mensaje de credenciales invalidas

#### Scenario: Usuario inexistente
- **WHEN** el `username` no existe
- **THEN** la respuesta es 401 con el mismo formato de mensaje de credenciales invalidas

### Requirement: Uso validado del campo client
El sistema SHALL validar el campo `client` de la solicitud y reflejarlo en el claim `client` del token emitido. Una solicitud con `client` no valido SHALL recibir 400 con `{"message": ...}`. La lista de clientes permitidos queda pendiente de decision (ver HU-01).

#### Scenario: Client valido aparece en el token
- **WHEN** el login es exitoso con un `client` permitido
- **THEN** el claim `client` del token coincide con el valor enviado

#### Scenario: Client no permitido es rechazado
- **WHEN** el `client` enviado no esta en la lista de clientes permitidos
- **THEN** la respuesta es 400 con un `message` que identifica el campo `client`

### Requirement: Forma del error interno
El sistema SHALL responder 500 con `{"message": ..., "exception": ...}` ante errores no controlados, sin cambiar la forma de ese cuerpo.

#### Scenario: Error no controlado
- **WHEN** ocurre un error no controlado durante el login
- **THEN** la respuesta es 500 con los campos `message` y `exception`

### Requirement: Campos desconocidos ignorados
El sistema SHALL ignorar los campos desconocidos del cuerpo de la solicitud.

#### Scenario: Campo extra en la solicitud
- **WHEN** la solicitud incluye un campo no definido en el contrato
- **THEN** el login se procesa como si ese campo no existiera
