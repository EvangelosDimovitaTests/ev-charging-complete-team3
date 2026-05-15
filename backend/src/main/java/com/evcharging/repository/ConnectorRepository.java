package com.evcharging.repository;

import com.evcharging.model.Connector;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, Long> {

    @Override
    @EntityGraph(attributePaths = {"chargingStation"})
    List<Connector> findAll();

    @Override
    @EntityGraph(attributePaths = {"chargingStation"})
    Optional<Connector> findById(Long id);

    @EntityGraph(attributePaths = {"chargingStation"})
    List<Connector> findByChargingStationId(Long stationId);

    long countByChargingStationId(Long stationId);

    /** Locks the connector while a booking is being created or moved. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"chargingStation"})
    @Query("SELECT c FROM Connector c WHERE c.id = :id")
    Optional<Connector> findByIdForUpdate(@Param("id") Long id);
}
