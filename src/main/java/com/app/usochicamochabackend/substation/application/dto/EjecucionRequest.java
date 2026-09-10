package com.app.usochicamochabackend.substation.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

public record EjecucionRequest(
        @Schema(description = "Fecha de la visita", example = "2026-09-07") LocalDate fecha,
        @Schema(description = "Mes de ejecución (1-12), editable si el registro es tardío", example = "9") Integer mesEjecucion,
        @Schema(description = "Semana de ejecución (1-4)", example = "2") Integer semanaEjecucion,
        @Schema(description = "ID de la estación visitada", example = "1") Long estacionId,
        @Schema(description = "Disciplina", example = "CIVIL") String disciplina,
        @Schema(description = "Tipo de mantenimiento", example = "PREVENTIVO") String tipoMantenimiento,
        @Schema(description = "Tipo de actividad", example = "MANTENIMIENTO") String tipoActividad,
        @Schema(description = "ID de la actividad del catálogo; null si no vino de una cita del cronograma") Long actividadId,
        @Schema(description = "ID de la cita de programación; null si no vino de una cita del cronograma") Long programacionId,
        @Schema(description = "Motivo cuando actividadId es null: NO_PROGRAMADO u OTRO") String motivoNoCatalogado,
        @Schema(description = "Resultado de la visita", example = "CONFORME") String resultado,
        @Schema(description = "Observaciones de la actividad realizada") String observaciones,
        @Schema(description = "Descripción libre; requerida cuando actividadId es null") String descripcionLibre,
        @Schema(description = "UUID generado por el cliente para idempotencia offline") UUID uuidCliente
) {
}
