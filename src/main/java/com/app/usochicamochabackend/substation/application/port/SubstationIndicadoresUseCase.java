package com.app.usochicamochabackend.substation.application.port;

import com.app.usochicamochabackend.substation.application.dto.CumplimientoResponse;
import com.app.usochicamochabackend.substation.application.dto.IndicadorEstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ResumenActividadResponse;

import java.util.List;

public interface SubstationIndicadoresUseCase {

    /** Cumplimiento de todas las estaciones para un mes+año+disciplina (una fila por cita del cronograma). */
    List<CumplimientoResponse> cumplimientoPorMes(Integer anio, Integer mes, String disciplina);

    /** Cumplimiento de una estación a lo largo del año, mes a mes. */
    List<CumplimientoResponse> cumplimientoPorEstacion(Long estacionId, Integer anio, String disciplina);

    /** Resumen de cumplimiento por estación (% cumplimiento, desglose programado/no programado, mantenimiento/inspección). */
    List<IndicadorEstacionResponse> indicadoresPorEstacion();

    /** Resumen anual por actividad, todas las estaciones — para ver qué actividad se salta más en la red. */
    List<ResumenActividadResponse> resumenPorActividad(String disciplina);
}
