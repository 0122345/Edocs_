# Edocs frontend: implementation record and way forward

_Last updated: 2 October 2026_

This document records what the frontend implements, module by module, and what every button does, then lists the work that remains. It is checked against:

- `documentation/web-technology-project-instruction.pdf`: course requirements 1–9 and the bonus items
- `documentation/Edocs_Project_Document (1).docx`: problem statement, feature list, architecture, security framework
- `documentation/edocs_class_diagram_v4.png`: domain model (User, Organization, Membership, Document/Contract, Party, Template, Signature, Workflow, AuditLog, Notification)
- `edocs_frontend/images/*.png`: Figma screens

---

## 1. How the frontend is put together

| Layer | Location | Notes |
| --- | --- | --- |
| Domain types | `src/data/types.ts` | Mirror the class diagram so the backend can return them unchanged. |
| Seed data | `src/data/mock.ts` | Demo users, documents, templates, audit log, notifications. Demo password `Demo@2026`; admin MFA code `246810`. |
| Mock backend | `src/services/mockDb.ts` | In-browser store persisted to `localStorage` (`edocs.mockdb.v1`). Notifies screens on every write. |
| **API boundary** | `src/services/api.ts` | The only place the UI gets data from. Each function has a mock implementation **and** the REST call the backend must serve. Setting `VITE_API_BASE_URL` switches every call to HTTP. |
| Auth state | `src/state/auth.tsx` | Session, login, MFA, OAuth2 SSO, logout, `can(permission)`. |
| RBAC | `src/lib/rbac.ts` | Role → permission matrix, used by route guards and buttons. |
| Feedback | `src/state/toast.tsx` | Toasts plus `useAction()`, which wraps every write and shows success or the error message. |
| Shared UI | `src/components/` | `ui.tsx` (badges, headers, loading/error), `overlay.tsx` (Modal, Menu, `Guarded` button), `modals.tsx` (Share, Invite, New document, Routing rules, Changelog, Confirm), `guards.tsx`, `SignaturePad.tsx`. |
| Pages | `src/pages/` | One file per route. |
| Safety | `src/lib/sanitize.ts` | DOMPurify allow-list for all document HTML before it touches the DOM. |

**Data flow:** page → `useResource(api.x)` → `api.x` → (mock DB | REST). Writes go through `useAction(() => api.y(...))`; the mock DB broadcasts the change and every open `useResource` reloads, so the dashboard, notifications and audit log update after any action.

---

## 2. Modules: status

Legend: ✅ done in the frontend (mock-backed) · 🔌 done, needs a real backend endpoint · ⏳ not started

| # | Module (source) | Status | What exists |
| --- | --- | --- | --- |
| 1 | **Authentication**: OAuth2 (instructions §6; doc “SSO integration (OIDC)”) | ✅🔌 | `/login`: email/password, **Continue with Google / Microsoft** (OAuth2 authorization-code redirect to `/oauth2/authorization/{provider}` when a backend is set), MFA step for accounts with `mfaEnabled`, logout from sidebar and avatar menu, session persisted, redirect back to the page you came from (`?next=`). |
| 2 | **RBAC** (instructions §8; doc “Granular Access Control”) | ✅🔌 | 4 roles × 12 permissions in `rbac.ts`. Route guards (`/sign`, `/archive`), permission-gated buttons (`Guarded` shows *why* it's disabled), role-filtered nav, signers only see their own documents. The mock API also enforces permissions, as the server must. |
| 3 | **Dashboard / analytics** (Figma *Enterprise Dashboard*; doc “Advanced Analytics”) | ✅ | KPIs (pending count is live), risk distribution with weekly/monthly, bottlenecks + routing-rules editor, live audit log with search, issue filter, pagination, links into the archive. |
| 4 | **Documents & contract lifecycle** (class diagram Document/Contract/Party) | ✅🔌 | `/documents`: status tabs, search, row actions (open, workflow, audit trail, send, delete draft), “waiting for your signature” highlighting. **New document** modal: template, title, value, effective/expiry dates, parties in signing order with role. |
| 5 | **Smart contract editor** (Figma *Smart Contract Editor*; doc “Smart Contract Builder”, “Real-Time Collaboration”) | ✅ | `/editor/:id`: File/Edit/Insert menus, paragraph style, font, size, B/I/U, undo/redo, redline toggle, AI clause suggestion + insert, compliance score reacting to content, comments (post/reply/resolve), version history + full changelog, save draft (new version + audit entry), unsaved-changes warning, send for signatures, read-only for sealed docs or non-editor roles, download HTML, print/PDF. |
| 6 | **Workflow orchestration** (Figma *E-Signature & Workflow*; class diagram Workflow) | ✅🔌 | `/workflow/:id`: 4-stage BPMN-style pipeline derived from document state, parties in signing order with signed dates, **Send reminder**, send for signature, export evidence package, activity feed from the audit log. |
| 7 | **E-signature execution** (Figma *E-Sign Document Execution*; class diagram Signature) | ✅🔌 | `/sign/:id`: real document rendered (sanitized), page/zoom/full screen, **find in document** with highlight count, EN/FR language switch, identity info popover, draw/type/certificate signature, **OTP sent through the notification channel and verified by the API**, ESIGN/UETA consent, signing seals the document (SHA-256 of content + signature), anchors it, notifies parties, and moves the workflow on. Already-signed / not-yet-sent states. |
| 8 | **WORM archive & audit trail** (Figma *WORM Archive*; class diagram AuditLog) | ✅🔌 | `/archive`: query + format + legal-hold + crypto filters, result list, record view (hash, copy, WORM state, cryptographic proof JSON), apply/release legal hold (admin, with confirmation), audit timeline per document, verify hash chain, export evidence package (JSON). |
| 9 | **Notifications** (instructions §7 RabbitMQ; class diagram Notification) | ✅🔌 | Bell menu with unread count, channel icons (email / SMS / in-app), mark one/all as read, deep links. Every send/share/invite/OTP/signature event produces a notification, which is the UI side of the RabbitMQ events. |
| 10 | **Templates** (doc “200+ pre-vetted templates”) | ✅🔌 | Search, **Use template** (prefilled New document), **Preview**, **New template** (legal/admin). |
| 11 | **Compliance** (doc “Security & Compliance Framework”) | ✅ | Controls with pass/warn, **Run checks now** (admin), remediation links for failing controls, full risk assessment section. |
| 12 | **Settings & members** (class diagram Organization/Membership) | ✅🔌 | Workspace settings form (admin; read-only otherwise), your account + own MFA toggle, members list with role change and deactivate/reactivate, invite, permission matrix, reset demo data. |
| 13 | **Sharing** | ✅🔌 | Share modal on every page: copy link; invite by email with view/comment/sign access when on a document. |
| 14 | **Help center** | ✅ | Getting started, role guide, security, compliance, **API contract table**, contact support (mailto). Footer links deep-link into it. |
| 15 | **Responsive layout** (instructions §3) | ✅ | Sidebar becomes a drawer below 1024px; top tabs move to a scrollable row; tables scroll horizontally; checked at 390px. |
| 16 | **Unit tests** (bonus “Testing & QA”) | 🟡 | `src/__tests__/rbac.test.ts`, `src/__tests__/api.test.ts` (auth, MFA, deactivation, document permissions, signer visibility, delete rules, OTP rejection, sealing). **Not yet run**: installing Vitest was interrupted by low memory. Run `npm i -D vitest && npm test`. |

---

## 3. Button inventory: what every control does

Permission in brackets means the control is disabled or hidden for other roles.

### App shell (every page)
| Control | Action |
| --- | --- |
| ☰ (mobile / editor) | Opens the navigation drawer; Esc or backdrop closes it. |
| Sidebar links | Dashboard, Documents, Templates, Audit logs [audit:read], Compliance, Settings. |
| Invite team [members:manage] | Opens the invite modal → `api.inviteMember` → member added, invitation notification + audit entry. |
| Help center | `/help`. |
| Log out | Ends the session (`api.logout`) and returns to `/login`. |
| Breadcrumb “Documents” | `/documents`. |
| Overview / Signatures / Audit trail / Settings tabs | `/`, the current document's workflow (or the out-for-signature list), its archive record [audit:read], `/settings`. |
| Share | Share modal: copy link; on document pages, invite by email with access level [document:share] → `api.shareDocument`. |
| New document [document:create] | New document modal → `api.createDocument` → opens the editor. |
| 🔔 | Notifications menu: open item (marks read, follows link), mark all as read. |
| ? | `/help`. |
| Avatar | Account menu: account and settings, help, log out. |
| Footer links | `/help#security`, `/help#compliance`, `/help#api`. |

### Login
| Control | Action |
| --- | --- |
| Continue with Google / Microsoft | OAuth2 redirect (backend) or admin session (mock). |
| Sign in | `api.login`; goes to the MFA step if the account requires it. |
| Verify and sign in | `api.verifyMfa`. |
| Use a different account | Back to the password step. |
| Demo account rows (mock only) | Fill email + password for that role. |

### Dashboard
| Control | Action |
| --- | --- |
| Weekly / Monthly | Switches risk distribution data. |
| View full risk assessment | `/compliance#risk`. |
| Optimize routing rules [workflow:manage] | Routing rules modal → `api.saveRoutingRules`. |
| Search audit logs / Filter | Client filter by text; “Issues only” shows pending/failed. |
| Document name in log | `/archive?doc=…` [audit:read]. |
| Previous / page numbers / Next | Pagination. |

### Documents
| Control | Action |
| --- | --- |
| New document / Create your first document [document:create] | New document modal. |
| Status tabs | Filter via `?status=` (shareable URL). |
| Search | Title, type or party. |
| Title link | Editor [document:edit] or workflow. |
| Sign (shown when it's your turn) [document:sign] | `/sign/:id`. |
| ⋯ → Open in editor / View signing workflow / View audit trail / Send for signature / Delete draft | Navigate, or `api.sendForSignature`, or confirm → `api.deleteDocument` (drafts only). |
| Clear filters | Resets search and status. |

### New document modal
Template, Title, Value, Effective/Expires (expiry must be after effective), Add party, party name/email/role, 🗑 remove party, **Create and open editor**, Cancel.

### Editor
| Control | Action |
| --- | --- |
| File → Save draft / Download as HTML / Print or save as PDF / Version history | Save, export sanitized HTML, browser print (print stylesheet hides chrome), changelog modal. |
| Edit → Undo / Redo / Select all / Clear formatting | Editor commands. |
| Insert → Today's date / Signature block / GDPR data retention clause / Divider | Inserts at the cursor (or at the end). |
| Redline | Shows or hides tracked changes (`del`/`ins`). |
| ↶ ↷, Paragraph style, Font, Size, **B** *I* U | Formatting on the selection. |
| AI clause suggestion | Shows the GDPR suggestion, or confirms there are none. |
| Insert clause / Dismiss | Adds the clause (score goes to 100%) / hides the suggestion. |
| View full changelog | Changelog modal. |
| Reply / Resolve | Prefills @mention / `api.resolveComment`. |
| Post comment | `api.addComment`. |
| Save draft | `api.saveDocument` → new version + audit entry. Disabled when nothing changed or read-only. |
| Send for signatures [document:share] | Saves if needed, `api.sendForSignature` → emails signers → workflow page. |

### Workflow
Export evidence package [audit:read] (JSON download) · Send for signature (drafts) [document:share] · Sign with QES (only when you have a signature to give) [document:sign] · Send reminder per unsigned signer [document:share] · View full audit ledger [audit:read].

### Signing portal
Page ‹ ›, zoom − +, 🔍 find in document (with match count, close), full screen toggle, EN/FR, ⓘ identity explanation, Draw / Type / Certificate tabs, Clear canvas, certificate file picker, **Send code / Resend** (30 s cooldown; code delivered as a notification in mock mode), two consent checkboxes, **Sign and seal document** (disabled with a list of what's missing until ready), View signing workflow / Back to documents after signing.

### Archive
Export evidence package [audit:export] · Verify hash chain · Run query / Reset · result rows (select record) · copy SHA-256 · Inspect / Hide cryptographic proof · Apply / Release legal hold [settings:manage] with confirmation · Open signing workflow.

### Templates
Search · Clear search · Use template [document:create] · Preview · New template [template:manage] → create.

### Compliance
Run checks now [compliance:run] · remediation buttons (Open settings, Review members) · Review documents waiting on legal.

### Settings
Workspace fields + Save settings / Discard changes [settings:manage] · Two-factor sign-in (own account) · Invite [members:manage] · per-member role select and Deactivate/Reactivate [members:manage; not yourself] · Reset demo data (mock only, with confirmation).

### Help
Section anchors · Email support (mailto).

---

## 4. Requirements coverage (course instructions)

| Instruction | Frontend status |
| --- | --- |
| 1. Problem, users, requirements, user stories | Covered by the project document; the UI implements the stories (create → negotiate → sign → archive). |
| 2. Domain & data modeling | Types in `src/data/types.ts` follow the class diagram; the backend schema (PK/FK, indexes) is still to be written. |
| 3. Responsive pages in a modern framework | ✅ React 19 + TypeScript + Tailwind v4, responsive down to 390px. |
| 4. Backend architecture | ⏳ Backend. The frontend is ready: `api.ts` lists every endpoint (also shown on `/help#api`). |
| 5. Relational + NoSQL storage | ⏳ Backend. Suggested split below. |
| 6. Secure auth incl. OAuth2; performance | ✅ UI flows for OAuth2/OIDC + MFA; 🔌 needs the auth server. |
| 7. RabbitMQ for email/SMS/events | 🔌 UI consumes the resulting notifications; producers/consumers belong to the backend. |
| 8. RBAC | ✅ UI + mock enforcement; 🔌 server must enforce the same matrix. |
| 9. Git with meaningful commits + PR | ⏳ The project folder is **not a git repository yet**. |
| Bonus: DevOps | ⏳ |
| Bonus: Testing & QA | 🟡 Tests written, not yet run (see module 16). |

---

## 5. Way forward

### Next steps, in order
1. **Put the project under Git** (`git init`, `.gitignore` is already present in `edocs_frontend/`), commit the frontend in logical commits, push, and open the PR the course requires.
2. **Run the unit tests**: `npm i -D vitest && npm test`. Add component tests later with `@testing-library/react` + `jsdom` (sign flow, guards).
3. **Build the backend against `src/services/api.ts`.** Each mock function documents its REST path, request body and response type. Recommended, per the project document's “modular monolith first” decision: Spring Boot or Node.js (NestJS) with modules `auth`, `documents`, `signatures`, `workflow`, `audit`, `notifications`, `members`.
   - **PostgreSQL** (relational): users, organizations, memberships, documents (metadata), parties, signatures, workflows, audit_logs (append-only, with hash-chain column), templates (metadata).
   - **MongoDB** (documents): document bodies and versions, template bodies, comment threads, evidence packages.
   - **RabbitMQ**: exchanges `document.events` and `notification.commands`; workers for email, SMS (OTP), and audit anchoring. The UI already shows the notification records these produce.
   - **OAuth2 / OIDC**: Spring Security OAuth2 Client (or Passport) with Google and Microsoft; issue a short-lived JWT plus a refresh cookie. `api.ts` already sends `Authorization: Bearer` and `credentials: 'include'`.
   - **RBAC**: enforce `src/lib/rbac.ts`'s matrix server-side (e.g. Spring `@PreAuthorize("hasAuthority('document:sign')")`). Keep the matrix in one shared place (endpoint `/auth/permissions` or a generated file) so UI and server can't drift.
4. **Switch the UI to the backend**: copy `.env.example` → `.env`, set `VITE_API_BASE_URL`, then remove `mockDb.ts`, the seed data and the demo-account panel. Replace `api.subscribe` with a WebSocket/SSE stream so screens still refresh on server events.
5. **Replace demo-only behaviour**:
   - OTP codes are generated in the browser and shown as notifications; move generation and delivery to the SMS/email workers.
   - Document hashes are SHA-256 of content in the browser; real sealing should hash the rendered PDF on the server and anchor it via the chosen chain.
   - Certificate signing only records the file name; real QES needs a trust service provider integration (PKCS#12 never leaves the user's device or HSM).
   - KPIs, risk bands, bottlenecks, collaborators' presence and AI suggestions are seed values; they need analytics and AI endpoints.
6. **Editor hardening**: `document.execCommand` is deprecated. Move to a structured editor (TipTap/ProseMirror or Lexical) for real tracked changes, template variables (`{{field}}` fill-in), and real-time collaboration (Yjs over WebSocket).
7. **DevOps (bonus)**: Dockerfile for the frontend (`npm run build` → nginx serving `dist/` with SPA fallback), docker-compose with backend + PostgreSQL + MongoDB + RabbitMQ, and a CI workflow running type-check, tests and build on every PR.
8. **Quality pass**: code-split routes with `React.lazy` (bundle is ~480 KB / 146 KB gzipped), add ESLint, run an accessibility audit (axe) and Lighthouse, add full i18n beyond the signing portal.

### Known limitations
- Mock data lives in each browser's `localStorage`; two browsers don't share state.
- The Figma *E-Signature & Workflow* sidebar highlights “Audit Logs”; the implementation highlights the active route instead.
- Pages 2+ in the signing viewer are placeholders until real PDF rendering (e.g. PDF.js) is added.
