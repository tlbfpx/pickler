package com.heypickler.common.constant;

public class RedisKey {
    private RedisKey() {}

    private static final String PREFIX = "heypickler:";

    public static String ranking(String type, String tier) {
        return PREFIX + "ranking:" + type + ":" + tier;
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
