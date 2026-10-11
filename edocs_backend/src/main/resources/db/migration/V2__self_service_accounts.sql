-- Self-service sign-up: accounts created through /auth/signup stay unverified until the emailed code is entered.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

-- One-time codes now also verify a new account's email and reset forgotten passwords.
ALTER TABLE otp_challenges DROP CONSTRAINT otp_challenges_purpose_check;
ALTER TABLE otp_challenges ADD CONSTRAINT ck_otp_purpose CHECK (purpose IN ('MFA', 'SIGNING', 'VERIFY', 'RESET'));
