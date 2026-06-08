-- Default super admin (password: admin123, bcrypt hash)
INSERT INTO `admin_user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '$2a$10$YZPJw5WLWFVP1IsHDZmsFetU.vUeJoxqbQm1Mdd/d0QjaFj7Cw76G', 'SUPER_ADMIN', 'ACTIVE');
