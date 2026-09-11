-- ============================================================
-- V35 — Corrige captura_movil_habilitada en mant_actividad y agrega
-- las 2 actividades civiles que faltaban en el seed.
--
-- V30 (versión aplicada originalmente en bases ya existentes) traía
-- captura_movil_habilitada=TRUE en ELECTRICO — reflejaba el alcance
-- original del plan, antes del pivote a Civil (2026-09-04). El archivo
-- V30 ya se corrigió en el código fuente para reflejar Civil, pero
-- Flyway no vuelve a ejecutar una migración ya aplicada (por diseño:
-- nunca se edita una migración ya corrida en una base real) — así que
-- cualquier base que ya tuviera V30 aplicada con el seed viejo necesita
-- esta migración nueva para llegar al mismo estado.
-- Ver SUBESTACIONES_PENDIENTE.md y la memoria de esta sesión para el
-- contexto completo del pivote de alcance.
-- ============================================================

UPDATE mant_actividad SET captura_movil_habilitada = TRUE  WHERE disciplina = 'CIVIL';
UPDATE mant_actividad SET captura_movil_habilitada = FALSE WHERE disciplina IN ('ELECTRICO', 'ELECTROMECANICO');

INSERT INTO mant_actividad (nombre, disciplina, captura_movil_habilitada)
SELECT 'Inspección maquinaria amarilla', 'CIVIL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM mant_actividad WHERE nombre = 'Inspección maquinaria amarilla' AND disciplina = 'CIVIL'
);

INSERT INTO mant_actividad (nombre, disciplina, captura_movil_habilitada)
SELECT 'Inspección vehículos y motocicletas', 'CIVIL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM mant_actividad WHERE nombre = 'Inspección vehículos y motocicletas' AND disciplina = 'CIVIL'
);
