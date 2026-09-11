package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.fuel.application.dto.FuelDistributionResponse;
import com.app.usochicamochabackend.fuel.application.port.GetFuelDistributionUseCase;
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
@RequestMapping("/api/v1/fuel/distribucion")
@RequiredArgsConstructor
public class FuelDistributionController {

    private final GetFuelDistributionUseCase getFuelDistributionUseCase;

    @GetMapping
    public ResponseEntity<FuelDistributionResponse> distribucion(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(getFuelDistributionUseCase.obtenerDistribucion(area, fechaInicio, fechaFin));
    }
}
