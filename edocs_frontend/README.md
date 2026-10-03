# Edocs frontend

React 19 + TypeScript + Tailwind CSS v4 frontend for Edocs, an enterprise platform for e-contracts, e-signatures and tamper-evident archiving. Screens follow the Figma exports in `images/`; behaviour follows `../documentation/`.

**Full module status, an inventory of every button and the roadmap: [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md).**

## Run

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # type-check + production build into dist/
npm run preview    # serve the production build
npm test           # unit tests (first: npm i -D vitest)
```

## Signing in (mock mode)

With no backend configured, the app runs on an in-browser mock database. The login page lists demo accounts; all use the password `Demo@2026`.

| Role | Email | Notes |
| --- | --- | --- |
| Administrator | j.davis@acme.corp | Two-factor sign-in on; code `246810`. “Continue with Google/Microsoft” also signs in here. |
| Legal counsel | s.jenkins@acme.corp | Drafts, shares and sends contracts. |
| Signer | m.vance@partnercorp.io | Has the Q3 agreement waiting for signature. |
| Auditor | e.rostova@acme.corp | Read-only archive and audit access. |

During signing, the one-time code arrives in the notifications menu (bell icon). Settings → **Reset demo data** restores the samples.

## Routes

| Route | Screen | Figma source |
| --- | --- | --- |
| `/login` | Sign in (password, OAuth2 SSO, MFA) | — |
| `/` | Dashboard | `Edocs Enterprise Dashboard.png` |
| `/documents` | Document list and lifecycle actions | — |
| `/editor/:id` | Smart contract editor | `Smart Contract Editor.png` |
| `/workflow/:id` | Signing workflow | `E-Signature & Workflow Orchestration.png` |
| `/sign/:id` | Signing portal | `E-Sign Document Execution.png` |
| `/archive?doc=:id` | WORM archive and audit trail | `WORM Archive & Legal Audit Trail.png` |
| `/templates`, `/compliance`, `/settings`, `/help` | Supporting screens | — |

## Backend hookup

All data goes through `src/services/api.ts`. Each function has a mock implementation and the REST call the backend must serve (also listed on `/help#api`). Copy `.env.example` to `.env` and set `VITE_API_BASE_URL` to switch to the real API.

## Structure

```
src/
  components/   shared UI, modals, menus, route guards, signature pad
  data/         domain types (from the class diagram) + seed data
  layouts/      AppShell: sidebar, top bar, notifications, user menu
  lib/          rbac, useResource, sanitize, download helpers
  pages/        one file per route
  services/     api.ts (single data boundary) + mockDb.ts
  state/        auth and toast providers
  __tests__/    unit tests (Vitest)
docs/           implementation record and way forward
```

## Design system

Tokens live in `src/index.css` under `@theme`: IBM Plex Sans for the interface, IBM Plex Mono only for machine data (hashes, IDs, timestamps), ink-black primary actions, and seal green / amber / alert red for verification states.
