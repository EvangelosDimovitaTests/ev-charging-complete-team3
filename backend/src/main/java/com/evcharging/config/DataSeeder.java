package com.evcharging.config;

import com.evcharging.model.ChargingSlot;
import com.evcharging.model.ChargingStation;
import com.evcharging.model.Connector;
import com.evcharging.model.User;
import com.evcharging.repository.ChargingSlotRepository;
import com.evcharging.repository.ChargingStationRepository;
import com.evcharging.repository.ConnectorRepository;
import com.evcharging.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Adds the demo users, stations, connectors and slots when the database is empty.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ChargingStationRepository stationRepo;

    @Autowired
    private ConnectorRepository connectorRepo;

    @Autowired
    private ChargingSlotRepository slotRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) {
            return;
        }

        seedUsers();
        List<ChargingStation> stations = seedStations();
        List<Connector> connectors = seedConnectors(stations);
        seedSlotsForAllConnectors(connectors);

        System.out.println("✅ Demo data seeded successfully!");
        System.out.println("   Admin: admin / admin123");
        System.out.println("   Driver: driver1 / driver123");
        System.out.println("   Driver: driver2 / driver123");
        System.out.println("   Stations seeded: " + stations.size());
        System.out.println("   Connectors seeded: " + connectors.size());
        System.out.println("   Slots seeded: " + slotRepo.count());
    }

    private void seedUsers() {
        User admin = new User(
                null,
                "admin",
                passwordEncoder.encode("admin123"),
                "admin@evcharge.com",
                User.Role.ADMIN
        );

        User driver1 = new User(
                null,
                "driver1",
                passwordEncoder.encode("driver123"),
                "driver1@email.com",
                User.Role.DRIVER
        );

        User driver2 = new User(
                null,
                "driver2",
                passwordEncoder.encode("driver123"),
                "driver2@email.com",
                User.Role.DRIVER
        );

        userRepo.saveAll(List.of(admin, driver1, driver2));
    }

    private List<ChargingStation> seedStations() {
        ChargingStation s1 = new ChargingStation(
                null,
                "London Victoria Hub",
                "Victoria Station, London SW1V 1JT",
                51.4965,
                -0.1442,
                "London",
                "UK",
                null
        );

        ChargingStation s2 = new ChargingStation(
                null,
                "Canary Wharf EV Point",
                "Canada Square, London E14 5AB",
                51.5054,
                -0.0235,
                "London",
                "UK",
                null
        );

        ChargingStation s3 = new ChargingStation(
                null,
                "King's Cross Rapid Charge",
                "King's Cross Station, London N1 9AP",
                51.5309,
                -0.1233,
                "London",
                "UK",
                null
        );

        ChargingStation s4 = new ChargingStation(
                null,
                "Heathrow T5 EV Bay",
                "Terminal 5, Heathrow Airport, TW6 2GA",
                51.4773,
                -0.4614,
                "London",
                "UK",
                null
        );

        ChargingStation s5 = new ChargingStation(
                null,
                "Westfield Stratford Charge",
                "Stratford City, London E20 1EJ",
                51.5435,
                -0.0041,
                "London",
                "UK",
                null
        );

        return stationRepo.saveAll(List.of(s1, s2, s3, s4, s5));
    }

    private List<Connector> seedConnectors(List<ChargingStation> stations) {
        ChargingStation s1 = stations.get(0);
        ChargingStation s2 = stations.get(1);
        ChargingStation s3 = stations.get(2);
        ChargingStation s4 = stations.get(3);
        ChargingStation s5 = stations.get(4);

        Connector c1a = new Connector(null, "CCS2", 150.0, s1, null);
        Connector c1b = new Connector(null, "Type2", 22.0, s1, null);

        Connector c2a = new Connector(null, "CHAdeMO", 50.0, s2, null);
        Connector c2b = new Connector(null, "CCS2", 150.0, s2, null);

        Connector c3a = new Connector(null, "CCS2", 350.0, s3, null);

        Connector c4a = new Connector(null, "Type2", 22.0, s4, null);
        Connector c4b = new Connector(null, "CCS2", 150.0, s4, null);

        Connector c5a = new Connector(null, "CCS2", 150.0, s5, null);

        return connectorRepo.saveAll(List.of(
                c1a,
                c1b,
                c2a,
                c2b,
                c3a,
                c4a,
                c4b,
                c5a
        ));
    }

    private void seedSlotsForAllConnectors(List<Connector> connectors) {
        LocalDate today = LocalDate.now();

        LocalTime[] startTimes = {
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                LocalTime.of(18, 0)
        };

        for (Connector connector : connectors) {
            for (int day = 0; day < 7; day++) {
                LocalDate slotDate = today.plusDays(day);

                for (LocalTime start : startTimes) {
                    ChargingSlot slot = new ChargingSlot();
                    slot.setConnector(connector);
                    slot.setDate(slotDate);
                    slot.setStartTime(start);
                    slot.setEndTime(start.plusHours(2));
                    slot.setAvailable(true);

                    slotRepo.save(slot);
                }
            }
        }
    }
}