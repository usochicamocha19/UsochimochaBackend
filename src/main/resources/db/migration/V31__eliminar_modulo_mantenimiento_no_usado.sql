-- ============================================================
-- V31 — Retiro del módulo `maintenance` (tabla mantenimientos)
-- No tenía consumidores reales: ningún endpoint de /api/v1/maintenance
-- era llamado desde el frontend web, y el móvil sincroniza cambios de
-- aceite contra /api/v1/oil/** (módulo `update`), no contra este.
-- Reemplazado de facto por oil_changes (módulo `update`) desde hace
-- meses sin que se limpiara el código viejo. Sin FKs de otras tablas
-- hacia mantenimientos, se puede retirar sin efectos colaterales.
-- ============================================================

DROP TABLE IF EXISTS mantenimientos;
