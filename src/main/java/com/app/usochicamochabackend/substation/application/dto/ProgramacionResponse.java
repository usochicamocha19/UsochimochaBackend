package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.ProgramacionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProgramacionResponse(
        @Schema(description = "ID de la cita de programación", example = "1") Long id,
        @Schema(description = "Año", example = "2026") Integer anio,
        @Schema(description = "Mes (1-12)", example = "9") Integer mes,
        @Schema(description = "ID de la estación", example = "1") Long estacionId,
        @Schema(description = "ID de la actividad", example = "1") Long actividadId,
        @Schema(description = "Nombre de la actividad", example = "Pintura muros Estaciones (segun estado)") String actividadNombre
) {
    public static ProgramacionResponse fromEntity(ProgramacionEntity entity) {
        return new ProgramacionResponse(
                entity.getId(),
                entity.getAnio(),
                entity.getMes(),
                entity.getEstacion().getId(),
                entity.getActividad().getId(),
                entity.getActividad().getNombre());
    }
}
