package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.fuel.application.dto.FuelMonthlyDiscountRequest;
import com.app.usochicamochabackend.fuel.application.dto.FuelMonthlyDiscountResponse;
import com.app.usochicamochabackend.fuel.application.port.ManageFuelMonthlyDiscountUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel/monthly-discount")
@RequiredArgsConstructor
public class FuelMonthlyDiscountController {

    private final ManageFuelMonthlyDiscountUseCase manageFuelMonthlyDiscountUseCase;

    @PostMapping
    public ResponseEntity<FuelMonthlyDiscountResponse> registrar(@RequestBody FuelMonthlyDiscountRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        FuelMonthlyDiscountResponse response = manageFuelMonthlyDiscountUseCase.registrar(request, userPrincipal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FuelMonthlyDiscountResponse>> listar() {
        return ResponseEntity.ok(manageFuelMonthlyDiscountUseCase.listar());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        manageFuelMonthlyDiscountUseCase.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
