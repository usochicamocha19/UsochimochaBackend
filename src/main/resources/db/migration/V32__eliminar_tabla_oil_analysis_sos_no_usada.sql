-- ============================================================
-- V32 — Retiro de oil_analysis_sos (creada en V7, nunca conectada)
-- Tenía entidad/repositorio/DTOs en el backend y su contraparte en el
-- móvil (OilAnalysisSosEntity/Dao), pero sin controlador ni pantalla
-- que la usara en ningún lado — mismo patrón que V31, un nivel más
-- adentro. Sin FKs de otras tablas hacia oil_analysis_sos.
-- No confundir con oil_change_requirements (también creada en V7):
-- es una tabla distinta, con su propia migración de retiro (V33) —
-- no se toca aquí.
-- ============================================================

DROP TABLE IF EXISTS oil_analysis_sos;
