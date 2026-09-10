package com.app.usochicamochabackend.substation.application.port;

import com.app.usochicamochabackend.substation.application.dto.ActividadResponse;
import com.app.usochicamochabackend.substation.application.dto.EstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ProgramacionResponse;

import java.util.List;

public interface SubstationCatalogUseCase {

    List<EstacionResponse> listarEstaciones();

    /** Actividades del catálogo habilitadas para captura móvil, para una disciplina. */
    List<ActividadResponse> listarActividadesCapturables(String disciplina);

    /** Citas del cronograma para una estación+mes+disciplina — lo que el técnico ve al elegir actividad. */
    List<ProgramacionResponse> listarProgramacion(Long estacionId, Integer anio, Integer mes, String disciplina);
}
