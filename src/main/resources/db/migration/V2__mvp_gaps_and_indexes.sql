-- V2__mvp_gaps_and_indexes.sql: huecos de Fase 1 (MVP) documentados en DATABASE_DESIGN.md §4
-- Los valores de los CHECK siguen en inglés porque son los enums de Kotlin.

-- ---------------------------------------------------------------------
-- A. Multi-tenancy de Asociación (usado por RanchContextInterceptor)
-- ---------------------------------------------------------------------
CREATE TABLE asociaciones (
    id UUID PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    clave VARCHAR(40) UNIQUE,
    alcance VARCHAR(20) NOT NULL DEFAULT 'STATE' CHECK (alcance IN ('STATE', 'NATIONAL')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE asociaciones_ranchos (
    asociacion_id UUID NOT NULL REFERENCES asociaciones(id),
    rancho_id UUID NOT NULL REFERENCES ranchos(id),
    afiliado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (asociacion_id, rancho_id)
);

ALTER TABLE usuarios ADD COLUMN asociacion_id UUID REFERENCES asociaciones(id);

-- ---------------------------------------------------------------------
-- B. Ficha individual (animales) y conciliación de partos
-- ---------------------------------------------------------------------
ALTER TABLE animales
    ADD COLUMN motivo_alta VARCHAR(20) NOT NULL DEFAULT 'BIRTH'
        CHECK (motivo_alta IN ('BIRTH', 'PURCHASE', 'REENTRY', 'TRANSFER', 'OTHER')),
    ADD COLUMN criador VARCHAR(150),
    ADD COLUMN foto_s3_key TEXT,
    ADD COLUMN parto_id UUID REFERENCES partos(id),
    ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE partos
    ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- rancho_id se llena desde el animal antes de volverlo obligatorio,
-- para que la migración funcione aunque staging ya tenga pesajes.
ALTER TABLE pesajes ADD COLUMN rancho_id UUID REFERENCES ranchos(id);

UPDATE pesajes p
SET rancho_id = a.rancho_id
FROM animales a
WHERE a.id = p.animal_id;

ALTER TABLE pesajes ALTER COLUMN rancho_id SET NOT NULL;

-- ---------------------------------------------------------------------
-- C. Índices de rendimiento
-- ---------------------------------------------------------------------
CREATE INDEX idx_animales_rancho_estado ON animales (rancho_id, estado);
CREATE INDEX idx_animales_padre ON animales (padre_id) WHERE padre_id IS NOT NULL;
CREATE INDEX idx_animales_madre ON animales (madre_id) WHERE madre_id IS NOT NULL;
CREATE INDEX idx_animales_destete_pendiente ON animales (fecha_nacimiento) WHERE estado = 'ACTIVE' AND fecha_destete IS NULL;
CREATE UNIQUE INDEX uq_animales_rancho_chip ON animales (rancho_id, chip_siniiga) WHERE chip_siniiga IS NOT NULL;
CREATE INDEX idx_pesajes_animal_fecha ON pesajes (animal_id, fecha DESC);
CREATE INDEX idx_partos_rancho_fecha ON partos (rancho_id, fecha DESC);
