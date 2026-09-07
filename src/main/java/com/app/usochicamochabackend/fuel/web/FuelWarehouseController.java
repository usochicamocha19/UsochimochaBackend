package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.fuel.application.dto.FuelWarehouseBalanceResponse;
import com.app.usochicamochabackend.fuel.application.dto.FuelWarehouseMovementsResponse;
import com.app.usochicamochabackend.fuel.application.port.GetFuelWarehouseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/fuel/almacen")
@RequiredArgsConstructor
public class FuelWarehouseController {

    private final GetFuelWarehouseUseCase getFuelWarehouseUseCase;

    @GetMapping("/saldos")
    public ResponseEntity<FuelWarehouseBalanceResponse> saldos() {
        return ResponseEntity.ok(getFuelWarehouseUseCase.obtenerSaldos());
    }

    @GetMapping("/movimientos")
    public ResponseEntity<FuelWarehouseMovementsResponse> movimientos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(getFuelWarehouseUseCase.obtenerMovimientos(fechaInicio, fechaFin));
    }
}
