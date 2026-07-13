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
}
