package com.evcharging.controller;

import com.evcharging.model.ChargingSlot;
import com.evcharging.model.ChargingStation;
import com.evcharging.model.Connector;
import com.evcharging.repository.BookingRepository;
import com.evcharging.repository.ChargingSlotRepository;
import com.evcharging.repository.ChargingStationRepository;
import com.evcharging.repository.ConnectorRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ChargingStationRepository stationRepository;

    @Autowired
    private ChargingSlotRepository slotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    /** Lists all connectors. */
    @GetMapping
    public ResponseEntity<List<Connector>> getAllConnectors() {
        return ResponseEntity.ok(connectorRepository.findAll());
    }

    /** Lists connectors for one station. */
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<Connector>> getConnectorsByStation(@PathVariable Long stationId) {
        return ResponseEntity.ok(connectorRepository.findByChargingStationId(stationId));
    }

    /** Gets one connector by id. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getConnector(@PathVariable Long id) {
        return connectorRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Adds a connector to a station and creates its default slots. Admin only. */
    @PostMapping("/station/{stationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createConnector(@PathVariable Long stationId,
                                             @Valid @RequestBody Connector connector) {
        ChargingStation station = stationRepository.findById(stationId).orElse(null);
        if (station == null) {
            return ResponseEntity.notFound().build();
        }

        connector.setChargingStation(station);
        Connector savedConnector = connectorRepository.save(connector);

        int generatedSlots = generateDefaultSlots(savedConnector);

        return ResponseEntity.ok(Map.of(
                "id", savedConnector.getId(),
                "connectorType", savedConnector.getConnectorType(),
                "powerKw", savedConnector.getPowerKw(),
                "stationId", station.getId(),
                "generatedSlots", generatedSlots
        ));
    }

    /** Updates a connector. Admin only. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateConnector(@PathVariable Long id,
                                             @Valid @RequestBody Connector updated) {
        return connectorRepository.findById(id).map(connector -> {
            connector.setConnectorType(updated.getConnectorType());
            connector.setPowerKw(updated.getPowerKw());
            return ResponseEntity.ok(connectorRepository.save(connector));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Deletes a connector if it has no booking history. Admin only. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteConnector(@PathVariable Long id) {
        return connectorRepository.findById(id).map(connector -> {
            long bookingCount = bookingRepository.countByConnectorId(id);
            if (bookingCount > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Cannot delete connector because it has existing bookings",
                        "bookingCount", bookingCount
                ));
            }

            connectorRepository.delete(connector);
            return ResponseEntity.ok(Map.of("message", "Connector deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private int generateDefaultSlots(Connector connector) {
        LocalDate today = LocalDate.now();
        LocalTime[] startTimes = {
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                LocalTime.of(18, 0)
        };

        List<ChargingSlot> slots = new ArrayList<>();

        for (int day = 0; day < 7; day++) {
            LocalDate slotDate = today.plusDays(day);

            for (LocalTime start : startTimes) {
                ChargingSlot slot = new ChargingSlot();
                slot.setConnector(connector);
                slot.setDate(slotDate);
                slot.setStartTime(start);
                slot.setEndTime(start.plusHours(2));
                slot.setAvailable(true);
                slots.add(slot);
            }
        }

        slotRepository.saveAll(slots);
        return slots.size();
    }
}
