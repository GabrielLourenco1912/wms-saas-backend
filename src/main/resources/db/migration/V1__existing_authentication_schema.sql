-- Initial authentication, account security and operational profile schema.
-- Development baseline: reset a disposable local database after changing this version.
CREATE TABLE users (
    ulid VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE TABLE refresh_tokens (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    replaced_by_token VARCHAR(255),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(ulid) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

CREATE TABLE access_profiles (
    code VARCHAR(32) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE profile_permissions (
    profile_code VARCHAR(32) NOT NULL,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (profile_code, permission),
    CONSTRAINT fk_permissions_profile FOREIGN KEY (profile_code) REFERENCES access_profiles(code)
);
INSERT INTO access_profiles (code, name, active, version) VALUES
    ('GESTOR', 'Gestor', TRUE, 0), ('OPERADOR', 'Operador', TRUE, 0);
INSERT INTO profile_permissions (profile_code, permission) VALUES
    ('GESTOR', 'WAREHOUSE_VIEW'),
    ('GESTOR', 'WAREHOUSE_CREATE'),
    ('GESTOR', 'WAREHOUSE_EDIT'),
    ('GESTOR', 'PRODUCT_VIEW'),
    ('GESTOR', 'PRODUCT_CREATE'),
    ('GESTOR', 'PRODUCT_EDIT'),
    ('GESTOR', 'PRE_RECEIPT_VIEW'),
    ('GESTOR', 'PRE_RECEIPT_CREATE'),
    ('GESTOR', 'PRE_RECEIPT_EDIT'),
    ('GESTOR', 'RECEIPT_VIEW'),
    ('GESTOR', 'PUTAWAY_VIEW'),
    ('GESTOR', 'ORDER_VIEW'),
    ('GESTOR', 'ORDER_CREATE'),
    ('GESTOR', 'ORDER_EDIT'),
    ('GESTOR', 'ORDER_CANCEL'),
    ('GESTOR', 'WAVE_VIEW'),
    ('GESTOR', 'WAVE_CREATE'),
    ('GESTOR', 'WAVE_CANCEL'),
    ('GESTOR', 'PICKING_VIEW'),
    ('GESTOR', 'TRANSFER_VIEW'),
    ('GESTOR', 'STOCK_VIEW'),
    ('GESTOR', 'STOCK_BLOCK'),
    ('GESTOR', 'STOCK_UNBLOCK'),
    ('GESTOR', 'AUDIT_VIEW'),
    ('GESTOR', 'SHIPMENT_VIEW'),
    ('GESTOR', 'INVENTORY_VIEW'),
    ('GESTOR', 'INVENTORY_ADJUST'),
    ('GESTOR', 'TASK_VIEW'),
    ('GESTOR', 'TASK_CANCEL'),
    ('GESTOR', 'TASK_QUANTITY_OVERRIDE'),
    ('GESTOR', 'USER_VIEW'),
    ('GESTOR', 'USER_CREATE'),
    ('GESTOR', 'USER_EDIT'),
    ('GESTOR', 'USER_PROFILE_ASSIGN'),
    ('GESTOR', 'PROFILE_VIEW'),
    ('GESTOR', 'PROFILE_EDIT'),
    ('OPERADOR', 'WAREHOUSE_VIEW'),
    ('OPERADOR', 'PRODUCT_VIEW'),
    ('OPERADOR', 'RECEIPT_VIEW'),
    ('OPERADOR', 'RECEIPT_EXECUTE'),
    ('OPERADOR', 'PUTAWAY_VIEW'),
    ('OPERADOR', 'PUTAWAY_EXECUTE'),
    ('OPERADOR', 'PICKING_VIEW'),
    ('OPERADOR', 'PICKING_EXECUTE'),
    ('OPERADOR', 'PICKING_REPORT_DIVERGENCE'),
    ('OPERADOR', 'TRANSFER_VIEW'),
    ('OPERADOR', 'TRANSFER_EXECUTE'),
    ('OPERADOR', 'STOCK_VIEW'),
    ('OPERADOR', 'SHIPMENT_VIEW'),
    ('OPERADOR', 'SHIPMENT_EXECUTE'),
    ('OPERADOR', 'INVENTORY_VIEW'),
    ('OPERADOR', 'INVENTORY_EXECUTE'),
    ('OPERADOR', 'TASK_VIEW');

ALTER TABLE users ADD COLUMN profile_code VARCHAR(32) NOT NULL DEFAULT 'OPERADOR';
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'INVITED';
ALTER TABLE users ADD COLUMN failed_authentication_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP(6);
ALTER TABLE users ADD COLUMN status_before_lock VARCHAR(20);
ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP(6);
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP(6);
ALTER TABLE users ADD CONSTRAINT fk_users_profile FOREIGN KEY (profile_code) REFERENCES access_profiles(code);
CREATE TABLE auth_challenges (
    id VARCHAR(26) NOT NULL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_digest VARCHAR(64) NOT NULL,
    code_nonce VARCHAR(32) NOT NULL,
    target_email VARCHAR(254) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    failed_attempts INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    requested_user_agent VARCHAR(255),
    requested_ip_address VARCHAR(45),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_challenges_user FOREIGN KEY (user_id) REFERENCES users(ulid) ON DELETE CASCADE
);
CREATE INDEX idx_auth_challenges_user_active ON auth_challenges(user_id, consumed_at, expires_at);
CREATE INDEX idx_auth_challenges_expiration ON auth_challenges(expires_at);
