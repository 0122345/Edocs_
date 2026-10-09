package com.edocs.security;

// Fine-grained permissions; names match edocs_frontend/src/lib/rbac.ts so both sides share one matrix.
public enum Permission {
    DOCUMENT_CREATE("document:create"),
    DOCUMENT_EDIT("document:edit"),
    DOCUMENT_SIGN("document:sign"),
    DOCUMENT_SHARE("document:share"),
    DOCUMENT_DELETE("document:delete"),
    WORKFLOW_MANAGE("workflow:manage"),
    AUDIT_READ("audit:read"),
    AUDIT_EXPORT("audit:export"),
    COMPLIANCE_RUN("compliance:run"),
    TEMPLATE_MANAGE("template:manage"),
    MEMBERS_MANAGE("members:manage"),
    SETTINGS_MANAGE("settings:manage");

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
