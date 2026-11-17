-- V3__reproduccion_salud_alimentacion_finanzas.sql
-- Empadres, salud, alimentación, competencias y movimientos de dinero.
-- Convenciones de V1/V2: id UUID generado en el celular (offline), VARCHAR + CHECK
-- en lugar de ENUM, sin borrado físico de animales (las FK a animals son RESTRICT).
-- Catálogos: id SMALLINT identity con sus valores iniciales.

-- ---------------------------------------------------------------------
-- Catálogos
-- ---------------------------------------------------------------------
CREATE TABLE tipos_tratamiento (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE,
    es_preventivo BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO tipos_tratamiento (nombre, es_preventivo) VALUES
    ('Vacuna', TRUE), ('Desparasitación', TRUE), ('Vitaminas', TRUE), ('Selenio', TRUE),
    ('Curación', FALSE), ('Enfermedad', FALSE), ('Revisión', FALSE);

CREATE TABLE tipos_alimento (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO tipos_alimento (nombre) VALUES
    ('Preiniciador'), ('Iniciador'), ('Crecimiento'), ('Engorda'), ('Pie de cría'),
    ('Concentrado'), ('Forraje'), ('Mineral'), ('Grano'), ('Pasta proteica'), ('Subproducto');

CREATE TABLE etapas_productivas (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL UNIQUE,
    proteina_min_pct NUMERIC(4, 1) NOT NULL,
    proteina_max_pct NUMERIC(4, 1) NOT NULL,
    CHECK (proteina_min_pct > 0 AND proteina_min_pct <= proteina_max_pct AND proteina_max_pct <= 40)
);

INSERT INTO etapas_productivas (nombre, proteina_min_pct, proteina_max_pct) VALUES
    ('Lactancia', 18, 22), ('Crecimiento', 16, 18), ('Desarrollo', 15, 17),
    ('Engorda', 13, 15), ('Mantenimiento', 7, 10);

CREATE TABLE resultados_competencia (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL UNIQUE,
    puntos SMALLINT NOT NULL CHECK (puntos BETWEEN 0 AND 100)
);

INSERT INTO resultados_competencia (nombre, puntos) VALUES
    ('Gran campeón', 100), ('Campeón', 90), ('Reservado de campeón', 80), ('1er lugar', 70),
    ('2do lugar', 60), ('3er lugar', 50), ('Mención honorífica', 30), ('Participó', 10);

-- ---------------------------------------------------------------------
-- Reproducción: empadres (el parto ya vive en birth_events)
-- ---------------------------------------------------------------------
CREATE TABLE empadres (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranches(id),
    hembra_id UUID NOT NULL REFERENCES animals(id),
    semental_id UUID REFERENCES animals(id),
    semental_externo_folio VARCHAR(60),
    metodo_concepcion VARCHAR(10) NOT NULL DEFAULT 'MN' CHECK (metodo_concepcion IN ('MN', 'IA', 'TE')),
    fecha DATE NOT NULL,
    diagnostico VARCHAR(10) CHECK (diagnostico IN ('PREGNANT', 'EMPTY')),
    fecha_diagnostico DATE,
    birth_event_id UUID UNIQUE REFERENCES birth_events(id),
    cerrado BOOLEAN NOT NULL DEFAULT FALSE,
    registrado_por_usuario_id UUID REFERENCES users(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (hembra_id <> semental_id),
    CHECK (semental_id IS NOT NULL OR semental_externo_folio IS NOT NULL OR metodo_concepcion <> 'MN'),
    CHECK ((diagnostico IS NULL) = (fecha_diagnostico IS NULL)),
    -- El ultrasonido es confiable a partir de unos 30 días del empadre
    CHECK (fecha_diagnostico >= fecha + 30)
);

CREATE INDEX idx_empadres_hembra ON empadres (hembra_id, fecha DESC);
CREATE INDEX idx_empadres_semental ON empadres (semental_id) WHERE semental_id IS NOT NULL;
CREATE INDEX idx_empadres_abiertos ON empadres (rancho_id) WHERE NOT cerrado;

-- ---------------------------------------------------------------------
-- Salud: calendario por rancho, tratamientos y a quién se aplicaron
-- ---------------------------------------------------------------------
CREATE TABLE esquemas_vacuna (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranches(id),
    tipo_tratamiento_id SMALLINT NOT NULL REFERENCES tipos_tratamiento(id),
    nombre VARCHAR(120) NOT NULL,
    edad_inicio_dias SMALLINT NOT NULL CHECK (edad_inicio_dias BETWEEN 0 AND 730),
    cada_dias SMALLINT NOT NULL CHECK (cada_dias BETWEEN 15 AND 730),
    aplica_a VARCHAR(10) NOT NULL DEFAULT 'ALL' CHECK (aplica_a IN ('ALL', 'FEMALE', 'MALE')),
    nota VARCHAR(300),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (rancho_id, nombre)
);

CREATE TABLE tratamientos (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranches(id),
    tipo_tratamiento_id SMALLINT NOT NULL REFERENCES tipos_tratamiento(id),
    esquema_vacuna_id UUID REFERENCES esquemas_vacuna(id) ON DELETE SET NULL,
    producto VARCHAR(160) NOT NULL,
    alcance VARCHAR(12) NOT NULL CHECK (alcance IN ('HERD', 'LAMBS', 'INDIVIDUAL')),
    fecha DATE NOT NULL,
    proxima_fecha DATE CHECK (proxima_fecha > fecha),
    notas VARCHAR(500),
    registrado_por_usuario_id UUID REFERENCES users(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tratamientos_rancho_fecha ON tratamientos (rancho_id, fecha DESC);

CREATE TABLE tratamientos_animal (
    id UUID PRIMARY KEY,
    tratamiento_id UUID NOT NULL REFERENCES tratamientos(id) ON DELETE CASCADE,
    animal_id UUID NOT NULL REFERENCES animals(id),
    dosis_ml NUMERIC(5, 2) CHECK (dosis_ml > 0 AND dosis_ml <= 50),
    observacion VARCHAR(200),
    UNIQUE (tratamiento_id, animal_id)
);

CREATE INDEX idx_tratamientos_animal_animal ON tratamientos_animal (animal_id);

-- ---------------------------------------------------------------------
-- Alimentación: catálogo con marcas, dieta por animal y mezclas del rancho
-- ---------------------------------------------------------------------
CREATE TABLE alimentos (
    id UUID PRIMARY KEY,
    rancho_id UUID REFERENCES ranches(id),
    tipo_alimento_id SMALLINT NOT NULL REFERENCES tipos_alimento(id),
    marca VARCHAR(80) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    proteina_pct NUMERIC(4, 1) CHECK (proteina_pct BETWEEN 0 AND 100),
    energia_mcal_kg NUMERIC(3, 2) CHECK (energia_mcal_kg BETWEEN 0 AND 5),
    kg_presentacion NUMERIC(7, 2) CHECK (kg_presentacion > 0),
    precio_presentacion NUMERIC(10, 2) CHECK (precio_presentacion >= 0),
    uso VARCHAR(300),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (precio_presentacion IS NULL OR kg_presentacion IS NOT NULL),
    -- rancho_id NULL = catálogo general; con valor = alimento propio del rancho
    UNIQUE NULLS NOT DISTINCT (rancho_id, marca, nombre)
);

CREATE TABLE raciones (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id),
    fecha_inicio DATE NOT NULL,
    nota VARCHAR(200),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (animal_id, fecha_inicio)
);

CREATE TABLE raciones_alimento (
    id UUID PRIMARY KEY,
    racion_id UUID NOT NULL REFERENCES raciones(id) ON DELETE CASCADE,
    alimento_id UUID NOT NULL REFERENCES alimentos(id),
    -- Más de 6 kg/día de un solo alimento es un error de captura en ovinos
    kg_dia NUMERIC(6, 3) NOT NULL CHECK (kg_dia > 0 AND kg_dia <= 6),
    UNIQUE (racion_id, alimento_id)
);

CREATE TABLE mezclas (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranches(id),
    etapa_productiva_id SMALLINT NOT NULL REFERENCES etapas_productivas(id),
    nombre VARCHAR(80) NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (rancho_id, nombre)
);

-- Que los porcentajes sumen 100 depende de varios renglones: lo valida v_mezcla_nutricion
CREATE TABLE mezclas_ingrediente (
    id UUID PRIMARY KEY,
    mezcla_id UUID NOT NULL REFERENCES mezclas(id) ON DELETE CASCADE,
    alimento_id UUID NOT NULL REFERENCES alimentos(id),
    porcentaje NUMERIC(5, 2) NOT NULL CHECK (porcentaje > 0 AND porcentaje <= 100),
    UNIQUE (mezcla_id, alimento_id)
);

-- ---------------------------------------------------------------------
-- Competencias y exposiciones
-- ---------------------------------------------------------------------
CREATE TABLE eventos_competencia (
    id UUID PRIMARY KEY,
    nombre VARCHAR(160) NOT NULL,
    lugar VARCHAR(160) NOT NULL,
    fecha DATE NOT NULL,
    juez VARCHAR(120),
    UNIQUE (nombre, fecha)
);

CREATE TABLE participaciones (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id),
    evento_competencia_id UUID NOT NULL REFERENCES eventos_competencia(id),
    resultado_competencia_id SMALLINT NOT NULL REFERENCES resultados_competencia(id),
    categoria VARCHAR(60) NOT NULL,
    foto_s3_key TEXT,
    nota VARCHAR(200),
    UNIQUE (animal_id, evento_competencia_id, categoria)
);

-- ---------------------------------------------------------------------
-- Dinero: ventas y gastos del rancho
-- ---------------------------------------------------------------------
CREATE TABLE movimientos (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranches(id),
    animal_id UUID REFERENCES animals(id),
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('INCOME', 'EXPENSE')),
    concepto VARCHAR(160) NOT NULL,
    monto NUMERIC(12, 2) NOT NULL CHECK (monto > 0 AND monto < 10000000),
    fecha DATE NOT NULL,
    registrado_por_usuario_id UUID REFERENCES users(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_movimientos_rancho_fecha ON movimientos (rancho_id, fecha);
