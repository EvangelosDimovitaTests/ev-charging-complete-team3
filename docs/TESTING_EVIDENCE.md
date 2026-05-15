# VoltSpot Testing Evidence

This document records the manual and automated tests used to verify the EV charging station booking system against the assessment criteria.

## Environment

- Backend: Spring Boot, Java 17
- Frontend: static HTML/CSS/JavaScript served at `http://localhost:5500`
- Local database: H2 in-memory
- API base during local testing: `http://localhost:8080/api`
- Demo accounts:
  - `admin / admin123`
  - `driver1 / driver123`
  - `driver2 / driver123`

## Request Logging Evidence

The backend logs every request with:

- timestamp
- HTTP method
- requested URI
- response status code
- processing duration
- instance identifier

Example format:

```text
[REQUEST_LOG] timestamp=2026-05-15T01:45:20.285537600Z method=POST uri=/api/bookings status=200 duration_ms=62 instance=a5c2e438-d526-4e9e-905c-ca97622b70d7
```

## Manual Test Checklist

| ID | Area | Test | Expected result |
|---|---|---|---|
| M1 | Startup | Start backend | Backend starts on port 8080 and demo data is seeded |
| M2 | Seed data | Confirm seeded data | 5 stations, 8 connectors, 336 slots |
| M3 | Frontend | Open frontend | Map and station list load |
| M4 | Authentication | Login as `driver1` | Login succeeds, Admin tab hidden |
| M5 | Authentication | Login as `admin` | Login succeeds, Admin tab visible |
| M6 | Validation | Empty/invalid login or registration data | Clean validation error shown |
| M7 | Booking | Driver books available slot | Booking confirmed; `POST /api/bookings` returns 200 |
| M8 | Booking | Duplicate booking through API | Rejected with 400 |
| M9 | Booking | Arbitrary non-slot time through API | Rejected with 400 |
| M10 | Booking | Blocked slot booking through API | Rejected with 400 |
| M11 | Booking | Driver modifies own booking to another slot | Booking updates successfully |
| M12 | Booking | Driver moves booking to another station/connector | Booking updates successfully |
| M13 | Booking | Admin creates booking for selected driver | Booking appears under selected driver |
| M14 | Booking | Admin reassigns booking to another driver | Booking moves to reassigned driver |
| M15 | RBAC | Driver tries `GET /api/bookings` | 403 Forbidden |
| M16 | RBAC | Driver tries `POST /api/stations` | 403 Forbidden |
| M17 | RBAC | Driver tries `POST /api/connectors/station/1` | 403 Forbidden |
| M18 | RBAC | Driver tries to modify another driver's booking | 403 Forbidden |
| M19 | RBAC | Driver tries to cancel another driver's booking | 403 Forbidden |
| M20 | Users | Admin creates/edits/deletes test user | Actions succeed when safe |
| M21 | Users | Admin self-delete/self-demotion | Blocked with clear error |
| M22 | Users | Delete user with confirmed booking | Rejected with 409/conflict-style message |
| M23 | Slots | Admin creates/edits/deletes slot | Actions succeed when safe |
| M24 | Slots | Driver tries to create slot via API | 403 Forbidden |
| M25 | Slots | Delete slot with booking history | Rejected with conflict response |
| M26 | Connectors | Admin creates connector | Connector created and 42 default slots generated |
| M27 | Connectors | Open `/api/connectors` | JSON list of all connectors returned |
| M28 | Deletion safety | Delete station/connector with booking history | Rejected with 409 |
| M29 | Build | Run `mvn test` | 12 tests pass |
| M30 | Package | Run `mvn clean package` | `BUILD SUCCESS`, JAR produced |
| M31 | JAR | Run generated JAR with Java 17 | Backend starts and `/api/stations` works |

## Useful Console Commands

### Driver cannot access all bookings

```javascript
fetch('http://localhost:8080/api/bookings', {
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('token')
  }
})
.then(async r => ({ status: r.status, body: await r.text() }))
.then(console.log);
```

Expected: `status: 403`

### Driver cannot create stations

```javascript
fetch('http://localhost:8080/api/stations', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('token'),
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    name: 'Illegal Driver Station',
    address: 'Driver Street',
    latitude: 51.5,
    longitude: -0.1,
    city: 'London',
    country: 'UK'
  })
})
.then(async r => ({ status: r.status, body: await r.text() }))
.then(console.log);
```

Expected: `status: 403`

### Arbitrary non-slot booking should fail

```javascript
fetch('http://localhost:8080/api/bookings', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('token'),
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    connectorId: 1,
    stationId: 1,
    date: '2026-05-16',
    startTime: '09:30',
    endTime: '10:30'
  })
})
.then(async r => ({ status: r.status, body: await r.text() }))
.then(console.log);
```

Expected: `status: 400`

### Global connector endpoint

```javascript
fetch('http://localhost:8080/api/connectors')
  .then(r => r.json())
  .then(console.log);
```

Expected: JSON list of connectors.

## Automated Tests

The backend includes:

```text
backend/src/test/java/com/evcharging/EvChargingIntegrationTests.java
```

The test suite verifies the highest-risk behaviours:

- driver cannot access admin user endpoint;
- admin can create a user and the created user can log in;
- admin can create/edit/delete slots;
- driver cannot create slots;
- booking must match an existing slot;
- duplicate booking is rejected;
- blocked slot cannot be booked;
- admin can create a booking for a selected driver;
- driver cannot use the admin booking endpoint;
- admin-created connector receives generated slots;
- station/connector deletion is blocked after booking history exists;
- global connector list endpoint returns connectors;
- driver cannot modify or cancel another driver's booking;
- admin can move and reassign a booking.

Run with:

```bash
cd backend
mvn test
```

Expected:

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Packaged JAR Test

Build:

```bash
cd backend
mvn clean package
```

Run with Java 17:

```bash
java -jar target/ev-charging-backend-1.0.0.jar
```

Expected:

```text
Tomcat started on port 8080
Started EvChargingApplication
```

Then verify:

```text
http://localhost:8080/api/stations
```

Expected: JSON station list.
