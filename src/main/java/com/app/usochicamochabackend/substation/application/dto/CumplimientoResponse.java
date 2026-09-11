package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.CumplimientoView;
import io.swagger.v3.oas.annotations.media.Schema;

public record CumplimientoResponse(
        @Schema(description = "ID de la cita de programación", example = "1") Long programacionId,
        @Schema(description = "Año", example = "2026") Integer anio,
        @Schema(description = "Mes (1-12)", example = "2") Integer mes,
        @Schema(description = "ID de la estación", example = "1") Long estacionId,
        @Schema(description = "Nombre de la estación", example = "Duitama") String estacionNombre,
        @Schema(description = "Tipo de estación", example = "BOMBEO") String estacionTipo,
        @Schema(description = "ID de la actividad", example = "1") Long actividadId,
        @Schema(description = "Nombre de la actividad") String actividadNombre,
        @Schema(description = "Código de disciplina", example = "CIVIL") String disciplina,
        @Schema(description = "Cantidad de ejecuciones registradas para esta cita", example = "1") Integer ejecutado,
        @Schema(description = "true si hay al menos una ejecución registrada para esta cita") Boolean cumple
) {
    public static CumplimientoResponse fromEntity(CumplimientoView v) {
        return new CumplimientoResponse(
                v.getProgramacionId(), v.getAnio(), v.getMes(),
                v.getEstacionId(), v.getEstacionNombre(), v.getEstacionTipo(),
                v.getActividadId(), v.getActividadNombre(), v.getDisciplina(),
                v.getEjecutado(), v.getCumple());
    }
}
