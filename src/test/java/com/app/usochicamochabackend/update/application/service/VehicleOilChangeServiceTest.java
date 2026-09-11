package com.app.usochicamochabackend.update.application.service;

import com.app.usochicamochabackend.actions.application.port.SaveActionUseCase;
import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.exception.ResourceNotFoundException;
import com.app.usochicamochabackend.notifications.application.PreventiveAlertCalculationService;
import com.app.usochicamochabackend.update.application.dto.VehicleOilChangeHistoryDTO;
import com.app.usochicamochabackend.update.application.dto.VehicleOilChangeRequest;
import com.app.usochicamochabackend.update.infrastructure.entity.BrandEntity;
import com.app.usochicamochabackend.update.infrastructure.entity.VehicleOilChangeEntity;
import com.app.usochicamochabackend.update.infrastructure.repository.BrandRepository;
import com.app.usochicamochabackend.update.infrastructure.repository.VehicleOilChangeRepository;
import com.app.usochicamochabackend.vehicle.infrastructure.entity.VehicleEntity;
import com.app.usochicamochabackend.vehicle.infrastructure.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleOilChangeServiceTest {

    @Mock
    private VehicleOilChangeRepository vehicleOilChangeRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private PreventiveAlertCalculationService preventiveAlertCalculationService;

    @Mock
    private SaveActionUseCase saveActionUseCase;

    private VehicleOilChangeService vehicleOilChangeService;

    @BeforeEach
    void setUp() {
        OilChangeValidationService validationService = new OilChangeValidationService();
        vehicleOilChangeService = new VehicleOilChangeService(
                vehicleOilChangeRepository, vehicleRepository, brandRepository, validationService,
                preventiveAlertCalculationService, saveActionUseCase);

        UserPrincipal principal = new UserPrincipal(1L, "tecnico");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private VehicleOilChangeRequest requestValido() {
        return new VehicleOilChangeRequest("ABC123", LocalDateTime.now(), "MOTOR", 2L,
                4.0, 15000, 5000, true);
    }

    private VehicleEntity vehiculo(int km) {
        return VehicleEntity.builder().idVehiculo(5).placa("ABC123").kilometrajeActual(km).build();
    }

    private VehicleOilChangeEntity existente() {
        return VehicleOilChangeEntity.builder()
                .id(1L)
                .vehicle(vehiculo(10000))
                .quantity(3.5)
                .kmAtChange(10000)
                .intervalKm(5000)
                .status(true)
                .build();
    }

    // ---- actualizar() ----

    @Test
    void actualizar_ConIdInexistente_LanzaResourceNotFound() {
        when(vehicleOilChangeRepository.findByIdAndStatus(99L, true)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> vehicleOilChangeService.actualizar(99L, requestValido()));

        verify(vehicleOilChangeRepository, never()).save(any());
    }

    @Test
    void actualizar_ConKmMayorAlActual_SubeKilometrajeActualDelVehiculo() {
        VehicleOilChangeEntity entity = existente();
        when(vehicleOilChangeRepository.findByIdAndStatus(1L, true)).thenReturn(Optional.of(entity));
        when(brandRepository.findById(2L)).thenReturn(Optional.of(BrandEntity.builder().id(2L).name("Mobil").build()));

        VehicleOilChangeRequest request = new VehicleOilChangeRequest("ABC123", LocalDateTime.now(), "MOTOR", 2L,
                4.0, 20000, 5000, true);

        vehicleOilChangeService.actualizar(1L, request);

        assertEquals(20000, entity.getVehicle().getKilometrajeActual());
        verify(vehicleOilChangeRepository).save(entity);
        verify(preventiveAlertCalculationService).calculateAndEmitAlerts();
    }

    @Test
    void actualizar_ConKmMenorAlActual_NoRetrocedeElKilometrajeDelVehiculo() {
        VehicleOilChangeEntity entity = existente(); // vehículo ya en 10000
        when(vehicleOilChangeRepository.findByIdAndStatus(1L, true)).thenReturn(Optional.of(entity));
        when(brandRepository.findById(2L)).thenReturn(Optional.of(BrandEntity.builder().id(2L).name("Mobil").build()));

        VehicleOilChangeRequest request = new VehicleOilChangeRequest("ABC123", LocalDateTime.now(), "MOTOR", 2L,
                4.0, 8000, 5000, true);

        vehicleOilChangeService.actualizar(1L, request);

        assertEquals(10000, entity.getVehicle().getKilometrajeActual());
        assertEquals(8000, entity.getKmAtChange());
        verify(vehicleRepository, never()).save(any());
    }

    // ---- eliminar() ----

    @Test
    void eliminar_ConIdInexistente_LanzaResourceNotFound() {
        when(vehicleOilChangeRepository.findByIdAndStatus(99L, true)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> vehicleOilChangeService.eliminar(99L));
    }

    @Test
    void eliminar_MarcaStatusFalseYNoTocaElKilometrajeDelVehiculo() {
        VehicleOilChangeEntity entity = existente();
        when(vehicleOilChangeRepository.findByIdAndStatus(1L, true)).thenReturn(Optional.of(entity));

        vehicleOilChangeService.eliminar(1L);

        assertEquals(false, entity.getStatus());
        verify(vehicleOilChangeRepository).save(entity);
        verify(vehicleRepository, never()).save(any());
        verify(preventiveAlertCalculationService).calculateAndEmitAlerts();
    }

    // ---- getHistoryByPlaca() incluye los campos nuevos ----

    @Test
    void getHistoryByPlaca_IncluyeOilTypeYBrandIdParaPrecargarLaEdicion() {
        VehicleOilChangeEntity entity = existente();
        entity.setOilType(com.app.usochicamochabackend.update.infrastructure.entity.OilType.MOTOR);
        entity.setBrand(BrandEntity.builder().id(2L).name("Mobil").build());
        when(vehicleOilChangeRepository.findAllByPlacaOrderByDateStampDesc("ABC123")).thenReturn(List.of(entity));

        List<VehicleOilChangeHistoryDTO> historial = vehicleOilChangeService.getHistoryByPlaca("ABC123");

        assertEquals(1, historial.size());
        assertEquals("MOTOR", historial.get(0).oilType());
        assertEquals(2L, historial.get(0).brandId());
        assertEquals("Mobil", historial.get(0).brandName());
    }
}
