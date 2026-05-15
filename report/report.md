# CCS6427 Cloud Computing Assessment
# VoltSpot EV Charging Station Booking System
## Technical Report — Team 3

---

## 1. Introduction

VoltSpot is a cloud-ready academic prototype for managing EV charging station bookings. It addresses the scenario described in the assessment handout: drivers need to discover charging stations, inspect connector and slot availability, and reserve suitable charging slots, while administrators need to manage the operational data behind the service. The final implementation consists of a Spring Boot REST API, a relational database model, a single-page browser client, JWT authentication, server-side role-based access control, structured request logging, automated integration tests, and externalised configuration suitable for PaaS deployment.

The system supports two roles. **DRIVER** users can browse stations on a map, inspect connectors and available slots, create bookings, modify or cancel their own bookings, and view their own booking history. **ADMIN** users can manage users, charging stations, connectors, charging slots, and all bookings. Admins can also create bookings for selected drivers, move bookings to a different station/connector/slot, and reassign a booking to another driver. Public registration is included as a demo convenience, but the main administrative user-management requirement is handled through a dedicated admin users panel.

The design aim was not to build an enterprise charging platform, but to produce a reliable academic prototype that demonstrates the main cloud-computing requirements: a REST backend, database-backed CRUD operations, authentication, RBAC, booking consistency, deployment configuration, request logging, and evidence that the system can move from local execution to a hosted environment.

---

## 2. Server-Side Design

The backend is implemented with Java 17 and Spring Boot 3.2. The application is divided into controllers, repositories, models, security components, a logging filter, and a service layer. The REST layer contains separate controllers for authentication, users, charging stations, connectors, charging slots, and bookings. This separation keeps each part of the API aligned with a domain concept from the handout.

`AuthController` handles login and public registration. `UserController` provides admin-only CRUD operations for users. `ChargingStationController` manages stations, including listing, search, creation, update, and deletion. `ConnectorController` manages station connectors and also exposes a global connector list endpoint. `ChargingSlotController` exposes public slot queries and admin slot CRUD. `BookingController` handles driver bookings, admin booking management, booking modification, cancellation, and admin booking creation for selected drivers.

The main business logic is concentrated in `BookingService`. This service validates booking requests, checks ownership and roles, ensures the selected connector belongs to the selected station, resolves the exact charging slot being booked, and prevents invalid or duplicate bookings. Repository interfaces are implemented with Spring Data JPA. Where lazy-loaded relationships are needed for API responses, repository methods use entity graphs or explicit fetch queries so that API responses do not depend on Hibernate's open-session-in-view behaviour.

The backend returns structured JSON errors for validation, access-denied cases, business conflicts, and unexpected failures. Business conflicts such as unsafe deletion are returned cleanly, for example as `409 Conflict` rather than as raw database exceptions. The application is packaged as a Maven JAR named `ev-charging-backend-1.0.0.jar`.

---

## 3. Database Design

The database model uses five principal entities: `User`, `ChargingStation`, `Connector`, `ChargingSlot`, and `Booking`. A `User` has a unique username, email address, BCrypt-hashed password, and role. A `ChargingStation` stores descriptive and geographic details such as name, address, city, country, latitude, and longitude. A `Connector` belongs to one station and stores its connector type and power rating. A `ChargingSlot` belongs to one connector and represents an administratively created bookable time window. A `Booking` records the user reservation.

The final design directly links bookings to charging slots. In other words, `Booking` references the exact `ChargingSlot` row it reserves through `slot_id`. This is important because a booking is not merely an arbitrary combination of date and time; it must correspond to a real slot that exists in the database. The booking also keeps station, connector, date, start time, and end time fields for simple API responses and frontend compatibility, but the slot reference is the authoritative reservation target.

This design makes the available charging slot concept a real managed database resource. Admins can create, update, disable, and delete slots when safe. A slot has an administrative availability flag, while live availability is derived from both the flag and booking state. A slot is therefore bookable only when it exists, is marked available, is not in the past, and has no confirmed booking attached to it.

Deletion rules are intentionally conservative. Stations, connectors, users, and slots are not blindly removed when they are involved in booking history. This protects referential integrity and preserves useful history. Booking cancellation is also implemented as a soft delete: the row remains in the database, but its status becomes `CANCELLED`. This is a better domain model than physically deleting reservations because it preserves evidence of previous activity while allowing the slot to become bookable again.

---

## 4. Booking Logic and Consistency

The booking workflow is the most important part of the application. The backend enforces the rules server-side rather than trusting the frontend. A valid booking must reference an existing station, an existing connector, and an existing charging slot. The connector must belong to the selected station. The requested time must not be in the past, the start time must be before the end time, and the slot must be administratively available.

The service resolves the requested connector, date, start time, and end time into a specific `ChargingSlot`. If no matching slot exists, the booking is rejected. This prevents users from booking arbitrary times such as 09:30–10:30 when the system only offers two-hour slots. If the matching slot is blocked by an admin, the booking is rejected. If another confirmed booking already references that slot, the request is also rejected.

Booking modification is implemented as rescheduling rather than simple field editing. A driver can move their own booking to another available slot. An admin can move any booking to another station, connector, date, and slot, and can also reassign it to another driver. This addresses the full booking-management requirement more completely than simply changing the time on the same connector. Ownership checks are still enforced: a driver attempting to modify or cancel another driver's booking receives a `403 Forbidden` response.

Concurrency is handled at the service layer by locking the selected connector/slot path during booking creation or modification and then checking confirmed bookings for the target slot. This is sufficient for the academic prototype because the critical check and save occur within a transaction. The system also uses direct slot references, so the duplicate-booking check is based on whether a confirmed booking already exists for that exact slot.

---

## 5. Authentication and RBAC

Authentication is implemented with username/password login and JWT tokens. Passwords are stored with BCrypt hashing. After login, the frontend stores the JWT and sends it in the `Authorization` header for protected requests. The backend uses a custom JWT filter and Spring Security to authenticate requests.

The two main roles are `DRIVER` and `ADMIN`. Role checks are enforced on the server, not only by hiding UI buttons. Drivers can access public station, connector, and slot data, and can create, view, modify, and cancel their own bookings. Admins can access all booking data and all management endpoints for users, stations, connectors, and slots.

Several RBAC abuse cases were tested. Drivers cannot create stations, create connectors, create slots, access the admin user endpoint, use the admin booking endpoint, modify another driver's booking, or cancel another driver's booking. Admins can create bookings for selected drivers and reassign bookings. The frontend reflects these rules by showing admin panels only to admins, but the backend remains the real enforcement point.

Public registration exists for demo/self-service purposes, but it creates driver accounts only. Administrative control over users is still provided through the admin user-management interface. This gives the demo a convenient account-creation path while preserving the stronger admin-management story expected by the assignment.

---

## 6. Client-Side Design

The frontend is a single static HTML file using vanilla JavaScript, CSS, and Leaflet.js. This was chosen to keep the client lightweight and easy to run from a simple static server. It communicates with the backend using REST calls through the browser `fetch()` API.

The main interface shows a map and station list. Drivers can select a station, inspect its connectors, choose a date, view available slots, and book a selected slot. Unavailable slots are visually disabled. Drivers can also open My Bookings and modify or cancel their own reservations.

For admins, an Admin tab exposes bookings, users, stations, connectors, and slots. The admin booking panel allows viewing all bookings, modifying them, cancelling them, creating bookings for selected drivers, and reassigning bookings. The user panel supports user CRUD. The station and connector panels support infrastructure management, and the slot panel supports available-slot CRUD.

The frontend has a local default API base of `http://localhost:8080/api`. For deployment testing, the API base can be overridden at runtime through a browser-console helper. This makes the frontend usable both locally and against a hosted backend without changing the whole file for every environment.

---

## 7. Cloud Deployment Configuration

The project is configured for local development and PaaS-style deployment. Locally, it uses an in-memory H2 database to make testing fast and repeatable. In deployment, the datasource can be changed to PostgreSQL through environment variables. The key variables are `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`, and `HIBERNATE_DIALECT`. This avoids relying on provider-specific database URL formats and makes the configuration explicit.

The application also externalises `PORT`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `CORS_ALLOWED_ORIGINS`, `H2_CONSOLE_ENABLED`, `DDL_AUTO`, and `INSTANCE_ID`. The H2 console should be enabled only for local development and disabled in deployment. CORS is controlled centrally through Spring Security configuration and should be restricted to the deployed frontend origin when hosted.

The repository includes `railway.toml` and a `Procfile`. The provided Railway configuration assumes that the service starts from the repository root, builds the backend with Maven, and runs the generated JAR. If the PaaS service root is configured as `backend/`, the commands can be simplified accordingly. The actual deployment evidence is recorded separately after deployment, including backend URL, frontend URL, database service, remote endpoint checks, and request logs.

The architecture is suitable for multi-instance deployment because the backend is stateless with respect to user sessions. Authentication state is carried in JWTs, and shared business state is stored in the database. The request log includes an instance identifier so that requests can be traced to a particular running instance or deployment replica.

---

## 8. Request Logging and Testing

The project includes a `RequestLoggingFilter` that records timestamp, HTTP method, requested URI, response status code, duration, and instance identifier. This directly supports the logging requirement in the handout and is also useful during debugging and demonstration. Example logged requests include `GET /api/stations`, `POST /api/auth/login`, `POST /api/bookings`, and `DELETE /api/bookings/{id}` with their status codes and duration in milliseconds.

Automated integration tests are implemented in `EvChargingIntegrationTests.java`. The final test suite contains 12 tests and covers the highest-risk behaviours: driver access restrictions, admin user creation, slot CRUD, driver slot restrictions, real-slot booking validation, duplicate-booking rejection, blocked-slot rejection, admin booking creation, admin booking reassignment, connector slot generation, deletion protection, and the global connector endpoint. The current expected Maven result is `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.

Manual testing was also used throughout development. The manual tests cover login validation, driver booking flow, duplicate booking attempts, arbitrary-time booking rejection, booking modification, cancellation, admin booking management, user CRUD, slot CRUD, station and connector CRUD, driver RBAC abuse cases, Maven packaging, JAR execution, and direct API checks. This combination of automated and manual testing gives evidence both for backend rules and for frontend workflows.

---

## 9. Key Implementation Decisions

The most important design decision was to make `ChargingSlot` a first-class database entity and to link bookings directly to slots. This makes slot CRUD meaningful and prevents arbitrary booking times. It also clarifies what a reservation actually reserves: not just a time range, but a specific available slot offered by a connector.

A second important decision was to use soft cancellation for bookings. The handout asks for cancellation, and preserving booking rows is more realistic than physically deleting booking history. By considering only `CONFIRMED` bookings as blocking, cancelled bookings remain useful for history while freeing the slot for future use.

Another decision was to block deletion of entities with booking history. This applies to stations, connectors, users, and slots where appropriate. Cascading deletion would make the interface appear simple but would destroy related booking data. Returning a clear conflict response is safer and easier to explain.

The project keeps the frontend simple. A framework-based client could be more scalable, but the assessment focuses on REST interaction, cloud deployment, authentication, and business rules. A static frontend is easier to run, easier to demonstrate in five minutes, and still shows all required interactions with the backend.

Finally, deployment configuration was kept explicit rather than overly clever. Using `DB_JDBC_URL` and related variables is straightforward and avoids hidden conversion logic. This keeps the project understandable while still demonstrating externalised configuration.

---

## 10. Limitations

VoltSpot is a cloud-ready academic prototype rather than a production charging network. It does not implement refresh tokens, account verification emails, payment processing, real charger telemetry, provider integration, or a formal audit-log table. The station search and nearby functions are simple and not a full geospatial indexing solution. The frontend is a single-page static application rather than a modular framework application.

These limitations are acceptable for the scope of the assignment. The project demonstrates the required database-backed REST API, CRUD operations, authentication, RBAC, booking consistency, request logging, testing, and deployment readiness. Future production improvements would include a stronger account lifecycle, migration tooling such as Flyway or Liquibase, richer audit records, production-grade monitoring, email notifications, and closer integration with real charging hardware.

---

## 11. Conclusion

VoltSpot implements the EV charging station booking system as a REST-based, cloud-ready academic prototype. It provides CRUD operations for users, charging stations, connectors, available charging slots, and bookings. Drivers can browse stations on a map, inspect connector and slot availability, create valid bookings, modify or cancel their own bookings, and view booking history. Admins can manage users, stations, connectors, slots, and all bookings, including creating bookings for selected drivers and reassigning bookings.

The backend enforces the main business rules server-side. Bookings must match real charging slots, blocked slots cannot be booked, duplicate confirmed bookings are rejected, ownership checks are enforced, and unsafe deletion is blocked with clear conflict responses. The system includes stateless JWT authentication, externalised configuration, structured request logging with instance identifiers, PostgreSQL-ready deployment settings, and automated integration tests.

Overall, the project addresses the main assessment criteria while remaining honest about its scope. It is not presented as a fully production-hardened commercial product; it is a complete, testable, cloud-ready university prototype that demonstrates the required cloud-computing, REST API, database, authentication, RBAC, and deployment concepts.

---

*Module: CCS6427 Cloud Computing | Team 3*
