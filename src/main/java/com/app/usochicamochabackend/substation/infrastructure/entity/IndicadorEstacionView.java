package com.app.usochicamochabackend.substation.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Mapea la vista {@code v_mant_indicadores_estacion} (V37) — solo lectura, resumen por estación. */
@Data
@Entity
@Immutable
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "v_mant_indicadores_estacion")
public class IndicadorEstacionView {

    @Id
    @Column(name = "estacion_id")
    private Long estacionId;

    @Column(name = "estacion_nombre")
    private String estacionNombre;

    @Column(name = "estacion_tipo")
    private String estacionTipo;

    private Integer programado;

    private Integer cumple;

    @Column(name = "no_cumple")
    private Integer noCumple;

    @Column(name = "porcentaje_cumplimiento")
    private java.math.BigDecimal porcentajeCumplimiento;

    @Column(name = "ejecutado_programado")
    private Integer ejecutadoProgramado;

    @Column(name = "ejecutado_no_programado")
    private Integer ejecutadoNoProgramado;

    @Column(name = "ejecutado_mantenimiento")
    private Integer ejecutadoMantenimiento;

    @Column(name = "ejecutado_inspeccion")
    private Integer ejecutadoInspeccion;

    @Column(name = "ejecutado_total")
    private Integer ejecutadoTotal;
}
