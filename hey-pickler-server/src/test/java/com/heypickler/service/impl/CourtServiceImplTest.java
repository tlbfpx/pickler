package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.dto.admin.CourtPricingBandBatchRequest;
import com.heypickler.dto.admin.CourtPricingBandRequest;
import com.heypickler.entity.Court;
import com.heypickler.entity.CourtPricingBand;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.CourtPricingBandMapper;
import com.heypickler.common.util.PricingBandValidator;
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

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourtServiceImplTest {

    @Mock
    CourtMapper courtMapper;

    @Mock
    CourtPricingBandMapper bandMapper;

    @Mock
    PricingBandValidator validator; // 注入校验器

    @InjectMocks
    CourtServiceImpl service;

    /**
     * replacePricingBands 走 LambdaQueryWrapper<CourtPricingBand> 删旧 band，
     * 纯单测无 MyBatis 启动流程 → 预热该实体的 TableInfo(参考 PointServiceImplTest)。
     * Court 走 selectById 不需预热。
     */
    @BeforeAll
    static void warm() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(CourtPricingBand.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, c);
        }
    }

    @Test
    void replacePricingBands_validatorRejects_throwsAndNoWrite() {
        CourtPricingBandBatchRequest req = new CourtPricingBandBatchRequest();
        CourtPricingBandRequest b = new CourtPricingBandRequest();
        b.setDayType("WEEKDAY");
        b.setStartTime(LocalTime.of(9, 0));
        b.setEndTime(LocalTime.of(12, 0));
        b.setPrice(new BigDecimal("40"));
        req.setBands(List.of(b));
        when(courtMapper.selectById(1L)).thenReturn(new Court());
        doThrow(new BizException(com.heypickler.common.exception.ErrorCode.PARAM_ERROR, "重叠"))
                .when(validator).validate(anyList());

        assertThrows(BizException.class, () -> service.replacePricingBands(1L, req));
        verify(bandMapper, never()).insert(any());
    }

    @Test
    void replacePricingBands_ok_deletesOldInsertsNew() {
        CourtPricingBandBatchRequest req = new CourtPricingBandBatchRequest();
        CourtPricingBandRequest b = new CourtPricingBandRequest();
        b.setDayType("WEEKDAY");
        b.setStartTime(LocalTime.of(9, 0));
        b.setEndTime(LocalTime.of(12, 0));
        b.setPrice(new BigDecimal("40"));
        req.setBands(List.of(b));
        when(courtMapper.selectById(1L)).thenReturn(new Court());
        doNothing().when(validator).validate(anyList());

        service.replacePricingBands(1L, req);

        verify(bandMapper).delete(any());           // 先清旧
        verify(bandMapper, times(1)).insert(any()); // 再写新
    }

    @Test
    void replacePricingBands_courtNotFound_throws() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.replacePricingBands(99L, new CourtPricingBandBatchRequest()));
    }

    @Test
    void listPricingBands_courtNotFound_throws() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.listPricingBands(99L));
    }

    @Test
    void listPricingBands_ok_returnsMappedBands() {
        when(courtMapper.selectById(1L)).thenReturn(new Court());
        CourtPricingBand b1 = new CourtPricingBand();
        b1.setId(11L);
        b1.setCourtId(1L);
        b1.setDayType("WEEKDAY");
        b1.setStartTime(LocalTime.of(9, 0));
        b1.setEndTime(LocalTime.of(12, 0));
        b1.setPrice(new BigDecimal("40"));
        CourtPricingBand b2 = new CourtPricingBand();
        b2.setId(12L);
        b2.setCourtId(1L);
        b2.setDayType("WEEKEND");
        b2.setStartTime(LocalTime.of(14, 0));
        b2.setEndTime(LocalTime.of(18, 0));
        b2.setPrice(new BigDecimal("60"));
        // selectList 返回顺序即编排顺序；wrapper 由实现内部决定 orderByAsc(startTime)
        when(bandMapper.selectList(any())).thenReturn(List.of(b1, b2));

        List<com.heypickler.vo.CourtPricingBandVO> vos = service.listPricingBands(1L);

        assertEquals(2, vos.size());
        assertEquals(11L, vos.get(0).getId());
        assertEquals("WEEKDAY", vos.get(0).getDayType());
        assertEquals(LocalTime.of(9, 0), vos.get(0).getStartTime());
        assertEquals(new BigDecimal("40"), vos.get(0).getPrice());
        assertEquals(12L, vos.get(1).getId());
        assertEquals("WEEKEND", vos.get(1).getDayType());
        verify(bandMapper).selectList(any());
    }
}
