# VoltSpot — EV Charging Station Booking System

**Team 3 — CCS6427 Cloud Computing Assessment**

VoltSpot is a cloud-ready EV charging station booking system. It provides a Spring Boot REST API, a browser-based REST client, JWT authentication, role-based access control, map-based station browsing, charging-station management, connector management, charging-slot management, booking management, structured request logging, automated integration tests, and configuration suitable for PaaS deployment.

The system has two main roles:

- **DRIVER** users can browse stations, inspect connectors and available slots, create bookings from real available slots, modify their own bookings, cancel their own bookings, and view their own booking history.
- **ADMIN** users can manage users, stations, connectors, slots, and all bookings. Admins can also create bookings for selected drivers, move bookings to other slots, and reassign bookings to another driver.

Public registration is available as a demo/self-service convenience, but administrative user management is also implemented through the Admin Users panel.

---

## Technology Stack

| Area | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2, Spring Web MVC |
| Security | Spring Security, JWT, BCrypt password hashing |
| Persistence | Spring Data JPA, Hibernate |
| Local database | H2 in-memory database |
| Cloud database target | PostgreSQL |
| Frontend | HTML, CSS, vanilla JavaScript |
| Map | Leaflet.js with OpenStreetMap/CARTO tiles |
| Build | Maven |
| Deployment config | `railway.toml`, `Procfile`, externalised environment variables |

---

## Project Structure

```text
ev-charging-complete-team3/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/evcharging/
│       │   ├── EvChargingApplication.java
│       │   ├── config/
│       │   │   ├── DataSeeder.java
│       │   │   └── SecurityConfig.java
│       │   ├── controller/
│       │   │   ├── AuthController.java
│       │   │   ├── BookingController.java
│       │   │   ├── ChargingSlotController.java
│       │   │   ├── ChargingStationController.java
│       │   │   ├── ConnectorController.java
│       │   │   └── UserController.java
│       │   ├── exception/
│       │   ├── filter/
│       │   ├── model/
│       │   ├── repository/
│       │   ├── security/
│       │   └── service/
│       └── test/java/com/evcharging/
│           └── EvChargingIntegrationTests.java
├── docs/
│   ├── DEPLOYMENT_EVIDENCE_TEMPLATE.md
│   ├── ORAL_DEFENSE_NOTES.md
│   └── TESTING_EVIDENCE.md
├── frontend/
│   └── index.html
├── report/
│   └── report.md
├── .env.template
├── .gitignore
├── Procfile
├── railway.toml
└── README.md
```

Do not include local IDE/build folders such as `.idea/` or `backend/target/` in the final submitted zip unless specifically requested.

---

## Local Requirements

Install or configure:

- Java 17
- Maven 3.8+ or IntelliJ IDEA's bundled Maven runner
- Python 3.x for serving the static frontend locally
- A modern browser

The backend is compiled for Java 17. If Java 8 is also installed on the machine, make sure IntelliJ and the terminal command used for this project point to Java 17.

---

## Running the Backend Locally

Open the backend Maven project in IntelliJ from:

```text
backend/pom.xml
```

Set the Project SDK to Java 17 and run:

```text
backend/src/main/java/com/evcharging/EvChargingApplication.java
```

Alternatively, from a terminal with Maven available:

```bash
cd backend
mvn spring-boot:run
```

A successful startup includes:

```text
Tomcat started on port 8080
Started EvChargingApplication
Demo data seeded successfully
Admin: admin / admin123
Driver: driver1 / driver123
Driver: driver2 / driver123
Stations seeded: 5
Connectors seeded: 8
Slots seeded: 336
```

Check the backend with:

```text
http://localhost:8080/api/stations
```

The response should be a JSON list of charging stations.

---

## Running the Packaged JAR

Build the backend:

```bash
cd backend
mvn clean package
```

The generated JAR is:

```text
backend/target/ev-charging-backend-1.0.0.jar
```

Run it with Java 17:

```bash
java -jar target/ev-charging-backend-1.0.0.jar
```

If the operating system default `java` points to Java 8, use the full path to the Java 17 executable instead.

---

## Running the Frontend Locally

Open a separate terminal:

```bash
cd frontend
py -m http.server 5500
```

If `py` is not available:

```bash
python -m http.server 5500
```

Then open:

```text
http://localhost:5500
```

The local backend runs on port `8080`; the frontend runs on port `5500`.

---

## Demo Accounts

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `driver1` | `driver123` | DRIVER |
| `driver2` | `driver123` | DRIVER |

The local H2 database resets when the backend restarts. The seeded accounts, stations, connectors, and slots are recreated automatically.

---

## Seeded Data

The local `DataSeeder` creates:

- 3 users
- 5 charging stations
- 8 connectors
- 336 charging slots

The slots are generated as:

```text
8 connectors × 7 days × 6 slots per day = 336 slots
```

Each connector receives these daily slots:

```text
08:00–10:00
10:00–12:00
12:00–14:00
14:00–16:00
16:00–18:00
18:00–20:00
```

Admin-created connectors also receive 42 default slots for the next seven days so they are immediately usable in the booking flow.

---

## H2 Console

For local development only:

```text
http://localhost:8080/h2-console
```

Use:

```text
JDBC URL: jdbc:h2:mem:evcharging
Username: sa
Password: <empty>
```

In deployment, the H2 console should be disabled by setting:

```text
H2_CONSOLE_ENABLED=false
```

---

## API Overview

### Authentication

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Authenticate and receive JWT |
| POST | `/api/auth/register` | Public | Register a DRIVER account for demo/self-service use |

### Users

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/api/users` | ADMIN | List users |
| GET | `/api/users/{id}` | ADMIN | Get one user |
| POST | `/api/users` | ADMIN | Create user |
| PUT | `/api/users/{id}` | ADMIN | Update user |
| DELETE | `/api/users/{id}` | ADMIN | Delete user when safe |

Admins cannot delete their own account or remove their own ADMIN role. Users with confirmed bookings cannot be deleted.

### Stations

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/api/stations` | Public | List stations |
| GET | `/api/stations/{id}` | Public | Get one station |
| GET | `/api/stations/search?q=...` | Public | Search stations |
| GET | `/api/stations/nearby` | Public | Find stations near coordinates |
| POST | `/api/stations` | ADMIN | Create station |
| PUT | `/api/stations/{id}` | ADMIN | Update station |
| DELETE | `/api/stations/{id}` | ADMIN | Delete station when safe |

Stations with booking history cannot be deleted. The API returns `409 Conflict` instead of allowing data loss or a database constraint failure.

### Connectors

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/api/connectors` | Public | List all connectors |
| GET | `/api/connectors/{id}` | Public | Get one connector |
| GET | `/api/connectors/station/{stationId}` | Public | List connectors for station |
| POST | `/api/connectors/station/{stationId}` | ADMIN | Create connector for station |
| PUT | `/api/connectors/{id}` | ADMIN | Update connector |
| DELETE | `/api/connectors/{id}` | ADMIN | Delete connector when safe |

New connectors automatically receive default slots for the next seven days. Connectors with booking history cannot be deleted.

### Charging Slots

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/api/slots` | Public | List/query slots |
| GET | `/api/slots/{id}` | Public | Get one slot |
| GET | `/api/slots/connector/{connectorId}` | Public | List available slots for a connector |
| GET | `/api/slots/connector/{connectorId}/all` | Public | List all slots for a connector/date |
| POST | `/api/slots` | ADMIN | Create slot |
| PUT | `/api/slots/{id}` | ADMIN | Update slot |
| DELETE | `/api/slots/{id}` | ADMIN | Delete slot when safe |

Slots are explicit database rows. A slot is bookable only if it exists, is administratively available, is not in the past, and has no confirmed booking attached to it.

### Bookings

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/api/bookings/my` | DRIVER/ADMIN | View own bookings |
| GET | `/api/bookings` | ADMIN | View all bookings |
| GET | `/api/bookings/{id}` | Owner or ADMIN | View booking |
| POST | `/api/bookings` | DRIVER/ADMIN | Create booking for authenticated user |
| POST | `/api/bookings/admin` | ADMIN | Create booking for selected driver |
| PUT | `/api/bookings/{id}` | Owner or ADMIN | Modify/reschedule booking |
| DELETE | `/api/bookings/{id}` | Owner or ADMIN | Cancel booking |

Bookings directly reference the exact `ChargingSlot` row they reserve through `slot_id`. Booking cancellation is implemented as a soft delete: the booking row remains for history, but its status becomes `CANCELLED`.

---

## Booking Rules

The backend enforces the main booking rules server-side:

- a booking must match an existing `ChargingSlot` exactly;
- the selected connector must belong to the selected station;
- blocked or administratively unavailable slots cannot be booked;
- past slots cannot be booked;
- duplicate confirmed bookings for the same slot are rejected;
- drivers can modify/cancel only their own bookings;
- admins can manage all bookings;
- admins can create bookings for selected drivers;
- admins can move bookings to another station/connector/slot and reassign them to another driver.

The frontend hides unavailable actions, but access control is enforced by the backend.

---

## Request Logging

`RequestLoggingFilter` logs each request with:

- timestamp
- HTTP method
- requested URI
- response status code
- processing duration in milliseconds
- instance identifier

Example:

```text
[REQUEST_LOG] timestamp=2026-05-15T01:45:20.285537600Z method=POST uri=/api/bookings status=200 duration_ms=62 instance=a5c2e438-d526-4e9e-905c-ca97622b70d7
```

The instance identifier is configured through:

```text
INSTANCE_ID
```

or falls back to a generated UUID.

---

## Automated Tests

The backend includes integration tests in:

```text
backend/src/test/java/com/evcharging/EvChargingIntegrationTests.java
```

Run tests through IntelliJ's Maven panel or terminal:

```bash
cd backend
mvn test
```

Expected result:

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The tests cover authentication, RBAC, user creation, slot CRUD, booking validation, duplicate-booking rejection, blocked-slot rejection, admin-created bookings, admin booking reassignment, connector slot generation, deletion protection, and the global connector endpoint.

---

## Environment Variables

Local development works without environment variables because H2 defaults are provided. Deployment should override these values.

| Variable | Purpose |
|---|---|
| `PORT` | Runtime port assigned by the platform |
| `DB_JDBC_URL` | JDBC database URL, for example `jdbc:postgresql://host:5432/db` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `DB_DRIVER` | Database driver, e.g. `org.postgresql.Driver` |
| `HIBERNATE_DIALECT` | Hibernate dialect, e.g. `org.hibernate.dialect.PostgreSQLDialect` |
| `DDL_AUTO` | Hibernate schema mode, e.g. `update` for prototype deployment |
| `JWT_SECRET` | Strong JWT signing secret |
| `JWT_EXPIRATION_MS` | Token expiry time in milliseconds |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins allowed by CORS |
| `H2_CONSOLE_ENABLED` | `true` locally, `false` in deployment |
| `INSTANCE_ID` | Instance identifier used in request logs |

Example deployment values:

```text
DB_JDBC_URL=jdbc:postgresql://<host>:<port>/<database>
DB_USERNAME=<hidden>
DB_PASSWORD=<hidden>
DB_DRIVER=org.postgresql.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
DDL_AUTO=update
JWT_SECRET=<long-random-secret>
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=https://your-frontend-domain
H2_CONSOLE_ENABLED=false
INSTANCE_ID=<platform-instance-id>
```

---

## Frontend API Base

For local development, the frontend defaults to:

```text
http://localhost:8080/api
```

For deployed testing, the API base can be changed in the browser console:

```javascript
setVoltSpotApiBase('https://your-backend-domain/api')
```

To clear the override:

```javascript
clearVoltSpotApiBase()
```

---

## Deployment Notes

The project includes:

```text
railway.toml
Procfile
.env.template
docs/DEPLOYMENT_EVIDENCE_TEMPLATE.md
```

The provided `railway.toml` assumes the PaaS service is started from the repository root:

```text
buildCommand = "cd backend && mvn clean package -DskipTests"
startCommand = "java -jar backend/target/ev-charging-backend-1.0.0.jar --server.port=$PORT"
```

If the PaaS service root is set to `backend/`, use equivalent commands without the `cd backend` prefix:

```text
buildCommand = "mvn clean package -DskipTests"
startCommand = "java -jar target/ev-charging-backend-1.0.0.jar --server.port=$PORT"
```

Deployment should use PostgreSQL through the `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD` variables. The H2 console should be disabled in deployment.

---

## Known Limitations

VoltSpot is a cloud-ready academic prototype, not a fully hardened production system. Known limitations include:

- no refresh-token flow;
- no email verification for public registration;
- no payment or charging-provider integration;
- no formal audit table beyond preserved booking rows and request logs;
- simple demo seed data;
- frontend implemented as a single static page for assessment clarity.

These limitations do not prevent the project from demonstrating the required REST API, RBAC, booking, slot-management, logging, testing, and deployment concepts.
