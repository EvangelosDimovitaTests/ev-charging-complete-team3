package com.evcharging.controller;

import com.evcharging.model.Booking;
import com.evcharging.model.ChargingSlot;
import com.evcharging.model.ChargingStation;
import com.evcharging.model.Connector;
import com.evcharging.repository.BookingRepository;
import com.evcharging.repository.ChargingSlotRepository;
import com.evcharging.repository.ConnectorRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/slots")
public class ChargingSlotController {

    @Autowired
    private ChargingSlotRepository slotRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private BookingRepository bookingRepository;

    /** Returns bookable slots for a connector and date. */
    @GetMapping("/connector/{connectorId}")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
            @PathVariable Long connectorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> availableSlots = slotRepository.findByConnectorIdAndDate(connectorId, date)
                .stream()
                .map(this::toSlotResponse)
                .filter(SlotResponse::available)
                .sorted(Comparator.comparing(SlotResponse::startTime))
                .toList();

        return ResponseEntity.ok(availableSlots);
    }

    /** Returns all slots for a connector and date, including booked or blocked ones. */
    @GetMapping("/connector/{connectorId}/all")
    public ResponseEntity<List<SlotResponse>> getAllSlotsForConnectorAndDate(
            @PathVariable Long connectorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> slots = slotRepository.findByConnectorIdAndDate(connectorId, date)
                .stream()
                .map(this::toSlotResponse)
                .sorted(Comparator.comparing(SlotResponse::startTime))
                .toList();

        return ResponseEntity.ok(slots);
    }

    /** General slot query used by the admin screens. */
    @GetMapping
    public ResponseEntity<?> getSlots(
            @RequestParam(required = false) Long connectorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (connectorId == null) {
            List<SlotResponse> slots = slotRepository.findAll()
                    .stream()
                    .map(this::toSlotResponse)
                    .sorted(Comparator
                            .comparing(SlotResponse::date)
                            .thenComparing(SlotResponse::startTime))
                    .toList();
            return ResponseEntity.ok(slots);
        }

        if (date != null) {
            List<SlotResponse> slots = slotRepository.findByConnectorIdAndDate(connectorId, date)
                    .stream()
                    .map(this::toSlotResponse)
                    .sorted(Comparator.comparing(SlotResponse::startTime))
                    .toList();
            return ResponseEntity.ok(slots);
        }

        List<SlotResponse> slots = slotRepository.findByConnectorIdOrderByDateAscStartTimeAsc(connectorId)
                .stream()
                .map(this::toSlotResponse)
                .toList();

        return ResponseEntity.ok(slots);
    }

    /** Gets a single slot. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSlotById(@PathVariable Long id) {
        return slotRepository.findByIdWithDetails(id)
                .map(slot -> ResponseEntity.ok(toSlotResponse(slot)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Creates a slot. Admin only. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createSlot(@Valid @RequestBody SlotRequest request) {
        try {
            validateTimeRange(request.date(), request.startTime(), request.endTime());

            Connector connector = connectorRepository.findById(request.connectorId())
                    .orElseThrow(() -> new SlotException("Connector not found"));

            List<ChargingSlot> overlappingSlots = slotRepository.findOverlappingSlots(
                    connector.getId(),
                    request.date(),
                    request.startTime(),
                    request.endTime()
            );

            if (!overlappingSlots.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "A slot already exists for this connector and time period"
                ));
            }

            ChargingSlot slot = new ChargingSlot();
            slot.setConnector(connector);
            slot.setDate(request.date());
            slot.setStartTime(request.startTime());
            slot.setEndTime(request.endTime());
            slot.setAvailable(request.available() == null || request.available());

            ChargingSlot savedSlot = slotRepository.save(slot);
            ChargingSlot responseSlot = slotRepository.findByIdWithDetails(savedSlot.getId())
                    .orElse(savedSlot);
            return ResponseEntity.ok(toSlotResponse(responseSlot));

        } catch (SlotException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Updates a slot. Slots with booking history can only be blocked/unblocked. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSlot(@PathVariable Long id,
                                        @Valid @RequestBody SlotRequest request) {
        try {
            ChargingSlot slot = slotRepository.findByIdWithDetails(id)
                    .orElseThrow(() -> new SlotException("Slot not found"));

            validateTimeRange(request.date(), request.startTime(), request.endTime());

            Connector connector = connectorRepository.findById(request.connectorId())
                    .orElseThrow(() -> new SlotException("Connector not found"));

            boolean hasBookingHistory = hasBookingHistory(slot);
            boolean timeOrConnectorChanged =
                    !slot.getConnector().getId().equals(connector.getId()) ||
                            !slot.getDate().equals(request.date()) ||
                            !slot.getStartTime().equals(request.startTime()) ||
                            !slot.getEndTime().equals(request.endTime());

            if (hasBookingHistory && timeOrConnectorChanged) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Cannot change the connector or time of a slot that has booking history"
                ));
            }

            List<ChargingSlot> overlappingSlots = slotRepository.findOverlappingSlotsExcluding(
                    connector.getId(),
                    request.date(),
                    request.startTime(),
                    request.endTime(),
                    id
            );

            if (!overlappingSlots.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Another slot already exists for this connector and time period"
                ));
            }

            slot.setConnector(connector);
            slot.setDate(request.date());
            slot.setStartTime(request.startTime());
            slot.setEndTime(request.endTime());
            slot.setAvailable(request.available() == null || request.available());

            ChargingSlot savedSlot = slotRepository.save(slot);
            ChargingSlot responseSlot = slotRepository.findByIdWithDetails(savedSlot.getId())
                    .orElse(savedSlot);
            return ResponseEntity.ok(toSlotResponse(responseSlot));

        } catch (SlotException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Deletes a slot if no booking has used it. Admin only. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSlot(@PathVariable Long id) {
        try {
            ChargingSlot slot = slotRepository.findByIdWithDetails(id)
                    .orElseThrow(() -> new SlotException("Slot not found"));

            if (hasBookingHistory(slot)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Cannot delete slot because it has booking history"
                ));
            }

            slotRepository.delete(slot);
            return ResponseEntity.ok(Map.of("message", "Slot deleted successfully"));

        } catch (SlotException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void validateTimeRange(LocalDate date,
                                   LocalTime startTime,
                                   LocalTime endTime) throws SlotException {
        if (date == null || startTime == null || endTime == null) {
            throw new SlotException("Date, start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new SlotException("Start time must be before end time");
        }
    }

    private boolean hasConfirmedBooking(ChargingSlot slot) {
        return bookingRepository.countBySlotIdAndStatus(
                slot.getId(),
                Booking.BookingStatus.CONFIRMED
        ) > 0;
    }

    private boolean hasBookingHistory(ChargingSlot slot) {
        return bookingRepository.countBySlotId(slot.getId()) > 0;
    }

    private SlotResponse toSlotResponse(ChargingSlot slot) {
        Connector connector = slot.getConnector();
        ChargingStation station = connector.getChargingStation();

        boolean administrativelyAvailable = Boolean.TRUE.equals(slot.getAvailable());
        boolean booked = hasConfirmedBooking(slot);
        boolean liveAvailable = administrativelyAvailable && !booked;

        return new SlotResponse(
                slot.getId(),
                connector.getId(),
                station != null ? station.getId() : null,
                station != null ? station.getName() : null,
                connector.getConnectorType(),
                connector.getPowerKw(),
                slot.getDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getAvailable(),
                booked,
                liveAvailable
        );
    }

    public record SlotRequest(
            @NotNull(message = "Connector ID is required")
            Long connectorId,

            @NotNull(message = "Date is required")
            LocalDate date,

            @NotNull(message = "Start time is required")
            LocalTime startTime,

            @NotNull(message = "End time is required")
            LocalTime endTime,

            Boolean available
    ) {}

    public record SlotResponse(
            Long id,
            Long connectorId,
            Long stationId,
            String stationName,
            String connectorType,
            Double powerKw,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Boolean administrativelyAvailable,
            Boolean booked,
            Boolean available
    ) {}

    private static class SlotException extends Exception {
        public SlotException(String message) {
            super(message);
        }
    }
}
