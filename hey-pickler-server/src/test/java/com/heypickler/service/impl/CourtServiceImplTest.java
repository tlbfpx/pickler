package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.dto.admin.CourtCreateRequest;
import com.heypickler.dto.admin.CourtPricingBandBatchRequest;
import com.heypickler.dto.admin.CourtPricingBandRequest;
import com.heypickler.entity.Court;
import com.heypickler.entity.CourtPricingBand;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.CourtPricingBandMapper;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.vo.CourtVO;
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
        for (Class<?> c : List.of(CourtPricingBand.class, Court.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, c);
        }
    }

    private static Court court(Long id, Long venueId, String name) {
        Court c = new Court();
        c.setId(id);
        c.setVenueId(venueId);
        c.setName(name);
        c.setCourtType("INDOOR");
        c.setSlotMinutes(60);
        c.setStatus("OPEN");
        return c;
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

    // ---------- listByVenue ----------
    @Test
    void listByVenue_returnsMappedVOs() {
        when(courtMapper.selectList(any())).thenReturn(List.of(
                court(1L, 100L, "A"), court(2L, 100L, "B")));
        List<CourtVO> vos = service.listByVenue(100L);
        assertEquals(2, vos.size());
        assertEquals("A", vos.get(0).getName());
        assertEquals(100L, vos.get(0).getVenueId());
        verify(courtMapper).selectList(any());
    }

    @Test
    void listByVenue_emptyReturnsEmpty() {
        when(courtMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.listByVenue(100L).isEmpty());
    }

    // ---------- get ----------
    @Test
    void get_found_returnsVO() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "A"));
        CourtVO vo = service.get(1L);
        assertEquals(1L, vo.getId());
        assertEquals("A", vo.getName());
    }

    @Test
    void get_notFound_throwsCOURT_NOT_FOUND() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.get(99L));
        assertEquals(ErrorCode.COURT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- create ----------
    @Test
    void create_withFullFields_ok() {
        CourtCreateRequest req = new CourtCreateRequest();
        req.setVenueId(100L);
        req.setName("A");
        req.setCourtType("OUTDOOR");
        req.setSlotMinutes(30);
        req.setStatus("CLOSED");
        req.setSortOrder(5);
        when(courtMapper.insert(any(Court.class))).thenAnswer(inv -> {
            Court c = inv.getArgument(0);
            c.setId(11L);
            return 1;
        });
        Long id = service.create(req);
        assertEquals(11L, id);
        verify(courtMapper).insert(any(Court.class));
    }

    @Test
    void create_missingVenueId_throws() {
        CourtCreateRequest req = new CourtCreateRequest();
        req.setName("A");
        BizException ex = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void create_defaults_applied() {
        CourtCreateRequest req = new CourtCreateRequest();
        req.setVenueId(100L);
        req.setName("A");
        // slotMinutes / status / courtType 都缺 → 应落到默认值
        when(courtMapper.insert(any(Court.class))).thenAnswer(inv -> {
            Court c = inv.getArgument(0);
            assertEquals(60, c.getSlotMinutes());
            assertEquals("OPEN", c.getStatus());
            assertEquals("INDOOR", c.getCourtType());
            c.setId(1L);
            return 1;
        });
        service.create(req);
    }

    // ---------- update ----------
    @Test
    void update_found_appliesAndPersists() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "old"));
        CourtCreateRequest req = new CourtCreateRequest();
        req.setName("new");
        req.setCourtType("OUTDOOR");
        req.setSlotMinutes(30);
        req.setStatus("CLOSED");
        req.setSortOrder(2);
        service.update(1L, req);
        verify(courtMapper).updateById(any(Court.class));
    }

    @Test
    void update_notFound_throws() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        CourtCreateRequest req = new CourtCreateRequest();
        req.setName("x");
        assertThrows(BizException.class, () -> service.update(99L, req));
    }

    @Test
    void update_partialFields_keepsExisting() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "A"));
        CourtCreateRequest req = new CourtCreateRequest();
        req.setName("renamed");
        // courtType / slotMinutes / status / sortOrder 都缺 → 保持原值
        when(courtMapper.updateById(any(Court.class))).thenAnswer(inv -> {
            Court c = inv.getArgument(0);
            assertEquals("INDOOR", c.getCourtType());
            assertEquals(60, c.getSlotMinutes());
            return 1;
        });
        service.update(1L, req);
    }

    // ---------- delete ----------
    @Test
    void delete_found_softDeletes() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "A"));
        service.delete(1L);
        verify(courtMapper).deleteById(1L);
    }

    @Test
    void delete_notFound_throws() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.delete(99L));
    }

    // ---------- copyPricingBands ----------
    @Test
    void copyPricingBands_ok_copiesAndValidates() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "target"));
        CourtPricingBand src = new CourtPricingBand();
        src.setCourtId(2L);
        src.setDayType("WEEKDAY");
        src.setStartTime(LocalTime.of(9, 0));
        src.setEndTime(LocalTime.of(12, 0));
        src.setPrice(new BigDecimal("40"));
        when(bandMapper.selectList(any())).thenReturn(List.of(src));
        doNothing().when(validator).validate(anyList());

        service.copyPricingBands(1L, 2L);

        verify(validator).validate(anyList());
        verify(bandMapper, times(1)).insert(any(CourtPricingBand.class));
    }

    @Test
    void copyPricingBands_validatorRejects_throwsAndNoInsert() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "target"));
        when(bandMapper.selectList(any())).thenReturn(List.of());
        doThrow(new BizException(ErrorCode.PARAM_ERROR, "重叠"))
                .when(validator).validate(anyList());
        assertThrows(BizException.class, () -> service.copyPricingBands(1L, 2L));
        verify(bandMapper, never()).delete(any());
        verify(bandMapper, never()).insert(any(CourtPricingBand.class));
    }

    @Test
    void copyPricingBands_targetNotFound_throws() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.copyPricingBands(99L, 2L));
        verify(bandMapper, never()).selectList(any());
    }

    @Test
    void copyPricingBands_emptySource_doesNotInsert() {
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 100L, "target"));
        when(bandMapper.selectList(any())).thenReturn(List.of());
        doNothing().when(validator).validate(anyList());
        service.copyPricingBands(1L, 2L);
        verify(bandMapper, times(1)).delete(any());
        verify(bandMapper, never()).insert(any(CourtPricingBand.class));
    }
}
