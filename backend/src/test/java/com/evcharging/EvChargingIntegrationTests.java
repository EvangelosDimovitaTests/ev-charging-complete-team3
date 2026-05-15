package com.evcharging;

import com.evcharging.model.ChargingSlot;
import com.evcharging.model.ChargingStation;
import com.evcharging.model.Connector;
import com.evcharging.model.User;
import com.evcharging.repository.BookingRepository;
import com.evcharging.repository.ChargingSlotRepository;
import com.evcharging.repository.ChargingStationRepository;
import com.evcharging.repository.ConnectorRepository;
import com.evcharging.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the main booking, slot and RBAC rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvChargingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChargingStationRepository stationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ChargingSlotRepository slotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long stationId;
    private Long connectorId;
    private LocalDate testDate;

    @BeforeEach
    void resetDatabase() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();

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

        userRepository.saveAll(List.of(admin, driver1, driver2));

        ChargingStation station = new ChargingStation(
                null,
                "Test Victoria Hub",
                "Victoria Station, London",
                51.4965,
                -0.1442,
                "London",
                "UK",
                null
        );

        ChargingStation savedStation = stationRepository.save(station);
        stationId = savedStation.getId();

        Connector connector = new Connector(null, "CCS2", 150.0, savedStation, null);
        Connector savedConnector = connectorRepository.save(connector);
        connectorId = savedConnector.getId();

        testDate = LocalDate.now().plusDays(1);

        LocalTime[] starts = {
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                LocalTime.of(18, 0)
        };

        for (LocalTime start : starts) {
            ChargingSlot slot = new ChargingSlot();
            slot.setConnector(savedConnector);
            slot.setDate(testDate);
            slot.setStartTime(start);
            slot.setEndTime(start.plusHours(2));
            slot.setAvailable(true);
            slotRepository.save(slot);
        }
    }

    @Test
    void driverCannotAccessAdminUserEndpoint() throws Exception {
        String driverToken = login("driver1", "driver123");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUserAndCreatedUserCanLogin() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "apitestdriver",
                                "email", "apitestdriver@example.com",
                                "password", "test123",
                                "role", "DRIVER"
                        ))))
                .andExpect(status().isOk());

        String newDriverToken = login("apitestdriver", "test123");
        assertTrue(newDriverToken.length() > 20);
    }

    @Test
    void adminCanCreateEditAndDeleteSlotButDriverCannotCreateSlot() throws Exception {
        String adminToken = login("admin", "admin123");
        String driverToken = login("driver1", "driver123");

        String createResponse = mockMvc.perform(post("/api/slots")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "20:00",
                                "endTime", "22:00",
                                "available", true
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long slotId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/slots/" + slotId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "20:00",
                                "endTime", "22:00",
                                "available", false
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/slots/" + slotId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/slots")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "20:00",
                                "endTime", "22:00",
                                "available", true
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingMustMatchExistingSlot() throws Exception {
        String driverToken = login("driver1", "driver123");

        String response = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "09:30",
                                "endTime", "10:30"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("Booking must match an existing available charging slot"));
    }

    @Test
    void duplicateBookingIsRejected() throws Exception {
        String driverToken = login("driver1", "driver123");

        Map<String, Object> request = Map.of(
                "stationId", stationId,
                "connectorId", connectorId,
                "date", testDate.toString(),
                "startTime", "08:00",
                "endTime", "10:00"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        String duplicateResponse = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(duplicateResponse.contains("This slot is already booked"));
    }

    @Test
    void blockedSlotCannotBeBooked() throws Exception {
        String adminToken = login("admin", "admin123");
        String driverToken = login("driver1", "driver123");

        Long blockedSlotId = findSlotId("12:00");

        mockMvc.perform(put("/api/slots/" + blockedSlotId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "12:00",
                                "endTime", "14:00",
                                "available", false
                        ))))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "12:00",
                                "endTime", "14:00"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("Selected charging slot is blocked or unavailable"));
    }

    @Test
    void adminCanCreateBookingForSelectedDriverAndDriverCanSeeIt() throws Exception {
        String adminToken = login("admin", "admin123");
        String driver2Token = login("driver2", "driver123");

        mockMvc.perform(post("/api/bookings/admin")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "driverUsername", "driver2",
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "10:00",
                                "endTime", "12:00"
                        ))))
                .andExpect(status().isOk());

        String driverBookings = mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", bearer(driver2Token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(driverBookings.contains("\"driverUsername\":\"driver2\""));
        assertTrue(driverBookings.contains("\"startTime\":\"10:00\""));
    }

    @Test
    void driverCannotUseAdminBookingEndpoint() throws Exception {
        String driverToken = login("driver1", "driver123");

        mockMvc.perform(post("/api/bookings/admin")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "driverUsername", "driver2",
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "10:00",
                                "endTime", "12:00"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatedConnectorGetsSlotsAndDeletionIsBlockedAfterBookingHistory() throws Exception {
        String adminToken = login("admin", "admin123");
        String driverToken = login("driver1", "driver123");

        String stationResponse = mockMvc.perform(post("/api/stations")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Auto Slot Station",
                                "address", "Auto Slot Road",
                                "latitude", 51.5200,
                                "longitude", -0.1100,
                                "city", "London",
                                "country", "UK"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long newStationId = objectMapper.readTree(stationResponse).get("id").asLong();

        String connectorResponse = mockMvc.perform(post("/api/connectors/station/" + newStationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "connectorType", "CCS2",
                                "powerKw", 150.0
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode connectorJson = objectMapper.readTree(connectorResponse);
        Long newConnectorId = connectorJson.get("id").asLong();

        assertEquals(42, connectorJson.get("generatedSlots").asInt());

        String slotsResponse = mockMvc.perform(get("/api/slots")
                        .param("connectorId", newConnectorId.toString())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(6, objectMapper.readTree(slotsResponse).size());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stationId", newStationId,
                                "connectorId", newConnectorId,
                                "date", testDate.toString(),
                                "startTime", "08:00",
                                "endTime", "10:00"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/connectors/" + newConnectorId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/stations/" + newStationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }


    @Test
    void globalConnectorListEndpointWorks() throws Exception {
        String response = mockMvc.perform(get("/api/connectors"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode connectors = objectMapper.readTree(response);
        assertTrue(connectors.isArray());
        assertTrue(connectors.size() >= 1);
    }

    @Test
    void driverCannotModifyOrCancelAnotherDriversBooking() throws Exception {
        String driver1Token = login("driver1", "driver123");
        String driver2Token = login("driver2", "driver123");

        String bookingResponse = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(driver2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "08:00",
                                "endTime", "10:00"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long bookingId = objectMapper.readTree(bookingResponse).get("id").asLong();

        mockMvc.perform(put("/api/bookings/" + bookingId)
                        .header("Authorization", bearer(driver1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "10:00",
                                "endTime", "12:00"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", bearer(driver1Token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanMoveBookingToAnotherConnectorAndReassignDriver() throws Exception {
        String adminToken = login("admin", "admin123");
        String driver2Token = login("driver2", "driver123");

        String bookingResponse = mockMvc.perform(post("/api/bookings/admin")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "driverUsername", "driver1",
                                "stationId", stationId,
                                "connectorId", connectorId,
                                "date", testDate.toString(),
                                "startTime", "14:00",
                                "endTime", "16:00"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long bookingId = objectMapper.readTree(bookingResponse).get("id").asLong();

        String stationResponse = mockMvc.perform(post("/api/stations")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Reassign Station",
                                "address", "Reassign Road",
                                "latitude", 51.5300,
                                "longitude", -0.1200,
                                "city", "London",
                                "country", "UK"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long newStationId = objectMapper.readTree(stationResponse).get("id").asLong();

        String connectorResponse = mockMvc.perform(post("/api/connectors/station/" + newStationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "connectorType", "Type2",
                                "powerKw", 22.0
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long newConnectorId = objectMapper.readTree(connectorResponse).get("id").asLong();

        mockMvc.perform(put("/api/bookings/" + bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "driverUsername", "driver2",
                                "stationId", newStationId,
                                "connectorId", newConnectorId,
                                "date", testDate.toString(),
                                "startTime", "08:00",
                                "endTime", "10:00"
                        ))))
                .andExpect(status().isOk());

        String driverBookings = mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", bearer(driver2Token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(driverBookings.contains("\"driverUsername\":\"driver2\""));
        assertTrue(driverBookings.contains("\"startTime\":\"08:00\""));
        assertTrue(driverBookings.contains("\"slotId\""));
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private Long findSlotId(String startTimePrefix) throws Exception {
        String response = mockMvc.perform(get("/api/slots")
                        .param("connectorId", connectorId.toString())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode slots = objectMapper.readTree(response);

        for (JsonNode slot : slots) {
            if (slot.get("startTime").asText().startsWith(startTimePrefix)) {
                return slot.get("id").asLong();
            }
        }

        throw new IllegalStateException("Could not find slot with start time " + startTimePrefix);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
