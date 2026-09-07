package com.app.usochicamochabackend.update.infrastructure.entity;

import com.app.usochicamochabackend.machine.infrastructure.entity.MachineEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "oil_changes")
public class OilChangeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dateStamp;
    private Boolean hydraulicOil;
    private Boolean motorOil;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "brand")
    private BrandEntity brand;

    private Double quantity;
    private Double hourMeter;
    private Integer averageHoursChange;

    @Column(name = "hour_stamp")
    private Integer hourStamp;

    @ManyToOne
    @JoinColumn(name = "machine_id")
    private MachineEntity machine;

    @Enumerated(EnumType.STRING)
    @Column(name = "oil_type")
    private OilType oilType;

    @Builder.Default
    private Boolean status = true;
}
