package com.heypickler.service;

import com.heypickler.common.exception.BizException;
import com.heypickler.dto.admin.TierItemUpdateRequest;
import com.heypickler.entity.TierConfig;
import com.heypickler.mapper.TierConfigMapper;
import com.heypickler.service.impl.TierConfigServiceImpl;
import com.heypickler.vo.TierConfigVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TierConfigServiceImpl 强校验 + patch 铁律测试。
 * <p>
 * 校验项：恰好 6 档、BRONZE=0、全 >=0、严格递增、tierCode 非空。
 * 铁律：patch 对象 tierCode 必须为 null（永不回写）。
 */
@ExtendWith(MockitoExtension.class)
class TierConfigServiceImplTest {

    @BeforeAll
    static void warmLambdaCache() {
        // MyBatis-Plus LambdaQueryWrapper 解析 SFunction→列名依赖 TableInfo lambda 缓存；
        // 纯单测需手动预热，否则 "can not find lambda cache"。
        Configuration cfg = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("com.heypickler.mapper.TierConfigMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, TierConfig.class);
    }

    @InjectMocks
    TierConfigServiceImpl service;
    @Mock
    TierConfigMapper tierConfigMapper;
    @Mock
    DictCacheService dictCacheService;
    @Mock
    RedisTemplate<String, Object> redisTemplate;

    private static final String[] CODES = {"BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER"};
    private static final int[] DEFAULT_THRESHOLDS = {0, 500, 1200, 2500, 5000, 10000};

    /** 构造 6 档合法请求（默认 STAR 阈值递增） */
    private List<TierItemUpdateRequest> validItems(int[] thresholds) {
        List<TierItemUpdateRequest> list = new ArrayList<>();
        for (int i = 0; i < CODES.length; i++) {
            TierItemUpdateRequest req = new TierItemUpdateRequest();
            req.setTierCode(CODES[i]);
            req.setTierName("name-" + CODES[i]);
            req.setTierColor("#" + i);
            req.setThreshold(thresholds[i]);
            req.setIcon("I" + i);
            list.add(req);
        }
        return list;
    }

    /** stub selectOne：按 tierCode 返回已存在的行（含 id） */
    private void stubExistingRows() {
        when(tierConfigMapper.selectOne(any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> w =
                    (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) inv.getArgument(0);
            w.getSqlSegment(); // 触发参数填充
            Object params = w.getParamNameValuePairs();
            String bound = params == null ? "" : params.toString();
            for (int i = 0; i < CODES.length; i++) {
                if (bound.contains(CODES[i])) {
                    TierConfig t = new TierConfig();
                    t.setId((long) (i + 1));
                    t.setTrack("STAR");
                    t.setTierCode(CODES[i]);
                    return t;
                }
            }
            return null;
        });
    }

    @Test
    void getByTrack_returnsMappedVOs() {
        TierConfig t = new TierConfig();
        t.setTrack("STAR"); t.setTierCode("BRONZE"); t.setTierName("青铜");
        t.setTierColor("#A56C2C"); t.setThreshold(0); t.setIcon("🥉"); t.setSort(0);
        when(tierConfigMapper.selectList(any())).thenReturn(List.of(t));

        List<TierConfigVO> vos = service.getByTrack("STAR");

        assertEquals(1, vos.size());
        assertEquals("BRONZE", vos.get(0).getTierCode());
        assertEquals("青铜", vos.get(0).getTierName());
        assertEquals("#A56C2C", vos.get(0).getTierColor());
    }

    @Test
    void updateTrack_validAscending_ok() {
        stubExistingRows();
        List<TierItemUpdateRequest> items = validItems(DEFAULT_THRESHOLDS);

        service.updateTrack("STAR", items);

        ArgumentCaptor<TierConfig> cap = ArgumentCaptor.forClass(TierConfig.class);
        verify(tierConfigMapper, times(6)).updateById(cap.capture());
        verify(dictCacheService).incrementVersion();
        // 校验 patch 内容：6 行，含 id，含 name/color/threshold/icon
        List<TierConfig> saved = cap.getAllValues();
        assertEquals(6, saved.size());
        for (int i = 0; i < 6; i++) {
            TierConfig patch = saved.get(i);
            assertEquals((long) (i + 1), patch.getId());
            assertEquals("name-" + CODES[i], patch.getTierName());
            assertEquals("#" + i, patch.getTierColor());
            assertEquals(DEFAULT_THRESHOLDS[i], patch.getThreshold());
            assertEquals("I" + i, patch.getIcon());
        }
    }

    @Test
    void updateTrack_nonZeroBronze_throws() {
        int[] bad = DEFAULT_THRESHOLDS.clone();
        bad[0] = 100; // BRONZE != 0
        assertThrows(BizException.class, () -> service.updateTrack("STAR", validItems(bad)));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_notAscending_throws() {
        int[] bad = DEFAULT_THRESHOLDS.clone();
        bad[1] = 0; // SILVER(0) <= BRONZE(0)，非严格递增
        assertThrows(BizException.class, () -> service.updateTrack("STAR", validItems(bad)));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_negative_throws() {
        int[] bad = DEFAULT_THRESHOLDS.clone();
        bad[3] = -1; // PLATINUM 负数
        assertThrows(BizException.class, () -> service.updateTrack("STAR", validItems(bad)));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_wrongCount_throws() {
        // 只传 5 档
        List<TierItemUpdateRequest> items = validItems(DEFAULT_THRESHOLDS).subList(0, 5);
        assertThrows(BizException.class, () -> service.updateTrack("STAR", items));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_tierCodeNotRewritten() {
        stubExistingRows();
        service.updateTrack("STAR", validItems(DEFAULT_THRESHOLDS));

        ArgumentCaptor<TierConfig> cap = ArgumentCaptor.forClass(TierConfig.class);
        verify(tierConfigMapper, times(6)).updateById(cap.capture());
        // 铁律：patch 对象的 tierCode 必须为 null（防回归，对齐 DictServiceImplTest）
        for (TierConfig patch : cap.getAllValues()) {
            assertNull(patch.getTierCode(), "tierCode 不得回写");
        }
    }

    @Test
    void updateTrack_blankTierCode_throws() {
        // @Valid 对 List 元素不级联，service 兜底校验 tierCode 非空
        List<TierItemUpdateRequest> items = validItems(DEFAULT_THRESHOLDS);
        items.get(0).setTierCode("   ");
        assertThrows(BizException.class, () -> service.updateTrack("STAR", items));
        verifyNoInteractions(tierConfigMapper);
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_duplicateTierCode_throws() {
        List<TierItemUpdateRequest> items = validItems(DEFAULT_THRESHOLDS);
        items.get(1).setTierCode("BRONZE"); // 重复 BRONZE
        assertThrows(BizException.class, () -> service.updateTrack("STAR", items));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_missingTier_throws() {
        // 6 条但缺 GOLD，多了 UNKNOWN
        List<TierItemUpdateRequest> items = validItems(DEFAULT_THRESHOLDS);
        items.get(2).setTierCode("UNKNOWN"); // GOLD 被替换
        assertThrows(BizException.class, () -> service.updateTrack("STAR", items));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_nullThreshold_throws() {
        List<TierItemUpdateRequest> items = validItems(DEFAULT_THRESHOLDS);
        items.get(2).setThreshold(null);
        assertThrows(BizException.class, () -> service.updateTrack("STAR", items));
        verifyNoInteractions(dictCacheService);
    }

    @Test
    void updateTrack_invalidatesRankingCache() {
        // 改段位后必须清该轨 ranking 缓存（RankingVO 含 tierName/tierColor，5min TTL 不清会读旧）
        stubExistingRows();
        Set<String> staleKeys = Set.of("heypickler:ranking:STAR:BRONZE:s1",
                "heypickler:ranking:STAR:top5");
        when(redisTemplate.keys("heypickler:ranking:STAR:*")).thenReturn(staleKeys);

        service.updateTrack("STAR", validItems(DEFAULT_THRESHOLDS));

        verify(redisTemplate).delete(staleKeys);
    }

    @Test
    void updateTrack_noRankingCacheKeys_noDelete() {
        // keys 返回空（无缓存）→ 不调 delete（不抛 NPE）
        stubExistingRows();
        when(redisTemplate.keys("heypickler:ranking:STAR:*")).thenReturn(java.util.Collections.emptySet());

        service.updateTrack("STAR", validItems(DEFAULT_THRESHOLDS));

        verify(redisTemplate, never()).delete(any(java.util.Collection.class));
    }
}
