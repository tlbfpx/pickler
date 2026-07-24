package com.heypickler.controller.admin;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingAdminVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Chunk 3 / Task 3.4 — direct method-call unit tests for AdminBookingController.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBookingControllerTest {

    @Mock private BookingService bookingService;
    @InjectMocks private AdminBookingController controller;

    @Test
    void list_returnsPage() {
        BookingAdminVO vo = new BookingAdminVO();
        vo.setId(1L);
        vo.setBookingNo("BK20260725-0001");
        PageResult<BookingAdminVO> stub = PageResult.of(1, 1, 20, List.of(vo));
        when(bookingService.listAdmin(any(BookingQueryRequest.class))).thenReturn(stub);

        BookingQueryRequest q = new BookingQueryRequest();
        q.setVenueId(10L);
        PageResult<BookingAdminVO> result = controller.list(q).getData();
        assertEquals(1L, result.getTotal());
        verify(bookingService).listAdmin(any(BookingQueryRequest.class));
    }

    @Test
    void get_returnsBookingAdminVO() {
        BookingAdminVO vo = new BookingAdminVO();
        vo.setId(7L);
        vo.setBookingNo("BK20260725-0007");
        when(bookingService.getAdmin(7L)).thenReturn(vo);

        BookingAdminVO result = controller.get(7L).getData();
        assertEquals(7L, result.getId());
        assertEquals("BK20260725-0007", result.getBookingNo());
    }

    @Test
    void complete_callsService() {
        controller.complete(7L);
        verify(bookingService).complete(7L);
    }

    @Test
    void noShow_callsService() {
        controller.noShow(7L);
        verify(bookingService).markNoShow(7L);
    }

    @Test
    void forceCancel_withBody_callsService() {
        BookingForceCancelRequest body = new BookingForceCancelRequest();
        body.setReason("admin cleanup");
        controller.cancel(7L, body);
        verify(bookingService).forceCancel(eq(7L), eq(body));
    }

    @Test
    void forceCancel_nullBodyStillCallsService() {
        controller.cancel(7L, null);
        verify(bookingService).forceCancel(eq(7L), eq(null));
    }

    @Test
    void get_notFoundPropagatesBizException() {
        doThrow(new BizException(ErrorCode.BOOKING_NOT_FOUND)).when(bookingService).getAdmin(99L);
        assertThrows(BizException.class, () -> controller.get(99L));
    }
}