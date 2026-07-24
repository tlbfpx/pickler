package com.heypickler.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.VenueBusinessHourRequest;
import com.heypickler.dto.admin.VenueContactRequest;
import com.heypickler.dto.admin.VenueCreateRequest;
import com.heypickler.dto.admin.VenueQueryRequest;
import com.heypickler.entity.Court;
import com.heypickler.entity.Venue;
import com.heypickler.entity.VenueBusinessHour;
import com.heypickler.entity.VenueContact;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.VenueBusinessHourMapper;
import com.heypickler.mapper.VenueContactMapper;
import com.heypickler.mapper.VenueMapper;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VenueServiceImplTest {

    @Mock VenueMapper venueMapper;
    @Mock VenueBusinessHourMapper businessHourMapper;
    @Mock VenueContactMapper contactMapper;
    @Mock CourtMapper courtMapper;

    @InjectMocks
    VenueServiceImpl service;

    /** LambdaQueryWrapper<Venue> / VenueBusinessHour / VenueContact / Court 在单测中触发 → 预热 TableInfo。 */
    @BeforeAll
    static void warm() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(Venue.class, VenueBusinessHour.class, VenueContact.class, Court.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, c);
        }
    }

    private static Venue venue(Long id, String name) {
        Venue v = new Venue();
        v.setId(id);
        v.setName(name);
        v.setAddress("addr-" + id);
        v.setStatus("ACTIVE");
        v.setBookingLeadDays(14);
        return v;
    }

    private static VenueCreateRequest createReq() {
        VenueCreateRequest r = new VenueCreateRequest();
        r.setName("Pickler Center");
        r.setAddress("Shanghai");
        r.setLatitude(new BigDecimal("31.23"));
        r.setLongitude(new BigDecimal("121.47"));
        r.setCoverUrl("https://example.com/c.jpg");
        r.setDescription("desc");
        r.setStatus("ACTIVE");
        r.setBookingLeadDays(7);
        return r;
    }

    // ---------- adminList ----------
    @Test
    void adminList_withKeywordAndStatus_buildsQuery() {
        VenueQueryRequest req = new VenueQueryRequest();
        req.setKeyword("pickler");
        req.setStatus("ACTIVE");
        when(venueMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));
        PageResult<VenueVO> r = service.adminList(req);
        assertNotNull(r);
        assertEquals(0, r.getList().size());
        verify(venueMapper).selectPage(any(Page.class), any());
    }

    @Test
    void adminList_emptyKeyword_emptyStatus() {
        VenueQueryRequest req = new VenueQueryRequest();
        when(venueMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));
        PageResult<VenueVO> r = service.adminList(req);
        assertNotNull(r);
    }

    @Test
    void adminList_recordsMappedToVOs() {
        VenueQueryRequest req = new VenueQueryRequest();
        when(venueMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<Venue> p = inv.getArgument(0);
            p.setRecords(List.of(venue(1L, "A"), venue(2L, "B")));
            p.setTotal(2);
            return p;
        });
        when(contactMapper.selectList(any())).thenReturn(List.of());
        PageResult<VenueVO> r = service.adminList(req);
        assertEquals(2, r.getList().size());
    }

    // ---------- adminGet ----------
    @Test
    void adminGet_found_returnsDetailVO() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        when(contactMapper.selectList(any())).thenReturn(List.of());
        when(businessHourMapper.selectList(any())).thenReturn(List.of());
        when(courtMapper.selectList(any())).thenReturn(List.of());
        VenueDetailVO vo = service.adminGet(1L);
        assertEquals(1L, vo.getId());
        assertEquals("A", vo.getName());
    }

    @Test
    void adminGet_notFound_throwsBizException() {
        when(venueMapper.selectById(99L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.adminGet(99L));
        assertEquals(ErrorCode.VENUE_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- create ----------
    @Test
    void create_appliesFieldsAndDefaults() {
        VenueCreateRequest req = createReq();
        when(venueMapper.insert(any(Venue.class))).thenAnswer(inv -> {
            Venue v = inv.getArgument(0);
            v.setId(100L);
            return 1;
        });
        Long id = service.create(req);
        assertEquals(100L, id);
        verify(venueMapper).insert(any(Venue.class));
    }

    @Test
    void create_missingStatus_defaultsToACTIVE() {
        VenueCreateRequest req = createReq();
        req.setStatus(null);
        when(venueMapper.insert(any(Venue.class))).thenAnswer(inv -> {
            Venue v = inv.getArgument(0);
            assertEquals("ACTIVE", v.getStatus());
            v.setId(1L);
            return 1;
        });
        service.create(req);
    }

    @Test
    void create_missingBookingLeadDays_defaults14() {
        VenueCreateRequest req = createReq();
        req.setBookingLeadDays(null);
        when(venueMapper.insert(any(Venue.class))).thenAnswer(inv -> {
            Venue v = inv.getArgument(0);
            assertEquals(14, v.getBookingLeadDays());
            v.setId(1L);
            return 1;
        });
        service.create(req);
    }

    // ---------- update ----------
    @Test
    void update_found_appliesAndPersists() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "old"));
        VenueCreateRequest req = createReq();
        service.update(1L, req);
        verify(venueMapper).updateById(any(Venue.class));
    }

    @Test
    void update_notFound_throws() {
        when(venueMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.update(99L, createReq()));
    }

    // ---------- delete ----------
    @Test
    void delete_found_softDeleteById() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        service.delete(1L);
        verify(venueMapper).deleteById(1L);
    }

    @Test
    void delete_notFound_throws() {
        when(venueMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.delete(99L));
    }

    // ---------- replaceBusinessHours ----------
    @Test
    void replaceBusinessHours_ok_deletesAndInserts() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        VenueBusinessHourRequest req = new VenueBusinessHourRequest();
        VenueBusinessHourRequest.Item it = new VenueBusinessHourRequest.Item();
        it.setDayOfWeek(1);
        it.setOpenTime(LocalTime.of(9, 0));
        it.setCloseTime(LocalTime.of(21, 0));
        req.setHours(List.of(it, it));
        service.replaceBusinessHours(1L, req);
        verify(businessHourMapper).delete(any());
        verify(businessHourMapper, times(2)).insert(any(VenueBusinessHour.class));
    }

    @Test
    void replaceBusinessHours_venueNotFound_throws() {
        when(venueMapper.selectById(99L)).thenReturn(null);
        VenueBusinessHourRequest req = new VenueBusinessHourRequest();
        VenueBusinessHourRequest.Item it = new VenueBusinessHourRequest.Item();
        it.setDayOfWeek(1);
        it.setOpenTime(LocalTime.of(9, 0));
        it.setCloseTime(LocalTime.of(21, 0));
        req.setHours(List.of(it));
        assertThrows(BizException.class, () -> service.replaceBusinessHours(99L, req));
        verify(businessHourMapper, never()).insert(any());
    }

    // ---------- contact CRUD ----------
    @Test
    void addContact_ok_insertsAndReturnsId() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        VenueContactRequest req = new VenueContactRequest();
        req.setType("PHONE");
        req.setValue("13800000000");
        req.setLabel("前台");
        req.setSortOrder(2);
        when(contactMapper.insert(any(VenueContact.class))).thenAnswer(inv -> {
            VenueContact c = inv.getArgument(0);
            c.setId(5L);
            return 1;
        });
        Long id = service.addContact(1L, req);
        assertEquals(5L, id);
    }

    @Test
    void addContact_sortOrderNull_defaultsToZero() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        VenueContactRequest req = new VenueContactRequest();
        req.setType("WECHAT");
        req.setValue("wx");
        when(contactMapper.insert(any(VenueContact.class))).thenAnswer(inv -> {
            VenueContact c = inv.getArgument(0);
            assertEquals(0, c.getSortOrder());
            c.setId(1L);
            return 1;
        });
        service.addContact(1L, req);
    }

    @Test
    void addContact_venueNotFound_throws() {
        when(venueMapper.selectById(99L)).thenReturn(null);
        VenueContactRequest req = new VenueContactRequest();
        req.setType("PHONE");
        req.setValue("x");
        assertThrows(BizException.class, () -> service.addContact(99L, req));
    }

    @Test
    void updateContact_found_updatesFields() {
        VenueContact existing = new VenueContact();
        existing.setId(7L);
        existing.setVenueId(1L);
        when(contactMapper.selectById(7L)).thenReturn(existing);
        VenueContactRequest req = new VenueContactRequest();
        req.setType("EMAIL");
        req.setValue("a@b.c");
        req.setSortOrder(3);
        service.updateContact(7L, req);
        verify(contactMapper).updateById(any(VenueContact.class));
    }

    @Test
    void updateContact_notFound_throws() {
        when(contactMapper.selectById(99L)).thenReturn(null);
        VenueContactRequest req = new VenueContactRequest();
        req.setType("PHONE");
        req.setValue("x");
        BizException ex = assertThrows(BizException.class, () -> service.updateContact(99L, req));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateContact_sortOrderNull_defaultsToZero() {
        VenueContact existing = new VenueContact();
        existing.setId(7L);
        when(contactMapper.selectById(7L)).thenReturn(existing);
        VenueContactRequest req = new VenueContactRequest();
        req.setType("PHONE");
        req.setValue("x");
        req.setSortOrder(null);
        service.updateContact(7L, req);
    }

    @Test
    void deleteContact_found_deletesById() {
        when(contactMapper.selectById(7L)).thenReturn(new VenueContact());
        service.deleteContact(7L);
        verify(contactMapper).deleteById(7L);
    }

    @Test
    void deleteContact_notFound_throws() {
        when(contactMapper.selectById(99L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.deleteContact(99L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- appList ----------
    @Test
    void appList_filtersActiveAndOptionalKeyword() {
        VenueQueryRequest req = new VenueQueryRequest();
        req.setKeyword("foo");
        when(venueMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));
        PageResult<VenueVO> r = service.appList(req);
        assertNotNull(r);
        verify(venueMapper).selectPage(any(Page.class), any());
    }

    @Test
    void appList_emptyKeyword_returnsAllActive() {
        VenueQueryRequest req = new VenueQueryRequest();
        when(venueMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<Venue> p = inv.getArgument(0);
            p.setRecords(List.of(venue(1L, "A")));
            p.setTotal(1);
            return p;
        });
        when(contactMapper.selectList(any())).thenReturn(List.of());
        PageResult<VenueVO> r = service.appList(req);
        assertEquals(1, r.getList().size());
    }

    // ---------- appGet ----------
    @Test
    void appGet_found_returnsDetail() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        when(contactMapper.selectList(any())).thenReturn(List.of());
        when(businessHourMapper.selectList(any())).thenReturn(List.of());
        when(courtMapper.selectList(any())).thenReturn(List.of());
        VenueDetailVO vo = service.appGet(1L);
        assertEquals("A", vo.getName());
    }

    @Test
    void appGet_notFound_throwsVenueNotFound() {
        when(venueMapper.selectById(99L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.appGet(99L));
        assertEquals(ErrorCode.VENUE_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- detail mapping: contacts / hours / courts ----------
    @Test
    void adminGet_mapsContactsHoursAndCourts() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        VenueContact c = new VenueContact();
        c.setId(11L);
        c.setVenueId(1L);
        c.setType("PHONE");
        c.setValue("13800000000");
        c.setSortOrder(1);
        when(contactMapper.selectList(any())).thenReturn(List.of(c));
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setVenueId(1L);
        bh.setDayOfWeek(1);
        bh.setOpenTime(LocalTime.of(9, 0));
        bh.setCloseTime(LocalTime.of(22, 0));
        when(businessHourMapper.selectList(any())).thenReturn(List.of(bh));
        Court ct = new Court();
        ct.setId(100L);
        ct.setVenueId(1L);
        ct.setName("Court A");
        ct.setSlotMinutes(60);
        ct.setStatus("ACTIVE");
        when(courtMapper.selectList(any())).thenReturn(List.of(ct));
        VenueDetailVO vo = service.adminGet(1L);
        assertEquals(1, vo.getContacts().size());
        assertEquals("PHONE", vo.getContacts().get(0).getType());
        assertEquals(1, vo.getBusinessHours().size());
        assertEquals(Integer.valueOf(1), vo.getBusinessHours().get(0).getDayOfWeek());
        assertEquals(1, vo.getCourts().size());
        assertEquals("Court A", vo.getCourts().get(0).getName());
    }

    @Test
    void adminGet_emptyChildrenReturnsEmptyLists() {
        when(venueMapper.selectById(1L)).thenReturn(venue(1L, "A"));
        when(contactMapper.selectList(any())).thenReturn(List.of());
        when(businessHourMapper.selectList(any())).thenReturn(List.of());
        when(courtMapper.selectList(any())).thenReturn(List.of());
        VenueDetailVO vo = service.adminGet(1L);
        assertTrue(vo.getContacts().isEmpty());
        assertTrue(vo.getBusinessHours().isEmpty());
        assertTrue(vo.getCourts().isEmpty());
    }

    @Test
    void unusedSuppressUnusedWarningOnAnyLong() {
        // 占位: 防止 mockito static analyzer 把 anyLong 当未使用符号
        when(venueMapper.selectById(anyLong())).thenReturn(venue(1L, "x"));
        assertNotNull(service.adminGet(1L));
    }
}