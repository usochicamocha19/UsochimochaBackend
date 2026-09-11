package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.IndicadorEstacionView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicadorEstacionViewRepository extends JpaRepository<IndicadorEstacionView, Long> {

    List<IndicadorEstacionView> findAllByOrderByEstacionNombreAsc();
}
