# UsochimochaBackend

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Flyway-4169E1?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36?logo=apachemaven&logoColor=white)

Backend Spring Boot del sistema de gestión de flota y maquinaria pesada de **Distrito de Riego Usochicamocha** (Colombia). Expone la API REST + WebSocket consumida por el panel administrativo web y la app móvil de campo.

## Tabla de contenido

- [Stack técnico](#stack-técnico)
- [Arquitectura](#arquitectura)
- [Prerequisitos](#prerequisitos)
- [Configuración](#configuración)
- [Variables de entorno](#variables-de-entorno)
- [Ejecutar localmente](#ejecutar-localmente)
- [Testing](#testing)
- [Base de datos](#base-de-datos)
- [Documentación de la API](#documentación-de-la-api)
- [CI/CD](#cicd)
- [Despliegue manual / contenedor](#despliegue-manual--contenedor)
- [Troubleshooting](#troubleshooting)

## Stack técnico

| Capa | Tecnología |
|---|---|
| Lenguaje / runtime | Java 17 |
| Framework | Spring Boot 3.5.4 (Web, Security, Data JPA, WebSocket, Validation) |
| Base de datos | PostgreSQL + Flyway (migraciones versionadas) |
| Autenticación | JWT (stateless) + control de acceso por rol |
| Tiempo real | WebSocket + STOMP (notificaciones de inspecciones y vencimiento de documentos) |
| Documentación API | SpringDoc OpenAPI 2.8.17 (Swagger UI) |
| Build | Maven |
| Contenedor | Docker (`eclipse-temurin:17-jre-alpine`) |

## Arquitectura

Cada módulo de dominio sigue **arquitectura hexagonal / clean architecture**, con la misma estructura en capas:

```
<dominio>/
  application/
    dto/       # Request y Response DTOs
    port/      # Interfaces de entrada/salida
    service/   # Lógica de negocio
  infrastructure/
    entity/       # Entidades JPA
    repository/   # Repositorios Spring Data
  web/         # Controladores REST
```

**Módulos de dominio:** `auth`, `vehicle`, `moto`, `machine`, `order`, `vehicleinspection`, `update` (cambios de aceite), `catalog`, `review`, `performance`, `notifications`, `fuel`, `maintenance`, `upload`, `user`, `actions`, `context`.

**Reglas transversales:**
- Soft-delete en todas partes — ningún registro se borra físicamente (`status: Boolean`).
- Toda escritura pasa por `SaveActionUseCase.save(...)` para auditoría.
- Los uploads de archivos tienen límite de 15 MB por archivo / 20 MB por request, almacenados en `uploads/` en la raíz del proyecto.

## Prerequisitos

- Java 17
- Maven 3.9+
- PostgreSQL (para los perfiles `dev` y `prod` — `test` usa H2 en memoria y no requiere nada externo)

## Configuración

El proyecto usa perfiles de Spring Boot. Los archivos versionados son:

| Perfil | Archivo | Uso |
|---|---|---|
| `dev` | `src/main/resources/application-dev.properties` | Desarrollo local. **No versionado** (ver `.gitignore`) |
| `test` | `src/test/resources/application-test.properties` | Pruebas automatizadas (`mvn test`), usa H2 en memoria |
| `prod` | `src/main/resources/application-prod.properties` | Producción, credenciales por variables de entorno |

`application-dev.properties` es un archivo local de cada desarrollador — la contraseña de BD y el secreto JWT que contenga son **solo para tu entorno local**, nunca deben coincidir con ninguna credencial real de test o producción, ni compartirse entre desarrolladores.

## Variables de entorno

Usadas por el perfil `prod` (cada una tiene un valor por defecto de desarrollo si no se define, ver `application-prod.properties`):

| Variable | Descripción |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Contraseña de la base de datos |
| `JWT_ISSUER` | Issuer incluido en los tokens JWT emitidos |
| `JWT_SECRET` | Secreto para firma de tokens JWT |
| `SHOW_SQL` | `true`/`false` — loguea el SQL generado por Hibernate (default `false`) |

En producción se configuran como secrets de despliegue (ver [CI/CD](#cicd)). Nunca reutilices un valor real de producción en tu entorno local.

## Ejecutar localmente

```bash
mvn clean install                                                                      # Build completo
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"   # Levantar con perfil dev
```

## Testing

```bash
mvn test                     # Tests unitarios (excluye @Tag("e2e"))
mvn test -Pe2e -Dgroups=e2e  # Tests end-to-end
```

El CI corre `mvn test` en toda rama y sube el reporte de cobertura JaCoCo como artefacto.

## Base de datos

- Flyway gestiona todas las migraciones versionadas (`src/main/resources/db/migration`).
- Perfil `test`: H2 en memoria, sin necesidad de una BD real.
- Perfiles `dev`/`prod`: PostgreSQL.
- La BD de producción no tenía historial previo de Flyway al adoptarlo — usa `baseline-on-migrate` con `baseline-version=0`.

## Documentación de la API

Con la app corriendo localmente:

| Recurso | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |

Los endpoints públicos de Swagger quedan accesibles sin login. Para probar endpoints protegidos, usa el botón **Authorize** con `Bearer <token JWT>`.

## CI/CD

Workflow: [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml)

| Job | Dispara en | Acción |
|---|---|---|
| `test` | Push a cualquier rama + PR hacia `main` | `mvn test` (perfil `test`) + sube reporte de cobertura JaCoCo |
| `build-and-deploy` | Push a `main` | Build del JAR, deploy a **producción** vía SCP/SSH, reinicia `usochicamocha-backend` |
| `deploy-test` | Push a `test` | Build del JAR, deploy a **TEST/QA** vía SCP/SSH, reinicia `usochicamocha-backend-test` |

Requiere Java 17 (`actions/setup-java@v4`, cache de Maven) y los secrets `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`.

## Despliegue manual / contenedor

```bash
# JAR directo
mvn clean package
java -jar target/*.jar --spring.profiles.active=prod

# Imagen Docker (perfil prod por defecto, puerto 8080)
docker build -t usochicamocha-backend back/UsochimochaBackend
```

Ver [`Dockerfile`](Dockerfile) — imagen base `eclipse-temurin:17-jre-alpine`.

> **Nota:** la imagen base usa un tag flotante (no un digest fijo), así que cada `docker build` trae los parches de seguridad más recientes de esa etiqueta al momento de construir. Un contenedor que quede corriendo mucho tiempo sin reconstruirse **no** recibe esos parches automáticamente — conviene reconstruir la imagen periódicamente (o en cada despliegue) en vez de reutilizar una imagen vieja indefinidamente.

## Troubleshooting

- **El perfil `test` falla al resolver properties** → revisa que `src/test/resources/application-test.properties` tenga los valores mínimos para H2/JWT.
- **Swagger no carga** → confirma que el perfil activo no tenga reglas de seguridad adicionales bloqueando `/swagger-ui/**`.
- **`spring-boot:run` arranca lento la primera vez** → normal, incluye la descarga de dependencias de Maven.
