package com.app.usochicamochabackend.substation.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Mapea la vista {@code v_mant_resumen_actividad} (V37) — solo lectura, rollup anual por actividad. */
@Data
@Entity
@Immutable
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "v_mant_resumen_actividad")
public class ResumenActividadView {

    @Id
    @Column(name = "actividad_id")
    private Long actividadId;

    @Column(name = "actividad_nombre")
    private String actividadNombre;

    private String disciplina;

    @Column(name = "programado_anual")
    private Integer programadoAnual;

    @Column(name = "ejecutado_anual")
    private Integer ejecutadoAnual;

    @Column(name = "ejecutado_no_programado")
    private Integer ejecutadoNoProgramado;

    private Integer mantenimiento;

    private Integer inspeccion;

    @Column(name = "ejecutado_total")
    private Integer ejecutadoTotal;
}
