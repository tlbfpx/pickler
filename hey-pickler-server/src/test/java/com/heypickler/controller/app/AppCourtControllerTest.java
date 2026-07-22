package com.heypickler.controller.app;

import com.heypickler.service.CourtService;
import com.heypickler.service.SlotService;
import com.heypickler.vo.CourtVO;
import com.heypickler.vo.SlotVO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller 单测（Chunk 3 / Task 3.6）—— 仿 {@code AdminEventControllerTest}，
 * 直接调方法取 {@code .getData()}，无 MockMvc。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppCourtControllerTest {

    @Mock private CourtService courtService;
    @Mock private SlotService slotService;
    @InjectMocks private AppCourtController controller;

    @Test
    void list_delegatesByVenue() {
        CourtVO vo = new CourtVO();
        vo.setId(3L);
        when(courtService.listByVenue(7L)).thenReturn(List.of(vo));
        List<CourtVO> data = controller.list(7L).getData();
        assertEquals(1, data.size());
        assertEquals(3L, data.get(0).getId());
    }

    @Test
    void slots_passesDateToServiceAndWrapsResult() {
        LocalDate date = LocalDate.of(2026, 8, 5);
        SlotVO slot = new SlotVO();
        slot.setStart(LocalDateTime.of(date, java.time.LocalTime.of(9, 0)));
        slot.setEnd(LocalDateTime.of(date, java.time.LocalTime.of(10, 0)));
        slot.setAvailable(true);
        slot.setPrice(new BigDecimal("40.00"));
        when(slotService.getCourtSlots(eq(42L), eq(date))).thenReturn(List.of(slot));

        List<SlotVO> data = controller.slots(42L, date).getData();

        // Date passed through transparently.
        verify(slotService).getCourtSlots(42L, date);
        // Result wrapped.
        assertEquals(1, data.size());
        assertEquals(new BigDecimal("40.00"), data.get(0).getPrice());
        assertTrue(data.get(0).isAvailable());
    }

    @Test
    void list_resultCodeIsZero() {
        when(courtService.listByVenue(anyLong())).thenReturn(List.of());
        assertEquals(0, controller.list(1L).getCode());
    }

    @Test
    void slots_emptyDateStillDelegates() {
        when(slotService.getCourtSlots(anyLong(), any())).thenReturn(List.of());
        controller.slots(1L, LocalDate.now().plusDays(1));
        verify(slotService).getCourtSlots(eq(1L), any());
    }
}
