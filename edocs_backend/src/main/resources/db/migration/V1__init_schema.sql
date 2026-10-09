-- Edocs relational schema (PostgreSQL); audit log, notifications and encrypted bodies live in MongoDB.

-- Identity & access management
CREATE TABLE organizations (
    org_id           UUID         PRIMARY KEY,
    name             VARCHAR(160) NOT NULL,
    tax_id           VARCHAR(64),
    domain           VARCHAR(120) UNIQUE,
    plan             VARCHAR(12)  NOT NULL DEFAULT 'ENTERPRISE' CHECK (plan IN ('FREE', 'TEAM', 'ENTERPRISE')),
    admin_email      VARCHAR(254) NOT NULL,
    signature_level  VARCHAR(3)   NOT NULL DEFAULT 'QES' CHECK (signature_level IN ('SES', 'AES', 'QES')),
    retention_years  INT          NOT NULL DEFAULT 7 CHECK (retention_years BETWEEN 1 AND 30),
    require_2fa      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    user_id                UUID         PRIMARY KEY,
    full_name              VARCHAR(120) NOT NULL,
    email                  VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    phone                  VARCHAR(32),
    password_hash          VARCHAR(100),
    role                   VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN', 'LEGAL', 'SIGNER', 'AUDITOR')),
    kyc_status             VARCHAR(10)  NOT NULL DEFAULT 'NONE' CHECK (kyc_status IN ('VERIFIED', 'PENDING', 'NONE')),
    mfa_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login             TIMESTAMPTZ,
    failed_login_attempts  INT          NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
    membership_id  UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    org_id         UUID        NOT NULL REFERENCES organizations (org_id) ON DELETE CASCADE,
    joined_at      DATE        NOT NULL DEFAULT CURRENT_DATE,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_membership_user_org UNIQUE (user_id, org_id)
);
CREATE INDEX ix_memberships_org ON memberships (org_id);

-- Core document domain (ContentItem -> Document -> Contract, ContentItem -> Template)
CREATE TABLE templates (
    template_id  UUID         PRIMARY KEY,
    org_id       UUID         NOT NULL REFERENCES organizations (org_id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    category     VARCHAR(80)  NOT NULL,
    content      TEXT         NOT NULL,
    usage_count  INT          NOT NULL DEFAULT 0 CHECK (usage_count >= 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_templates_org_updated ON templates (org_id, updated_at DESC);

CREATE TABLE template_variables (
    template_id  UUID        NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
    position     INT         NOT NULL,
    variable     VARCHAR(64) NOT NULL,
    PRIMARY KEY (template_id, position)
);

CREATE TABLE documents (
    doc_id         UUID         PRIMARY KEY,
    org_id         UUID         NOT NULL REFERENCES organizations (org_id) ON DELETE CASCADE,
    owner_id       UUID         NOT NULL REFERENCES users (user_id),
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(80)  NOT NULL,
    format         VARCHAR(4)   NOT NULL CHECK (format IN ('PDF', 'DOCX')),
    version        INT          NOT NULL DEFAULT 1 CHECK (version >= 1),
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'IN_REVIEW', 'OUT_FOR_SIGNATURE', 'SIGNED', 'ARCHIVED')),
    encrypted_key  VARCHAR(255) NOT NULL,
    legal_hold     BOOLEAN      NOT NULL DEFAULT FALSE,
    hold_until     DATE,
    sha256         VARCHAR(64)  CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    anchor_tx      VARCHAR(80),
    size_kb        INT          NOT NULL DEFAULT 1 CHECK (size_kb > 0),
    pages          INT          NOT NULL DEFAULT 1 CHECK (pages > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_documents_hold CHECK (legal_hold OR hold_until IS NULL),
    CONSTRAINT ck_documents_sealed CHECK (status NOT IN ('SIGNED', 'ARCHIVED') OR sha256 IS NOT NULL)
);
CREATE INDEX ix_documents_org_updated ON documents (org_id, updated_at DESC);
CREATE INDEX ix_documents_org_status ON documents (org_id, status);
CREATE INDEX ix_documents_sealed ON documents (org_id) WHERE sha256 IS NOT NULL;

CREATE TABLE contracts (
    doc_id          UUID           PRIMARY KEY REFERENCES documents (doc_id) ON DELETE CASCADE,
    template_id     UUID           REFERENCES templates (template_id) ON DELETE SET NULL,
    terms           TEXT,
    effective_date  DATE,
    expiry_date     DATE,
    contract_value  NUMERIC(15, 2) CHECK (contract_value >= 0),
    CONSTRAINT ck_contracts_dates CHECK (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date)
);
CREATE INDEX ix_contracts_template ON contracts (template_id);

CREATE TABLE parties (
    party_id         UUID         PRIMARY KEY,
    contract_id      UUID         NOT NULL REFERENCES contracts (doc_id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    email            VARCHAR(254) NOT NULL,
    role             VARCHAR(10)  NOT NULL CHECK (role IN ('SIGNER', 'APPROVER', 'VIEWER')),
    signature_order  INT          NOT NULL CHECK (signature_order >= 1),
    signed_at        TIMESTAMPTZ,
    CONSTRAINT uq_party_contract_email UNIQUE (contract_id, email)
);
CREATE INDEX ix_parties_email ON parties (email);

CREATE TABLE document_shares (
    share_id    UUID         PRIMARY KEY,
    doc_id      UUID         NOT NULL REFERENCES documents (doc_id) ON DELETE CASCADE,
    email       VARCHAR(254) NOT NULL,
    access      VARCHAR(8)   NOT NULL CHECK (access IN ('VIEW', 'COMMENT', 'SIGN')),
    shared_by   UUID         NOT NULL REFERENCES users (user_id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_share_doc_email UNIQUE (doc_id, email)
);
CREATE INDEX ix_shares_email ON document_shares (email);

-- Operations: signatures, workflows, routing rules, compliance, OTP challenges
CREATE TABLE signatures (
    sig_id         UUID        PRIMARY KEY,
    doc_id         UUID        NOT NULL REFERENCES documents (doc_id) ON DELETE RESTRICT,
    party_id       UUID        NOT NULL UNIQUE REFERENCES parties (party_id) ON DELETE RESTRICT,
    signer_id      UUID        REFERENCES users (user_id),
    type           VARCHAR(3)  NOT NULL CHECK (type IN ('SES', 'AES', 'QES')),
    mode           VARCHAR(20) NOT NULL,
    signed_at      TIMESTAMPTZ NOT NULL,
    certificate    TEXT        NOT NULL,
    ip_address     VARCHAR(45),
    evidence_hash  VARCHAR(64) NOT NULL CHECK (evidence_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_signatures_doc ON signatures (doc_id);

CREATE TABLE workflows (
    workflow_id   UUID         PRIMARY KEY,
    doc_id        UUID         NOT NULL UNIQUE REFERENCES documents (doc_id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    trigger_type  VARCHAR(10)  NOT NULL CHECK (trigger_type IN ('MANUAL', 'ON_CREATE', 'ON_SEND')),
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    current_step  INT          NOT NULL DEFAULT 1 CHECK (current_step BETWEEN 1 AND 5),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workflow_steps (
    step_id            UUID         PRIMARY KEY,
    workflow_id        UUID         NOT NULL REFERENCES workflows (workflow_id) ON DELETE CASCADE,
    position           INT          NOT NULL CHECK (position >= 1),
    title              VARCHAR(80)  NOT NULL,
    description        VARCHAR(255) NOT NULL,
    completion_status  VARCHAR(10)  NOT NULL CHECK (completion_status IN ('COMPLETED', 'APPROVED', 'VERIFIED')),
    CONSTRAINT uq_workflow_step_position UNIQUE (workflow_id, position)
);

CREATE TABLE routing_rules (
    rule_id      UUID         PRIMARY KEY,
    org_id       UUID         NOT NULL REFERENCES organizations (org_id) ON DELETE CASCADE,
    code         VARCHAR(40)  NOT NULL,
    label        VARCHAR(120) NOT NULL,
    description  VARCHAR(255) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_rule_org_code UNIQUE (org_id, code)
);

CREATE TABLE compliance_controls (
    control_id       UUID         PRIMARY KEY,
    org_id           UUID         NOT NULL REFERENCES organizations (org_id) ON DELETE CASCADE,
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(120) NOT NULL,
    framework        VARCHAR(60)  NOT NULL,
    status           VARCHAR(4)   NOT NULL CHECK (status IN ('PASS', 'WARN')),
    last_checked_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_control_org_code UNIQUE (org_id, code)
);

CREATE TABLE otp_challenges (
    challenge_id  UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    doc_id        UUID        REFERENCES documents (doc_id) ON DELETE CASCADE,
    purpose       VARCHAR(8)  NOT NULL CHECK (purpose IN ('MFA', 'SIGNING')),
    code_hash     VARCHAR(64) NOT NULL CHECK (char_length(code_hash) = 64),
    expires_at    TIMESTAMPTZ NOT NULL,
    attempts      INT         NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_otp_signing_doc CHECK (purpose <> 'SIGNING' OR doc_id IS NOT NULL)
);
CREATE INDEX ix_otp_lookup ON otp_challenges (user_id, purpose, doc_id, created_at DESC);
