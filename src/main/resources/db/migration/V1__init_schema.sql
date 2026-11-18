-- V1__init_schema.sql: Esquema base PostgreSQL 16 (AgroTech / OviApp)

CREATE TABLE ranchos (
    id UUID PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    direccion TEXT NOT NULL,
    propietario VARCHAR(150) NOT NULL,
    dias_meta_destete INT NOT NULL DEFAULT 75,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    rancho_id UUID REFERENCES ranchos(id),
    usuario VARCHAR(100) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(40) NOT NULL CHECK (rol IN ('OWNER', 'WORKER_BRANCH', 'VET_TECHNICIAN', 'ASSOCIATION_SUPERUSER')),
    permisos JSONB NOT NULL DEFAULT '{}'::jsonb,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE animales (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranchos(id),
    folio_interno VARCHAR(60) NOT NULL,
    chip_siniiga VARCHAR(20),
    registro_uno VARCHAR(60),
    adn VARCHAR(60),
    raza VARCHAR(80) NOT NULL,
    color VARCHAR(60) NOT NULL,
    sexo VARCHAR(10) NOT NULL CHECK (sexo IN ('FEMALE', 'MALE')),
    fecha_nacimiento DATE NOT NULL,
    fecha_destete DATE,
    tipo_parto VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    metodo_concepcion VARCHAR(10) NOT NULL DEFAULT 'MN',
    padre_id UUID REFERENCES animales(id),
    madre_id UUID REFERENCES animales(id),
    folio_padre_externo VARCHAR(60),
    folio_madre_externo VARCHAR(60),
    etapa_vida VARCHAR(30) NOT NULL,
    estado_reproductivo VARCHAR(30) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (estado IN ('ACTIVE', 'SOLD', 'DECEASED')),
    fecha_muerte DATE,
    causa_muerte TEXT,
    etapa_al_morir VARCHAR(30),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (rancho_id, folio_interno)
);

CREATE TABLE partos (
    id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranchos(id),
    madre_id UUID NOT NULL REFERENCES animales(id),
    padre_id UUID REFERENCES animales(id),
    fecha DATE NOT NULL,
    nacidos_vivos INT NOT NULL,
    nacidos_muertos INT NOT NULL DEFAULT 0,
    foto_evidencia_s3_key TEXT NOT NULL,
    registrado_por_usuario_id UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE auditorias_parto (
    id UUID PRIMARY KEY,
    parto_id UUID NOT NULL REFERENCES partos(id),
    rancho_id UUID NOT NULL REFERENCES ranchos(id),
    aprobado_por_usuario_id UUID NOT NULL REFERENCES usuarios(id),
    estado_anterior JSONB NOT NULL,
    estado_nuevo JSONB NOT NULL,
    foto_evidencia_s3_key TEXT NOT NULL,
    motivo TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pesajes (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animales(id),
    hito VARCHAR(30) NOT NULL CHECK (hito IN ('BIRTH', 'WEANING', 'DAY_120', 'DAY_150', 'EVALUATION')),
    peso_kg NUMERIC(6, 2) NOT NULL,
    circunferencia_escrotal_cm NUMERIC(5, 2),
    fecha DATE NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE mutaciones_procesadas (
    mutacion_id UUID PRIMARY KEY,
    rancho_id UUID NOT NULL REFERENCES ranchos(id),
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
