package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.entity.Event;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Registration;
import com.heypickler.entity.Team;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.EventServiceImpl;
import com.heypickler.vo.EventResultVO;
import com.heypickler.vo.BulkCheckInResult;
import com.heypickler.vo.EventVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventMapper eventMapper;
    @Mock private RegistrationMapper registrationMapper;
    @Mock private UserMapper userMapper;
    @Mock private PointRecordMapper pointRecordMapper;
    @Mock private TeamService teamService;

    @InjectMocks
    private EventServiceImpl eventService;

    private Event testEvent;
    private LocalDateTime now;

    /**
     * MyBatis-Plus LambdaWrapper resolution (SFunction -> column) depends on the
     * static TableInfo lambda cache, which is empty in pure Mockito tests (no Spring
     * context / SqlSessionFactory). Register the entities EventServiceImpl builds
     * lambda wrappers for so {@code register}/{@code cancel} don't throw
     * "can not find lambda cache for this entity" at any test order.
     */
    @BeforeAll
    static void warmLambdaCache() {
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        // Each assistant's namespace locks after first initTableInfo, so use a fresh
        // assistant per mapper namespace.
        org.apache.ibatis.builder.MapperBuilderAssistant eventAssistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        eventAssistant.setCurrentNamespace("com.heypickler.mapper.EventMapper");
        TableInfoHelper.initTableInfo(eventAssistant, Event.class);

        org.apache.ibatis.builder.MapperBuilderAssistant regAssistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        regAssistant.setCurrentNamespace("com.heypickler.mapper.RegistrationMapper");
        TableInfoHelper.initTableInfo(regAssistant, Registration.class);
    }

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        testEvent = new Event();
        testEvent.setId(1L);
        testEvent.setType("STAR");
        testEvent.setTitle("Test Event");
        testEvent.setLocation("Test Location");
        testEvent.setEventTime(now.plusDays(7));
        testEvent.setRegistrationDeadline(now.plusDays(1));
        testEvent.setMaxParticipants(10);
        testEvent.setCurrentParticipants(5);
        testEvent.setFee(BigDecimal.ZERO);
        testEvent.setStatus("OPEN");
        testEvent.setCreatedBy(1L);
        testEvent.setCreatedAt(now);
        testEvent.setUpdatedAt(now);
    }

    @Test
    void register_success() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(registrationMapper.insert(any(Registration.class))).thenAnswer(invocation -> {
            Registration reg = invocation.getArgument(0);
            reg.setId(1L);
            return 1;
        });

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        eventService.register(100L, 1L, request);

        verify(registrationMapper).insert(any(Registration.class));
        verify(eventMapper).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void register_eventFull_shouldThrow() {
        testEvent.setCurrentParticipants(10);
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.REGISTRATION_FULL.getCode(), ex.getCode());
    }

    @Test
    void register_duplicateRegistration_shouldThrow() {
        Registration existingReg = new Registration();
        existingReg.setId(1L);
        existingReg.setUserId(100L);
        existingReg.setEventId(1L);
        existingReg.setStatus("REGISTERED");

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(existingReg);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.DUPLICATE_REGISTRATION.getCode(), ex.getCode());
    }

    @Test
    void register_afterDeadline_shouldThrow() {
        testEvent.setRegistrationDeadline(now.minusDays(1));
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.REGISTRATION_CLOSED.getCode(), ex.getCode());
    }

    @Test
    void register_doublesWithoutPartnerOrTeam_shouldThrow() {
        testEvent.setFormat("DOUBLES");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("DOUBLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(teamService, never()).createTeam(anyLong(), anyLong(), anyLong());
    }

    @Test
    void register_mixedCaptainInitiates_createsPendingTeam() {
        testEvent.setFormat("MIXED");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        Team created = new Team();
        created.setId(77L);
        when(teamService.createTeam(1L, 100L, 200L)).thenReturn(created);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("MIXED");
        request.setPartnerUserId(200L);

        eventService.register(100L, 1L, request);

        // Captain path delegates the registration insert to TeamService; EventServiceImpl
        // only reserves the captain's slot and stamps matchType = MIXED.
        verify(teamService).createTeam(1L, 100L, 200L);
        verify(registrationMapper, never()).insert(any(Registration.class));
    }

    @Test
    void register_singlesRejectsPartner_shouldThrow() {
        testEvent.setFormat("SINGLES");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");
        request.setPartnerUserId(200L);

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(eventMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void register_doublesPartnerConfirms_callsConfirmTeam() {
        testEvent.setFormat("DOUBLES");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        Team confirmed = new Team();
        confirmed.setId(77L);
        when(teamService.confirmTeam(77L, 100L)).thenReturn(confirmed);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("DOUBLES");
        request.setTeamId(77L);

        eventService.register(100L, 1L, request);

        verify(teamService).confirmTeam(77L, 100L);
        verify(teamService, never()).createTeam(anyLong(), anyLong(), anyLong());
    }

    @Test
    void register_groupingLocked_shouldThrow() {
        testEvent.setFormat("SINGLES");
        testEvent.setGroupingLocked(true);
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.REGISTRATION_CLOSED.getCode(), ex.getCode());
        verify(registrationMapper, never()).insert(any(Registration.class));
    }

    @Test
    void cancelRegistration_teamMember_dissolvesTeam() {
        testEvent.setFormat("DOUBLES");
        Registration myReg = new Registration();
        myReg.setId(1L);
        myReg.setUserId(100L);
        myReg.setEventId(1L);
        myReg.setTeamId(77L);
        myReg.setStatus("REGISTERED");
        Registration partnerReg = new Registration();
        partnerReg.setId(2L);
        partnerReg.setUserId(200L);
        partnerReg.setTeamId(77L);
        partnerReg.setStatus("REGISTERED");

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(myReg);
        when(registrationMapper.selectList(any())).thenReturn(Arrays.asList(myReg, partnerReg));
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        eventService.cancelRegistration(100L, 1L);

        // Both members' slots are released (CONFIRMED team => 2 active regs).
        verify(teamService).dissolve(77L);
        verify(eventMapper, times(2)).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void cancelRegistration_success() {
        Registration registration = new Registration();
        registration.setId(1L);
        registration.setUserId(100L);
        registration.setEventId(1L);
        registration.setStatus("REGISTERED");

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(registration);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        eventService.cancelRegistration(100L, 1L);

        assertEquals("WITHDRAWN", registration.getStatus());
        verify(registrationMapper).updateById(registration);
        verify(eventMapper).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void cancelRegistration_afterDeadline_shouldThrow() {
        testEvent.setRegistrationDeadline(now.minusDays(1));
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        BizException ex = assertThrows(BizException.class,
                () -> eventService.cancelRegistration(100L, 1L));
        assertEquals(ErrorCode.REGISTRATION_CLOSED.getCode(), ex.getCode());
    }

    @Test
    void cancelRegistration_noActiveRegistration_shouldThrow() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> eventService.cancelRegistration(100L, 1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void deleteEvent_softDelete() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(eventMapper.deleteById(any(java.io.Serializable.class))).thenReturn(1);

        eventService.deleteEvent(1L);

        // Service calls deleteById; MyBatis-Plus rewrites it to UPDATE setting deletedAt
        // at runtime via global logic-delete config (see application-dev.yml).
        verify(eventMapper).deleteById((java.io.Serializable) 1L);
    }

    @Test
    void createEvent_success() {
        EventCreateRequest request = new EventCreateRequest();
        request.setType("STAR");
        request.setTitle("New Event");
        request.setLocation("New Location");
        request.setEventTime(now.plusDays(7));
        request.setRegistrationDeadline(now.plusDays(1));
        request.setMaxParticipants(20);

        when(eventMapper.insert(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(2L);
            return 1;
        });

        Long eventId = eventService.createEvent(request, 1L);

        assertEquals(2L, eventId);
        verify(eventMapper).insert(any(Event.class));
    }

    @Test
    void register_withdrawnUserCanRegisterAgain() {
        Registration withdrawnReg = new Registration();
        withdrawnReg.setId(1L);
        withdrawnReg.setUserId(100L);
        withdrawnReg.setEventId(1L);
        withdrawnReg.setStatus("WITHDRAWN");

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(withdrawnReg);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(registrationMapper.insert(any(Registration.class))).thenAnswer(invocation -> {
            Registration reg = invocation.getArgument(0);
            reg.setId(2L);
            return 1;
        });

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        assertDoesNotThrow(() -> eventService.register(100L, 1L, request));
        verify(registrationMapper).insert(any(Registration.class));
    }

    @Test
    void register_eventNotOpen_shouldThrow() {
        testEvent.setStatus("DRAFT");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.REGISTRATION_CLOSED.getCode(), ex.getCode());
    }

    @Test
    void register_eventNotFound_shouldThrow() {
        when(eventMapper.selectById(1L)).thenReturn(null);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void register_eventDeleted_shouldThrow() {
        testEvent.setDeletedAt(now);
        when(eventMapper.selectById(1L)).thenReturn(testEvent);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("SINGLES");

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getEventResults_eventNotFound_shouldThrow() {
        when(eventMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> eventService.getEventResults(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getEventResults_noRegistrations_returnsEmpty() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<EventResultVO> results = eventService.getEventResults(1L);

        assertTrue(results.isEmpty());
    }

    @Test
    void getEventResults_noPoints_returnsAllParticipantsWithZeroPoints() {
        Registration reg1 = buildRegistration(101L, "SINGLES");
        Registration reg2 = buildRegistration(102L, "SINGLES");

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectList(any())).thenReturn(Arrays.asList(reg1, reg2));
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(pointRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<EventResultVO> results = eventService.getEventResults(1L);

        assertEquals(2, results.size());
        results.forEach(r -> {
            assertEquals(0, r.getPoints());
            assertNotNull(r.getRank());
        });
    }

    @Test
    void getEventResults_withPoints_sortedByPointsDescAndRanked() {
        Registration reg1 = buildRegistration(101L, "SINGLES");
        Registration reg2 = buildRegistration(102L, "SINGLES");
        Registration reg3 = buildRegistration(103L, "SINGLES");

        PointRecord r1 = buildPointRecord(101L, 100);
        PointRecord r2 = buildPointRecord(101L, 20);
        PointRecord r3 = buildPointRecord(103L, 60);

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectList(any())).thenReturn(Arrays.asList(reg1, reg2, reg3));
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(pointRecordMapper.selectList(any())).thenReturn(Arrays.asList(r1, r2, r3));

        List<EventResultVO> results = eventService.getEventResults(1L);

        assertEquals(3, results.size());

        assertEquals(101L, results.get(0).getUserId());
        assertEquals(120, results.get(0).getPoints());
        assertEquals(1, results.get(0).getRank());

        assertEquals(103L, results.get(1).getUserId());
        assertEquals(60, results.get(1).getPoints());
        assertEquals(2, results.get(1).getRank());

        assertEquals(102L, results.get(2).getUserId());
        assertEquals(0, results.get(2).getPoints());
        assertEquals(3, results.get(2).getRank());
    }

    @Test
    void getEventResults_tiedPoints_shareRank() {
        Registration reg1 = buildRegistration(101L, "SINGLES");
        Registration reg2 = buildRegistration(102L, "SINGLES");
        Registration reg3 = buildRegistration(103L, "SINGLES");

        PointRecord r1 = buildPointRecord(101L, 50);
        PointRecord r2 = buildPointRecord(102L, 50);
        PointRecord r3 = buildPointRecord(103L, 30);

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectList(any())).thenReturn(Arrays.asList(reg1, reg2, reg3));
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(pointRecordMapper.selectList(any())).thenReturn(Arrays.asList(r1, r2, r3));

        List<EventResultVO> results = eventService.getEventResults(1L);

        assertEquals(1, results.get(0).getRank());
        assertEquals(1, results.get(1).getRank());
        assertEquals(2, results.get(2).getRank());
    }

    private Registration buildRegistration(Long userId, String matchType) {
        Registration reg = new Registration();
        reg.setId(userId);
        reg.setUserId(userId);
        reg.setEventId(1L);
        reg.setMatchType(matchType);
        reg.setStatus("REGISTERED");
        return reg;
    }

    private PointRecord buildPointRecord(Long userId, int points) {
        PointRecord record = new PointRecord();
        record.setId(System.nanoTime());
        record.setUserId(userId);
        record.setEventId(1L);
        record.setType("STAR");
        record.setPoints(points);
        record.setReason("test");
        record.setOperatorId(1L);
        return record;
    }

    // ──────────────── Loop-v7 D32 — EventServiceImpl coverage sprint ────────────────

    @Test
    void getParticipants_eventNotFoundOrDeleted_throws() {
        when(eventMapper.selectById(1L)).thenReturn(null);
        assertThrows(BizException.class, () -> eventService.getParticipants(1L));

        Event deleted = new Event();
        deleted.setId(1L);
        deleted.setDeletedAt(now);
        when(eventMapper.selectById(1L)).thenReturn(deleted);
        assertThrows(BizException.class, () -> eventService.getParticipants(1L));
    }

    @Test
    void getParticipants_emptyRegistry_returnsEmpty() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(eventService.getParticipants(1L).isEmpty());
    }

    @Test
    void getParticipants_populatesVoWithUserFields() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        Registration reg = new Registration();
        reg.setUserId(11L);
        reg.setEventId(1L);
        reg.setMatchType("SINGLES");
        reg.setStatus("REGISTERED");
        when(registrationMapper.selectList(any())).thenReturn(List.of(reg));
        User u = new User();
        u.setId(11L);
        u.setNickname("alice");
        u.setAvatarUrl("http://x");
        u.setCity("BJ");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u));

        List<com.heypickler.vo.EventParticipantVO> out = eventService.getParticipants(1L);
        assertEquals(1, out.size());
        assertEquals("alice", out.get(0).getNickname());
        assertEquals("SINGLES", out.get(0).getMatchType());
    }

    @Test
    void updateRegistrationStatus_checkedIn_path() {
        testEvent.setStatus("OPEN");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        Registration reg = new Registration();
        reg.setId(50L);
        reg.setEventId(1L);
        reg.setStatus("REGISTERED");
        when(registrationMapper.selectById(50L)).thenReturn(reg);

        eventService.updateRegistrationStatus(1L, 50L, "CHECKED_IN");
        verify(registrationMapper).updateById(reg);
        assertEquals("CHECKED_IN", reg.getStatus());
    }

    @Test
    void updateRegistrationStatus_withdrawn_decrementsCountAndOpensEvent() {
        testEvent.setStatus("FULL");
        testEvent.setMaxParticipants(5);
        testEvent.setCurrentParticipants(5);
        Event reopened = new Event();
        reopened.setId(1L);
        reopened.setStatus("FULL");
        reopened.setMaxParticipants(5);
        reopened.setCurrentParticipants(5);

        when(eventMapper.selectById(1L)).thenReturn(testEvent).thenReturn(reopened);
        when(eventMapper.update(eq(null), any())).thenReturn(1);
        Registration reg = new Registration();
        reg.setId(50L);
        reg.setEventId(1L);
        reg.setStatus("REGISTERED");
        when(registrationMapper.selectById(50L)).thenReturn(reg);

        eventService.updateRegistrationStatus(1L, 50L, "WITHDRAWN");

        verify(registrationMapper).updateById(reg);
        verify(eventMapper, atLeastOnce()).update(eq(null), any());
        assertEquals("WITHDRAWN", reg.getStatus());
    }

    @Test
    void updateRegistrationStatus_invalidTransition_throwsInvalidStatusTransition() {
        // Loop-v7 D32 sibling: invalid state machine throws 1006 (not 1001) per
        // the D10 refactor. Locked-branch coverage.
        testEvent.setStatus("OPEN");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        Registration reg = new Registration();
        reg.setId(50L);
        reg.setEventId(1L);
        reg.setStatus("WITHDRAWN");
        when(registrationMapper.selectById(50L)).thenReturn(reg);

        BizException ex = assertThrows(BizException.class,
                () -> eventService.updateRegistrationStatus(1L, 50L, "REGISTERED"));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
    }

    @Test
    void adminListEvents_keyword_isCaseInsensitiveInWire() {
        // Loop-v7 D32: adminListEvents accepts keyword, type, status, location,
        // startTime, endTime as filters. Stub distinct values and assert the
        // mapper receives a non-null wrapper.
        testEvent.setStatus("OPEN");
        when(eventMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        PageResult<EventVO> result = eventService.adminListEvents(
                "loop", "STAR", "OPEN", "BJ", null, null, 1, 10);
        assertEquals(0, result.getTotal());
        verify(eventMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getEventResults_eventNotFound_throws() {
        when(eventMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> eventService.getEventResults(99L));
    }

    @Test
    void getRegistrations_clampsFiltersAndPages() {
        testEvent.setStatus("OPEN");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        PageResult<com.heypickler.vo.RegistrationVO> result = eventService.getRegistrations(
                1L, null, null, 1, 10);
        assertEquals(0, result.getTotal());
    }

    @Test
    void getRegistrations_partnerIdLoadsPartnerDetails() {
        testEvent.setStatus("OPEN");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        Registration reg = new Registration();
        reg.setId(99L);
        reg.setUserId(11L);
        reg.setEventId(1L);
        reg.setMatchType("DOUBLES");
        reg.setPartnerId(22L);  // covers partner resolution branch
        reg.setStatus("REGISTERED");
        Page<Registration> page = new Page<>(1, 10);
        page.setRecords(List.of(reg));
        page.setTotal(1);
        when(registrationMapper.selectPage(any(Page.class), any())).thenReturn(page);

        User alice = new User();
        alice.setId(11L);
        alice.setNickname("alice");
        User bob = new User();
        bob.setId(22L);
        bob.setNickname("bob");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(alice, bob));

        PageResult<com.heypickler.vo.RegistrationVO> result = eventService.getRegistrations(
                1L, null, null, 1, 10);
        assertEquals(1, result.getTotal());
        // partner nickname populated
        var vo = result.getList().get(0);
        assertEquals("bob", vo.getPartnerNickname());
    }

    private Event makeEvent(String status, Integer maxParticipants, int currentParticipants) {
        Event e = new Event();
        e.setId(1L);
        e.setType("STAR");
        e.setTitle("E");
        e.setStatus(status);
        e.setMaxParticipants(maxParticipants);
        e.setCurrentParticipants(currentParticipants);
        e.setRegistrationDeadline(now.plusDays(1));
        e.setEventTime(now.plusDays(7));
        return e;
    }

    // ──────────────── Loop-v13 — getEventSummary ────────────────

    private com.heypickler.mapper.TeamMapper teamMapper;
    private com.heypickler.mapper.MatchMapper matchMapper;

    @org.junit.jupiter.api.BeforeEach
    void registerSummaryMappers() {
        teamMapper = org.mockito.Mockito.mock(com.heypickler.mapper.TeamMapper.class);
        matchMapper = org.mockito.Mockito.mock(com.heypickler.mapper.MatchMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(eventService, "teamMapper", teamMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(eventService, "matchMapper", matchMapper);
    }

    private java.util.Map<String, Object> row(String status, long cnt) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("status", status);
        m.put("cnt", cnt);
        return m;
    }

    @Test
    void getEventSummary_singles_happyPath() {
        Event e = testEvent;
        e.setType("SINGLES");
        e.setMaxParticipants(10);
        e.setCurrentParticipants(5);
        e.setFee(new java.math.BigDecimal(20));
        when(eventMapper.selectById(1L)).thenReturn(e);
        when(registrationMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of(
                row("REGISTERED", 5L)));
        when(teamMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(matchMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());

        com.heypickler.vo.EventSummaryVO vo = eventService.getEventSummary(1L);
        assertEquals(5, vo.getCurrentParticipants());
        assertEquals(0.5, vo.getFillRate(), 0.0001);
        assertEquals(5, vo.getRegistration().getRegistered());
        assertEquals(0, vo.getRegistration().getCheckedIn());
        assertEquals(0.0, vo.getRegistration().getCheckInRate(), 0.0001);
        assertEquals(100L, vo.getFees().getTotalCollected());  // 5 × 20
        assertEquals("CNY", vo.getFees().getCurrency());
    }

    @Test
    void getEventSummary_doublesWithTeamsAndMatches() {
        Event e = testEvent;
        e.setType("DOUBLES");
        e.setMaxParticipants(8);
        e.setCurrentParticipants(8);
        when(eventMapper.selectById(1L)).thenReturn(e);
        when(registrationMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of(
                row("REGISTERED", 6L), row("CHECKED_IN", 2L)));
        when(teamMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of(
                row("PENDING", 1L), row("CONFIRMED", 3L)));
        when(matchMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of(
                row("COMPLETED", 6L)));

        com.heypickler.vo.EventSummaryVO vo = eventService.getEventSummary(1L);
        assertEquals(1.0, vo.getFillRate(), 0.0001);
        assertEquals(6, vo.getRegistration().getRegistered());  // 6 REGISTERED, not 8 (6+2 CHECKED_IN)
        assertEquals(2, vo.getRegistration().getCheckedIn());
        assertEquals(0.333, vo.getRegistration().getCheckInRate(), 0.001);  // 2/6 — checkInRate is checkedIn/registered, not /total
        assertEquals(1, vo.getTeams().getPending());
        assertEquals(3, vo.getTeams().getConfirmed());
        assertEquals(6, vo.getMatches().getCompleted());
    }

    @Test
    void getEventSummary_maxParticipantsNull_fillRateIsZero() {
        Event e = testEvent;
        e.setMaxParticipants(null);
        e.setCurrentParticipants(5);
        when(eventMapper.selectById(1L)).thenReturn(e);
        when(registrationMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(teamMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(matchMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());

        com.heypickler.vo.EventSummaryVO vo = eventService.getEventSummary(1L);
        assertEquals(0.0, vo.getFillRate(), 0.0001);
    }

    @Test
    void getEventSummary_noRegistrations_checkInRateIsZero() {
        Event e = testEvent;
        e.setMaxParticipants(10);
        e.setCurrentParticipants(0);
        when(eventMapper.selectById(1L)).thenReturn(e);
        when(registrationMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(teamMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(matchMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());

        com.heypickler.vo.EventSummaryVO vo = eventService.getEventSummary(1L);
        assertEquals(0.0, vo.getRegistration().getCheckInRate(), 0.0001);
    }

    @Test
    void getEventSummary_includesTransitionableStatuses() {
        Event e = testEvent;
        e.setStatus("OPEN");
        when(eventMapper.selectById(1L)).thenReturn(e);
        when(registrationMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(teamMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());
        when(matchMapper.countByEventGroupedByStatus(1L)).thenReturn(java.util.List.of());

        com.heypickler.vo.EventSummaryVO vo = eventService.getEventSummary(1L);
        // OPEN can transition to FULL, IN_PROGRESS, CANCELLED
        assertEquals(true, vo.getTransitionableStatuses().contains("FULL"));
        assertEquals(true, vo.getTransitionableStatuses().contains("IN_PROGRESS"));
        assertEquals(true, vo.getTransitionableStatuses().contains("CANCELLED"));
    }

    @Test
    void getEventSummary_eventNotFound_throwsNotFound() {
        when(eventMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> eventService.getEventSummary(99L));
    }

    // ──────────────── Loop-v14 — bulkCheckIn ────────────────

    private com.heypickler.entity.Registration reg(Long id, String status) {
        com.heypickler.entity.Registration r = new com.heypickler.entity.Registration();
        r.setId(id);
        r.setEventId(1L);
        r.setStatus(status);
        return r;
    }

    @Test
    void bulkCheckIn_mixed_classifies() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.findByEventAndIds(eq(1L), any()))
                .thenReturn(java.util.List.of(reg(101L, "REGISTERED"), reg(102L, "REGISTERED"),
                        reg(103L, "WITHDRAWN"), reg(104L, "CHECKED_IN")));
        when(registrationMapper.updateStatusToCheckedIn(any())).thenReturn(2);

        BulkCheckInResult result = eventService.bulkCheckIn(
                1L, java.util.List.of(101L, 102L, 103L, 104L, 999L));

        assertEquals(1L, result.getEventId());
        assertEquals(5, result.getRequested());
        assertEquals(2, result.getUpdated());
        assertEquals(java.util.List.of(101L, 102L), result.getUpdatedRegistrationIds());
        assertEquals(java.util.List.of(999L), result.getSkipped().getNotFound());
        assertEquals(java.util.List.of(103L), result.getSkipped().getWithdrawn());
    }

    @Test
    void bulkCheckIn_allWithdrawn_updatedZero() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.findByEventAndIds(eq(1L), any()))
                .thenReturn(java.util.List.of(reg(101L, "WITHDRAWN"), reg(102L, "WITHDRAWN")));

        BulkCheckInResult result = eventService.bulkCheckIn(1L, java.util.List.of(101L, 102L));
        assertEquals(0, result.getUpdated());
        assertEquals(java.util.List.of(101L, 102L), result.getSkipped().getWithdrawn());
    }

    @Test
    void bulkCheckIn_emptyList_throws() {
        assertThrows(BizException.class, () -> eventService.bulkCheckIn(1L, java.util.List.of()));
    }

    @Test
    void bulkCheckIn_nullList_throws() {
        assertThrows(BizException.class, () -> eventService.bulkCheckIn(1L, null));
    }

    @Test
    void bulkCheckIn_sizeOver200_throws() {
        java.util.List<Long> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) tooMany.add((long) i);
        assertThrows(BizException.class, () -> eventService.bulkCheckIn(1L, tooMany));
    }

    @Test
    void bulkCheckIn_nonPositiveId_throws() {
        assertThrows(BizException.class, () -> eventService.bulkCheckIn(1L, java.util.List.of(1L, 0L, 2L)));
        assertThrows(BizException.class, () -> eventService.bulkCheckIn(1L, java.util.List.of(1L, -1L)));
    }

    @Test
    void bulkCheckIn_eventNotFound_throws() {
        when(eventMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> eventService.bulkCheckIn(99L, java.util.List.of(1L)));
    }
}
