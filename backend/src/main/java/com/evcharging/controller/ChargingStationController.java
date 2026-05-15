package com.evcharging.controller;

import com.evcharging.model.ChargingStation;
import com.evcharging.repository.BookingRepository;
import com.evcharging.repository.ChargingStationRepository;
import com.evcharging.repository.ConnectorRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
public class ChargingStationController {

    @Autowired
    private ChargingStationRepository stationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private BookingRepository bookingRepository;

    /** Lists all stations. */
    @GetMapping
    public ResponseEntity<List<ChargingStation>> getAllStations() {
        return ResponseEntity.ok(stationRepository.findAll());
    }

    /** Gets one station by id. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getStation(@PathVariable Long id) {
        return stationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Searches stations by text. */
    @GetMapping("/search")
    public ResponseEntity<List<ChargingStation>> searchStations(@RequestParam String q) {
        return ResponseEntity.ok(stationRepository.searchStations(q));
    }

    /** Finds stations near a map position. */
    @GetMapping("/nearby")
    public ResponseEntity<List<ChargingStation>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "0.5") double radius) {
        return ResponseEntity.ok(stationRepository.findNearby(lat, lng, radius));
    }

    /** Creates a station. Admin only. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChargingStation> createStation(@Valid @RequestBody ChargingStation station) {
        ChargingStation saved = stationRepository.save(station);
        return ResponseEntity.ok(saved);
    }

    /** Updates a station. Admin only. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStation(@PathVariable Long id,
                                           @Valid @RequestBody ChargingStation updated) {
        return stationRepository.findById(id).map(station -> {
            station.setName(updated.getName());
            station.setAddress(updated.getAddress());
            station.setLatitude(updated.getLatitude());
            station.setLongitude(updated.getLongitude());
            station.setCity(updated.getCity());
            station.setCountry(updated.getCountry());
            return ResponseEntity.ok(stationRepository.save(station));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Deletes a station if it has no booking history. Admin only. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteStation(@PathVariable Long id) {
        return stationRepository.findById(id).map(station -> {
            long bookingCount = bookingRepository.countByStationId(id);
            if (bookingCount > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Cannot delete station because it has existing bookings",
                        "bookingCount", bookingCount
                ));
            }

            long connectorCount = connectorRepository.countByChargingStationId(id);
            stationRepository.delete(station);

            return ResponseEntity.ok(Map.of(
                    "message", "Station deleted",
                    "deletedConnectors", connectorCount
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
