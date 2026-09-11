package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.CumplimientoView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CumplimientoViewRepository extends JpaRepository<CumplimientoView, Long> {

    List<CumplimientoView> findByEstacionIdAndAnioAndDisciplinaOrderByMesAsc(Long estacionId, Integer anio, String disciplina);

    List<CumplimientoView> findByAnioAndMesAndDisciplinaOrderByEstacionNombreAsc(Integer anio, Integer mes, String disciplina);
}
