# Edocs backend

Spring Boot 4.1 (Java 21) API for Edocs, an e-contract, e-signature and tamper-evident archive platform. It implements the REST contract that `../edocs_frontend/src/services/api.ts` already calls, so the React app switches from mock data to this API by setting `VITE_API_BASE_URL=http://localhost:8080/api`.

Full write-ups: `../documentation/Edocs_Project_Report.pdf` (assignment requirements) and `../documentation/Edocs_Backend_Implementation_Log.pdf` (what was built and how it was verified).

## Architecture

Layered **modular monolith**, with event-driven messaging for side effects. This is phase 1 of the hybrid approach recommended in the project document.

```
com.edocs
├── identity      Users, organizations (tenants), memberships, auth, MFA, members, settings
├── document      ContentItem → Document → Contract, Party, Template, shares, archive
├── signing       OTP intent-to-sign, signatures, Merkle anchoring listener
├── workflow      Contract workflows, routing rules, review escalation job
├── audit         Hash-chained, append-only audit log (MongoDB)
├── notification  In-app notifications (MongoDB) + email/SMS through RabbitMQ
├── compliance    Controls evaluated against live data
├── dashboard     KPIs, risk bands, bottlenecks
├── messaging     RabbitMQ topology, publisher (after-commit), consumers
├── security      JWT resource server, OAuth2 login, RBAC matrix, rate limiting
└── common        Errors, hashing, envelope encryption, HTML sanitizer
```

| Store | Holds |
| --- | --- |
| PostgreSQL (Flyway `db/migration`) | organizations, users, memberships, templates, documents, contracts, parties, shares, signatures, workflows, routing rules, compliance controls, OTP challenges |
| MongoDB | encrypted document bodies + version snapshots + comments, audit log, notifications, raw signature captures |
| RabbitMQ | `edocs.notifications` (email, SMS), `edocs.events` (document lifecycle), dead-letter `edocs.dlq` |

## Run

### Option A: whole stack in Docker (needs Docker Desktop with WSL 2)

```bash
docker compose up --build        # from the repository root
```

Frontend http://localhost:5173 · API docs http://localhost:8080/api/docs · RabbitMQ UI http://localhost:15672 (edocs/edocs) · Mailpit (captured emails) http://localhost:8025

### Option B: no Docker

Runs the API with in-process PostgreSQL, a MongoDB-wire server and an AMQP broker (test-scope dependencies only):

```bash
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.edocs.LocalDevApplication
```

### Option C: your own services

```bash
cp .env.example .env   # keys and connection settings, imported by application.yml
./mvnw spring-boot:run
```

`.env` is git-ignored; OS environment variables override it. With `DEMO_MFA_CODE` empty and `OTP_ECHO_IN_APP=false`, MFA and signing codes are delivered only by email through RabbitMQ, so read them in Mailpit (http://localhost:8025).

### Demo accounts (seeded on first start)

Password for all accounts: `Demo@2026`.

| Role | Email | Notes |
| --- | --- | --- |
| Admin | j.davis@acme.corp | MFA on. The code is emailed; the `dev` profile also accepts `246810`. |
| Legal | s.jenkins@acme.corp | Drafts, shares, sends |
| Signer | m.vance@partnercorp.io | Can sign the Q3 MSA |
| Auditor | e.rostova@acme.corp | Read-only archive and audit |

In the `dev` profile, signing codes are also copied to the in-app bell (`OTP_ECHO_IN_APP`).

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local `edocs` | PostgreSQL |
| `MONGO_URI` | `mongodb://localhost:27017/edocs` | MongoDB |
| `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD` | localhost / guest | RabbitMQ |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | localhost:1025, `no-reply@edocs.local` | SMTP (Mailpit in compose) and sender address |
| `JWT_SECRET` | none (dev value in `dev`) | HS256 signing key, at least 32 bytes. **Required** outside the `dev` profile: the API refuses to start when it is empty or a published placeholder. |
| `JWT_TTL` | `PT2H` | Access-token lifetime |
| `MASTER_KEY` | none (dev value in `dev`) | Base64 AES-256 key-encryption key. **Required** outside the `dev` profile: the API refuses to start when it is empty or a published placeholder. |
| `GOOGLE_CLIENT_ID/SECRET`, `MICROSOFT_CLIENT_ID/SECRET` | placeholders | OAuth2 login providers. Redirect URI: `{base}/api/login/oauth2/code/{google\|microsoft}`. Only verified emails link to members (Google `email_verified`; Microsoft needs the `xms_edov` optional claim). MFA members need the IdP to assert `amr=mfa`. |
| `FRONTEND_URL`, `CORS_ORIGINS` | `http://localhost:5173` | OAuth redirect target, allowed origins |
| `SEED_DEMO_DATA` | `false` (`true` in `dev`) | Seed the demo workspace into an empty database (all demo accounts share one password) |

## Test

```bash
./mvnw verify
```

- Unit tests: RBAC matrix (also checked against the frontend's `rbac.ts`), envelope encryption, hash chain tamper detection, Merkle roots, HTML sanitizing, signing-order rules, compliance evaluation.
- `ContainersFlowTest`: end-to-end journeys on real PostgreSQL 17, MongoDB 8 and RabbitMQ 4 via Testcontainers. Runs whenever Docker is available, for example in CI.
- `EmbeddedFlowTest`: the same journeys on in-process stand-ins. Runs only when Docker is not available.
- `LocalInfraFlowTest`: the same journeys on locally installed PostgreSQL, MongoDB, RabbitMQ and Mailpit, using isolated `edocs_it` stores (database, Mongo database, vhost) that it wipes on each run. Run with `EDOCS_IT_LOCAL=true ./mvnw test -Dtest=LocalInfraFlowTest`.
- Coverage report: `target/site/jacoco/index.html`.

## API

Interactive OpenAPI docs are at `/api/docs`. All paths are relative to `/api`.

| Method | Path | Permission |
| --- | --- | --- |
| POST | `/auth/login`, `/auth/mfa/verify` | public (rate limited, lockout after 5 failures) |
| GET | `/oauth2/authorization/{google\|microsoft}` | public, OAuth2 authorization-code flow |
| GET / POST | `/auth/me`, `/auth/logout` | authenticated |
| GET | `/dashboard`, `/templates`, `/settings`, `/members`, `/notifications`, `/compliance/controls`, `/workflow/routing-rules`, `/archive/metrics` | authenticated |
| GET | `/audit-logs` | authenticated (own entries) · `audit:read` (all) |
| POST | `/audit-logs/verify` | `audit:read` |
| GET | `/documents`, `/documents/{id}`, `/documents/{id}/workflow` | authenticated; signers only see their own documents |
| POST | `/documents` | `document:create` |
| PATCH | `/documents/{id}` | `document:edit` |
| DELETE | `/documents/{id}` | `document:delete` (drafts only) |
| POST | `/documents/{id}/comments`, `…/comments/{cid}/resolve` | authenticated with access |
| POST | `/documents/{id}/shares`, `/send`, `/parties/{pid}/remind` | `document:share` |
| POST | `/documents/{id}/otp`, `/documents/{id}/signatures` | `document:sign`, and the caller must be the next pending signer |
| GET | `/documents/{id}/evidence`, `/archive`, `/archive/{id}` | `audit:read` |
| PUT | `/archive/{id}/legal-hold`, `/settings` | `settings:manage` |
| PUT | `/workflow/routing-rules` | `workflow:manage` |
| POST | `/templates` | `template:manage` |
| POST | `/compliance/checks` | `compliance:run` |
| POST / PATCH | `/members`, `/members/{id}` | `members:manage` (users may toggle their own MFA) |
