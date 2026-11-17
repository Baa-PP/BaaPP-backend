-- V2__mvp_gaps_and_indexes.sql: huecos de Fase 1 (MVP) documentados en DATABASE_DESIGN.md §4

-- ---------------------------------------------------------------------
-- A. Multi-tenancy de Asociación (usado por RanchContextInterceptor)
-- ---------------------------------------------------------------------
CREATE TABLE associations (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(40) UNIQUE,
    scope VARCHAR(20) NOT NULL DEFAULT 'STATE' CHECK (scope IN ('STATE', 'NATIONAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE association_ranches (
    association_id UUID NOT NULL REFERENCES associations(id),
    ranch_id UUID NOT NULL REFERENCES ranches(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (association_id, ranch_id)
);

ALTER TABLE users ADD COLUMN association_id UUID REFERENCES associations(id);

-- ---------------------------------------------------------------------
-- B. Ficha individual (animals) y conciliación de partos (birth_events)
-- ---------------------------------------------------------------------
ALTER TABLE animals
    ADD COLUMN entry_reason VARCHAR(20) NOT NULL DEFAULT 'BIRTH'
        CHECK (entry_reason IN ('BIRTH', 'PURCHASE', 'REENTRY', 'TRANSFER', 'OTHER')),
    ADD COLUMN criador_name VARCHAR(150),
    ADD COLUMN photo_s3_key TEXT,
    ADD COLUMN birth_event_id UUID REFERENCES birth_events(id),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE birth_events
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- ranch_id se llena desde el animal antes de volverlo obligatorio,
-- para que la migración funcione aunque staging ya tenga pesajes.
ALTER TABLE weight_measurements ADD COLUMN ranch_id UUID REFERENCES ranches(id);

UPDATE weight_measurements w
SET ranch_id = a.ranch_id
FROM animals a
WHERE a.id = w.animal_id;

ALTER TABLE weight_measurements ALTER COLUMN ranch_id SET NOT NULL;

-- ---------------------------------------------------------------------
-- C. Índices de rendimiento
-- ---------------------------------------------------------------------
CREATE INDEX idx_animals_ranch_status ON animals (ranch_id, status);
CREATE INDEX idx_animals_sire ON animals (sire_id) WHERE sire_id IS NOT NULL;
CREATE INDEX idx_animals_dam ON animals (dam_id) WHERE dam_id IS NOT NULL;
CREATE INDEX idx_animals_weaning_pending ON animals (birth_date) WHERE status = 'ACTIVE' AND weaning_date IS NULL;
CREATE UNIQUE INDEX uq_animals_ranch_siniiga ON animals (ranch_id, siniiga_chip) WHERE siniiga_chip IS NOT NULL;
CREATE INDEX idx_weights_animal_date ON weight_measurements (animal_id, measured_at DESC);
CREATE INDEX idx_births_ranch_date ON birth_events (ranch_id, birth_date DESC);
