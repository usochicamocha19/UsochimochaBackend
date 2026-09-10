package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.ActividadEntity;
import io.swagger.v3.oas.annotations.media.Schema;

public record ActividadResponse(
        @Schema(description = "ID de la actividad", example = "1") Long id,
        @Schema(description = "Nombre de la actividad", example = "Pintura muros Estaciones (segun estado)") String nombre,
        @Schema(description = "Disciplina", example = "CIVIL") String disciplina
) {
    public static ActividadResponse fromEntity(ActividadEntity entity) {
        return new ActividadResponse(entity.getId(), entity.getNombre(), entity.getDisciplina().getCodigo());
    }
}
