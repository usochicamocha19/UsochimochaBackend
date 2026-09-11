package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.fuel.application.dto.AssetFuelConfigRequest;
import com.app.usochicamochabackend.fuel.application.dto.AssetFuelConfigResponse;
import com.app.usochicamochabackend.fuel.application.port.ManageAssetFuelConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel/config")
@RequiredArgsConstructor
public class AssetFuelConfigController {

    private final ManageAssetFuelConfigUseCase manageAssetFuelConfigUseCase;

    @GetMapping
    public ResponseEntity<List<AssetFuelConfigResponse>> listar() {
        return ResponseEntity.ok(manageAssetFuelConfigUseCase.listar());
    }

    @PutMapping("/vehicle/{vehicleId}")
    public ResponseEntity<AssetFuelConfigResponse> configurarVehiculo(
            @PathVariable Integer vehicleId, @RequestBody AssetFuelConfigRequest request) {
        return ResponseEntity.ok(manageAssetFuelConfigUseCase.configurarVehiculo(vehicleId, request));
    }

    @PutMapping("/machine/{machineId}")
    public ResponseEntity<AssetFuelConfigResponse> configurarMaquina(
            @PathVariable Long machineId, @RequestBody AssetFuelConfigRequest request) {
        return ResponseEntity.ok(manageAssetFuelConfigUseCase.configurarMaquina(machineId, request));
    }
}
