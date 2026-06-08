-- Default super admin (password: admin123, bcrypt hash)
INSERT INTO `admin_user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'SUPER_ADMIN', 'ACTIVE');
