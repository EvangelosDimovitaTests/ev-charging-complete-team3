package com.evcharging.controller;

import com.evcharging.model.Booking;
import com.evcharging.model.ChargingStation;
import com.evcharging.model.Connector;
import com.evcharging.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings(Authentication auth) {
        List<BookingResponse> bookings = bookingService.getUserBookings(auth.getName())
                .stream()
                .map(this::toBookingResponse)
                .toList();

        return ResponseEntity.ok(bookings);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        List<BookingResponse> bookings = bookingService.getAllBookings()
                .stream()
                .map(this::toBookingResponse)
                .toList();

        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBooking(@PathVariable Long id, Authentication auth) {
        boolean isAdmin = isAdmin(auth);

        return bookingService.getBookingById(id).map(booking -> {
            if (!isAdmin && !booking.getUser().getUsername().equals(auth.getName())) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }

            return ResponseEntity.ok(toBookingResponse(booking));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Creates a booking for the logged-in driver. */
    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest req, Authentication auth) {
        try {
            Booking booking = bookingService.createBooking(
                    auth.getName(),
                    req.connectorId(),
                    req.stationId(),
                    LocalDate.parse(req.date()),
                    LocalTime.parse(req.startTime()),
                    LocalTime.parse(req.endTime())
            );

            return ResponseEntity.ok(toBookingResponse(booking));
        } catch (BookingService.BookingAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (BookingService.BookingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid booking request"));
        }
    }

    /** Lets an admin create a booking for a selected driver. */
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createBookingForDriver(@RequestBody AdminBookingRequest req) {
        try {
            Booking booking = bookingService.createBookingForDriver(
                    req.driverUsername(),
                    req.connectorId(),
                    req.stationId(),
                    LocalDate.parse(req.date()),
                    LocalTime.parse(req.startTime()),
                    LocalTime.parse(req.endTime())
            );

            return ResponseEntity.ok(toBookingResponse(booking));
        } catch (BookingService.BookingAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (BookingService.BookingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid admin booking request"));
        }
    }

    /** Updates a booking. Admins can also reassign it to another driver. */
    @PutMapping("/{id}")
    public ResponseEntity<?> modifyBooking(@PathVariable Long id,
                                           @RequestBody BookingRequest req,
                                           Authentication auth) {
        boolean isAdmin = isAdmin(auth);

        try {
            Booking booking = bookingService.modifyBooking(
                    auth.getName(),
                    id,
                    req.stationId(),
                    req.connectorId(),
                    LocalDate.parse(req.date()),
                    LocalTime.parse(req.startTime()),
                    LocalTime.parse(req.endTime()),
                    req.driverUsername(),
                    isAdmin
            );

            return ResponseEntity.ok(toBookingResponse(booking));
        } catch (BookingService.BookingAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (BookingService.BookingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid booking request"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id, Authentication auth) {
        boolean isAdmin = isAdmin(auth);

        try {
            Booking booking = bookingService.cancelBooking(auth.getName(), id, isAdmin);

            return ResponseEntity.ok(Map.of(
                    "message", "Booking cancelled",
                    "booking", toBookingResponse(booking)
            ));
        } catch (BookingService.BookingAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (BookingService.BookingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );
    }

    private BookingResponse toBookingResponse(Booking booking) {
        ChargingStation station = booking.getStation();
        Connector connector = booking.getConnector();

        StationSummary stationSummary = new StationSummary(
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getCity(),
                station.getCountry()
        );

        ConnectorSummary connectorSummary = new ConnectorSummary(
                connector.getId(),
                connector.getConnectorType(),
                connector.getPowerKw()
        );

        Long slotId = booking.getSlot() != null ? booking.getSlot().getId() : null;

        return new BookingResponse(
                booking.getId(),
                slotId,
                booking.getUser().getUsername(),
                stationSummary,
                connectorSummary,
                booking.getDate().toString(),
                booking.getStartTime().toString(),
                booking.getEndTime().toString(),
                booking.getStatus().name()
        );
    }

    public record BookingRequest(
            String driverUsername,
            Long connectorId,
            Long stationId,
            String date,
            String startTime,
            String endTime
    ) {}

    public record AdminBookingRequest(
            String driverUsername,
            Long connectorId,
            Long stationId,
            String date,
            String startTime,
            String endTime
    ) {}

    public record BookingResponse(
            Long id,
            Long slotId,
            String driverUsername,
            StationSummary station,
            ConnectorSummary connector,
            String date,
            String startTime,
            String endTime,
            String status
    ) {}

    public record StationSummary(
            Long id,
            String name,
            String address,
            String city,
            String country
    ) {}

    public record ConnectorSummary(
            Long id,
            String connectorType,
            Double powerKw
    ) {}
}
