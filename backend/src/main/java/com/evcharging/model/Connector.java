package com.evcharging.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Entity
@Table(name = "connectors")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) @NotBlank
    private String connectorType;

    @Column(nullable = false)
    private Double powerKw;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private ChargingStation chargingStation;

    @JsonIgnore
    @OneToMany(mappedBy = "connector", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChargingSlot> slots;

    public Connector() {}

    public Connector(Long id, String connectorType, Double powerKw,
                     ChargingStation chargingStation, List<ChargingSlot> slots) {
        this.id = id; this.connectorType = connectorType; this.powerKw = powerKw;
        this.chargingStation = chargingStation; this.slots = slots;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }
    public Double getPowerKw() { return powerKw; }
    public void setPowerKw(Double powerKw) { this.powerKw = powerKw; }
    public ChargingStation getChargingStation() { return chargingStation; }
    public void setChargingStation(ChargingStation chargingStation) { this.chargingStation = chargingStation; }
    public List<ChargingSlot> getSlots() { return slots; }
    public void setSlots(List<ChargingSlot> slots) { this.slots = slots; }
}