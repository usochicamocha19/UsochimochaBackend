package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.ActividadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActividadRepository extends JpaRepository<ActividadEntity, Long> {
    List<ActividadEntity> findByDisciplina_CodigoAndCapturaMovilHabilitadaTrueAndStatusTrueOrderByNombreAsc(String disciplinaCodigo);
}
