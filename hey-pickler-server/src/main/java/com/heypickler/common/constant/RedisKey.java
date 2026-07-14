package com.heypickler.common.constant;

public class RedisKey {
    private RedisKey() {}

    private static final String PREFIX = "heypickler:";

    /**
     * 排名缓存键。必须含 seasonCode：赛季轮转后同一 (type,tier) 在不同赛季是不同数据，
     * 不含 season 会让当前赛季视图读到归档赛季的缓存。
     */
    public static String ranking(String type, String tier, String seasonCode) {
        return PREFIX + "ranking:" + type + ":" + tier + ":" + seasonCode;
    }

    public static String rankingTop5(String type) {
        return PREFIX + "ranking:" + type + ":top5";
    }

    public static String adminSession(String tokenId) {
        return PREFIX + "session:admin:" + tokenId;
    }

    public static String rateLimit(String ip) {
        return PREFIX + "ratelimit:" + ip;
    }

    public static String rateLimitAdmin(String adminId) {
        return PREFIX + "ratelimit:admin:" + adminId;
    }

    public static String wxSession(String openid) {
        return PREFIX + "wx:session:" + openid;
    }

    /** 全局字典版本号（任何字典写操作自增，前端据此增量刷新）。
     *  注：字典表数据量小（< 50 行），本期不做 per-dict / bundle 对象缓存——
     *  RedisConfig 关闭了 default typing，Jackson2JsonRedisSerializer 反序列化会得到
     *  LinkedHashMap 而非实体，访问 getter 会 ClassCastException，对象缓存坑大收益低。
     *  仅靠 version 让前端增量重拉 bundle；数据量增长时再以 StringRedisTemplate +
     *  显式反序列化加类型安全缓存。 */
    public static String dictVersion() {
        return PREFIX + "dict:version";
    }

    /**
     * 段位配置缓存键（per-track：STAR / PARTY）。
     * <p>
     * 本期 TierResolver 暂未启用 Redis 缓存（tier_config 仅 12 行，DB 读极快），
     * 此键预留给 Chunk 2 admin 写时加失效逻辑。
     */
    public static String tierConfig(String track) {
        return PREFIX + "dict:tier:" + track;
    }
}
