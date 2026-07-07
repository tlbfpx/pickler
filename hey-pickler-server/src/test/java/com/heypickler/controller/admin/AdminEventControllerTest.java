package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.admin.PlacementPointsRequest;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.entity.Event;
import com.heypickler.service.EventService;
import com.heypickler.vo.EventVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Loop-v8 coverage sprint — moves AdminEventController from 52.4% to ~75%+.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminEventControllerTest {

    @Mock private EventService eventService;
    @Mock private com.heypickler.service.PlacementService placementService;
    @Mock private com.heypickler.service.PointService pointService;
    @InjectMocks private AdminEventController controller;

    @Test
    void listEvents_returnsPage() {
        PageResult<EventVO> stub = PageResult.of(0, 1, 10, java.util.List.of());
        doReturn(stub).when(eventService).adminListEvents(
                any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        PageResult<EventVO> result = controller.listEvents(
                "STAR", "OPEN", "loop", "BJ", null, null, 1, 10).getData();
        assertEquals(0L, result.getTotal());
    }

    @Test
    void changeStatus_delegates() {
        controller.changeStatus(7L, Map.of("status", "OPEN"));
    }

    @Test
    void getParticipants_delegates() {
        doReturn(List.of()).when(eventService).getParticipants(7L);
        assertEquals(0, controller.getParticipants(7L).getData().size());
    }

    @Test
    void detail_returnsEventVO() {
        EventVO e = new EventVO();
        doReturn(e).when(eventService).getEventDetail(7L);
        assertEquals(e, controller.detail(7L).getData());
    }

    @Test
    void enterPoints_delegates() {
        PointEntryRequest req = new PointEntryRequest();
        req.setEventId(1L);
        req.setType("STAR");
        req.setRecords(java.util.List.of(new PointEntryRequest.PointRecordItem() {{
            setUserId(7L);
            setPoints(50);
            setReason("test");
        }}));
        jakarta.servlet.http.HttpServletRequest httpReq =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(httpReq.getAttribute("adminId")).thenReturn(99L);
        controller.enterPoints(httpReq, 1L, req);
    }

    @Test
    void getRegistrations_delegates() {
        PageResult<com.heypickler.vo.RegistrationVO> stub = PageResult.of(0, 1, 10, java.util.List.of());
        doReturn(stub).when(eventService).getRegistrations(
                anyLong(), anyString(), anyString(), anyInt(), anyInt());
        controller.getRegistrations(7L, null, null, 1, 10);
    }

    @Test
    void deleteEvent_delegates() {
        controller.deleteEvent(7L);
    }

    @Test
    void createEvent_delegates() {
        EventCreateRequest req = new EventCreateRequest();
        doReturn(99L).when(eventService).createEvent(req, 1L);
        jakarta.servlet.http.HttpServletRequest httpReq =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(httpReq.getAttribute("adminId")).thenReturn(1L);
        java.util.Map<String, Long> data = controller.createEvent(httpReq, req).getData();
        assertEquals(99L, data.get("id"));
    }

    @Test
    void updateEvent_delegates() {
        controller.updateEvent(7L, new EventUpdateRequest());
    }

    @Test
    void placementPoints_getSet() {
        doReturn(new com.heypickler.vo.PlacementPointsVO()).when(placementService).getPoints(7L);
        controller.getPlacementPoints(7L);
        controller.setPlacementPoints(7L, new PlacementPointsRequest());
    }

    @Test
    void getPlacements_delegates() {
        doReturn(List.of()).when(placementService).listByEventId(7L);
        controller.getPlacements(7L);
    }

    // ──────────────── Loop-v13 — getEventSummary endpoint ────────────────

    @Test
    void getEventSummary_delegatesToService() {
        com.heypickler.vo.EventSummaryVO stub = new com.heypickler.vo.EventSummaryVO();
        stub.setEventId(7L);
        stub.setTitle("Cup");
        doReturn(stub).when(eventService).getEventSummary(7L);
        assertEquals(stub, controller.getEventSummary(7L).getData());
    }

    @Test
    void getEventSummary_serviceThrowsPropagates() {
        doThrow(new com.heypickler.common.exception.BizException(
                com.heypickler.common.exception.ErrorCode.NOT_FOUND, "not found"))
                .when(eventService).getEventSummary(99L);
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.getEventSummary(99L));
    }

    // ──────────────── Loop-v14 — bulkCheckIn endpoint ────────────────

    @Test
    void bulkCheckIn_happyPath_delegatesToService() {
        com.heypickler.dto.admin.BulkCheckInRequest req =
                new com.heypickler.dto.admin.BulkCheckInRequest();
        req.setRegistrationIds(java.util.List.of(101L, 102L, 103L));
        com.heypickler.vo.BulkCheckInResult stub = new com.heypickler.vo.BulkCheckInResult();
        stub.setEventId(1L);
        stub.setRequested(3);
        stub.setUpdated(3);
        doReturn(stub).when(eventService).bulkCheckIn(anyLong(), any());
        assertEquals(stub, controller.bulkCheckIn(1L, req).getData());
    }

    @Test
    void bulkCheckIn_servicePropagatesException() {
        com.heypickler.dto.admin.BulkCheckInRequest req =
                new com.heypickler.dto.admin.BulkCheckInRequest();
        req.setRegistrationIds(java.util.List.of(1L));
        doThrow(new com.heypickler.common.exception.BizException(
                com.heypickler.common.exception.ErrorCode.NOT_FOUND, "not found"))
                .when(eventService).bulkCheckIn(anyLong(), any());
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.bulkCheckIn(99L, req));
    }
}
