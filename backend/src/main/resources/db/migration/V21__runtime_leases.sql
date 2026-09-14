CREATE TABLE runtime_leases (
    lease_key VARCHAR(200) PRIMARY KEY,
    owner_id VARCHAR(200) NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_runtime_leases_expires_at ON runtime_leases (expires_at);
