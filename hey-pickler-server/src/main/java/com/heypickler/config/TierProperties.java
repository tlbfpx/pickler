package com.heypickler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hey-pickler.tier")
public class TierProperties {
    private List<String> keys;          // [BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER]
    private List<String> names;         // [青铜, 白银, 黄金, 铂金, 钻石, 王者]
    private StarTier star;
    private PartyTier party;

    @Data
    public static class StarTier { private List<Integer> thresholds; }
    @Data
    public static class PartyTier { private List<Integer> thresholds; }

    /** type 非 PARTY 一律按 STAR 阈值 */
    public String keyFor(int points, String type) {
        List<Integer> th = ("PARTY".equals(type) && party != null) ? party.getThresholds() : star.getThresholds();
        String result = keys.get(0);
        for (int i = 0; i < keys.size(); i++) {
            if (points >= th.get(i)) result = keys.get(i);
        }
        return result;
    }

    public String nameFor(String key) {
        int i = keys.indexOf(key);
        return i >= 0 ? names.get(i) : names.get(0);
    }

    public List<String> cacheKeysWithNull() {
        List<String> all = new ArrayList<>(keys);
        all.add(null);
        return all;
    }
}
