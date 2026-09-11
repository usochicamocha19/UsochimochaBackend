package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.EjecucionEdicionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record EjecucionEdicionResponse(
        @Schema(description = "Usuario que hizo la edición") String usuario,
        @Schema(description = "Motivo de la edición") String motivo,
        @Schema(description = "Fecha/hora de la edición") LocalDateTime editadoEn
) {
    public static EjecucionEdicionResponse fromEntity(EjecucionEdicionEntity entity) {
        return new EjecucionEdicionResponse(
                entity.getUsuario().getUsername(),
                entity.getMotivo(),
                entity.getEditadoEn());
    }
}
