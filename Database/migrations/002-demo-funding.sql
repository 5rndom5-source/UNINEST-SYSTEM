-- Additive demo-only workflow. No live payment credentials or bursary documents.
CREATE TABLE IF NOT EXISTS demo_payment_plans (
    booking_id INT PRIMARY KEY,
    deposit DECIMAL(10,2) NOT NULL,
    rent DECIMAL(10,2) NOT NULL,
    funding_source VARCHAR(16) NULL,
    provider_name VARCHAR(100) NULL,
    funding_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    covered_deposit DECIMAL(10,2) NOT NULL DEFAULT 0,
    covered_rent DECIMAL(10,2) NOT NULL DEFAULT 0,
    verification_reference VARCHAR(100) NULL,
    terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    terms_version INT NOT NULL DEFAULT 0,
    student_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    sponsor_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    external_reference VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
