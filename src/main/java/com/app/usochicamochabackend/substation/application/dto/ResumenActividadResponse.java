package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.ResumenActividadView;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResumenActividadResponse(
        @Schema(description = "ID de la actividad", example = "1") Long actividadId,
        @Schema(description = "Nombre de la actividad") String actividadNombre,
        @Schema(description = "Código de disciplina", example = "CIVIL") String disciplina,
        @Schema(description = "Total de citas programadas para esta actividad, todas las estaciones") Integer programadoAnual,
        @Schema(description = "Total de ejecuciones registradas para esta actividad") Integer ejecutadoAnual,
        @Schema(description = "Ejecuciones sin cita asociada") Integer ejecutadoNoProgramado,
        @Schema(description = "Ejecuciones de tipo MANTENIMIENTO") Integer mantenimiento,
        @Schema(description = "Ejecuciones de tipo INSPECCION") Integer inspeccion,
        @Schema(description = "Total de ejecuciones") Integer ejecutadoTotal
) {
    public static ResumenActividadResponse fromEntity(ResumenActividadView v) {
        return new ResumenActividadResponse(
                v.getActividadId(), v.getActividadNombre(), v.getDisciplina(),
                v.getProgramadoAnual(), v.getEjecutadoAnual(), v.getEjecutadoNoProgramado(),
                v.getMantenimiento(), v.getInspeccion(), v.getEjecutadoTotal());
    }
}
