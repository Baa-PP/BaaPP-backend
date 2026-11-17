-- R__vistas.sql: vistas de lectura para la app móvil y el portal web.
-- Migración repetible de Flyway: se vuelve a aplicar cada vez que cambia este archivo,
-- por eso primero se borran las vistas y luego se crean de nuevo.
-- Nada de esto se guarda: edades, GDP, tasas e indicadores se calculan al consultar.
-- Cordero = life_stage LAMB_LACTATING o LAMB_FATTENING · gestación 150 días · peso de venta 35 kg.

DROP VIEW IF EXISTS
    v_sabana, v_desempeno_semental, v_composicion_rebano, v_tamano_rebano_mensual,
    v_competencias_animal, v_finanzas_mensual, v_mezcla_nutricion, v_catalogo_alimento,
    v_alimentacion_hoy, v_consumo_marca_rancho, v_racion_periodo, v_historial_salud,
    v_cobertura_vacunas, v_estado_vacunas, v_peso_etapa, v_ranking_crecimiento,
    v_historial_reproductivo_hembra, v_reproduccion_temporada, v_partos, v_cargadas,
    v_genealogia, v_pesaje_ganancia, v_resumen_rancho, v_animal_resumen
CASCADE;

-- 1. Ficha y lista del rebaño
CREATE VIEW v_animal_resumen AS
SELECT a.id                                         AS animal_id,
       a.ranch_id                                   AS rancho_id,
       a.folio_interno,
       a.siniiga_chip,
       a.registro_uno,
       a.sex                                        AS sexo,
       a.life_stage                                 AS etapa,
       a.reproductive_status                        AS estado_reproductivo,
       a.breed                                      AS raza,
       a.color,
       a.birth_date                                 AS fecha_nacimiento,
       CURRENT_DATE - a.birth_date                  AS edad_dias,
       a.weaning_date                               AS fecha_destete,
       a.entry_reason                               AS motivo_alta,
       a.criador_name                               AS criador,
       r.owner_name                                 AS propietario,
       a.photo_s3_key                               AS foto_s3_key,
       up.weight_kg                                 AS ultimo_peso_kg,
       up.measured_at                               AS fecha_ultimo_peso,
       pt.partos,
       EXISTS (SELECT 1 FROM empadres e
                WHERE e.hembra_id = a.id AND NOT e.cerrado
                  AND e.diagnostico IS DISTINCT FROM 'EMPTY') AS cargada,
       a.status                                     AS estado,
       a.mortality_date                             AS fecha_muerte,
       a.mortality_cause                            AS causa_muerte,
       a.sire_id                                    AS padre_id,
       a.dam_id                                     AS madre_id
FROM animals a
JOIN ranches r ON r.id = a.ranch_id
LEFT JOIN LATERAL (
    SELECT w.weight_kg, w.measured_at FROM weight_measurements w
    WHERE w.animal_id = a.id
    ORDER BY w.measured_at DESC LIMIT 1
) up ON TRUE
CROSS JOIN LATERAL (
    SELECT COUNT(*)::INT AS partos FROM birth_events b WHERE b.dam_id = a.id
) pt;

-- 2. Inicio y "todos mis ranchos" (GROUP BY)
CREATE VIEW v_resumen_rancho AS
SELECT r.id                                                                         AS rancho_id,
       r.name                                                                       AS rancho,
       r.owner_name                                                                 AS propietario,
       COUNT(v.animal_id)                                                           AS animales_activos,
       COUNT(v.animal_id) FILTER (WHERE v.sexo = 'FEMALE' AND v.etapa NOT LIKE 'LAMB%') AS hembras,
       COUNT(v.animal_id) FILTER (WHERE v.cargada)                                  AS cargadas,
       COUNT(v.animal_id) FILTER (WHERE v.etapa LIKE 'LAMB%')                       AS corderos,
       COUNT(v.animal_id) FILTER (WHERE v.sexo = 'MALE' AND v.etapa NOT LIKE 'LAMB%')   AS machos,
       COUNT(v.animal_id) FILTER (WHERE v.estado_reproductivo = 'STUD_RAM')         AS sementales,
       COUNT(v.animal_id) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM raciones ra WHERE ra.animal_id = v.animal_id))             AS animales_sin_dieta
FROM ranches r
LEFT JOIN v_animal_resumen v ON v.rancho_id = r.id AND v.estado = 'ACTIVE'
GROUP BY r.id, r.name, r.owner_name;

-- 3. Pesaje: diferencia contra el pesaje anterior (LAG)
CREATE VIEW v_pesaje_ganancia AS
SELECT s.pesaje_id,
       s.animal_id,
       s.rancho_id,
       s.folio_interno,
       s.hito,
       s.fecha,
       s.edad_dias,
       s.peso_kg,
       s.peso_anterior_kg,
       s.fecha - s.fecha_anterior                                       AS dias_desde_anterior,
       s.peso_kg - s.peso_anterior_kg                                   AS ganancia_kg,
       ROUND(1000 * (s.peso_kg - s.peso_anterior_kg)
             / NULLIF(s.fecha - s.fecha_anterior, 0))                   AS ganancia_diaria_g
FROM (
    SELECT w.id AS pesaje_id, w.animal_id, w.ranch_id AS rancho_id, a.folio_interno,
           w.milestone AS hito, w.measured_at AS fecha, w.weight_kg AS peso_kg,
           w.measured_at - a.birth_date                                          AS edad_dias,
           LAG(w.weight_kg)   OVER (PARTITION BY w.animal_id ORDER BY w.measured_at) AS peso_anterior_kg,
           LAG(w.measured_at) OVER (PARTITION BY w.animal_id ORDER BY w.measured_at) AS fecha_anterior
    FROM weight_measurements w
    JOIN animals a ON a.id = w.animal_id
) s;

-- 4. Genealogía hasta bisabuelos (CTE recursiva). ruta: M = madre, P = padre
CREATE VIEW v_genealogia AS
WITH RECURSIVE arbol (animal_id, ancestro_id, generacion, ruta) AS (
    SELECT a.id, x.pid, 1, x.lado::TEXT
    FROM animals a
    CROSS JOIN LATERAL (VALUES (a.dam_id, 'M'), (a.sire_id, 'P')) AS x (pid, lado)
    WHERE x.pid IS NOT NULL
    UNION ALL
    SELECT ar.animal_id, x.pid, ar.generacion + 1, ar.ruta || x.lado
    FROM arbol ar
    JOIN animals p ON p.id = ar.ancestro_id
    CROSS JOIN LATERAL (VALUES (p.dam_id, 'M'), (p.sire_id, 'P')) AS x (pid, lado)
    WHERE x.pid IS NOT NULL AND ar.generacion < 3
)
SELECT ar.animal_id,
       ar.ancestro_id,
       ar.generacion,
       ar.ruta,
       CASE ar.generacion
           WHEN 1 THEN CASE WHEN an.sex = 'FEMALE' THEN 'Madre' ELSE 'Padre' END
           WHEN 2 THEN CASE WHEN an.sex = 'FEMALE' THEN 'Abuela' ELSE 'Abuelo' END
           ELSE        CASE WHEN an.sex = 'FEMALE' THEN 'Bisabuela' ELSE 'Bisabuelo' END
       END                                                        AS parentesco,
       CASE LEFT(ar.ruta, 1) WHEN 'M' THEN 'materna' ELSE 'paterna' END AS linea,
       an.folio_interno,
       an.registro_uno,
       an.sex                                                     AS sexo,
       an.breed                                                   AS raza,
       an.color,
       an.criador_name                                            AS criador,
       an.birth_date                                              AS fecha_nacimiento
FROM arbol ar
JOIN animals an ON an.id = ar.ancestro_id;

-- 5. Borregas cargadas y fecha probable de parto
CREATE VIEW v_cargadas AS
SELECT e.id                                   AS empadre_id,
       e.rancho_id,
       h.id                                   AS hembra_id,
       h.folio_interno                        AS hembra_folio,
       COALESCE(s.folio_interno, e.semental_externo_folio) AS semental_folio,
       e.metodo_concepcion,
       e.fecha                                AS fecha_empadre,
       CURRENT_DATE - e.fecha                 AS dias_gestacion,
       e.fecha + 150                          AS fecha_probable_parto,
       (e.fecha + 150) - CURRENT_DATE         AS dias_faltan,
       e.diagnostico,
       e.fecha_diagnostico
FROM empadres e
JOIN animals h ON h.id = e.hembra_id
LEFT JOIN animals s ON s.id = e.semental_id
WHERE NOT e.cerrado AND h.status = 'ACTIVE'
  AND e.diagnostico IS DISTINCT FROM 'EMPTY';

-- 6. Partos registrados con sus crías
CREATE VIEW v_partos AS
SELECT b.id                                        AS parto_id,
       b.ranch_id                                  AS rancho_id,
       b.dam_id                                    AS madre_id,
       m.folio_interno                             AS madre_folio,
       COALESCE(s.folio_interno, e.semental_externo_folio) AS padre_folio,
       b.birth_date                                AS fecha,
       b.live_born_count                           AS nacidos_vivos,
       b.stillborn_count                           AS nacidos_muertos,
       CASE b.live_born_count + b.stillborn_count
           WHEN 1 THEN 'SIMPLE' WHEN 2 THEN 'TWIN' WHEN 3 THEN 'TRIPLET'
           WHEN 4 THEN 'QUADRUPLET' ELSE 'QUINTUPLET'
       END                                         AS tipo_parto,
       c.crias_registradas,
       c.crias_muertas_despues,
       c.folios_crias,
       CURRENT_DATE - b.birth_date > 30            AS bloqueado,
       b.evidence_photo_s3_key                     AS foto_evidencia_s3_key
FROM birth_events b
JOIN animals m ON m.id = b.dam_id
LEFT JOIN animals s ON s.id = b.sire_id
LEFT JOIN empadres e ON e.birth_event_id = b.id
CROSS JOIN LATERAL (
    SELECT COUNT(*)::INT                                       AS crias_registradas,
           COUNT(*) FILTER (WHERE x.status = 'DECEASED')::INT  AS crias_muertas_despues,
           STRING_AGG(x.folio_interno, ', ' ORDER BY x.folio_interno) AS folios_crias
    FROM animals x WHERE x.birth_event_id = b.id
) c;

-- 7. Reproducción por temporada (trimestre del empadre, GROUP BY)
CREATE VIEW v_reproduccion_temporada AS
SELECT e.rancho_id,
       DATE_TRUNC('quarter', e.fecha)::DATE                                     AS temporada,
       COUNT(*)                                                                 AS expuestas,
       COUNT(*) FILTER (WHERE e.diagnostico IS NOT NULL OR b.id IS NOT NULL)     AS diagnosticadas,
       COUNT(*) FILTER (WHERE e.diagnostico = 'PREGNANT' OR b.id IS NOT NULL)    AS prenadas,
       ROUND(100.0 * COUNT(*) FILTER (WHERE e.diagnostico = 'PREGNANT' OR b.id IS NOT NULL)
             / NULLIF(COUNT(*) FILTER (WHERE e.diagnostico IS NOT NULL OR b.id IS NOT NULL), 0), 1) AS pct_prenez,
       COUNT(b.id)                                                              AS partos,
       ROUND(100.0 * COUNT(b.id) / COUNT(*), 1)                                 AS pct_parto,
       COALESCE(SUM(b.live_born_count + b.stillborn_count), 0)                  AS crias_nacidas,
       ROUND(SUM(b.live_born_count + b.stillborn_count)::NUMERIC / NULLIF(COUNT(b.id), 0), 2) AS crias_por_parto,
       COALESCE(SUM(b.stillborn_count + cm.muertas_despues), 0)                 AS crias_muertas,
       ROUND(100.0 * SUM(b.stillborn_count + cm.muertas_despues)
             / NULLIF(SUM(b.live_born_count + b.stillborn_count), 0), 1)        AS pct_mortalidad
FROM empadres e
LEFT JOIN birth_events b ON b.id = e.birth_event_id
LEFT JOIN LATERAL (
    SELECT COUNT(*)::INT AS muertas_despues FROM animals x
    WHERE x.birth_event_id = b.id AND x.status = 'DECEASED'
) cm ON TRUE
GROUP BY e.rancho_id, DATE_TRUNC('quarter', e.fecha);

-- 8. Historial reproductivo de cada hembra y vientres improductivos
CREATE VIEW v_historial_reproductivo_hembra AS
SELECT h.animal_id, h.rancho_id, h.folio_interno, h.edad_dias,
       h.empadres, h.diagnosticados, h.prenadas, h.pct_prenez,
       h.partos, h.crias_nacidas, h.crias_por_parto, h.crias_muertas, h.pct_mortalidad,
       h.ultimo_parto, h.dias_desde_ultimo_parto, h.cargada, h.improductiva,
       CASE
           WHEN h.improductiva THEN 'CULL'
           WHEN h.diagnosticados >= 2 AND h.pct_prenez < 50 THEN 'CULL'
           WHEN h.pct_mortalidad > 30 THEN 'SELL'
           ELSE 'BREEDING'
       END AS destino_sugerido
FROM (
    SELECT a.id AS animal_id, a.ranch_id AS rancho_id, a.folio_interno,
           CURRENT_DATE - a.birth_date                                        AS edad_dias,
           em.empadres, em.diagnosticados, em.prenadas,
           ROUND(100.0 * em.prenadas / NULLIF(em.diagnosticados, 0), 1)       AS pct_prenez,
           pt.partos, pt.crias_nacidas,
           ROUND(pt.crias_nacidas::NUMERIC / NULLIF(pt.partos, 0), 2)         AS crias_por_parto,
           pt.crias_muertas,
           ROUND(100.0 * pt.crias_muertas / NULLIF(pt.crias_nacidas, 0), 1)   AS pct_mortalidad,
           pt.ultimo_parto,
           CURRENT_DATE - pt.ultimo_parto                                     AS dias_desde_ultimo_parto,
           em.abiertos > 0                                                    AS cargada,
           (CURRENT_DATE - a.birth_date > 600 AND em.abiertos = 0
             AND (pt.ultimo_parto IS NULL OR pt.ultimo_parto < CURRENT_DATE - 365)) AS improductiva
    FROM animals a
    CROSS JOIN LATERAL (
        SELECT COUNT(*)::INT                                                   AS empadres,
               COUNT(*) FILTER (WHERE e.diagnostico IS NOT NULL)::INT          AS diagnosticados,
               COUNT(*) FILTER (WHERE e.diagnostico = 'PREGNANT')::INT         AS prenadas,
               COUNT(*) FILTER (WHERE NOT e.cerrado
                                  AND e.diagnostico IS DISTINCT FROM 'EMPTY')::INT AS abiertos
        FROM empadres e WHERE e.hembra_id = a.id
    ) em
    CROSS JOIN LATERAL (
        SELECT COUNT(*)::INT                                                   AS partos,
               COALESCE(SUM(b.live_born_count + b.stillborn_count), 0)::INT    AS crias_nacidas,
               COALESCE(SUM(b.stillborn_count + (SELECT COUNT(*) FROM animals x
                    WHERE x.birth_event_id = b.id AND x.status = 'DECEASED')), 0)::INT AS crias_muertas,
               MAX(b.birth_date)                                               AS ultimo_parto
        FROM birth_events b WHERE b.dam_id = a.id
    ) pt
    WHERE a.sex = 'FEMALE' AND a.status = 'ACTIVE' AND a.life_stage NOT LIKE 'LAMB%'
) h;

-- 9. Ranking de crecimiento de corderos (regresión + PERCENT_RANK)
--    GDP = pendiente de la recta de sus últimos 4 pesajes
CREATE VIEW v_ranking_crecimiento AS
WITH ult AS (
    SELECT w.animal_id, w.weight_kg, (w.measured_at - a.birth_date)::NUMERIC AS edad_pesaje,
           ROW_NUMBER() OVER (PARTITION BY w.animal_id ORDER BY w.measured_at DESC) AS n
    FROM weight_measurements w JOIN animals a ON a.id = w.animal_id
), recta AS (
    SELECT animal_id,
           REGR_SLOPE(weight_kg, edad_pesaje)     AS kg_dia,
           REGR_INTERCEPT(weight_kg, edad_pesaje) AS ordenada
    FROM ult WHERE n <= 4
    GROUP BY animal_id
    HAVING COUNT(*) >= 2
), c AS (
    SELECT a.id AS animal_id, a.ranch_id AS rancho_id, a.folio_interno, a.sex AS sexo,
           CURRENT_DATE - a.birth_date                                             AS edad_dias,
           ROUND((rc.kg_dia * 1000)::NUMERIC)                                      AS ganancia_diaria_g,
           ROUND((rc.ordenada + rc.kg_dia * (CURRENT_DATE - a.birth_date))::NUMERIC, 1) AS peso_estimado_hoy_kg,
           rc.kg_dia
    FROM recta rc
    JOIN animals a ON a.id = rc.animal_id
    WHERE a.status = 'ACTIVE' AND a.life_stage LIKE 'LAMB%'
)
SELECT c.animal_id,
       c.rancho_id,
       c.folio_interno,
       c.sexo,
       c.edad_dias,
       c.ganancia_diaria_g,
       c.peso_estimado_hoy_kg,
       ROUND(PERCENT_RANK() OVER (PARTITION BY c.rancho_id ORDER BY c.ganancia_diaria_g)::NUMERIC * 100) AS percentil_rancho,
       c.ganancia_diaria_g < 130                                                   AS crece_lento,
       CASE
           WHEN c.peso_estimado_hoy_kg >= 35 THEN CURRENT_DATE
           WHEN c.kg_dia > 0.02 THEN CURRENT_DATE + CEIL((35 - c.peso_estimado_hoy_kg) / c.kg_dia)::INT
       END                                                                         AS fecha_estimada_venta,
       CASE
           WHEN c.ganancia_diaria_g < 130 THEN 'SELL'
           WHEN c.sexo = 'FEMALE'
                AND PERCENT_RANK() OVER (PARTITION BY c.rancho_id ORDER BY c.ganancia_diaria_g) >= 0.5 THEN 'BREEDING'
           ELSE 'FATTENING'
       END                                                                         AS destino_sugerido
FROM c;

-- 10. Peso por etapa (hitos de la sábana) contra el promedio del rancho (AVG OVER)
CREATE VIEW v_peso_etapa AS
SELECT x.animal_id,
       x.rancho_id,
       x.folio_interno,
       x.sexo,
       x.hito,
       x.fecha,
       x.peso_kg,
       x.circunferencia_escrotal_cm,
       ROUND(AVG(x.peso_kg) OVER (PARTITION BY x.rancho_id, x.sexo, x.hito), 2)              AS promedio_rancho_kg,
       x.peso_kg - ROUND(AVG(x.peso_kg) OVER (PARTITION BY x.rancho_id, x.sexo, x.hito), 2)  AS diferencia_kg
FROM (
    SELECT DISTINCT ON (w.animal_id, w.milestone)
           w.animal_id, w.ranch_id AS rancho_id, a.folio_interno, a.sex AS sexo,
           w.milestone AS hito, w.measured_at AS fecha, w.weight_kg AS peso_kg,
           w.scrotal_circumference_cm AS circunferencia_escrotal_cm
    FROM weight_measurements w
    JOIN animals a ON a.id = w.animal_id
    ORDER BY w.animal_id, w.milestone, w.measured_at DESC
) x;

-- 11. Qué vacunas tiene y cuáles le faltan a cada animal
CREATE VIEW v_estado_vacunas AS
SELECT s.rancho_id,
       s.animal_id,
       s.folio_interno,
       s.esquema_vacuna_id,
       s.vacuna,
       s.tipo,
       s.ultima_aplicacion,
       s.ultima_aplicacion + s.cada_dias                       AS proxima_aplicacion,
       (s.ultima_aplicacion + s.cada_dias) - CURRENT_DATE      AS dias_para_proxima,
       CASE
           WHEN s.edad_dias < s.edad_inicio_dias THEN 'NOT_YET'
           WHEN s.ultima_aplicacion IS NULL THEN 'MISSING'
           WHEN s.ultima_aplicacion + s.cada_dias < CURRENT_DATE THEN 'OVERDUE'
           WHEN s.ultima_aplicacion + s.cada_dias <= CURRENT_DATE + 15 THEN 'DUE_SOON'
           ELSE 'UP_TO_DATE'
       END                                                     AS estado
FROM (
    SELECT a.ranch_id AS rancho_id, a.id AS animal_id, a.folio_interno,
           ev.id AS esquema_vacuna_id, ev.nombre AS vacuna, tt.nombre AS tipo,
           ev.edad_inicio_dias, ev.cada_dias,
           CURRENT_DATE - a.birth_date AS edad_dias,
           (SELECT MAX(t.fecha)
              FROM tratamientos_animal ta
              JOIN tratamientos t ON t.id = ta.tratamiento_id
             WHERE ta.animal_id = a.id AND t.esquema_vacuna_id = ev.id) AS ultima_aplicacion
    FROM animals a
    JOIN esquemas_vacuna ev   ON ev.rancho_id = a.ranch_id
    JOIN tipos_tratamiento tt ON tt.id = ev.tipo_tratamiento_id
    WHERE a.status = 'ACTIVE'
      AND (ev.aplica_a = 'ALL' OR ev.aplica_a = a.sex)
) s;

-- 12. Cobertura del calendario de vacunas por rancho (GROUP BY, vista sobre vista)
CREATE VIEW v_cobertura_vacunas AS
SELECT ev.rancho_id,
       ev.id                                                             AS esquema_vacuna_id,
       ev.nombre                                                         AS vacuna,
       tt.nombre                                                         AS tipo,
       ev.edad_inicio_dias,
       ev.cada_dias,
       COUNT(v.animal_id) FILTER (WHERE v.estado <> 'NOT_YET')           AS animales_que_aplican,
       COUNT(v.animal_id) FILTER (WHERE v.estado = 'UP_TO_DATE')         AS al_dia,
       COUNT(v.animal_id) FILTER (WHERE v.estado = 'DUE_SOON')           AS proximas,
       COUNT(v.animal_id) FILTER (WHERE v.estado = 'MISSING')            AS faltan,
       COUNT(v.animal_id) FILTER (WHERE v.estado = 'OVERDUE')            AS vencidas,
       ROUND(100.0 * COUNT(v.animal_id) FILTER (WHERE v.estado IN ('UP_TO_DATE', 'DUE_SOON'))
             / NULLIF(COUNT(v.animal_id) FILTER (WHERE v.estado <> 'NOT_YET'), 0), 1) AS pct_cobertura
FROM esquemas_vacuna ev
JOIN tipos_tratamiento tt ON tt.id = ev.tipo_tratamiento_id
LEFT JOIN v_estado_vacunas v ON v.esquema_vacuna_id = ev.id
GROUP BY ev.rancho_id, ev.id, ev.nombre, tt.nombre, ev.edad_inicio_dias, ev.cada_dias;

-- 13. Historial sanitario (la ficha filtra con :animal = ANY(animal_ids))
CREATE VIEW v_historial_salud AS
SELECT t.id                                                     AS tratamiento_id,
       t.rancho_id,
       t.fecha,
       tt.nombre                                                AS tipo,
       t.producto,
       t.alcance,
       ev.nombre                                                AS vacuna_calendario,
       t.proxima_fecha,
       COUNT(ta.animal_id)                                      AS animales_tratados,
       ARRAY_AGG(ta.animal_id ORDER BY a.folio_interno) FILTER (WHERE ta.animal_id IS NOT NULL) AS animal_ids,
       CASE WHEN COUNT(ta.animal_id) <= 3
            THEN STRING_AGG(a.folio_interno, ', ' ORDER BY a.folio_interno) END AS folios,
       t.notas
FROM tratamientos t
JOIN tipos_tratamiento tt       ON tt.id = t.tipo_tratamiento_id
LEFT JOIN esquemas_vacuna ev    ON ev.id = t.esquema_vacuna_id
LEFT JOIN tratamientos_animal ta ON ta.tratamiento_id = t.id
LEFT JOIN animals a             ON a.id = ta.animal_id
GROUP BY t.id, t.rancho_id, t.fecha, tt.nombre, t.producto, t.alcance, ev.nombre, t.proxima_fecha, t.notas;

-- 14. Dieta de cada animal con su periodo (LEAD)
CREATE VIEW v_racion_periodo AS
SELECT rp.racion_id,
       rp.animal_id,
       a.ranch_id                                                AS rancho_id,
       a.folio_interno,
       rp.fecha_inicio,
       rp.fecha_fin,
       rp.fecha_fin - rp.fecha_inicio                            AS dias,
       rp.es_ultima AND rp.fecha_inicio <= CURRENT_DATE          AS es_actual,
       rp.nota,
       al.id                                                     AS alimento_id,
       al.marca,
       al.nombre                                                 AS alimento,
       ti.nombre                                                 AS tipo_alimento,
       ra.kg_dia,
       ROUND(ra.kg_dia * (rp.fecha_fin - rp.fecha_inicio), 1)    AS kg_periodo,
       ROUND(ra.kg_dia * (rp.fecha_fin - rp.fecha_inicio)
             * al.precio_presentacion / al.kg_presentacion, 2)   AS costo_periodo
FROM (
    SELECT r.id AS racion_id, r.animal_id, r.fecha_inicio, r.nota,
           COALESCE(LEAD(r.fecha_inicio) OVER w, CURRENT_DATE)   AS fecha_fin,
           LEAD(r.fecha_inicio) OVER w IS NULL                   AS es_ultima
    FROM raciones r
    WINDOW w AS (PARTITION BY r.animal_id ORDER BY r.fecha_inicio)
) rp
JOIN animals a           ON a.id = rp.animal_id
JOIN raciones_alimento ra ON ra.racion_id = rp.racion_id
JOIN alimentos al        ON al.id = ra.alimento_id
JOIN tipos_alimento ti   ON ti.id = al.tipo_alimento_id;

-- 15. Consumo por marca en el rancho (GROUP BY + SUM OVER)
CREATE VIEW v_consumo_marca_rancho AS
SELECT g.rancho_id,
       g.marca,
       g.alimento,
       g.tipo_alimento,
       g.animales,
       g.kg_consumidos,
       g.costo_estimado,
       ROUND(100 * g.kg_consumidos / NULLIF(SUM(g.kg_consumidos) OVER (PARTITION BY g.rancho_id), 0), 1) AS pct_kg_rancho
FROM (
    SELECT rancho_id, marca, alimento, tipo_alimento,
           COUNT(DISTINCT animal_id) AS animales,
           SUM(kg_periodo)           AS kg_consumidos,
           SUM(costo_periodo)        AS costo_estimado
    FROM v_racion_periodo
    GROUP BY rancho_id, marca, alimento, tipo_alimento
) g;

-- 16. Qué se sirve hoy, cuántos kilos y cuánto cuesta (GROUP BY)
CREATE VIEW v_alimentacion_hoy AS
SELECT rp.rancho_id,
       rp.alimento_id,
       rp.marca,
       rp.alimento,
       rp.tipo_alimento,
       COUNT(DISTINCT rp.animal_id)                                                AS animales,
       SUM(rp.kg_dia)                                                              AS kg_dia,
       ROUND(SUM(rp.kg_dia * al.precio_presentacion / al.kg_presentacion), 2)      AS costo_dia,
       ROUND(SUM(rp.kg_dia * al.precio_presentacion / al.kg_presentacion) * 30, 2) AS costo_mes
FROM v_racion_periodo rp
JOIN alimentos al ON al.id = rp.alimento_id
JOIN animals a    ON a.id = rp.animal_id
WHERE rp.es_actual AND a.status = 'ACTIVE'
GROUP BY rp.rancho_id, rp.alimento_id, rp.marca, rp.alimento, rp.tipo_alimento;

-- 17. Catálogo para recomendar y comparar alimentos (RANK)
--     La app filtra con rancho_id IS NULL OR rancho_id = :rancho
CREATE VIEW v_catalogo_alimento AS
SELECT al.id                                                              AS alimento_id,
       al.rancho_id,
       al.marca,
       al.nombre,
       ti.nombre                                                          AS tipo_alimento,
       al.proteina_pct,
       al.energia_mcal_kg,
       al.kg_presentacion,
       al.precio_presentacion,
       ROUND(al.precio_presentacion / al.kg_presentacion, 2)              AS precio_kg,
       ROUND(al.precio_presentacion / al.kg_presentacion / NULLIF(al.proteina_pct / 100, 0), 2) AS precio_kg_proteina,
       RANK() OVER (PARTITION BY ti.nombre
                    ORDER BY al.precio_presentacion / al.kg_presentacion) AS lugar_precio_en_su_tipo,
       et.etapas_recomendadas,
       al.uso
FROM alimentos al
JOIN tipos_alimento ti ON ti.id = al.tipo_alimento_id
LEFT JOIN LATERAL (
    SELECT STRING_AGG(ep.nombre, ', ' ORDER BY ep.id) AS etapas_recomendadas
    FROM etapas_productivas ep
    WHERE ti.nombre IN ('Preiniciador', 'Iniciador', 'Crecimiento', 'Engorda', 'Pie de cría')
      AND al.proteina_pct BETWEEN ep.proteina_min_pct AND ep.proteina_max_pct
) et ON TRUE;

-- 18. Simulador de mezclas: proteína, energía, costo y suma de 100 % (GROUP BY)
CREATE VIEW v_mezcla_nutricion AS
SELECT m.id                                                              AS mezcla_id,
       m.rancho_id,
       m.nombre                                                          AS mezcla,
       ep.nombre                                                         AS etapa,
       COUNT(mi.alimento_id)                                             AS ingredientes,
       SUM(mi.porcentaje)                                                AS suma_porcentaje,
       ABS(SUM(mi.porcentaje) - 100) <= 0.5                              AS suma_correcta,
       ROUND(SUM(mi.porcentaje * COALESCE(al.proteina_pct, 0)) / NULLIF(SUM(mi.porcentaje), 0), 1)    AS proteina_pct,
       ROUND(SUM(mi.porcentaje * COALESCE(al.energia_mcal_kg, 0)) / NULLIF(SUM(mi.porcentaje), 0), 2) AS energia_mcal_kg,
       ROUND(SUM(mi.porcentaje * al.precio_presentacion / al.kg_presentacion) / NULLIF(SUM(mi.porcentaje), 0), 2) AS costo_kg,
       ep.proteina_min_pct,
       ep.proteina_max_pct
FROM mezclas m
JOIN etapas_productivas ep   ON ep.id = m.etapa_productiva_id
JOIN mezclas_ingrediente mi  ON mi.mezcla_id = m.id
JOIN alimentos al            ON al.id = mi.alimento_id
GROUP BY m.id, m.rancho_id, m.nombre, ep.nombre, ep.proteina_min_pct, ep.proteina_max_pct;

-- 19. Ventas y gastos por mes con utilidad acumulada (GROUP BY + SUM OVER)
CREATE VIEW v_finanzas_mensual AS
SELECT f.rancho_id,
       f.mes,
       f.ingresos,
       f.gastos,
       f.ingresos - f.gastos                                                       AS utilidad,
       SUM(f.ingresos - f.gastos) OVER (PARTITION BY f.rancho_id ORDER BY f.mes)   AS utilidad_acumulada
FROM (
    SELECT rancho_id,
           DATE_TRUNC('month', fecha)::DATE                          AS mes,
           COALESCE(SUM(monto) FILTER (WHERE tipo = 'INCOME'), 0)    AS ingresos,
           COALESCE(SUM(monto) FILTER (WHERE tipo = 'EXPENSE'), 0)   AS gastos
    FROM movimientos
    GROUP BY rancho_id, DATE_TRUNC('month', fecha)
) f;

-- 20. Competencias de cada animal (RANK)
CREATE VIEW v_competencias_animal AS
SELECT pa.id                                      AS participacion_id,
       a.id                                       AS animal_id,
       a.ranch_id                                 AS rancho_id,
       a.folio_interno,
       ev.fecha,
       ev.nombre                                  AS evento,
       ev.lugar,
       pa.categoria,
       rc.nombre                                  AS resultado,
       rc.puntos,
       RANK() OVER (PARTITION BY a.id ORDER BY rc.puntos DESC) = 1 AS es_mejor_resultado,
       ev.juez,
       pa.foto_s3_key,
       pa.nota
FROM participaciones pa
JOIN animals a                  ON a.id = pa.animal_id
JOIN eventos_competencia ev     ON ev.id = pa.evento_competencia_id
JOIN resultados_competencia rc  ON rc.id = pa.resultado_competencia_id;

-- 21. Tamaño del rebaño al cierre de cada uno de los últimos 12 meses
--     (los vendidos no tienen fecha de venta en V1, así que solo cuentan mientras siguen activos)
CREATE VIEW v_tamano_rebano_mensual AS
SELECT r.id                                                                    AS rancho_id,
       m.mes::DATE                                                             AS mes,
       COUNT(a.id) FILTER (
           WHERE a.birth_date <= (m.mes + INTERVAL '1 month - 1 day')::DATE
             AND (a.status = 'ACTIVE'
                  OR (a.status = 'DECEASED' AND a.mortality_date > (m.mes + INTERVAL '1 month - 1 day')::DATE))) AS animales,
       COUNT(a.id) FILTER (WHERE a.entry_reason = 'BIRTH'
                             AND DATE_TRUNC('month', a.birth_date) = m.mes)    AS nacimientos,
       COUNT(a.id) FILTER (WHERE DATE_TRUNC('month', a.mortality_date) = m.mes) AS muertes
FROM ranches r
CROSS JOIN GENERATE_SERIES(DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '11 months',
                           DATE_TRUNC('month', CURRENT_DATE), INTERVAL '1 month') AS m (mes)
LEFT JOIN animals a ON a.ranch_id = r.id
GROUP BY r.id, m.mes;

-- 22. Composición del rebaño y peso por raza (GROUP BY, vista sobre vista)
CREATE VIEW v_composicion_rebano AS
SELECT rancho_id,
       sexo,
       etapa,
       raza,
       COUNT(*)                       AS animales,
       COUNT(ultimo_peso_kg)          AS con_peso,
       ROUND(AVG(ultimo_peso_kg), 1)  AS peso_promedio_kg,
       MIN(ultimo_peso_kg)            AS peso_min_kg,
       MAX(ultimo_peso_kg)            AS peso_max_kg,
       ROUND(AVG(edad_dias))          AS edad_promedio_dias
FROM v_animal_resumen
WHERE estado = 'ACTIVE'
GROUP BY rancho_id, sexo, etapa, raza;

-- 23. Desempeño de sementales para la selección
CREATE VIEW v_desempeno_semental AS
SELECT s.animal_id, s.rancho_id, s.folio_interno, s.registro_uno, s.raza, s.edad_dias,
       s.hembras_cubiertas, s.diagnosticados, s.prenadas, s.pct_prenez,
       s.crias, s.crias_muertas, s.ganancia_crias_g,
       CASE
           WHEN s.diagnosticados >= 3 AND s.pct_prenez < 65 THEN 'CULL'
           WHEN s.edad_dias > 365 * 6 THEN 'SELL'
           ELSE 'STUD_RAM'
       END AS destino_sugerido
FROM (
    SELECT a.id AS animal_id, a.ranch_id AS rancho_id, a.folio_interno, a.registro_uno,
           a.breed AS raza, CURRENT_DATE - a.birth_date AS edad_dias,
           em.hembras_cubiertas, em.diagnosticados, em.prenadas,
           ROUND(100.0 * em.prenadas / NULLIF(em.diagnosticados, 0), 1) AS pct_prenez,
           cr.crias, cr.crias_muertas,
           (SELECT ROUND(AVG(rc.ganancia_diaria_g)) FROM v_ranking_crecimiento rc
              JOIN animals h ON h.id = rc.animal_id
             WHERE h.sire_id = a.id)                                    AS ganancia_crias_g
    FROM animals a
    CROSS JOIN LATERAL (
        SELECT COUNT(DISTINCT e.hembra_id)::INT                         AS hembras_cubiertas,
               COUNT(*) FILTER (WHERE e.diagnostico IS NOT NULL)::INT   AS diagnosticados,
               COUNT(*) FILTER (WHERE e.diagnostico = 'PREGNANT')::INT  AS prenadas
        FROM empadres e WHERE e.semental_id = a.id
    ) em
    CROSS JOIN LATERAL (
        SELECT COUNT(*)::INT                                       AS crias,
               COUNT(*) FILTER (WHERE h.status = 'DECEASED')::INT  AS crias_muertas
        FROM animals h WHERE h.sire_id = a.id
    ) cr
    WHERE a.sex = 'MALE' AND a.status = 'ACTIVE' AND a.reproductive_status = 'STUD_RAM'
) s;

-- 24. Sábana oficial de 24 columnas (A–X), DATABASE_DESIGN.md §5
CREATE VIEW v_sabana AS
SELECT a.id                                                        AS animal_id,
       a.ranch_id                                                  AS rancho_id,
       a.folio_interno                                             AS folio,                -- A
       a.adn,                                                                               -- B
       a.siniiga_chip                                              AS chip,                 -- C
       a.birth_date                                                AS fecha_nacimiento,     -- D
       a.weaning_date                                              AS fecha_destete,        -- E
       ROUND((CURRENT_DATE - a.birth_date) / 30.0, 1)              AS edad_meses,           -- F
       CURRENT_DATE - a.birth_date                                 AS edad_dias,            -- G
       p.peso_nacimiento,                                                                   -- H
       p.peso_120_dias,                                                                     -- I
       p.peso_150_dias,                                                                     -- J
       p.peso_evaluacion,                                                                   -- K
       p.circunferencia,                                                                    -- L
       p.peso_destete,                                                                      -- M
       a.parturition_type                                          AS tipo_destete,         -- N
       COALESCE(s.folio_interno, a.external_sire_folio)            AS padre,                -- O
       s.registro_uno                                              AS folio_padre_uno,      -- P
       COALESCE(d.folio_interno, a.external_dam_folio)             AS madre,                -- Q
       d.registro_uno                                              AS folio_madre_uno,      -- R
       a.breed                                                     AS raza,                 -- S
       a.parturition_type                                          AS tipo_parto,           -- T
       a.conception_method                                         AS concepcion,           -- U
       a.color,                                                                             -- V
       CASE a.sex WHEN 'FEMALE' THEN 'HEMBRA' ELSE 'MACHO' END     AS sexo,                 -- W
       CURRENT_DATE                                                AS fecha_corte,          -- X
       a.status                                                    AS estado
FROM animals a
LEFT JOIN animals s ON s.id = a.sire_id
LEFT JOIN animals d ON d.id = a.dam_id
LEFT JOIN LATERAL (
    SELECT (ARRAY_AGG(w.weight_kg ORDER BY w.measured_at DESC) FILTER (WHERE w.milestone = 'BIRTH'))[1]      AS peso_nacimiento,
           (ARRAY_AGG(w.weight_kg ORDER BY w.measured_at DESC) FILTER (WHERE w.milestone = 'DAY_120'))[1]    AS peso_120_dias,
           (ARRAY_AGG(w.weight_kg ORDER BY w.measured_at DESC) FILTER (WHERE w.milestone = 'DAY_150'))[1]    AS peso_150_dias,
           (ARRAY_AGG(w.weight_kg ORDER BY w.measured_at DESC) FILTER (WHERE w.milestone = 'EVALUATION'))[1] AS peso_evaluacion,
           (ARRAY_AGG(w.scrotal_circumference_cm ORDER BY w.measured_at DESC)
                FILTER (WHERE w.scrotal_circumference_cm IS NOT NULL))[1]                                    AS circunferencia,
           (ARRAY_AGG(w.weight_kg ORDER BY w.measured_at DESC) FILTER (WHERE w.milestone = 'WEANING'))[1]    AS peso_destete
    FROM weight_measurements w WHERE w.animal_id = a.id
) p ON TRUE;
