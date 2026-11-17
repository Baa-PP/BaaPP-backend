# Diseño y Documentación de Base de Datos — BaaPP / OviApp

> **Motor principal:** PostgreSQL 16.10 (Amazon RDS `db.t4g.micro`, DB `oviapp`, migraciones vía Flyway)  
> **Caché offline móvil:** SQLite vía SQLDelight (`BaaPP-app/shared/src/commonMain/sqldelight/com/agrotech/oviapp/db/OviAppDatabase.sq`)  
> **Principio rector:** Cero borrado físico en animales/auditorías, identificadores de negocio desacoplados de llaves primarias `UUIDv7` y separación estricta entre **Fase 1 (MVP)** y **Fase 2**.

---

## 1. Estado Actual vs. Regla Crítica de Flyway

El archivo `BaaPP-backend/src/main/resources/db/migration/V1__init_schema.sql` **ya se encuentra ejecutado en RDS (`baapp-staging-db`)**.

> **IMPORTANTE — Nunca editar `V1__init_schema.sql`:** Flyway valida el checksum al arrancar Spring Boot; modificar `V1` rompería el arranque en Elastic Beanstalk. Todo ajuste debe crearse como una nueva migración incremental (`V2__*.sql`, `V3__*.sql`, etc.).

### Migraciones (rama `feat/v2-mvp-gaps`)

| Archivo | Contenido | Estado |
| :--- | :--- | :--- |
| `V1__init_schema.sql` | 7 tablas base | Aplicada en RDS |
| `V2__mvp_gaps_and_indexes.sql` | §4: asociaciones, columnas faltantes de `animals` / `birth_events` / `weight_measurements` y 7 índices | Nueva |
| `V3__reproduccion_salud_alimentacion_finanzas.sql` | 4 catálogos + 12 tablas (§8): empadres, salud, alimentación, competencias y movimientos | Nueva |
| `R__vistas.sql` | 24 vistas de lectura (§9). Migración repetible: Flyway la vuelve a aplicar cada vez que cambia | Nueva |

> V2 llena `weight_measurements.ranch_id` desde `animals` antes de ponerle `NOT NULL`, así que funciona aunque staging ya tenga pesajes. Probado en PostgreSQL (Neon) con y sin pesajes previos.
> Las tablas nuevas de V3 van con nombres en **español**; las de V1/V2 conservan sus nombres en inglés porque V1 ya está aplicada. Los **valores** de los `CHECK` siguen en inglés (`PREGNANT`, `HERD`, `INCOME`…) igual que los enums de Kotlin.

### Inventario de lo ya construido en `V1` (7 tablas)

| Tabla | Propósito actual | Observación / Estado |
| :--- | :--- | :--- |
| `ranches` | Catálogo de ranchos (`weaning_days_target` = 75) | Falta vínculo con Asociación. |
| `users` | Cuentas `OWNER`, `WORKER_BRANCH`, `VET_TECHNICIAN`, `ASSOCIATION_SUPERUSER` + `branch_permissions` (`JSONB`) | Falta relación multi-rancho para Asociación/Técnico. |
| `animals` | Ficha individual con dimensiones ortogonales (`sex`, `life_stage`, `reproductive_status`, `status`), pedigrí (`sire_id`, `dam_id`), mortalidad | Faltan `entry_reason`, `criador_name`, `photo_s3_key`, `birth_event_id`, e índices. |
| `birth_events` | Eventos de parto con foto obligatoria (`evidence_photo_s3_key`) y conteo `live_born_count` / `stillborn_count` | Falta `parturition_type`, `conception_method` y estado de bloqueo/auditoría. |
| `birth_audit_logs` | Bitácora inmutable (`previous_state`, `new_state` en `JSONB`, foto y motivo) para correcciones de Asociación/Técnico | Completa para MVP. |
| `weight_measurements` | Pesajes (`BIRTH`, `WEANING`, `DAY_120`, `DAY_150`, `EVALUATION`) + `scrotal_circumference_cm` | Falta `ranch_id` e índice por animal/fecha. |
| `processed_sync_mutations` | Idempotencia de sincronización offline (`mutation_id`) | Completa para MVP. |

---

## 2. Arquitectura Entidad-Relación (ERD)

```mermaid
erDiagram
    associations ||--o{ association_ranches : agrupa
    ranches ||--o{ association_ranches : afiliado_a
    associations ||--o{ users : tiene_superusuarios
    ranches ||--o{ users : tiene_dueno_y_ramas
    ranches ||--o{ animals : posee
    ranches ||--o{ birth_events : registra
    ranches ||--o{ processed_sync_mutations : deduplica

    animals ||--o{ animals : "sire_id / dam_id"
    animals ||--o{ birth_events : "dam_id / sire_id"
    birth_events ||--o{ animals : "birth_event_id (crias)"
    birth_events ||--o{ birth_audit_logs : audita
    users ||--o{ birth_events : registra
    users ||--o{ birth_audit_logs : aprueba
    animals ||--o{ weight_measurements : registra_pesos

    associations {
        uuid id PK
        varchar name
        varchar scope "STATE | NATIONAL"
    }
    association_ranches {
        uuid association_id PK,FK
        uuid ranch_id PK,FK
    }
    ranches {
        uuid id PK
        varchar name
        text address
        varchar owner_name
        int weaning_days_target "default 75"
    }
    users {
        uuid id PK
        uuid ranch_id FK "nullable para superuser"
        uuid association_id FK "V2"
        varchar username UK
        varchar password_hash
        varchar role
        jsonb branch_permissions
    }
    animals {
        uuid id PK
        uuid ranch_id FK
        uuid birth_event_id FK "V2"
        varchar folio_interno
        varchar siniiga_chip
        varchar registro_uno
        varchar adn
        varchar entry_reason "V2"
        varchar criador_name "V2"
        text photo_s3_key "V2"
        varchar sex
        varchar life_stage
        varchar reproductive_status
        varchar status "ACTIVE | SOLD | DECEASED"
        uuid sire_id FK
        uuid dam_id FK
    }
    birth_events {
        uuid id PK
        uuid ranch_id FK
        uuid dam_id FK
        uuid sire_id FK
        date birth_date
        int live_born_count
        int stillborn_count
        text evidence_photo_s3_key
        uuid registered_by_user_id FK
    }
    birth_audit_logs {
        uuid id PK
        uuid birth_event_id FK
        uuid ranch_id FK
        uuid approved_by_user_id FK
        jsonb previous_state
        jsonb new_state
        text evidence_photo_s3_key
        text reason
    }
    weight_measurements {
        uuid id PK
        uuid ranch_id FK "V2"
        uuid animal_id FK
        varchar milestone
        numeric weight_kg
        numeric scrotal_circumference_cm
        date measured_at
    }
```

---

## 3. Decisiones de Diseño Clave

1. **UUIDv7 vs. Identificadores de Negocio:** Todas las tablas usan `id UUID PRIMARY KEY` generable offline en el móvil (`BaaPP-app`). El folio de corral (`folio_interno`, ej. `79`), el arete SINIIGA (`siniiga_chip`, prefijo `484...`) y el registro (`registro_uno`, ej. `268309MM-RP`) son columnas de negocio separadas con unicidad compuesta `UNIQUE (ranch_id, folio_interno)`, permitiendo reemplazar aretes perdidos sin romper llaves foráneas.
2. **Dimensiones Ortogonales en `animals`:** Nunca mezclar sexo, etapa y estado fisiológico en un solo campo. Se mantienen separados:
   - `sex`: `FEMALE`, `MALE`
   - `life_stage`: `LAMB_LACTATING`, `LAMB_FATTENING`, `EWE_REPLACEMENT`, `ADULT`
   - `reproductive_status`: `EMPTY`, `PREGNANT`, `LACTATING`, `STUD_RAM`
   - `status`: `ACTIVE`, `SOLD`, `DECEASED`
3. **`VARCHAR + CHECK` en lugar de `CREATE TYPE AS ENUM`:** Consistente con `V1`, evita migraciones complejas en PostgreSQL al agregar valores futuros.
4. **Cero Borrado Físico (`DELETE` prohibido en `animals`):** Un animal fallecido pasa a `status = 'DECEASED'` con `mortality_date`, `mortality_cause` y `life_stage_at_death`. Así se preservan indicadores de mortalidad y la detección de vientres improductivos.
5. **Regla Anti-Fraude de 30 Días e Inmutabilidad:**
   - Todo parto en `birth_events` exige `evidence_photo_s3_key` no vacío y valida `0 <= today - birth_date <= 30` días.
   - Productores (`OWNER`, `WORKER_BRANCH`) no tienen permiso de `UPDATE/DELETE` sobre `birth_events`.
   - Correcciones posteriores o altas fuera de plazo las ejecuta exclusivamente `ASSOCIATION_SUPERUSER` o `VET_TECHNICIAN`, dejando registro obligatorio en `birth_audit_logs`.
6. **Genealogía Bidireccional (`WITH RECURSIVE`):** `sire_id` y `dam_id` apuntan a `animals(id)` (con respaldo de texto `external_sire_folio` / `external_dam_folio` para progenitores externos). Permite navegar Madre ↔ Crías ↔ Semental y resolver árboles de 3–4 generaciones en una sola consulta recursiva.
7. **Campos Derivados NO Almacenados:** Edad en meses/días, ganancia diaria de peso (GDP), tasa de preñez, tasa de partos, prolificidad, intervalo entre ciclos (275 días) y días de engorda se calculan en dominio (`ZootechnicalCalculator`) o en la exportación Excel (`FastExcelSabanaExporter`), nunca como columnas persistidas que puedan desincronizarse.

---

## 4. Diccionario de Datos — Delta Necesario en `V2` (Fase 1 MVP)

`V1` cubre la base, pero presenta **huecos concretos para operar Fase 1** que deben resolverse en `V2__mvp_gaps_and_indexes.sql`:

### A. Multi-tenancy de Asociación (faltante en `V1`, referenciado en `RanchContextInterceptor`)
* **`associations`**: `id UUID PK`, `name VARCHAR(150) NOT NULL`, `code VARCHAR(40) UNIQUE`, `created_at TIMESTAMPTZ`.
* **`association_ranches`**: `association_id UUID FK`, `ranch_id UUID FK`, `joined_at TIMESTAMPTZ`, `PRIMARY KEY (association_id, ranch_id)`.
* **`users.association_id`**: `UUID REFERENCES associations(id)` (nullable; usado por `ASSOCIATION_SUPERUSER` y `VET_TECHNICIAN` para autorizar el header `X-Active-Ranch-Id`).

### B. Ficha Individual (`animals`) y Conciliación de Partos (`birth_events`)
* **`animals.entry_reason`**: `VARCHAR(20) NOT NULL DEFAULT 'BIRTH' CHECK (entry_reason IN ('BIRTH', 'PURCHASE', 'REENTRY', 'TRANSFER', 'OTHER'))` — requerido por la Reseña MVP (Sesión 2, p. 14).
* **`animals.criador_name`**: `VARCHAR(150)` — distingue *Criador* de *Propietario actual* en el certificado rosa/azul (Sesión 2, p. 8 y 17).
* **`animals.photo_s3_key`**: `TEXT` — fotografía del ejemplar en su ficha.
* **`animals.birth_event_id`**: `UUID REFERENCES birth_events(id)` — vincula las crías creadas con su evento de parto para conciliar solicitudes de chips SINIIGA a los 5 meses (`live_born_count` menos crías con `status = 'DECEASED'`).
* **`animals.updated_at`** y **`birth_events.updated_at`**: `TIMESTAMPTZ NOT NULL DEFAULT NOW()` — necesario para sincronización incremental (pull) en móvil.
* **`weight_measurements.ranch_id`**: `UUID NOT NULL REFERENCES ranches(id)` — evita joins innecesarios al validar aislamiento por rancho y al sincronizar.

### C. Índices de Rendimiento (ausentes en `V1`)
* `CREATE INDEX idx_animals_ranch_status ON animals (ranch_id, status);`
* `CREATE INDEX idx_animals_sire ON animals (sire_id) WHERE sire_id IS NOT NULL;`
* `CREATE INDEX idx_animals_dam ON animals (dam_id) WHERE dam_id IS NOT NULL;`
* `CREATE INDEX idx_animals_weaning_pending ON animals (birth_date) WHERE status = 'ACTIVE' AND weaning_date IS NULL;` (para `WeaningAlertScheduler` a los 75 días).
* `CREATE UNIQUE INDEX uq_animals_ranch_siniiga ON animals (ranch_id, siniiga_chip) WHERE siniiga_chip IS NOT NULL;`
* `CREATE INDEX idx_weights_animal_date ON weight_measurements (animal_id, measured_at DESC);`
* `CREATE INDEX idx_births_ranch_date ON birth_events (ranch_id, birth_date DESC);`

---

## 5. Mapeo Exacto de las 24 Columnas de la Sábana (`A`–`X`)

Basado en `SABANA OCT 2026 DORPER.xlsx` (Sesión 2, pp. 12–13):

| Col | Encabezado Sábana | Origen en Base de Datos / Cálculo |
| :---: | :--- | :--- |
| **A** | `folio` | `animals.folio_interno` |
| **B** | `adn` | `animals.adn` |
| **C** | `CHIP` (SINIIGA) | `animals.siniiga_chip` |
| **D** | `FECHA NACIMIENTO` | `animals.birth_date` |
| **E** | `FECHA DESTETE` | `animals.weaning_date` |
| **F** | `EDAD MESES` | **Derivado:** `(fecha_corte - birth_date) / 30.0` |
| **G** | `EDAD DIAS` | **Derivado:** `fecha_corte - birth_date` |
| **H** | `PESO NACIMIENTO` | `weight_measurements.weight_kg` (`milestone = 'BIRTH'`) |
| **I** | `PESO 120 DIAS` | `weight_measurements.weight_kg` (`milestone = 'DAY_120'`) |
| **J** | `PESO 150 DIAS` | `weight_measurements.weight_kg` (`milestone = 'DAY_150'`) |
| **K** | `PESO A LA EVALUACION` | `weight_measurements.weight_kg` (`milestone = 'EVALUATION'`) |
| **L** | `CIRCUNFERENCIA` | `weight_measurements.scrotal_circumference_cm` (machos) |
| **M** | `PESO DESTETE` | `weight_measurements.weight_kg` (`milestone = 'WEANING'`) |
| **N** | `TIPO DESTETE` | `animals.parturition_type` *(o campo separado si difiere por mortalidad previa al destete)* |
| **O** | `PADRE` | `sire.folio_interno` o `animals.external_sire_folio` |
| **P** | `FOLIO PADRE` (UNO) | `sire.registro_uno` *(pendiente validar si padre externo requiere `external_sire_registro_uno`)* |
| **Q** | `MADRE` | `dam.folio_interno` o `animals.external_dam_folio` |
| **R** | `FOLIO MADRE` (UNO) | `dam.registro_uno` |
| **S** | `RAZA` | `animals.breed` |
| **T** | `TIPO DE PARTO` | `animals.parturition_type` (`SIMPLE`, `TWIN`, `TRIPLET`, `QUADRUPLET`, `QUINTUPLET`) |
| **U** | `CONCEPCION` | `animals.conception_method` (`MN`, `IA`, `TE`) |
| **V** | `COLOR` | `animals.color` |
| **W** | `SEXO` | `animals.sex` (`FEMALE` → HEMBRA, `MALE` → MACHO) |
| **X** | `(=AHORA())` | **Derivado:** fecha de corte de generación del `.xlsx` |

---

## 6. Paridad Offline en SQLite (`BaaPP-app` / SQLDelight)

El archivo `BaaPP-app/shared/src/commonMain/sqldelight/com/agrotech/oviapp/db/OviAppDatabase.sq` actualmente solo tiene `local_animals` (incompleta) y `pending_sync_events`. Para soportar captura y lectura offline en Fase 1 debe ampliarse con:
1. Completar `local_animals` con `adn`, `parturition_type`, `conception_method`, `external_sire_folio`, `external_dam_folio`, `entry_reason`, `criador_name`, `photo_s3_key`, `birth_event_id`, `mortality_date`, `mortality_cause`, `updated_at`.
2. Agregar **`local_birth_events`** y **`local_weight_measurements`** (espejo mínimo de PostgreSQL) para consultar historial y ejecutar validaciones locales antes de que `WorkManager` vacíe `pending_sync_events` hacia `POST /api/v1/sync/batch`.

---

## 7. Alcance Diferido a Fase 2 (`V3+` — NO construir en MVP)

> **Actualización:** parte de este alcance se adelantó en `V3` (ver §8): sanidad (calendario y tratamientos), nutrición (catálogo con marcas, dietas por animal y mezclas), competencias y movimientos de dinero. Siguen pendientes: corrales y cuarentena, precios regionales por rancho, parámetros del ciclo de 275 días.

Siguiendo la hoja de ruta de los documentos de arquitectura y requerimientos, estas tablas quedan **documentadas como evolución futura** pero excluidas del MVP para evitar sobreingeniería:
1. **Corrales y Sanidad (`corrals`, `sanitary_events`):** Asignación de corral físico (incluyendo área de Cuarentena / Enfermería con estado en tratamiento y diagnóstico) y cartilla sanitaria en 4 rubros: Vacunación, Vitaminación, Selenio y Desparasitación.
2. **Nutrición y Costos por Etapa (`nutritional_stage_requirements`, `feed_ingredients`, `ranch_ingredient_prices`, `diet_formulas`, `diet_formula_items`):** Requerimientos fijos por las 5 etapas (*Lactancia, Crecimiento, Desarrollo, Engorda, Mantenimiento*), catálogo de granos con `% proteína` fijo y precio regional editable por rancho para simular sustituciones (`$/kg TCO`).
3. **Parámetros Productivos y Financieros (Ciclo 275 días):** `ranch_production_parameters` (sobrescribiendo valores base del Excel del Mtro. Meléndez) y `financial_transactions` (costos no alimenticios: medicamentos, mano de obra, transporte, infraestructura y ventas).
4. **Exposiciones / Campeonatos (`animal_championships`):** Premios en ferias ganaderas (Cordera, Joven, Raza, Suprema, fecha y foto S3).

---

## 8. Tablas de `V3` (nombres en español)

```mermaid
erDiagram
    ranches ||--o{ empadres : registra
    animals ||--o{ empadres : "hembra / semental"
    birth_events |o--o| empadres : "resulta en"
    ranches ||--o{ esquemas_vacuna : calendario
    tipos_tratamiento ||--o{ esquemas_vacuna : tipo
    tipos_tratamiento ||--o{ tratamientos : tipo
    esquemas_vacuna |o--o{ tratamientos : cumple
    tratamientos ||--o{ tratamientos_animal : "se aplica a"
    animals ||--o{ tratamientos_animal : recibe
    tipos_alimento ||--o{ alimentos : tipo
    animals ||--o{ raciones : come
    raciones ||--o{ raciones_alimento : contiene
    alimentos ||--o{ raciones_alimento : "kg por día"
    etapas_productivas ||--o{ mezclas : etapa
    mezclas ||--o{ mezclas_ingrediente : contiene
    alimentos ||--o{ mezclas_ingrediente : porcentaje
    eventos_competencia ||--o{ participaciones : tiene
    animals ||--o{ participaciones : participa
    resultados_competencia ||--o{ participaciones : resultado
    ranches ||--o{ movimientos : registra
```

| Tabla | Tipo | Qué guarda | Reglas (`CHECK` / `UNIQUE`) |
| :--- | :--- | :--- | :--- |
| `tipos_tratamiento` | Catálogo | Vacuna, Desparasitación, Vitaminas, Selenio, Curación, Enfermedad, Revisión | `nombre` único |
| `tipos_alimento` | Catálogo | Preiniciador … Subproducto | `nombre` único |
| `etapas_productivas` | Catálogo | Las 5 etapas con rango de proteína | `min <= max <= 40` |
| `resultados_competencia` | Catálogo | Gran campeón … Participó, con puntos | `puntos 0..100` |
| `empadres` | Entidad | Monta / IA / TE, diagnóstico de gestación y parto resultante | hembra ≠ semental; diagnóstico ≥ 30 días después; `PREGNANT`/`EMPTY` |
| `esquemas_vacuna` | Entidad | Calendario sanitario por rancho | edad inicio 0..730; frecuencia 15..730 días |
| `tratamientos` | Entidad | Aplicación al rebaño, a corderos o individual | próxima fecha > fecha |
| `tratamientos_animal` | M:N | A qué animales se aplicó y con qué dosis | dosis 0..50 ml; un renglón por animal |
| `alimentos` | Entidad | Catálogo con marca, proteína, energía y precio | `rancho_id` NULL = catálogo general |
| `raciones` | Entidad | Dieta de un animal desde una fecha (historial) | una por animal y fecha |
| `raciones_alimento` | M:N | Kilos por día de cada alimento | 0..6 kg/día |
| `mezclas` | Entidad | Fórmula del rancho para una etapa | nombre único por rancho |
| `mezclas_ingrediente` | M:N | Porcentaje de cada ingrediente | 0..100; la suma de 100 % la valida `v_mezcla_nutricion` |
| `eventos_competencia` | Entidad | Ferias y exposiciones | nombre + fecha únicos |
| `participaciones` | M:N | Animal × evento: categoría, resultado y foto | una por animal, evento y categoría |
| `movimientos` | Entidad | Ventas (`INCOME`) y gastos (`EXPENSE`) | monto > 0 |

Las FK hacia `animals` no tienen `ON DELETE` (RESTRICT): un animal nunca se borra físicamente (§3.4). Los renglones hijos de `tratamientos`, `raciones` y `mezclas` sí se borran en cascada con su padre.

---

## 9. Vistas de lectura (`R__vistas.sql`)

Cada pantalla pide su vista con un filtro sencillo (`WHERE rancho_id = :rancho`, `animal_id = :animal`). Los indicadores se calculan al consultar; no se guardan (§3.7).

| Vista | Pantalla | Técnica |
| :--- | :--- | :--- |
| `v_animal_resumen` | Lista del rebaño y ficha | LATERAL (último peso) |
| `v_resumen_rancho` | Inicio / selector multi-rancho | GROUP BY, vista sobre vista |
| `v_pesaje_ganancia` | Pesaje e historial de peso | `LAG` |
| `v_genealogia` | Certificado rosa/azul (3 generaciones) | `WITH RECURSIVE` |
| `v_cargadas` | Borregas gestantes y fecha probable (150 d) | — |
| `v_partos` | Partos, crías y bloqueo de 30 días | LATERAL, `STRING_AGG` |
| `v_reproduccion_temporada` | % preñez, % parto, prolificidad y mortalidad por trimestre | GROUP BY |
| `v_historial_reproductivo_hembra` | Selección de hembras y vientres improductivos | LATERAL |
| `v_ranking_crecimiento` | GDP de corderos, fecha de venta (35 kg) y destino | `REGR_SLOPE`, `PERCENT_RANK` |
| `v_peso_etapa` | Peso por hito contra el promedio del rancho | `DISTINCT ON`, `AVG OVER` |
| `v_estado_vacunas` | Vacunas faltantes por animal | subconsulta |
| `v_cobertura_vacunas` | Cobertura del calendario por rancho | GROUP BY, vista sobre vista |
| `v_historial_salud` | Historial sanitario (ficha: `:animal = ANY(animal_ids)`) | GROUP BY, `ARRAY_AGG` |
| `v_racion_periodo` | Dieta actual e historial por animal | `LEAD` |
| `v_consumo_marca_rancho` | Consumo y costo por marca | GROUP BY, `SUM OVER` |
| `v_alimentacion_hoy` | Kilos y costo de hoy / al mes | GROUP BY |
| `v_catalogo_alimento` | Recomendar y comparar alimentos | `RANK` |
| `v_mezcla_nutricion` | Simulador de dietas (proteína, energía, $/kg, suma 100 %) | GROUP BY |
| `v_finanzas_mensual` | Ventas, gastos y utilidad acumulada | GROUP BY, `SUM OVER` |
| `v_competencias_animal` | Premios del animal | `RANK` |
| `v_tamano_rebano_mensual` | Tamaño del rebaño en 12 meses | `GENERATE_SERIES` |
| `v_composicion_rebano` | Composición por sexo, etapa y raza | GROUP BY, vista sobre vista |
| `v_desempeno_semental` | Selección de sementales | LATERAL |
| `v_sabana` | Sábana oficial de 24 columnas (§5) | LATERAL, `ARRAY_AGG FILTER` |

> **Limitación conocida:** V1 no guarda la fecha de venta, así que `v_tamano_rebano_mensual` solo cuenta a los vendidos mientras siguen activos. Si se necesita, agregar `animals.sale_date` en una migración futura.
