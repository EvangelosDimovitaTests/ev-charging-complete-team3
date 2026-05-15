package com.evcharging.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Entity
@Table(name = "charging_stations")
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) @NotBlank
    private String name;

    @Column(nullable = false) @NotBlank
    private String address;

    @Column(nullable = false) @NotNull
    private Double latitude;

    @Column(nullable = false) @NotNull
    private Double longitude;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @JsonIgnore
    @OneToMany(mappedBy = "chargingStation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Connector> connectors;

    public ChargingStation() {}

    public ChargingStation(Long id, String name, String address, Double latitude,
                           Double longitude, String city, String country, List<Connector> connectors) {
        this.id = id; this.name = name; this.address = address;
        this.latitude = latitude; this.longitude = longitude;
        this.city = city; this.country = country; this.connectors = connectors;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public List<Connector> getConnectors() { return connectors; }
    public void setConnectors(List<Connector> connectors) { this.connectors = connectors; }
}