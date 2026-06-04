CREATE DATABASE IF NOT EXISTS busbooking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE busbooking;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uid VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(32) NOT NULL UNIQUE,
    role VARCHAR(24) NOT NULL DEFAULT 'USER',
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS bus_companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(160) NULL,
    address VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    INDEX idx_bus_companies_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS routes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    origin_id VARCHAR(80) NULL,
    destination_id VARCHAR(80) NULL,
    origin VARCHAR(120) NOT NULL,
    destination VARCHAR(120) NOT NULL,
    distance INT NOT NULL,
    seat_count INT NOT NULL DEFAULT 34,
    suggested_price BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    INDEX idx_routes_origin_destination (origin, destination),
    INDEX idx_routes_seat_count (seat_count),
    INDEX idx_routes_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS buses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NULL,
    bus_name VARCHAR(120) NOT NULL,
    total_seats INT NOT NULL,
    license_plate VARCHAR(40) NOT NULL UNIQUE,
    seat_layout_json TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    INDEX idx_buses_company (company_id),
    INDEX idx_buses_active (is_active),
    CONSTRAINT fk_buses_company FOREIGN KEY (company_id) REFERENCES bus_companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS staff_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    company_id BIGINT NULL,
    staff_code VARCHAR(80) NULL UNIQUE,
    position VARCHAR(60) NOT NULL DEFAULT 'STAFF',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    INDEX idx_staff_profiles_company (company_id),
    INDEX idx_staff_profiles_status (status),
    CONSTRAINT fk_staff_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_profiles_company FOREIGN KEY (company_id) REFERENCES bus_companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS staff_bus_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    staff_id BIGINT NOT NULL,
    bus_id BIGINT NOT NULL,
    company_id BIGINT NULL,
    role_on_bus VARCHAR(60) NOT NULL DEFAULT 'STAFF',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at BIGINT NOT NULL,
    unassigned_at BIGINT NULL,
    note VARCHAR(255) NULL,
    UNIQUE KEY uk_staff_bus_active (staff_id, bus_id, is_active),
    INDEX idx_staff_bus_staff (staff_id),
    INDEX idx_staff_bus_bus (bus_id),
    INDEX idx_staff_bus_company (company_id),
    CONSTRAINT fk_staff_bus_staff FOREIGN KEY (staff_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_bus_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_bus_company FOREIGN KEY (company_id) REFERENCES bus_companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS seats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bus_id BIGINT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    floor INT NOT NULL DEFAULT 1,
    row_index INT NOT NULL DEFAULT 0,
    column_index INT NOT NULL DEFAULT 0,
    is_window BOOLEAN NOT NULL DEFAULT FALSE,
    is_aisle BOOLEAN NOT NULL DEFAULT FALSE,
    seat_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_seats_bus_number (bus_id, seat_number),
    CONSTRAINT fk_seats_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS trips (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NULL,
    route_id BIGINT NOT NULL,
    bus_id BIGINT NOT NULL,
    departure_time BIGINT NOT NULL,
    arrival_time BIGINT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    trip_date BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    INDEX idx_trips_company (company_id),
    INDEX idx_trips_route_date (route_id, trip_date),
    INDEX idx_trips_departure (departure_time),
    CONSTRAINT fk_trips_company FOREIGN KEY (company_id) REFERENCES bus_companies(id),
    CONSTRAINT fk_trips_route FOREIGN KEY (route_id) REFERENCES routes(id),
    CONSTRAINT fk_trips_bus FOREIGN KEY (bus_id) REFERENCES buses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS trip_staff_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trip_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    bus_id BIGINT NULL,
    company_id BIGINT NULL,
    role_on_trip VARCHAR(60) NOT NULL DEFAULT 'STAFF',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    assigned_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    UNIQUE KEY uk_trip_staff (trip_id, staff_id),
    INDEX idx_trip_staff_trip (trip_id),
    INDEX idx_trip_staff_staff (staff_id),
    INDEX idx_trip_staff_company (company_id),
    CONSTRAINT fk_trip_staff_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_staff_staff FOREIGN KEY (staff_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_staff_bus FOREIGN KEY (bus_id) REFERENCES buses(id),
    CONSTRAINT fk_trip_staff_company FOREIGN KEY (company_id) REFERENCES bus_companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    bus_id BIGINT NULL,
    payment_id VARCHAR(80) NULL,
    booking_time BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    cancellation_reason VARCHAR(255) NULL,
    refund_amount DECIMAL(12,2) NULL,
    refund_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    updated_at BIGINT NULL,
    INDEX idx_tickets_user (user_id),
    INDEX idx_tickets_payment (payment_id),
    CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_tickets_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_tickets_seat FOREIGN KEY (seat_id) REFERENCES seats(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS trip_seats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trip_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    ticket_id BIGINT NULL,
    user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    UNIQUE KEY uk_trip_seats_trip_seat (trip_id, seat_id),
    CONSTRAINT fk_trip_seats_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_seats_seat FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(80) PRIMARY KEY,
    ticket_id BIGINT NULL,
    user_id BIGINT NULL,
    trip_id BIGINT NULL,
    seat_id BIGINT NULL,
    amount DECIMAL(12,2) NOT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'VNPAY',
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    vnp_txn_ref VARCHAR(80) NULL,
    vnp_transaction_no VARCHAR(80) NULL,
    payment_url TEXT NULL,
    qr_content TEXT NULL,
    qr_image_base64 LONGTEXT NULL,
    qr_mime_type VARCHAR(40) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    expires_at BIGINT NULL,
    INDEX idx_payments_status (status),
    INDEX idx_payments_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    booking_code VARCHAR(80) NOT NULL UNIQUE,
    booking_type VARCHAR(32) NOT NULL DEFAULT 'ONE_WAY',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NULL,
    INDEX idx_bookings_user (user_id),
    INDEX idx_bookings_status (status),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS booking_tickets (
    booking_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    PRIMARY KEY (booking_id, ticket_id),
    CONSTRAINT fk_booking_tickets_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_tickets_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS payment_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id VARCHAR(80) NOT NULL,
    ticket_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_payment_items_payment_ticket (payment_id, ticket_id),
    INDEX idx_payment_items_ticket (ticket_id),
    CONSTRAINT fk_payment_items_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_items_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ticket_checkins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    method VARCHAR(32) NOT NULL DEFAULT 'QR',
    checked_in_at BIGINT NOT NULL,
    note VARCHAR(255) NULL,
    UNIQUE KEY uk_ticket_checkins_ticket (ticket_id),
    INDEX idx_ticket_checkins_trip (trip_id),
    INDEX idx_ticket_checkins_staff (staff_id),
    CONSTRAINT fk_ticket_checkins_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_checkins_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_checkins_staff FOREIGN KEY (staff_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Compatibility migrations for older local databases.
SET @has_username_column = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'username'
);
SET @drop_username_sql = IF(@has_username_column > 0, 'ALTER TABLE users DROP COLUMN username', 'SELECT 1');
PREPARE drop_username_stmt FROM @drop_username_sql;
EXECUTE drop_username_stmt;
DEALLOCATE PREPARE drop_username_stmt;
SET @has_ticket_seat_unique = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tickets' AND INDEX_NAME = 'uk_tickets_trip_seat'
);
SET @drop_ticket_seat_unique_sql = IF(@has_ticket_seat_unique > 0, 'ALTER TABLE tickets DROP INDEX uk_tickets_trip_seat', 'SELECT 1');
PREPARE drop_ticket_seat_unique_stmt FROM @drop_ticket_seat_unique_sql;
EXECUTE drop_ticket_seat_unique_stmt;
DEALLOCATE PREPARE drop_ticket_seat_unique_stmt;
ALTER TABLE buses ADD COLUMN IF NOT EXISTS company_id BIGINT NULL AFTER id;
ALTER TABLE routes ADD COLUMN IF NOT EXISTS seat_count INT NOT NULL DEFAULT 34 AFTER distance;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS company_id BIGINT NULL AFTER user_id;
ALTER TABLE staff_bus_assignments ADD COLUMN IF NOT EXISTS company_id BIGINT NULL AFTER bus_id;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS company_id BIGINT NULL AFTER id;
ALTER TABLE trip_staff_assignments ADD COLUMN IF NOT EXISTS company_id BIGINT NULL AFTER bus_id;
UPDATE routes SET seat_count = 34 WHERE seat_count IS NULL OR seat_count NOT IN (24, 34);

-- Data seed removed. Fixed data was loaded once; rolling trips are generated at runtime.
SET FOREIGN_KEY_CHECKS = 1;


