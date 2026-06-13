package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.entity.Event;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.EventServiceImpl;
import com.heypickler.vo.EventResultVO;
import org.junit.jupiter.api.AfterAll;
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

    private static boolean tableInfoInitialized = false;

    @Mock private EventMapper eventMapper;
    @Mock private RegistrationMapper registrationMapper;
    @Mock private UserMapper userMapper;
    @Mock private PointRecordMapper pointRecordMapper;

    @InjectMocks
    private EventServiceImpl eventService;

    private Event testEvent;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        // Initialize MyBatis Plus table info cache for Event entity
        if (!tableInfoInitialized) {
            TableInfo tableInfo = TableInfoHelper.getTableInfo(Event.class);
            tableInfoInitialized = true;
        }
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
    void register_doublesWithoutPartner_shouldThrow() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("DOUBLES");
        request.setPartnerId(null);

        BizException ex = assertThrows(BizException.class,
                () -> eventService.register(100L, 1L, request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void register_mixedWithPartner_success() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(registrationMapper.insert(any(Registration.class))).thenAnswer(invocation -> {
            Registration reg = invocation.getArgument(0);
            reg.setId(1L);
            return 1;
        });

        RegisterRequest request = new RegisterRequest();
        request.setMatchType("MIXED");
        request.setPartnerId(200L);

        eventService.register(100L, 1L, request);

        verify(registrationMapper).insert(any(Registration.class));
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
}
