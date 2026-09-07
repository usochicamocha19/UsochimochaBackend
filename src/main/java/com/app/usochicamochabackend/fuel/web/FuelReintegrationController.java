package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.fuel.application.dto.FuelReintegrationRequest;
import com.app.usochicamochabackend.fuel.application.dto.FuelReintegrationResponse;
import com.app.usochicamochabackend.fuel.application.port.RegisterFuelReintegrationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fuel/reintegros")
@RequiredArgsConstructor
public class FuelReintegrationController {

    private final RegisterFuelReintegrationUseCase registerFuelReintegrationUseCase;

    @PostMapping
    public ResponseEntity<FuelReintegrationResponse> registrar(@RequestBody FuelReintegrationRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        FuelReintegrationResponse response = registerFuelReintegrationUseCase.registrar(request, userPrincipal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
