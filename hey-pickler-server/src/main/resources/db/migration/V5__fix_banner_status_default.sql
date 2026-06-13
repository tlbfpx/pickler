-- Banner 表 status 默认值原本是 'ENABLED'，但 DTO @Pattern 只允许 ACTIVE|INACTIVE，
-- 直接 SQL 插入会拿到非法值。统一为 'ACTIVE'。
-- 现有数据已经全是 ACTIVE（service 层默认），仅修 schema 默认值即可。
ALTER TABLE banner MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
