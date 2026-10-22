# Spec Delta

## Purpose
Define el comportamiento observable de los tokens de sesion emitidos por el servicio de autenticacion: claims minimos, firma verificable, expiracion y origen del secreto.

## ADDED Requirements

### Requirement: Token firmado con claims minimos
El sistema SHALL emitir un token que contenga los claims `sub`, `client`, `iat` y `exp`. El token SHALL ser verificable mediante una firma calculada con el secreto configurado.

#### Scenario: Login valido produce token con claims completos
- **WHEN** un usuario se autentica correctamente con `username`, `password` y `client` validos
- **THEN** el token devuelto contiene los claims `sub` (username del usuario), `client`, `iat` y `exp`
- **AND** la firma del token es verificable con el secreto configurado

### Requirement: Rechazo de tokens con firma invalida
El sistema SHALL rechazar cualquier token cuya firma no corresponda al secreto configurado o cuyo contenido haya sido alterado.

#### Scenario: Token con contenido alterado es rechazado
- **WHEN** se presenta un token cuyo payload fue modificado tras su emision
- **THEN** la verificacion falla y el token no se acepta como valido

#### Scenario: Token firmado con otro secreto es rechazado
- **WHEN** se presenta un token firmado con un secreto distinto al configurado
- **THEN** la verificacion falla y el token no se acepta como valido

#### Scenario: Token en formato anterior es rechazado
- **WHEN** se presenta un token en el formato Base64 sin firma emitido por la version anterior
- **THEN** la verificacion falla y el token no se acepta como valido

### Requirement: Expiracion verificable
El sistema SHALL rechazar los tokens cuyo claim `exp` sea anterior al momento de verificacion. El valor `expiresIn` de la respuesta de login SHALL ser coherente con `exp - iat` dentro de una tolerancia documentada.

#### Scenario: Token expirado es rechazado
- **WHEN** se presenta un token cuyo claim `exp` ya paso
- **THEN** la verificacion falla con un error de token expirado

#### Scenario: expiresIn coincide con la vigencia del token
- **WHEN** un login es exitoso
- **THEN** `expiresIn` coincide con `exp - iat` del token devuelto, dentro de la tolerancia documentada

### Requirement: Secreto configurado por entorno
El sistema SHALL obtener el secreto de firma de la variable de entorno `AUTH_TOKEN_SECRET`. El sistema SHALL NOT emitir ni aceptar tokens si la variable no esta definida. El secreto SHALL NOT aparecer como literal en el codigo fuente de `src/main`.

#### Scenario: Sin variable de entorno no se emiten tokens
- **WHEN** `AUTH_TOKEN_SECRET` no esta definida y se solicita un login valido
- **THEN** el sistema no devuelve un token y responde con un error de configuracion
- **AND** la respuesta es 500 con los campos `message` y `exception`, sin el campo `token`

#### Scenario: Secreto configurado permite emitir tokens
- **WHEN** `AUTH_TOKEN_SECRET` esta definida con un valor no vacio y se solicita un login valido
- **THEN** el sistema devuelve un token firmado con ese secreto

### Requirement: Secretos y tokens fuera de los logs
El sistema SHALL NOT registrar en logs el token emitido, el secreto de firma ni el valor de la contrasena.

#### Scenario: Login exitoso no registra el token
- **WHEN** un login es exitoso
- **THEN** ningun registro de log contiene el valor del token ni del secreto

#### Scenario: Login fallido no registra la contrasena
- **WHEN** un login falla por credenciales invalidas
- **THEN** ningun registro de log contiene la contrasena enviada

### Requirement: Duracion de token unica
El sistema SHALL usar una unica duracion configurada para calcular `exp` y `expiresIn`. Por defecto la duracion SHALL ser 3600 segundos, igual que el contrato actual.

#### Scenario: Duracion por defecto preserva el contrato
- **WHEN** no se configura una duracion distinta y un login es exitoso
- **THEN** `expiresIn` es `3600` y `exp - iat` es `3600` dentro de la tolerancia documentada
