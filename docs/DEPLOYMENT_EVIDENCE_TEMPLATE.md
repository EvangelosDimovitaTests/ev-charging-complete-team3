# Deployment Evidence Template

This file is a working template. Complete it after the backend and frontend have been deployed.
Do not submit this file unfinished unless the final report/README clearly says deployment evidence is provided elsewhere.

## Backend

- PaaS platform:
- Backend URL:
- Public health/API check:
  - `GET /api/stations`

## Frontend

- Frontend hosting platform:
- Frontend URL:
- API base used by frontend:

## Cloud Database

- Database service:
- Database type: PostgreSQL
- Evidence that deployed backend is not using local H2:
  - H2 console disabled: `H2_CONSOLE_ENABLED=false`
  - PostgreSQL JDBC URL configured through `DB_JDBC_URL`

## Runtime Environment Variables

Secrets should be hidden or partially masked.

```text
DB_JDBC_URL=jdbc:postgresql://<host>:<port>/<database>
DB_USERNAME=<hidden>
DB_PASSWORD=<hidden>
DB_DRIVER=org.postgresql.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
DDL_AUTO=update
JWT_SECRET=<hidden>
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=<frontend URL>
H2_CONSOLE_ENABLED=false
INSTANCE_ID=<deployment/replica id or configured value>
```

## Request Logging Evidence

Paste a few deployed request log lines here. Each should show timestamp, method, URI, status, duration and instance id.

```text
[REQUEST_LOG] timestamp=... method=GET uri=/api/stations status=200 duration_ms=... instance=...
[REQUEST_LOG] timestamp=... method=POST uri=/api/auth/login status=200 duration_ms=... instance=...
[REQUEST_LOG] timestamp=... method=POST uri=/api/bookings status=200 duration_ms=... instance=...
```

## Remote Functional Tests

| Test | Result | Evidence |
|---|---|---|
| `GET /api/stations` returns stations |  |  |
| Login works remotely |  |  |
| Driver booking works remotely |  |  |
| Duplicate booking is rejected remotely |  |  |
| Admin endpoint requires ADMIN role |  |  |
| Admin can view/manage bookings remotely |  |  |

## Notes

- Local H2 is used only for development/testing.
- Deployed execution uses external PostgreSQL through environment variables.
- JWT authentication is stateless, supporting multiple backend instances.
