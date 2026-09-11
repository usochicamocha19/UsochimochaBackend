-- Vistas de indicadores/cumplimiento para el módulo de subestaciones.
--
-- Origen: reemplazan 3 hojas de cálculo del Excel que el distrito llevaba a mano
-- (CONTROL_CUMPLIMIENTO, INDICADORES, RESUMEN_ANUAL) — misma forma de datos, pero
-- calculadas en vivo desde mant_programacion/mant_ejecucion en vez de re-tabuladas
-- manualmente. No se filtran a una sola disciplina: mant_programacion ya trae el
-- cronograma completo de las 3 disciplinas (ver V30), aunque solo Civil capture
-- ejecuciones desde el móvil por ahora.
--
-- v_mant_indicadores_estacion y v_mant_resumen_actividad se apoyan en
-- v_mant_cumplimiento con CTEs separados (no un solo JOIN múltiple) para evitar
-- el fan-out clásico de dos LEFT JOIN independientes sobre la misma fila padre.

CREATE VIEW v_mant_cumplimiento AS
SELECT
    p.id                AS programacion_id,
    p.anio,
    p.mes,
    e.id                AS estacion_id,
    e.nombre            AS estacion_nombre,
    e.tipo              AS estacion_tipo,
    a.id                AS actividad_id,
    a.nombre            AS actividad_nombre,
    d.codigo            AS disciplina,
    COUNT(ej.id)        AS ejecutado,
    (COUNT(ej.id) > 0)  AS cumple
FROM mant_programacion p
JOIN mant_estacion e ON e.id = p.estacion_id
JOIN mant_actividad a ON a.id = p.actividad_id
JOIN mant_disciplina d ON d.id = a.disciplina_id
LEFT JOIN mant_ejecucion ej ON ej.programacion_id = p.id
WHERE p.status = TRUE
GROUP BY p.id, p.anio, p.mes, e.id, e.nombre, e.tipo, a.id, a.nombre, d.codigo;

CREATE VIEW v_mant_indicadores_estacion AS
WITH cumplimiento AS (
    SELECT estacion_id,
           COUNT(*)                          AS programado,
           COUNT(*) FILTER (WHERE cumple)    AS cumple,
           COUNT(*) FILTER (WHERE NOT cumple) AS no_cumple
    FROM v_mant_cumplimiento
    GROUP BY estacion_id
),
ejecuciones AS (
    SELECT estacion_id,
           COUNT(*) FILTER (WHERE es_programada)                    AS ejecutado_programado,
           COUNT(*) FILTER (WHERE NOT es_programada)                AS ejecutado_no_programado,
           COUNT(*) FILTER (WHERE tipo_actividad = 'MANTENIMIENTO') AS ejecutado_mantenimiento,
           COUNT(*) FILTER (WHERE tipo_actividad = 'INSPECCION')    AS ejecutado_inspeccion,
           COUNT(*)                                                  AS ejecutado_total
    FROM mant_ejecucion
    GROUP BY estacion_id
)
SELECT
    e.id                                                                       AS estacion_id,
    e.nombre                                                                   AS estacion_nombre,
    e.tipo                                                                     AS estacion_tipo,
    COALESCE(c.programado, 0)                                                  AS programado,
    COALESCE(c.cumple, 0)                                                      AS cumple,
    COALESCE(c.no_cumple, 0)                                                   AS no_cumple,
    ROUND(100.0 * COALESCE(c.cumple, 0) / NULLIF(c.programado, 0), 1)          AS porcentaje_cumplimiento,
    COALESCE(ej.ejecutado_programado, 0)                                       AS ejecutado_programado,
    COALESCE(ej.ejecutado_no_programado, 0)                                    AS ejecutado_no_programado,
    COALESCE(ej.ejecutado_mantenimiento, 0)                                    AS ejecutado_mantenimiento,
    COALESCE(ej.ejecutado_inspeccion, 0)                                       AS ejecutado_inspeccion,
    COALESCE(ej.ejecutado_total, 0)                                            AS ejecutado_total
FROM mant_estacion e
LEFT JOIN cumplimiento c ON c.estacion_id = e.id
LEFT JOIN ejecuciones ej ON ej.estacion_id = e.id
WHERE e.status = TRUE;

CREATE VIEW v_mant_resumen_actividad AS
WITH cumplimiento AS (
    SELECT actividad_id,
           COUNT(*)                       AS programado,
           COUNT(*) FILTER (WHERE cumple) AS cumple
    FROM v_mant_cumplimiento
    GROUP BY actividad_id
),
ejecuciones AS (
    SELECT actividad_id,
           COUNT(*) FILTER (WHERE NOT es_programada)                    AS ejecutado_no_programado,
           COUNT(*) FILTER (WHERE tipo_actividad = 'MANTENIMIENTO')     AS mantenimiento,
           COUNT(*) FILTER (WHERE tipo_actividad = 'INSPECCION')        AS inspeccion,
           COUNT(*)                                                      AS ejecutado_total
    FROM mant_ejecucion
    WHERE actividad_id IS NOT NULL
    GROUP BY actividad_id
)
SELECT
    a.id                                     AS actividad_id,
    a.nombre                                 AS actividad_nombre,
    d.codigo                                 AS disciplina,
    COALESCE(c.programado, 0)                AS programado_anual,
    COALESCE(ej.ejecutado_total, 0)          AS ejecutado_anual,
    COALESCE(ej.ejecutado_no_programado, 0)  AS ejecutado_no_programado,
    COALESCE(ej.mantenimiento, 0)            AS mantenimiento,
    COALESCE(ej.inspeccion, 0)               AS inspeccion,
    COALESCE(ej.ejecutado_total, 0)          AS ejecutado_total
FROM mant_actividad a
JOIN mant_disciplina d ON d.id = a.disciplina_id
LEFT JOIN cumplimiento c ON c.actividad_id = a.id
LEFT JOIN ejecuciones ej ON ej.actividad_id = a.id
WHERE a.status = TRUE;
