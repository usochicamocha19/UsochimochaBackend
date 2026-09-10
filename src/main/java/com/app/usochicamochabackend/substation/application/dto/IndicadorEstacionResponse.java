package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.IndicadorEstacionView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record IndicadorEstacionResponse(
        @Schema(description = "ID de la estación", example = "1") Long estacionId,
        @Schema(description = "Nombre de la estación", example = "Duitama") String estacionNombre,
        @Schema(description = "Tipo de estación", example = "BOMBEO") String estacionTipo,
        @Schema(description = "Total de citas programadas") Integer programado,
        @Schema(description = "Citas con al menos una ejecución registrada") Integer cumple,
        @Schema(description = "Citas sin ninguna ejecución registrada") Integer noCumple,
        @Schema(description = "Porcentaje de cumplimiento, 0-100") BigDecimal porcentajeCumplimiento,
        @Schema(description = "Ejecuciones que correspondían a una cita del cronograma") Integer ejecutadoProgramado,
        @Schema(description = "Ejecuciones sin cita asociada (no previstas u otras)") Integer ejecutadoNoProgramado,
        @Schema(description = "Ejecuciones de tipo MANTENIMIENTO") Integer ejecutadoMantenimiento,
        @Schema(description = "Ejecuciones de tipo INSPECCION") Integer ejecutadoInspeccion,
        @Schema(description = "Total de ejecuciones registradas en la estación") Integer ejecutadoTotal
) {
    public static IndicadorEstacionResponse fromEntity(IndicadorEstacionView v) {
        return new IndicadorEstacionResponse(
                v.getEstacionId(), v.getEstacionNombre(), v.getEstacionTipo(),
                v.getProgramado(), v.getCumple(), v.getNoCumple(), v.getPorcentajeCumplimiento(),
                v.getEjecutadoProgramado(), v.getEjecutadoNoProgramado(),
                v.getEjecutadoMantenimiento(), v.getEjecutadoInspeccion(), v.getEjecutadoTotal());
    }
}
