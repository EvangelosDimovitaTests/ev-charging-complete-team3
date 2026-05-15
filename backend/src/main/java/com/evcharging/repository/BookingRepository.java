package com.evcharging.repository;

import com.evcharging.model.Booking;
import com.evcharging.model.Booking.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    List<Booking> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    List<Booking> findByUserUsername(String username);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    List<Booking> findByStationId(Long stationId);

    @Override
    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    List<Booking> findAll();

    @Override
    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    Optional<Booking> findById(Long id);

    long countByStationId(Long stationId);

    long countByConnectorId(Long connectorId);

    long countByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    List<Booking> findBySlotId(Long slotId);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    List<Booking> findBySlotIdAndStatus(Long slotId, BookingStatus status);

    long countBySlotId(Long slotId);

    long countBySlotIdAndStatus(Long slotId, BookingStatus status);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    @Query("SELECT b FROM Booking b WHERE b.connector.id = :connectorId " +
            "AND b.date = :date AND b.status = 'CONFIRMED'")
    List<Booking> findConfirmedBookingsByConnectorAndDate(@Param("connectorId") Long connectorId,
                                                          @Param("date") LocalDate date);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    @Query("SELECT b FROM Booking b WHERE b.connector.id = :connectorId " +
            "AND b.date = :date AND b.status = 'CONFIRMED' " +
            "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<Booking> findOverlappingBookings(@Param("connectorId") Long connectorId,
                                          @Param("date") LocalDate date,
                                          @Param("startTime") LocalTime startTime,
                                          @Param("endTime") LocalTime endTime);

    @EntityGraph(attributePaths = {"user", "station", "connector", "slot"})
    @Query("SELECT b FROM Booking b WHERE b.connector.id = :connectorId " +
            "AND b.date = :date AND b.status = 'CONFIRMED' " +
            "AND b.startTime < :endTime AND b.endTime > :startTime " +
            "AND b.id <> :excludeId")
    List<Booking> findOverlappingBookingsExcluding(@Param("connectorId") Long connectorId,
                                                   @Param("date") LocalDate date,
                                                   @Param("startTime") LocalTime startTime,
                                                   @Param("endTime") LocalTime endTime,
                                                   @Param("excludeId") Long excludeId);
}
