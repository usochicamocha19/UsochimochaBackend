package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.fuel.application.dto.FuelPurchaseRequest;
import com.app.usochicamochabackend.fuel.application.dto.FuelPurchaseResponse;
import com.app.usochicamochabackend.fuel.application.port.RegisterFuelPurchaseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/fuel/purchases")
@RequiredArgsConstructor
public class FuelPurchaseController {

    private final RegisterFuelPurchaseUseCase registerFuelPurchaseUseCase;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FuelPurchaseResponse> registrar(
            @RequestPart("areaCosto") String areaCosto,
            @RequestPart("fuelTypeId") String fuelTypeId,
            @RequestPart("cantidad") String cantidad,
            @RequestPart("precioUnitario") String precioUnitario,
            @RequestPart(value = "descuento", required = false) String descuento,
            @RequestPart("totalIngresado") String totalIngresado,
            @RequestPart("factura") MultipartFile factura) {
        FuelPurchaseRequest request = new FuelPurchaseRequest(
                areaCosto,
                Long.valueOf(fuelTypeId),
                new BigDecimal(cantidad),
                new BigDecimal(precioUnitario),
                descuento != null && !descuento.isBlank() ? new BigDecimal(descuento) : null,
                new BigDecimal(totalIngresado));

        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        FuelPurchaseResponse response = registerFuelPurchaseUseCase.registrar(request, factura, userPrincipal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<FuelPurchaseResponse>> listar(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(registerFuelPurchaseUseCase.listar(pageable));
    }
}
