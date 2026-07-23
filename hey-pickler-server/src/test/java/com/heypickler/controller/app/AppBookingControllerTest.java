package com.heypickler.controller.app;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingCreateResultVO;
import com.heypickler.vo.BookingVO;
import jakarta.servlet.http.HttpServletRequest;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Chunk 3 / Task 3.4 — direct method-call unit tests for AppBookingController.
 * No MockMvc; mock BookingService only. Verifies delegation + Result wrapping.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppBookingControllerTest {

    @Mock private BookingService bookingService;
    @InjectMocks private AppBookingController controller;

    private HttpServletRequest mockReq() {
        return mock(HttpServletRequest.class);
    }

    @Test
    void create_delegatesToServiceAndWraps() {
        BookingCreateRequest body = new BookingCreateRequest();
        body.setCourtId(11L);
        body.setSlotStart(LocalDateTime.of(2026, 7, 25, 9, 0));
        body.setSlotsCount(2);

        BookingCreateResultVO vo = new BookingCreateResultVO();
        vo.setId(1L);
        vo.setBookingNo("BK20260725-0001");
        vo.setStatus("CONFIRMED");
        when(bookingService.create(any(HttpServletRequest.class), eq(body))).thenReturn(vo);

        BookingCreateResultVO result = controller.create(mockReq(), body).getData();
        assertNotNull(result);
        assertEquals("BK20260725-0001", result.getBookingNo());
        verify(bookingService).create(any(HttpServletRequest.class), eq(body));
    }

    @Test
    void my_usesGroupDefaultUpcoming() {
        BookingVO v = new BookingVO();
        v.setId(1L);
        v.setBookingNo("BK20260725-0002");
        PageResult<BookingVO> stub = PageResult.of(1, 1, 10, List.of(v));
        when(bookingService.listMine(any(HttpServletRequest.class), eq("upcoming"), eq(1), eq(10)))
                .thenReturn(stub);

        PageResult<BookingVO> result = controller.my(mockReq(), "upcoming", 1, 10).getData();
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        verify(bookingService).listMine(any(HttpServletRequest.class), eq("upcoming"), eq(1), eq(10));
    }

    @Test
    void my_historyGroupDelegates() {
        when(bookingService.listMine(any(), eq("history"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(0, 1, 10, List.of()));
        PageResult<BookingVO> result = controller.my(mockReq(), "history", 1, 10).getData();
        assertEquals(0L, result.getTotal());
        verify(bookingService).listMine(any(), eq("history"), eq(1), eq(10));
    }

    @Test
    void cancel_callsCancelMine() {
        controller.cancel(mockReq(), 42L);
        verify(bookingService).cancelMine(any(HttpServletRequest.class), eq(42L));
    }

    @Test
    void cancel_notFoundPropagatesBizException() {
        doThrow(new BizException(ErrorCode.BOOKING_NOT_FOUND))
                .when(bookingService).cancelMine(any(HttpServletRequest.class), eq(99L));
        assertThrows(BizException.class, () -> controller.cancel(mockReq(), 99L));
    }
}