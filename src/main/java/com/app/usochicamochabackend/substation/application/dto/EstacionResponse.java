package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.EstacionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

public record EstacionResponse(
        @Schema(description = "ID de la estación", example = "1") Long id,
        @Schema(description = "Nombre de la estación", example = "Duitama") String nombre,
        @Schema(description = "Tipo de estación", example = "BOMBEO") String tipo,
        @Schema(description = "Frecuencia base de mantenimiento", example = "TRIMESTRAL") String frecuenciaBase
) {
    public static EstacionResponse fromEntity(EstacionEntity entity) {
        return new EstacionResponse(entity.getId(), entity.getNombre(), entity.getTipo(), entity.getFrecuenciaBase());
    }
}
