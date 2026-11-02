# citas-api

API REST de reserva de citas.

Stack: Java 21, Spring Boot, MySQL, Flyway, JWT.

## Requisitos

Java 21. Docker y Docker Compose.

## Configuración

Copia `.env.example` a `.env` y reemplaza los valores de ejemplo. El archivo `.env` no se versiona. `JWT_SECRET` es un valor Base64 de 32 bytes o más.

```
# Linux o macOS
cp .env.example .env
openssl rand -base64 32
# PowerShell
Copy-Item .env.example .env
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

El segundo comando de cada sistema genera el valor de `JWT_SECRET`. Al iniciar, la aplicación crea el usuario ADMIN con `ADMIN_EMAIL` y `ADMIN_PASSWORD` si no existe. `ADMIN_PASSWORD` necesita entre 8 y 72 caracteres.

## Ejecución

```
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev          # Linux o macOS
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"    # Windows
```

El puerto es el valor de `APP_PORT`, 8080 por defecto. Swagger UI existe con el perfil dev. En el perfil prod está desactivado.

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Pruebas

Ejecuta `./mvnw -B verify` en Linux o macOS, o `.\mvnw.cmd -B verify` en Windows. Requiere Docker en ejecución, porque las pruebas de integración usan Testcontainers. Tarda unos 5 minutos. El informe de cobertura queda en `target/site/jacoco/index.html`.

## Docker

`docker compose --profile app up -d --build` levanta MySQL y la API en contenedores. La API corre con el perfil de Spring prod. `SPRING_PROFILES_ACTIVE` en `.env` cambia ese perfil. `docker compose --profile app stop app` detiene solo la API.

## Variables

| Variable | Descripción |
|---|---|
| `APP_PORT` | Puerto de la API. 8080 por defecto. |
| `DB_HOST` | Host de MySQL. `localhost` con el compose del proyecto. |
| `DB_PORT` | Puerto de MySQL en la máquina. |
| `DB_NAME` | Nombre de la base de datos. |
| `DB_USER` | Usuario de MySQL de la aplicación. |
| `DB_PASS` | Clave de `DB_USER`. |
| `DB_ROOT_PASS` | Clave de root del contenedor de MySQL. |
| `JWT_SECRET` | Secreto de firma de los JWT. Base64 de 32 bytes o más. |
| `JWT_EXPIRATION_MINUTES` | Vigencia de los tokens, en minutos. |
| `ADMIN_EMAIL` | Email del usuario ADMIN inicial. |
| `ADMIN_PASSWORD` | Clave del usuario ADMIN inicial. |
| `SPRING_PROFILES_ACTIVE` | Opcional. Perfil de Spring del contenedor `app`. prod por defecto. |

## Postman

Importa `postman/citas-api.postman_collection.json` y `postman/local.postman_environment.json`. En el environment Citas API (local), completa `baseUrl`, `adminEmail` y `adminPassword`. `baseUrl` es `http://localhost:8080/api/v1` con el puerto de `APP_PORT`. Ejecuta la colección Citas API con Collection Runner, en el orden por defecto.

## Problemas comunes

| Problema | Solución |
|---|---|
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` debe apuntar a la carpeta del JDK, no a su carpeta `bin`. |
| Puerto 3306 ocupado | Cambia `DB_PORT` en `.env`. |
| Puerto 8080 ocupado | Cambia `APP_PORT` en `.env`, por ejemplo a 8081. |
| `verify` o `docker compose` no conectan con Docker | Inicia Docker Desktop y espera a que `docker info` responda. |
| La aplicación ignora la primera variable de `.env` | El archivo tiene BOM. Guárdalo como UTF-8 sin BOM. |
| `docker compose --build` falla con `failed to execute bake` | Abre la terminal en la carpeta del proyecto con la capitalización exacta de la ruta. |

## Documentación

`docs/GUIA.md` contiene el diseño, las reglas de negocio y el contrato de la API.
