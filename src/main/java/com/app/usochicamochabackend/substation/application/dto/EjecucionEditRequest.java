package com.app.usochicamochabackend.substation.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Corrección de datos de una ejecución ya registrada. La estación, la disciplina,
 * la cita de programación y el uuidCliente NO son editables aquí: editar es
 * corregir lo que se reportó de la visita, no reasignarla a otra estación/cita.
 */
public record EjecucionEditRequest(
        @Schema(description = "Fecha de la visita", example = "2026-09-07") LocalDate fecha,
        @Schema(description = "Mes de ejecución (1-12)", example = "9") Integer mesEjecucion,
        @Schema(description = "Semana de ejecución (1-4)", example = "2") Integer semanaEjecucion,
        @Schema(description = "Tipo de mantenimiento", example = "PREVENTIVO") String tipoMantenimiento,
        @Schema(description = "Tipo de actividad", example = "MANTENIMIENTO") String tipoActividad,
        @Schema(description = "ID de la actividad del catálogo; null si no vino de una cita del cronograma") Long actividadId,
        @Schema(description = "Motivo cuando actividadId es null") String motivoNoCatalogado,
        @Schema(description = "Resultado de la visita", example = "CONFORME") String resultado,
        @Schema(description = "Observaciones de la actividad realizada") String observaciones,
        @Schema(description = "Descripción libre; requerida cuando actividadId es null") String descripcionLibre,
        @Schema(description = "Motivo de la edición, mínimo 15 caracteres. Queda en el historial del registro.")
        String motivoEdicion
) {
}
