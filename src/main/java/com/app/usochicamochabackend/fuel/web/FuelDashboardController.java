package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.fuel.application.dto.FuelBudgetProjectionRow;
import com.app.usochicamochabackend.fuel.application.dto.FuelDashboardResponse;
import com.app.usochicamochabackend.fuel.application.dto.FuelTrendResponse;
import com.app.usochicamochabackend.fuel.application.port.GetFuelDashboardUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel/dashboard")
@RequiredArgsConstructor
public class FuelDashboardController {

    private final GetFuelDashboardUseCase getFuelDashboardUseCase;

    @GetMapping("/financiero")
    public ResponseEntity<FuelDashboardResponse> financiero(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(getFuelDashboardUseCase.obtenerDashboard(fechaInicio, fechaFin));
    }

    @GetMapping("/tendencia")
    public ResponseEntity<List<FuelTrendResponse>> tendencia(
            @RequestParam(required = false) Integer meses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(getFuelDashboardUseCase.obtenerTendencia(meses, fechaFin));
    }

    @GetMapping("/proyeccion")
    public ResponseEntity<List<FuelBudgetProjectionRow>> proyeccion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(getFuelDashboardUseCase.obtenerProyeccionPresupuestal(fechaFin));
    }
}
