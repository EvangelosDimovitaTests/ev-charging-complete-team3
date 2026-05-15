package com.evcharging.repository;

import com.evcharging.model.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {

    List<ChargingStation> findByCity(String city);

    @Query("SELECT s FROM ChargingStation s WHERE " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.city) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.address) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ChargingStation> searchStations(@Param("query") String query);

    @Query("SELECT s FROM ChargingStation s WHERE " +
           "(:lat - s.latitude) * (:lat - s.latitude) + " +
           "(:lng - s.longitude) * (:lng - s.longitude) < :radius * :radius")
    List<ChargingStation> findNearby(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("radius") double radius);
}
