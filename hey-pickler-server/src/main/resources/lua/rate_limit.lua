-- rate_limit.lua  (D6 fix: dual-key atomic limit)
--
-- KEYS[1] = user key (may be nil if no user; pass empty string then)
-- KEYS[2] = ip key   (always present)
-- ARGV[1] = max requests per window
-- ARGV[2] = window seconds (TTL)
--
-- Returns: 1 if allowed, 0 if blocked.
--
-- 设计要点：
-- - user 与 ip 双 key 必须共一次 EVAL，保证原子性。
-- - KEYS[1] 为空串时跳过 user 计数（匿名场景）。
-- - 任一维度过限，立即返回 0。两个 INCR 都已发生，所以下一个窗口
--   才会重新计数（与原行为一致：失败也计入窗口，避免突发刷新）。

local userKey = KEYS[1]
local ipKey   = KEYS[2]
local maxN    = tonumber(ARGV[1])
local ttl     = tonumber(ARGV[2])

local userCount = 0
if userKey ~= nil and userKey ~= '' then
    userCount = redis.call('INCR', userKey)
    if userCount == 1 then
        redis.call('EXPIRE', userKey, ttl)
    end
end

local ipCount = redis.call('INCR', ipKey)
if ipCount == 1 then
    redis.call('EXPIRE', ipKey, ttl)
end

if userCount > maxN or ipCount > maxN then
    return 0
end
return 1
