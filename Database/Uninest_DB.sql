-- ============================================
-- UNINEST DATABASE SCHEMA
-- Based on the UniNest ERD with an added settings table
-- ============================================

CREATE DATABASE IF NOT EXISTS uninest_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE uninest_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS listing_photo_details;
DROP TABLE IF EXISTS listing_photo_rooms;
DROP TABLE IF EXISTS listing_photo_uploads;
DROP TABLE IF EXISTS listing_details;

DROP TABLE IF EXISTS transaction_history;
DROP TABLE IF EXISTS demo_payment_plans;
DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS settings;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS favorites;
DROP TABLE IF EXISTS maintenance_requests;
DROP TABLE IF EXISTS inquiries;
DROP TABLE IF EXISTS room_amenities;
DROP TABLE IF EXISTS room_images;
DROP TABLE IF EXISTS tenancy_records;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS amenities;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS admins;
DROP TABLE IF EXISTS landlords;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- USER & ACCOUNT MANAGEMENT
-- ============================================

CREATE TABLE users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    password_changed_at DATETIME(3) NULL,
    phone VARCHAR(20),
    role ENUM('student', 'landlord', 'admin') NOT NULL,
    status ENUM('active', 'inactive', 'suspended', 'pending_verification') NOT NULL DEFAULT 'active',
    last_login DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_role (role),
    INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE documents (
    document_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    document_type ENUM(
        'national_id',
        'passport',
        'student_card',
        'proof_of_registration',
        'proof_of_address',
        'ownership_proof',
        'lease_document',
        'other'
    ) NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    upload_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verification_status ENUM('pending', 'approved', 'rejected') NOT NULL DEFAULT 'pending',
    verified_by INT,
    rejection_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_documents_verified_by
        FOREIGN KEY (verified_by) REFERENCES users(user_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_documents_user (user_id),
    INDEX idx_documents_status (verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE students (
    student_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE,
    institution VARCHAR(120),
    program VARCHAR(120),
    year_level VARCHAR(50),
    emergency_contact VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_students_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_students_user (user_id),
    INDEX idx_students_institution (institution)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE landlords (
    landlord_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE,
    business_name VARCHAR(120),
    id_number VARCHAR(50) NOT NULL UNIQUE,
    business_address VARCHAR(255),
    approval_status ENUM('pending', 'approved', 'rejected') NOT NULL DEFAULT 'pending',
    verification_date DATETIME,
    bank_account VARCHAR(80),
    bank_name VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_landlords_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_landlords_user (user_id),
    INDEX idx_landlords_approval_status (approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admins (
    admin_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE,
    admin_position VARCHAR(120),
    access_level ENUM('super_admin', 'admin', 'moderator') NOT NULL DEFAULT 'admin',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admins_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_admins_user (user_id),
    INDEX idx_admins_access_level (access_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Added because the ERD did not include a settings table.
-- One row stores the dashboard/account preferences for each user.
CREATE TABLE settings (
    setting_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE,
    theme ENUM('light', 'dark', 'system') NOT NULL DEFAULT 'light',
    language VARCHAR(20) NOT NULL DEFAULT 'en',
    timezone VARCHAR(80) NOT NULL DEFAULT 'Africa/Johannesburg',
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    sms_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    booking_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    message_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    maintenance_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    profile_visibility ENUM('private', 'landlords_only', 'public') NOT NULL DEFAULT 'landlords_only',
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_settings_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_settings_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(user_id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- ACCOMMODATION MANAGEMENT
-- ============================================

CREATE TABLE amenities (
    amenity_id INT PRIMARY KEY AUTO_INCREMENT,
    amenity_name VARCHAR(80) NOT NULL UNIQUE,
    icon_url VARCHAR(255),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rooms (
    room_id INT PRIMARY KEY AUTO_INCREMENT,
    landlord_id INT NOT NULL,
    room_number VARCHAR(50) NOT NULL,
    room_type ENUM('single_room', 'shared_room', 'studio', 'apartment', 'house', 'residence') NOT NULL,
    location VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    capacity INT NOT NULL DEFAULT 1,
    description TEXT,
    availability_status ENUM('available', 'occupied', 'maintenance', 'inactive') NOT NULL DEFAULT 'available',
    approval_status ENUM('pending', 'approved', 'denied') NOT NULL DEFAULT 'pending',
    admin_note VARCHAR(255),
    reviewed_by INT,
    reviewed_at DATETIME,
    furnishing ENUM('unfurnished', 'semi_furnished', 'fully_furnished') NOT NULL DEFAULT 'unfurnished',
    property_size VARCHAR(50),
    bedrooms INT,
    bathrooms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    area VARCHAR(100) NULL DEFAULT NULL,
    CONSTRAINT fk_rooms_landlord
        FOREIGN KEY (landlord_id) REFERENCES landlords(landlord_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_rooms_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(user_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_rooms_landlord (landlord_id),
    INDEX idx_rooms_location (location),
    INDEX idx_rooms_price (price),
    INDEX idx_rooms_availability (availability_status),
    INDEX idx_rooms_approval_status (approval_status),
    FULLTEXT INDEX ft_rooms_search (room_number, location, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE room_images (
    category VARCHAR(32) NOT NULL DEFAULT 'building',
    image_id INT PRIMARY KEY AUTO_INCREMENT,
    room_id INT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_room_images_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_room_images_room (room_id),
    INDEX idx_room_images_primary (room_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE room_amenities (
    room_id INT NOT NULL,
    amenity_id INT NOT NULL,
    PRIMARY KEY (room_id, amenity_id),
    CONSTRAINT fk_room_amenities_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_room_amenities_amenity
        FOREIGN KEY (amenity_id) REFERENCES amenities(amenity_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bookings (
    booking_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    room_id INT NOT NULL,
    booking_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_in DATE NOT NULL,
    check_out DATE,
    booking_status ENUM('pending', 'confirmed', 'cancelled', 'completed') NOT NULL DEFAULT 'pending',
    cancellation_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_student
        FOREIGN KEY (student_id) REFERENCES students(student_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_bookings_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_bookings_student (student_id),
    INDEX idx_bookings_room (room_id),
    INDEX idx_bookings_status (booking_status),
    INDEX idx_bookings_check_in (check_in)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenancy_records (
    tenancy_id INT PRIMARY KEY AUTO_INCREMENT,
    booking_id INT NOT NULL,
    tenancy_start DATE NOT NULL,
    tenancy_end DATE,
    tenancy_status ENUM('active', 'completed', 'terminated') NOT NULL DEFAULT 'active',
    lease_document_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenancy_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_tenancy_booking (booking_id),
    INDEX idx_tenancy_status (tenancy_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- COMMUNICATION & SUPPORT
-- ============================================

CREATE TABLE inquiries (
    inquiry_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    landlord_id INT NOT NULL,
    room_id INT NOT NULL,
    message TEXT NOT NULL,
    inquiry_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reply_status ENUM('pending', 'replied', 'resolved') NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiries_student
        FOREIGN KEY (student_id) REFERENCES students(student_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_inquiries_landlord
        FOREIGN KEY (landlord_id) REFERENCES landlords(landlord_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_inquiries_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_inquiries_student (student_id),
    INDEX idx_inquiries_landlord (landlord_id),
    INDEX idx_inquiries_room (room_id),
    INDEX idx_inquiries_reply_status (reply_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
    message_id INT PRIMARY KEY AUTO_INCREMENT,
    sender_id INT NOT NULL,
    recipient_id INT NOT NULL,
    inquiry_id INT,
    message_text TEXT NOT NULL,
    attachment_url VARCHAR(255),
    sent_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_date DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_messages_recipient
        FOREIGN KEY (recipient_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_messages_inquiry
        FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_messages_sender (sender_id),
    INDEX idx_messages_recipient (recipient_id),
    INDEX idx_messages_conversation (sender_id, recipient_id),
    INDEX idx_messages_inquiry (inquiry_id),
    INDEX idx_messages_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE maintenance_requests (
    request_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    room_id INT NOT NULL,
    issue_description TEXT NOT NULL,
    priority ENUM('low', 'medium', 'high', 'urgent') NOT NULL DEFAULT 'medium',
    request_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_status ENUM('pending', 'in_progress', 'resolved', 'cancelled') NOT NULL DEFAULT 'pending',
    resolved_date DATETIME,
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_maintenance_student
        FOREIGN KEY (student_id) REFERENCES students(student_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_maintenance_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_maintenance_student (student_id),
    INDEX idx_maintenance_room (room_id),
    INDEX idx_maintenance_status (request_status),
    INDEX idx_maintenance_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- FINANCE
-- ============================================

CREATE TABLE payments (
    payment_id INT PRIMARY KEY AUTO_INCREMENT,
    tenancy_id INT NOT NULL,
    landlord_id INT NOT NULL,
    student_id INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_date DATETIME,
    due_date DATE NOT NULL,
    payment_method ENUM('bank_transfer', 'card', 'cash', 'mobile_money', 'other') NOT NULL,
    payment_status ENUM('pending', 'completed', 'failed', 'overdue', 'cancelled') NOT NULL DEFAULT 'pending',
    receipt_number VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy_records(tenancy_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_payments_landlord
        FOREIGN KEY (landlord_id) REFERENCES landlords(landlord_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_payments_student
        FOREIGN KEY (student_id) REFERENCES students(student_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_payments_tenancy (tenancy_id),
    INDEX idx_payments_landlord (landlord_id),
    INDEX idx_payments_student (student_id),
    INDEX idx_payments_status (payment_status),
    INDEX idx_payments_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transaction_history (
    transaction_id INT PRIMARY KEY AUTO_INCREMENT,
    payment_id INT NOT NULL,
    transaction_type ENUM('debit', 'credit', 'refund', 'adjustment') NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_before DECIMAL(10, 2),
    balance_after DECIMAL(10, 2),
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_number VARCHAR(100),
    notes TEXT,
    CONSTRAINT fk_transaction_payment
        FOREIGN KEY (payment_id) REFERENCES payments(payment_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_transaction_payment (payment_id),
    INDEX idx_transaction_reference (reference_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- ENGAGEMENT
-- ============================================

CREATE TABLE reviews (
    review_id INT PRIMARY KEY AUTO_INCREMENT,
    from_user_id INT NOT NULL,
    to_user_id INT NOT NULL,
    room_id INT,
    rating INT NOT NULL,
    review_text TEXT,
    review_type ENUM('room_review', 'landlord_review', 'student_review') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_reviews_from_user
        FOREIGN KEY (from_user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_reviews_to_user
        FOREIGN KEY (to_user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_reviews_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT uq_reviews_user_room_type UNIQUE (from_user_id, room_id, review_type),
    INDEX idx_reviews_from_user (from_user_id),
    INDEX idx_reviews_to_user (to_user_id),
    INDEX idx_reviews_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE favorites (
    favorite_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    room_id INT NOT NULL,
    saved_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_favorites_student_room UNIQUE (student_id, room_id),
    CONSTRAINT fk_favorites_student
        FOREIGN KEY (student_id) REFERENCES students(student_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_favorites_room
        FOREIGN KEY (room_id) REFERENCES rooms(room_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_favorites_student (student_id),
    INDEX idx_favorites_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    notification_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    notification_type ENUM(
        'booking_confirmed',
        'booking_update',
        'payment_due',
        'message_received',
        'inquiry_response',
        'maintenance_update',
        'document_update',
        'system_alert'
    ) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at DATETIME,
    action_url VARCHAR(255),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_read (is_read),
    INDEX idx_notifications_type (notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- REFERENCE DATA
-- ============================================

INSERT INTO amenities (amenity_name, description) VALUES
('WiFi', 'Internet access'),
('Parking', 'Parking available'),
('Furnished', 'Furniture included'),
('Security', 'Security features available'),
('Laundry', 'Laundry access'),
('Study Desk', 'Study desk included'),
('Kitchen', 'Kitchen access'),
('Water Included', 'Water included in rent');

-- ============================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reset_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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
-- Additive migration. Existing listings and image URLs are preserved.
CREATE TABLE IF NOT EXISTS listing_details (
 room_id INT PRIMARY KEY,
 suburb VARCHAR(100) NOT NULL DEFAULT '', city VARCHAR(100) NOT NULL DEFAULT '',
 deposit DECIMAL(10,2) NOT NULL DEFAULT 0, bedrooms INT NOT NULL DEFAULT 1,
 bathrooms INT NOT NULL DEFAULT 1, campus_distance DECIMAL(7,2) NULL,
 contact_name VARCHAR(100) NOT NULL DEFAULT '', contact_email VARCHAR(254) NOT NULL DEFAULT '',
 contact_phone VARCHAR(40) NOT NULL DEFAULT '',
 FOREIGN KEY (room_id) REFERENCES rooms(room_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS listing_photo_rooms (
 group_id VARCHAR(36) PRIMARY KEY, room_id INT NOT NULL,
 room_type VARCHAR(30) NOT NULL, label VARCHAR(80) NOT NULL, display_order INT NOT NULL,
 FOREIGN KEY (room_id) REFERENCES rooms(room_id) ON DELETE CASCADE,
 INDEX (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS listing_photo_details (
 image_id INT PRIMARY KEY, group_id VARCHAR(36) NOT NULL, caption VARCHAR(80) NOT NULL DEFAULT '',
 FOREIGN KEY (image_id) REFERENCES room_images(image_id) ON DELETE CASCADE,
 FOREIGN KEY (group_id) REFERENCES listing_photo_rooms(group_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS listing_photo_uploads (
 token VARCHAR(36) PRIMARY KEY, landlord_id INT NOT NULL, image_data MEDIUMBLOB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (landlord_id) REFERENCES landlords(landlord_id) ON DELETE CASCADE,
 INDEX (created_at), INDEX (landlord_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- END OF SCHEMA
-- ============================================
