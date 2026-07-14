package com.heypickler.service;

import com.heypickler.entity.TierConfig;
import com.heypickler.mapper.TierConfigMapper;
import com.heypickler.service.impl.TierResolverImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TierResolver 行为等价测试（对齐旧 TierProperties 既有阈值行为）。
 * <p>
 * STAR 阈值 [0,500,1200,2500,5000,10000]；PARTY 阈值 [0,200,500,1200,2500,5000]。
 * tier_code 双轨统一 BRONZE..MASTER；name/color per-track（STAR=青铜…王者，PARTY=见习…传奇）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TierResolverTest {

    @Mock
    private TierConfigMapper tierConfigMapper;

    @InjectMocks
    private TierResolverImpl tierResolver;

    @BeforeAll
    static void warmLambdaCache() {
        // MyBatis-Plus LambdaQueryWrapper 解析 SFunction→列名依赖 TableInfo lambda 缓存；
        // 纯单测需手动预热，否则 "can not find lambda cache"。
        Configuration cfg = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("com.heypickler.mapper.TierConfigMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, TierConfig.class);
    }

    /** STAR 6 档 seed（V19） */
    private List<TierConfig> starTiers() {
        return tiers("STAR",
                new String[]{"青铜", "白银", "黄金", "铂金", "钻石", "王者"},
                new String[]{"#A56C2C", "#9CA3AF", "#E6A23C", "#409EFF", "#9C27B0", "#EF4444"},
                new String[]{"🥉", "🥈", "🥇", "💎", "💠", "👑"},
                new int[]{0, 500, 1200, 2500, 5000, 10000});
    }

    /** PARTY 6 档 seed（V19）：见习/活力/热血/资深/明星/传奇 */
    private List<TierConfig> partyTiers() {
        return tiers("PARTY",
                new String[]{"见习球友", "活力球友", "热血球友", "资深球友", "明星球友", "传奇球友"},
                new String[]{"#94A3B8", "#FBBF24", "#F97316", "#EF4444", "#EC4899", "#F59E0B"},
                new String[]{"🌟", "⭐", "✨", "⭐⭐", "🏅", "👑"},
                new int[]{0, 200, 500, 1200, 2500, 5000});
    }

    private List<TierConfig> tiers(String track, String[] names, String[] colors, String[] icons, int[] thresholds) {
        String[] codes = {"BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER"};
        List<TierConfig> list = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            TierConfig t = new TierConfig();
            t.setTrack(track);
            t.setTierCode(codes[i]);
            t.setTierName(names[i]);
            t.setTierColor(colors[i]);
            t.setIcon(icons[i]);
            t.setThreshold(thresholds[i]);
            t.setSort(i);
            list.add(t);
        }
        return list;
    }

    private void stubTiers() {
        // LambdaQueryWrapper.eq(track, "PARTY") 的值在 getParamNameValuePairs() 里，
        // 但参数填充是 lazy 的——mock 拦截早于 MyBatis 评估。显式触发 getSqlSegment()
        // 强制构建表达式并填充 paramNameValuePairs，据此路由到对应 6 档。
        when(tierConfigMapper.selectList(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            String bound = "";
            if (arg instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper) {
                com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> w =
                        (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) arg;
                w.getSqlSegment(); // 触发参数填充
                Object params = w.getParamNameValuePairs();
                bound = params == null ? "" : params.toString();
            }
            if (bound.contains("PARTY")) {
                return partyTiers();
            }
            return starTiers(); // STAR 或未识别一律 STAR（对齐 normalizeTrack）
        });
    }

    // ===== keyFor：STAR 边界（对齐旧 TierProperties） =====

    @Test
    void keyFor_starBoundaries_alignWithOldProperties() {
        stubTiers();
        assertEquals("BRONZE",   tierResolver.keyFor(0,     "STAR"));
        assertEquals("BRONZE",   tierResolver.keyFor(499,   "STAR"));
        assertEquals("SILVER",   tierResolver.keyFor(500,   "STAR"));
        assertEquals("SILVER",   tierResolver.keyFor(1199,  "STAR"));
        assertEquals("GOLD",     tierResolver.keyFor(1200,  "STAR"));
        assertEquals("PLATINUM", tierResolver.keyFor(2500,  "STAR"));
        assertEquals("DIAMOND",  tierResolver.keyFor(5000,  "STAR"));
        assertEquals("MASTER",   tierResolver.keyFor(10000, "STAR"));
        assertEquals("MASTER",   tierResolver.keyFor(99999, "STAR"));
    }

    // ===== keyFor：PARTY 边界（PARTY 阈值，球友称号系） =====

    @Test
    void keyFor_partyBoundaries_alignWithOldProperties() {
        stubTiers();
        assertEquals("BRONZE",   tierResolver.keyFor(0,     "PARTY"));
        assertEquals("BRONZE",   tierResolver.keyFor(199,   "PARTY"));
        assertEquals("SILVER",   tierResolver.keyFor(200,   "PARTY"));
        assertEquals("GOLD",     tierResolver.keyFor(500,   "PARTY"));
        assertEquals("MASTER",   tierResolver.keyFor(5000,  "PARTY"));
        assertEquals("MASTER",   tierResolver.keyFor(99999, "PARTY"));
    }

    // ===== 非 PARTY 一律按 STAR =====

    @Test
    void keyFor_nonPartyTreatedAsStar() {
        stubTiers();
        assertEquals("SILVER", tierResolver.keyFor(600, "ACTIVITY")); // 600>=500(STAR)
        assertEquals("SILVER", tierResolver.keyFor(600, null));
    }

    // ===== nameFor：per-track =====

    @Test
    void nameFor_starAndParty_arePerTrack() {
        stubTiers();
        assertEquals("青铜",     tierResolver.nameFor("STAR",  "BRONZE"));
        assertEquals("王者",     tierResolver.nameFor("STAR",  "MASTER"));
        assertEquals("见习球友", tierResolver.nameFor("PARTY", "BRONZE"));
        assertEquals("传奇球友", tierResolver.nameFor("PARTY", "MASTER"));
        assertEquals("活力球友", tierResolver.nameFor("PARTY", "SILVER"));
    }

    // ===== colorFor：per-track =====

    @Test
    void colorFor_party_isPartyColor() {
        stubTiers();
        assertEquals("#FBBF24", tierResolver.colorFor("PARTY", "SILVER")); // 活力
        assertEquals("#EF4444", tierResolver.colorFor("STAR",  "MASTER")); // 王者
    }

    // ===== iconFor：per-track =====

    @Test
    void iconFor_returnsConfiguredIcon() {
        stubTiers();
        assertEquals("👑", tierResolver.iconFor("STAR",  "MASTER"));
        assertEquals("🌟", tierResolver.iconFor("PARTY", "BRONZE"));
    }

    // ===== defaultKey =====

    @Test
    void defaultKey_returnsBronze() {
        assertEquals("BRONZE", tierResolver.defaultKey("STAR"));
        assertEquals("BRONZE", tierResolver.defaultKey("PARTY"));
    }

    // ===== cacheKeysWithNull：6 档 + null（对齐旧实现） =====

    @Test
    void cacheKeysWithNull_containsSixTiersAndNull() {
        List<String> keys = tierResolver.cacheKeysWithNull();
        assertEquals(7, keys.size());
        assertEquals("BRONZE",   keys.get(0));
        assertEquals("SILVER",   keys.get(1));
        assertEquals("GOLD",     keys.get(2));
        assertEquals("PLATINUM", keys.get(3));
        assertEquals("DIAMOND",  keys.get(4));
        assertEquals("MASTER",   keys.get(5));
        assertNull(keys.get(6));
    }

    // ===== fallback：未命中 tier_code =====

    @Test
    void nameFor_unknownKey_fallsBackToKeyItself() {
        stubTiers();
        assertEquals("UNKNOWN", tierResolver.nameFor("STAR", "UNKNOWN"));
    }

    @Test
    void colorFor_unknownKey_fallsBackToGray() {
        stubTiers();
        assertEquals("#6B7280", tierResolver.colorFor("STAR", "UNKNOWN"));
    }

    @Test
    void iconFor_unknownKey_returnsNull() {
        stubTiers();
        assertNull(tierResolver.iconFor("STAR", "UNKNOWN"));
    }
}
