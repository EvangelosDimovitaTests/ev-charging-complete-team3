package com.evcharging.repository;

import com.evcharging.model.ChargingSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingSlotRepository extends JpaRepository<ChargingSlot, Long> {

    @Override
    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    List<ChargingSlot> findAll();

    @Override
    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    Optional<ChargingSlot> findById(Long id);


    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    @Query("SELECT s FROM ChargingSlot s LEFT JOIN FETCH s.connector c LEFT JOIN FETCH c.chargingStation WHERE s.id = :id")
    Optional<ChargingSlot> findByIdWithDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    List<ChargingSlot> findByConnectorIdAndDateAndAvailableTrue(Long connectorId, LocalDate date);

    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    List<ChargingSlot> findByConnectorIdAndDate(Long connectorId, LocalDate date);

    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    List<ChargingSlot> findByConnectorIdOrderByDateAscStartTimeAsc(Long connectorId);

    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    Optional<ChargingSlot> findByConnectorIdAndDateAndStartTimeAndEndTime(Long connectorId,
                                                                          LocalDate date,
                                                                          LocalTime startTime,
                                                                          LocalTime endTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    @Query("SELECT s FROM ChargingSlot s WHERE s.connector.id = :connectorId " +
            "AND s.date = :date AND s.startTime = :startTime AND s.endTime = :endTime")
    Optional<ChargingSlot> findExactSlotForUpdate(@Param("connectorId") Long connectorId,
                                                  @Param("date") LocalDate date,
                                                  @Param("startTime") LocalTime startTime,
                                                  @Param("endTime") LocalTime endTime);

    /** Checks whether a new slot overlaps existing slots. */
    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    @Query("SELECT s FROM ChargingSlot s WHERE s.connector.id = :connectorId " +
            "AND s.date = :date " +
            "AND s.startTime < :endTime AND s.endTime > :startTime")
    List<ChargingSlot> findOverlappingSlots(@Param("connectorId") Long connectorId,
                                            @Param("date") LocalDate date,
                                            @Param("startTime") LocalTime startTime,
                                            @Param("endTime") LocalTime endTime);

    /** Same check, but ignores the slot being edited. */
    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    @Query("SELECT s FROM ChargingSlot s WHERE s.connector.id = :connectorId " +
            "AND s.date = :date " +
            "AND s.startTime < :endTime AND s.endTime > :startTime " +
            "AND s.id <> :excludeId")
    List<ChargingSlot> findOverlappingSlotsExcluding(@Param("connectorId") Long connectorId,
                                                     @Param("date") LocalDate date,
                                                     @Param("startTime") LocalTime startTime,
                                                     @Param("endTime") LocalTime endTime,
                                                     @Param("excludeId") Long excludeId);

    /** Query used by older slot checks. */
    @EntityGraph(attributePaths = {"connector", "connector.chargingStation"})
    @Query("SELECT s FROM ChargingSlot s WHERE s.connector.id = :connectorId " +
            "AND s.date = :date AND s.available = false " +
            "AND s.startTime < :endTime AND s.endTime > :startTime")
    List<ChargingSlot> findOverlappingBookedSlots(@Param("connectorId") Long connectorId,
                                                  @Param("date") LocalDate date,
                                                  @Param("startTime") LocalTime startTime,
                                                  @Param("endTime") LocalTime endTime);
}
