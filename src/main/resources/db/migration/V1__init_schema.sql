-- V1__init_schema.sql: Esquema base PostgreSQL 16 (AgroTech / OviApp)

CREATE TABLE ranches (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address TEXT NOT NULL,
    owner_name VARCHAR(150) NOT NULL,
    weaning_days_target INT NOT NULL DEFAULT 75,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    ranch_id UUID REFERENCES ranches(id),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(40) NOT NULL CHECK (role IN ('OWNER', 'WORKER_BRANCH', 'VET_TECHNICIAN', 'ASSOCIATION_SUPERUSER')),
    branch_permissions JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE animals (
    id UUID PRIMARY KEY,
    ranch_id UUID NOT NULL REFERENCES ranches(id),
    folio_interno VARCHAR(60) NOT NULL,
    siniiga_chip VARCHAR(20),
    registro_uno VARCHAR(60),
    adn VARCHAR(60),
    breed VARCHAR(80) NOT NULL,
    color VARCHAR(60) NOT NULL,
    sex VARCHAR(10) NOT NULL CHECK (sex IN ('FEMALE', 'MALE')),
    birth_date DATE NOT NULL,
    weaning_date DATE,
    parturition_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    conception_method VARCHAR(10) NOT NULL DEFAULT 'MN',
    sire_id UUID REFERENCES animals(id),
    dam_id UUID REFERENCES animals(id),
    external_sire_folio VARCHAR(60),
    external_dam_folio VARCHAR(60),
    life_stage VARCHAR(30) NOT NULL,
    reproductive_status VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SOLD', 'DECEASED')),
    mortality_date DATE,
    mortality_cause TEXT,
    life_stage_at_death VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ranch_id, folio_interno)
);

CREATE TABLE birth_events (
    id UUID PRIMARY KEY,
    ranch_id UUID NOT NULL REFERENCES ranches(id),
    dam_id UUID NOT NULL REFERENCES animals(id),
    sire_id UUID REFERENCES animals(id),
    birth_date DATE NOT NULL,
    live_born_count INT NOT NULL,
    stillborn_count INT NOT NULL DEFAULT 0,
    evidence_photo_s3_key TEXT NOT NULL,
    registered_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE birth_audit_logs (
    id UUID PRIMARY KEY,
    birth_event_id UUID NOT NULL REFERENCES birth_events(id),
    ranch_id UUID NOT NULL REFERENCES ranches(id),
    approved_by_user_id UUID NOT NULL REFERENCES users(id),
    previous_state JSONB NOT NULL,
    new_state JSONB NOT NULL,
    evidence_photo_s3_key TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE weight_measurements (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id),
    milestone VARCHAR(30) NOT NULL CHECK (milestone IN ('BIRTH', 'WEANING', 'DAY_120', 'DAY_150', 'EVALUATION')),
    weight_kg NUMERIC(6, 2) NOT NULL,
    scrotal_circumference_cm NUMERIC(5, 2),
    measured_at DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE processed_sync_mutations (
    mutation_id UUID PRIMARY KEY,
    ranch_id UUID NOT NULL REFERENCES ranches(id),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
