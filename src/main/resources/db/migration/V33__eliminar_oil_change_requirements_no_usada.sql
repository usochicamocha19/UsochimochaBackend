-- ============================================================
-- V33 — Retiro de oil_change_requirements y sus columnas de enlace
-- Propuesta (V7) para vincular cambios de aceite a un catálogo de
-- intervalos recomendados por tipo de aceite/activo. Nunca se conectó:
-- OilChangeRequirementRepository sin consumidores, OilChangeEntity.
-- requirement siempre NULL (nada lo setea al crear un cambio de aceite),
-- y MachineOilChangeResponseDTO (que lo leía) tampoco lo usaba ningún
-- controlador real. id_requirement/percentage_used en vehicle_oil_changes
-- nunca se llegaron a mapear en Java — muertas desde que se crearon en V7.
-- No confundir con oil_type (sí en uso, no se toca).
-- ============================================================

ALTER TABLE oil_changes DROP COLUMN IF EXISTS id_requirement;
ALTER TABLE oil_changes DROP COLUMN IF EXISTS percentage_used;

ALTER TABLE vehicle_oil_changes DROP COLUMN IF EXISTS id_requirement;
ALTER TABLE vehicle_oil_changes DROP COLUMN IF EXISTS percentage_used;

DROP TABLE IF EXISTS oil_change_requirements;
