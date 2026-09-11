package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.ResumenActividadView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumenActividadViewRepository extends JpaRepository<ResumenActividadView, Long> {

    List<ResumenActividadView> findByDisciplinaOrderByActividadNombreAsc(String disciplina);
}
