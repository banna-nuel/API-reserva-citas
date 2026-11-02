# Citas API

API REST para reservar citas con profesionales: catálogo de servicios, horarios laborales, disponibilidad, reservas
sin doble reserva (también con peticiones simultáneas) y cambios de estado de cada cita. Autenticación con JWT y tres
roles: `ADMIN`, `PROFESSIONAL` y `CLIENT`.

La guía de diseño completa (reglas de negocio, contrato de la API, plan por fases y decisiones) está en
[docs/GUIA.md](docs/GUIA.md).

## Stack

Java 21 · Spring Boot 4 (Web MVC, Security, Data JPA, Validation) · MySQL 8.4 · Flyway · JJWT · springdoc-openapi ·
JUnit 5, Mockito, Testcontainers y JaCoCo · Docker Compose · GitHub Actions.

## Requisitos

- **JDK 21** (`java -version`). Maven no hace falta: el proyecto trae `mvnw`.
- **Docker** con Docker Compose (Docker Desktop en Windows y macOS), para MySQL y para las pruebas de integración.
- Git.

## Cómo correrla

Los comandos van en PowerShell (Windows) y en bash (Linux y macOS).

**1. Clonar**

```bash
git clone <url-del-repositorio> citas-api
cd citas-api
```

**2. Crear el `.env`** a partir de la plantilla (el `.env` nunca se sube al repositorio):

```powershell
Copy-Item .env.example .env        # PowerShell
```

```bash
cp .env.example .env               # bash
```

Ábrelo con un editor y cambia los valores de ejemplo:

- `DB_PASS`, `DB_ROOT_PASS` y `ADMIN_PASSWORD`: claves nuevas. `ADMIN_PASSWORD` necesita de 8 a 72 caracteres.
- `ADMIN_EMAIL`: el email del primer administrador.
- `JWT_SECRET`: un secreto aleatorio de 32 bytes en Base64. Para generarlo:

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

```bash
openssl rand -base64 32
```

El mismo comando sirve para generar las claves de la base. Guarda el `.env` en **UTF-8 sin BOM** (lo normal en VS Code); ver *Problemas comunes*.

**3. Levantar MySQL**

```bash
docker compose up -d
```

Crea el contenedor `citas-mysql`, publicado solo en `127.0.0.1:${DB_PORT}`. Espera a que `docker compose ps` lo muestre `healthy`.

**4. Arrancar la API** con el perfil `dev`, que incluye Swagger y el log de SQL. Flyway crea las tablas y, si no existe, el ADMIN de `.env`.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"      # PowerShell
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev            # bash
```

**5. Comprobar** (con el puerto de `APP_PORT`, 8080 por defecto):

- Salud: <http://localhost:8080/actuator/health>. Debe responder `{"status":"UP",...}`.
- Swagger UI: <http://localhost:8080/swagger-ui.html>. Especificación en `/v3/api-docs`.
  - Para los endpoints con candado: haz `POST /api/v1/auth/login`, copia `accessToken` y pégalo en **Authorize**.

## Variables de `.env`

| Variable | Para qué |
|---|---|
| `APP_PORT` | Puerto de la API en tu máquina (8080 por defecto) |
| `DB_HOST`, `DB_PORT` | Dónde está MySQL (`localhost` y `3306` con el compose del proyecto) |
| `DB_NAME`, `DB_USER`, `DB_PASS` | Base de datos y usuario de la aplicación; el compose los crea con estos valores |
| `DB_ROOT_PASS` | Clave del usuario `root` del contenedor de MySQL |
| `JWT_SECRET` | Secreto de firma de los JWT: Base64 de 32 bytes o más |
| `JWT_EXPIRATION_MINUTES` | Vigencia de los tokens (60 por defecto) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Primer administrador; se crea al arrancar si no existe |
| `SPRING_PROFILES_ACTIVE` | Opcional, solo para la API en Docker (ver abajo) |

## Usuarios de prueba

El repositorio no trae usuarios ni contraseñas reales:

- **ADMIN**: el de `ADMIN_EMAIL` y `ADMIN_PASSWORD` de tu `.env`, creado al primer arranque. Inicia sesión con `POST /api/v1/auth/login`.
- **Cliente**: cualquiera con `POST /api/v1/auth/register` (el registro público siempre crea un `CLIENT`).
- **Profesional**: lo da de alta el ADMIN con `POST /api/v1/professionals`, que crea el usuario y su perfil; después inicia sesión como cualquier usuario.

  Su horario se define con `PUT /api/v1/professionals/{id}/working-hours`.

La colección de Postman hace todo este recorrido sola (ver abajo).

## Pruebas y cobertura

Con Docker encendido (Testcontainers levanta su propio MySQL; no usa el de `docker compose`):

```powershell
.\mvnw.cmd -B verify       # PowerShell
```

```bash
./mvnw -B verify           # bash
```

- Corre las pruebas unitarias, las de integración (MockMvc), las de controlador (`@WebMvcTest`) y las de extremo a extremo (servidor real).
- Falla si la cobertura de líneas del paquete `service` baja del 70 %.
- El informe de cobertura queda en `target/site/jacoco/index.html`.
- En GitHub Actions corre el mismo `verify` en cada push a `main` o `develop` y en cada pull request (`.github/workflows/ci.yml`).

## Postman

1. *Import* → arrastra `postman/citas-api.postman_collection.json` y `postman/local.postman_environment.json`.
2. Elige el environment **Citas API (local)** y completa:
   - `baseUrl`: `http://localhost:8080/api/v1`, con tu `APP_PORT`;
   - `adminEmail` y `adminPassword`: los de tu `.env`.
3. En la colección **Citas API**, *Run* → orden por defecto, 1 iteración → las 33 peticiones deben salir en verde.

La colección se corre completa y en orden: los scripts guardan los tokens y los ids que usan las peticiones siguientes. Detalle en la sección 12.2 de [docs/GUIA.md](docs/GUIA.md).

## API en Docker (opcional)

```bash
docker compose --profile app up -d --build
```

- Construye la imagen con el `Dockerfile` (multi-etapa, usuario sin privilegios, `TZ=America/Bogota`) y levanta MySQL más la API en `127.0.0.1:${APP_PORT}`.
- El contenedor toma las variables de `.env`. Dentro de la red de compose usa `DB_HOST=mysql` y el puerto interno `3306`.
- Corre con el perfil **`prod`**, sin Swagger. Para otro perfil, pon `SPRING_PROFILES_ACTIVE=dev` en el `.env` o en la consola antes del comando.
- `docker compose up -d` sin `--profile app` sigue levantando solo MySQL.
- Para detener solo la API: `docker compose --profile app stop app`.

## Problemas comunes

| Síntoma | Solución |
|---|---|
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` debe apuntar a la carpeta del JDK, no a su `bin`. Para la consola actual: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'` |
| El puerto 8080 está ocupado por otro programa | Pon `APP_PORT=8081` (u otro libre) en `.env` y usa ese puerto en las URLs y en `baseUrl` de Postman |
| El puerto 3306 está ocupado (otro MySQL) | Cambia `DB_PORT` en `.env` (p. ej. `3307`) y vuelve a `docker compose up -d` |
| `Could not find a valid Docker environment` en `verify`, o `docker compose` no conecta | Docker está apagado: abre Docker Desktop y espera a que `docker info` responda |
| La API no toma la primera variable del `.env` (Docker Compose sí la lee) | El `.env` se guardó con BOM y empieza por una variable; `Set-Content -Encoding utf8` de PowerShell 5.1, por ejemplo, añade el BOM. Guárdalo como UTF-8 sin BOM |
| `docker compose --profile app up --build` falla con `failed to execute bake` o `additional privileges requested` | Construye la imagen aparte con `docker build -t citas-api:local .` y luego `docker compose --profile app up -d` (sin `--build`) |
| `The container name "/citas-mysql" is already in use` | Otra copia del proyecto ya creó ese contenedor. Detén y quita esa copia (`docker compose down` en su carpeta), o arranca su contenedor en lugar de crear otro |

## Estructura

```
├── src/main/java/com/q/q/
│   ├── config/        Seguridad, OpenAPI, propiedades tipadas y Clock del negocio
│   ├── controller/    Endpoints REST (delgados)
│   ├── dto/           Request y response (records con validación)
│   ├── entity/        Entidades JPA y enums
│   ├── exception/     ApiError y manejo global de errores
│   ├── mapper/        Entidad → DTO
│   ├── repository/    Spring Data JPA (bloqueos FOR UPDATE, Specifications)
│   ├── security/      JWT, filtro y usuario autenticado
│   ├── service/       Reglas de negocio
│   └── validation/    Validadores propios
├── src/main/resources/  application*.yml y db/migration (Flyway)
├── src/test/            Unitarias, integración, @WebMvcTest y extremo a extremo
├── postman/             Colección y environment importables
├── docs/GUIA.md         Guía de diseño completa
├── Dockerfile · docker-compose.yml · .env.example
└── .github/workflows/ci.yml
```

## Más documentación

- [docs/GUIA.md](docs/GUIA.md): requisitos y reglas de negocio, modelo de datos, contrato de la API (sección 8), plan por fases, seguridad, pruebas y despliegue.
- Swagger UI en `/swagger-ui.html`, con la API corriendo en el perfil `dev`.
