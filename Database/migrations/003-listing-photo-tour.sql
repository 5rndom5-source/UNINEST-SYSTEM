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
