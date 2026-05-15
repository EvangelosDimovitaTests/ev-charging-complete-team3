package com.evcharging.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "charging_slots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_slot_connector_date_time",
                        columnNames = {"connector_id", "date", "start_time", "end_time"}
                )
        }
)
public class ChargingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /*
     * Admins can block a slot without deleting it.
     */
    @Column(nullable = false)
    private Boolean available = true;

    public ChargingSlot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Connector getConnector() { return connector; }
    public void setConnector(Connector connector) { this.connector = connector; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
}
