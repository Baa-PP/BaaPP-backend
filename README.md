# BaaPP Backend (OviApp) — API Central en Kotlin + Spring Boot 3.4

> **Backend unificado bajo Onion Architecture para la App Móvil Offline-First y el Portal Web de Asociación**  
> **Proyecto Integrador — AgroTech Challenge (TECOWEEK Winter Edition 2026) · UAG**

---

## Descripción

**`BaaPP-backend`** es la única fuente de verdad (Single Backend API & Database) de la plataforma **BaaPP (OviApp)**. Da servicio simultáneamente a:
* **[`BaaPP-app`](https://github.com/Baa-PP/BaaPP-app):** Cliente móvil **Kotlin Multiplatform (KMP)** + Jetpack Compose para captura offline en corrales (`POST /api/v1/sync/batch`).
* **[`BaaPP_webapp`](https://github.com/Baa-PP/BaaPP_webapp):** Portal Web de escritorio en **React 19 + TypeScript** para auditoría multi-rancho de la Asociación (`X-Active-Ranch-Id`), Sábana de 24 columnas y árboles genealógicos.

---

## Arquitectura Onion (4 Anillos) y Reglas de Negocio

Para evitar que los detalles de base de datos o del framework web contaminen las reglas zootécnicas y antifraude, el proyecto aplica una **Onion Architecture estricta** verificada mediante pruebas automatizadas con **ArchUnit**:

1. **`domain` (Núcleo Puro — Cero Dependencias de Spring/JPA):**
   * **`model`:** Entidades con dimensiones ortogonales (`Sex`, `LifeStage`, `ReproductiveStatus`, `AnimalStatus`) e identificadores de negocio desacoplados (`folioInterno`, `siniigaChip` `484...`, `registroUno`).
   * **`BirthAntiFraudPolicy`:** Ventana estricta de **30 días (cierre mensual)** y fotografía obligatoria al registrar nacimientos; bloqueo de edición para productores (`OWNER`, `WORKER_BRANCH`) y correcciones auditadas restringidas exclusivamente a `ASSOCIATION_SUPERUSER` y `VET_TECHNICIAN`.
   * **`ZootechnicalCalculator`:** Fórmulas deterministas en `BigDecimal` (`RoundingMode.HALF_UP`) del modelo en Excel del **Mtro. Alejandro Meléndez** (% preñez, % partos, prolificidad, ciclo de 275 días, días de engorda y conversión alimenticia de MS a TCO).
   * **`MaternalEvaluationService`:** Evaluación reproductiva para detectar vientres improductivos (infertilidad o mortalidad consecutiva de camadas) para desecho/rastro.
2. **`application` (Casos de Uso y Fronteras `@Transactional`):**
   * `RegisterBirthUseCase`, `CorrectLockedBirthUseCase`, `ConfirmWeaningUseCase` (transición de *Lactancia* a *Mantenimiento* + cambio automático de dieta), `ProcessOfflineSyncBatchUseCase`, `ExportSabanaExcelUseCase`.
3. **`infrastructure` (Adaptadores — Base de Datos, Excel, AWS, Cron):**
   * **Persistencia:** Spring Data JPA + **PostgreSQL 16 (Amazon RDS)** gestionado con migraciones **Flyway** (`V1__init_schema.sql`).
   * **Excel Sábana:** Generación e importación streaming de las 24 columnas (`A` a `X`) de `SABANA OCT 2026 DORPER.xlsx` con **FastExcel (`org.dhatim`)**.
   * **Media Storage:** URLs pre-firmadas (PUT/GET) hacia el bucket privado `agrotech-oviapp-media` en **Amazon S3**.
   * **Scheduler:** `WeaningAlertScheduler` (`@Scheduled(cron = "0 0 6 * * *")`) que detecta corderos al cumplir **75 días** desde el parto y envía alertas push vía **Firebase Cloud Messaging (FCM)**.
4. **`presentation` (Controladores REST y Seguridad Multi-Tenant):**
   * Endpoints `/api/v1/animals`, `/api/v1/births`, `/api/v1/sync/batch`, `/api/v1/reports/sabana`, `/api/v1/association/audits`.
   * Autenticación **JWT Stateless** + `RanchContextInterceptor` (`X-Active-Ranch-Id`) para aislamiento por rancho (**Cuenta Matriz + Ramas**) y cambio de contexto multi-rancho para Asociación y Técnicos.

---

## Stack Tecnológico (`BaaPP-backend`)

| Componente | Tecnología | Justificación |
| :--- | :--- | :--- |
| **Lenguaje y Framework** | **Kotlin 2.1 + Spring Boot 3.4 (Java 21)** | Cumplimiento estricto de POO, tipado nulo seguro y compatibilidad directa con el modelo de dominio KMP. |
| **Base de Datos** | **PostgreSQL 16 (Amazon RDS `db.t4g.micro`)** | Llaves `UUIDv7` generables offline, `UNIQUE (ranch_id, folio_interno)`, consultas recursivas `WITH RECURSIVE` (pedigrí 4 generaciones), auditorías inmutables en `JSONB` y prohibición de borrado físico (`status = 'DECEASED'`). |
| **Motor Excel** | **FastExcel (`org.dhatim`)** | Exportación e importación de alto rendimiento y bajo consumo de RAM para la Sábana de 24 columnas. |
| **Despliegue Cloud (Zero-Docker)** | **AWS Elastic Beanstalk (Corretto 21 Single Instance `t3.micro`) + S3** | Despliegue directo del artefacto `./gradlew bootJar` desde GitHub Actions con **$0.00/mes** en el AWS Free Tier (`-Xms256m -Xmx512m -XX:+UseSerialGC`). |

---

## Estructura del Repositorio

```text
BaaPP-backend/
├── src/main/
│   ├── kotlin/com/agrotech/oviapp/
│   │   ├── domain/
│   │   │   ├── model/                   # Animal y enums ortogonales
│   │   │   ├── service/                 # BirthAntiFraudPolicy, ZootechnicalCalculator
│   │   │   └── port/out/                # Interfaces de repositorios y adaptadores externos
│   │   ├── application/usecase/         # Casos de uso transaccionales
│   │   ├── infrastructure/              # Adaptadores JPA, FastExcel, S3 y WeaningAlertScheduler
│   │   └── presentation/                # Controladores REST, JWT y RanchContextInterceptor
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__init_schema.sql      # Esquema relacional PostgreSQL 16
├── src/test/kotlin/com/agrotech/oviapp/
│   └── ArchitectureAndDomainTest.kt     # Verificación ArchUnit y pruebas de fórmulas/antifraude
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Ejecución Local (Desarrollo)

### Instalación Rápida en Windows (Recomendado)
Desde la carpeta raíz de `BaaPP-backend`, abre PowerShell y ejecuta:
```powershell
# Instala todo (Backend + Web + Android)
powershell -ExecutionPolicy Bypass -File .\scripts\setup-dev.ps1

# O si solo trabajarás en Backend y Web (omite Android SDK)
powershell -ExecutionPolicy Bypass -File .\scripts\setup-dev.ps1 -SkipAndroid
```

**¿Qué hace [`scripts/setup-dev.ps1`](scripts/setup-dev.ps1)?**
1. **JDK 21 (Amazon Corretto):** Lo instala vía `winget` si no existe, configura `JAVA_HOME` y lo agrega a tu `PATH` de usuario.
2. **Node.js 22 LTS:** Instala `fnm` + Node 22 LTS y lo agrega a tu `PATH` de usuario.
3. **PostgreSQL 16.16:** Descarga la versión portable en `%LOCALAPPDATA%\Programs\PostgreSQL\16`, inicializa el clúster (`initdb` con usuario `postgres`), arranca el servidor y crea la base `oviapp`.
4. **Android SDK 35** *(salvo con `-SkipAndroid`)*: Descarga Command-Line Tools en `%LOCALAPPDATA%\Android\Sdk`, configura `ANDROID_HOME`, acepta licencias e instala `platform-tools`, `platforms;android-35` y `build-tools;35.0.0`.
5. **Dependencias y verificación:** Si `../BaaPP_webapp` está en la misma carpeta padre, corre `npm install`; finalmente ejecuta `.\gradlew.bat test` para comprobar que el backend compila y pasa ArchUnit y las pruebas de dominio.

> Una vez terminado, abre una **nueva terminal** para tomar las variables de entorno y ejecuta `.\gradlew.bat bootRun`.

### 1. Requisitos Previos (Manual)
* **JDK 21** (Amazon Corretto 21 o Eclipse Temurin 21).
* **PostgreSQL 16** corriendo localmente en el puerto `5432`.

### 2. Preparar la Base de Datos
Si usas la instalación local de PostgreSQL 16 en `%LOCALAPPDATA%\Programs\PostgreSQL\16`, inicia el servicio y crea la base `oviapp` (ya creada en este equipo; **Flyway** ejecutará `V1__init_schema.sql` automáticamente al arrancar):
```powershell
pg_ctl -D "$env:LOCALAPPDATA\Programs\PostgreSQL\16\data" -l "$env:LOCALAPPDATA\Programs\PostgreSQL\16\postgres.log" start
```
En una máquina nueva, crea la base vacía en PostgreSQL:
```sql
CREATE DATABASE oviapp;
```

### 3. Variables de Entorno (Opcional)
Por defecto [`application.yml`](src/main/resources/application.yml) conecta a `jdbc:postgresql://localhost:5432/oviapp` con usuario `postgres` y contraseña `postgres` en el puerto `5000`. Para usar credenciales distintas en PowerShell:
```powershell
$env:JDBC_DATABASE_URL="jdbc:postgresql://localhost:5432/oviapp"
$env:RDS_USERNAME="postgres"
$env:RDS_PASSWORD="tu_password"
$env:PORT="5000"
```

### 4. Compilar, Probar y Levantar el Servidor
* **Desde IntelliJ IDEA:** Abre la carpeta `BaaPP-backend`, espera a que sincronice Gradle y ejecuta `OviAppBackendApplication.kt`.
* **Desde Terminal** (si es la primera vez sin el wrapper, ejecuta antes `gradle wrapper`):
  ```bash
  # Ejecutar pruebas de ArchUnit, fórmulas zootécnicas y reglas antifraude
  ./gradlew test

  # Iniciar el servidor Spring Boot en http://localhost:5000
  ./gradlew bootRun
  ```

---

## Documentación Central (`Baa-PP/docs`)

La documentación oficial del ecosistema se mantiene de forma centralizada en **[`Baa-PP/docs`](https://github.com/Baa-PP/docs)** (única fuente de verdad, sin copias locales en este repositorio):
* **[Diseño de Base de Datos (`DATABASE_DESIGN.md`)](https://github.com/Baa-PP/docs/blob/main/arquitectura-y-bd/DATABASE_DESIGN.md)**
* **[Estándares de Documentación (`DOCUMENTATION_STANDARDS.md`)](https://github.com/Baa-PP/docs/blob/main/guias/DOCUMENTATION_STANDARDS.md)**
* **[Reglas de Commits y PRs (`COMMIT_AND_PR_RULES.md`)](https://github.com/Baa-PP/docs/blob/main/guias/COMMIT_AND_PR_RULES.md)**

---

## Equipo de Desarrollo

* **[Lucas Patrick Castineiras]** — Ingeniería de Software (5.º Semestre) · [GitHub](https://github.com/Locas1000)
* **[Carlo Virgilio Buelna]** — Ingeniería de Software (5.º Semestre) · [GitHub](https://github.com/carlovirgil)
* **[Carlos Abdel Parra Oliva]** — Ingeniería de Software (3.º Semestre) · [GitHub](https://github.com/0Chuck)
* **[César Conrado González Muñoz]** — Ingeniería de Software (3.º Semestre) · [GitHub](https://github.com/)
* **[Santiago Orozco Reynosa]** — Ingeniería de Software (3.º Semestre) · [GitHub](https://github.com/)
* **[Jonathan Enciso Jiménez]** — Ingeniería en Inteligencia Artificial (3.º Semestre) · [GitHub](https://github.com/Purpleskull3747)
