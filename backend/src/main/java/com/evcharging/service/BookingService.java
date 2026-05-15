package com.evcharging.service;

import com.evcharging.model.Booking;
import com.evcharging.model.ChargingSlot;
import com.evcharging.model.ChargingStation;
import com.evcharging.model.Connector;
import com.evcharging.model.User;
import com.evcharging.repository.BookingRepository;
import com.evcharging.repository.ChargingSlotRepository;
import com.evcharging.repository.ChargingStationRepository;
import com.evcharging.repository.ConnectorRepository;
import com.evcharging.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ChargingStationRepository stationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChargingSlotRepository slotRepository;

    /** Creates a booking for the logged-in driver. */
    @Transactional
    public Booking createBooking(String username,
                                 Long connectorId,
                                 Long stationId,
                                 LocalDate date,
                                 LocalTime startTime,
                                 LocalTime endTime) throws BookingException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BookingException("User not found"));

        return createBookingForUser(user, connectorId, stationId, date, startTime, endTime);
    }

    /** Creates a booking for a driver selected by an admin. */
    @Transactional
    public Booking createBookingForDriver(String driverUsername,
                                          Long connectorId,
                                          Long stationId,
                                          LocalDate date,
                                          LocalTime startTime,
                                          LocalTime endTime) throws BookingException {

        User driver = resolveDriverUser(driverUsername);
        return createBookingForUser(driver, connectorId, stationId, date, startTime, endTime);
    }

    /** Common validation and save logic for new bookings. */
    private Booking createBookingForUser(User user,
                                         Long connectorId,
                                         Long stationId,
                                         LocalDate date,
                                         LocalTime startTime,
                                         LocalTime endTime) throws BookingException {

        validateTimeRange(date, startTime, endTime);

        Connector connector = connectorRepository.findByIdForUpdate(connectorId)
                .orElseThrow(() -> new BookingException("Connector not found"));

        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new BookingException("Station not found"));

        validateConnectorBelongsToStation(connector, station);

        ChargingSlot slot = resolveAvailableSlotForUpdate(
                connector.getId(),
                date,
                startTime,
                endTime
        );

        ensureSlotIsNotAlreadyConfirmed(slot.getId(), null);

        Booking booking = new Booking();
        booking.setUser(user);
        applySlotToBooking(booking, slot);
        booking.setStatus(Booking.BookingStatus.CONFIRMED);

        return bookingRepository.save(booking);
    }

    /** Moves a booking to another valid slot. Admins may also change the driver. */
    @Transactional
    public Booking modifyBooking(String username,
                                 Long bookingId,
                                 Long newStationId,
                                 Long newConnectorId,
                                 LocalDate newDate,
                                 LocalTime newStartTime,
                                 LocalTime newEndTime,
                                 String newDriverUsername,
                                 boolean isAdmin) throws BookingException {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found"));

        if (!isAdmin && !booking.getUser().getUsername().equals(username)) {
            throw new BookingAccessDeniedException("You can only modify your own bookings");
        }

        if (!isAdmin && newDriverUsername != null && !newDriverUsername.trim().isBlank()
                && !newDriverUsername.trim().equals(username)) {
            throw new BookingAccessDeniedException("Drivers cannot reassign bookings");
        }

        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new BookingException("Only confirmed bookings can be modified");
        }

        LocalDateTime originalSlotStart = LocalDateTime.of(
                booking.getDate(),
                booking.getStartTime()
        );

        if (LocalDateTime.now().isAfter(originalSlotStart)) {
            throw new BookingException("Cannot modify a booking after it has started");
        }

        validateTimeRange(newDate, newStartTime, newEndTime);

        Long stationIdToUse = newStationId != null
                ? newStationId
                : booking.getStation().getId();

        Long connectorIdToUse = newConnectorId != null
                ? newConnectorId
                : booking.getConnector().getId();

        Connector connector = connectorRepository.findByIdForUpdate(connectorIdToUse)
                .orElseThrow(() -> new BookingException("Connector not found"));

        ChargingStation station = stationRepository.findById(stationIdToUse)
                .orElseThrow(() -> new BookingException("Station not found"));

        validateConnectorBelongsToStation(connector, station);

        ChargingSlot newSlot = resolveAvailableSlotForUpdate(
                connector.getId(),
                newDate,
                newStartTime,
                newEndTime
        );

        ensureSlotIsNotAlreadyConfirmed(newSlot.getId(), bookingId);

        if (isAdmin && newDriverUsername != null && !newDriverUsername.trim().isBlank()) {
            User newDriver = resolveDriverUser(newDriverUsername);
            booking.setUser(newDriver);
        }

        applySlotToBooking(booking, newSlot);

        return bookingRepository.save(booking);
    }

    /** Keeps the older update call working. */
    @Transactional
    public Booking modifyBooking(String username,
                                 Long bookingId,
                                 LocalDate newDate,
                                 LocalTime newStartTime,
                                 LocalTime newEndTime,
                                 boolean isAdmin) throws BookingException {

        return modifyBooking(
                username,
                bookingId,
                null,
                null,
                newDate,
                newStartTime,
                newEndTime,
                null,
                isAdmin
        );
    }

    /** Keeps the older admin update call working. */
    @Transactional
    public Booking modifyBooking(String username,
                                 Long bookingId,
                                 LocalDate newDate,
                                 LocalTime newStartTime,
                                 LocalTime newEndTime) throws BookingException {
        return modifyBooking(
                username,
                bookingId,
                newDate,
                newStartTime,
                newEndTime,
                false
        );
    }

    /** Cancels a booking without deleting its row. */
    @Transactional
    public Booking cancelBooking(String username,
                                 Long bookingId,
                                 boolean isAdmin) throws BookingException {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found"));

        if (!isAdmin && !booking.getUser().getUsername().equals(username)) {
            throw new BookingAccessDeniedException("You can only cancel your own bookings");
        }

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new BookingException("Booking is already cancelled");
        }

        if (!isAdmin) {
            LocalDateTime slotStart = LocalDateTime.of(
                    booking.getDate(),
                    booking.getStartTime()
            );

            if (LocalDateTime.now().isAfter(slotStart)) {
                throw new BookingException("Cannot cancel a booking after it has started");
            }
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }

    public List<Booking> getUserBookings(String username) {
        return bookingRepository.findByUserUsername(username);
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public Optional<Booking> getBookingById(Long id) {
        return bookingRepository.findById(id);
    }

    private User resolveDriverUser(String driverUsername) throws BookingException {
        if (driverUsername == null || driverUsername.trim().isBlank()) {
            throw new BookingException("Driver username is required");
        }

        User driver = userRepository.findByUsername(driverUsername.trim())
                .orElseThrow(() -> new BookingException("Driver user not found"));

        if (driver.getRole() != User.Role.DRIVER) {
            throw new BookingException("Admin bookings can only be created for DRIVER users");
        }

        return driver;
    }

    private ChargingSlot resolveAvailableSlotForUpdate(Long connectorId,
                                                       LocalDate date,
                                                       LocalTime startTime,
                                                       LocalTime endTime) throws BookingException {
        ChargingSlot slot = slotRepository
                .findExactSlotForUpdate(
                        connectorId,
                        date,
                        startTime,
                        endTime
                )
                .orElseThrow(() -> new BookingException(
                        "Booking must match an existing available charging slot"
                ));

        if (!Boolean.TRUE.equals(slot.getAvailable())) {
            throw new BookingException("Selected charging slot is blocked or unavailable");
        }

        return slot;
    }

    private void ensureSlotIsNotAlreadyConfirmed(Long slotId,
                                                 Long bookingIdToExclude) throws BookingException {
        List<Booking> confirmedBookings = bookingRepository.findBySlotIdAndStatus(
                slotId,
                Booking.BookingStatus.CONFIRMED
        );

        boolean blocked = confirmedBookings.stream()
                .anyMatch(existing -> bookingIdToExclude == null ||
                        !existing.getId().equals(bookingIdToExclude));

        if (blocked) {
            throw new BookingException("This slot is already booked for the requested time period");
        }
    }

    private void applySlotToBooking(Booking booking, ChargingSlot slot) throws BookingException {
        Connector connector = slot.getConnector();
        ChargingStation station = connector.getChargingStation();

        if (station == null) {
            throw new BookingException("Selected slot is not linked to a valid station");
        }

        booking.setSlot(slot);
        booking.setConnector(connector);
        booking.setStation(station);
        booking.setDate(slot.getDate());
        booking.setStartTime(slot.getStartTime());
        booking.setEndTime(slot.getEndTime());
    }

    private void validateConnectorBelongsToStation(Connector connector,
                                                   ChargingStation station) throws BookingException {
        if (connector.getChargingStation() == null ||
                !connector.getChargingStation().getId().equals(station.getId())) {
            throw new BookingException("Connector does not belong to the selected station");
        }
    }

    private void validateTimeRange(LocalDate date,
                                   LocalTime startTime,
                                   LocalTime endTime) throws BookingException {

        if (date == null || startTime == null || endTime == null) {
            throw new BookingException("Date, start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new BookingException("Start time must be before end time");
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (date.isBefore(today) || (date.isEqual(today) && startTime.isBefore(now))) {
            throw new BookingException("Cannot book a slot in the past");
        }
    }

    public static class BookingException extends Exception {
        public BookingException(String message) {
            super(message);
        }
    }

    public static class BookingAccessDeniedException extends BookingException {
        public BookingAccessDeniedException(String message) {
            super(message);
        }
    }
}
