package com.heypickler.controller.admin;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.VenueBusinessHourRequest;
import com.heypickler.dto.admin.VenueContactRequest;
import com.heypickler.dto.admin.VenueCreateRequest;
import com.heypickler.dto.admin.VenueQueryRequest;
import com.heypickler.service.VenueService;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Controller 单测（Chunk 3 / Task 3.6）—— 仿 {@code AdminEventControllerTest}，
 * 直接调方法取 {@code .getData()}，无 MockMvc。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminVenueControllerTest {

    @Mock private VenueService venueService;
    @InjectMocks private AdminVenueController controller;

    @Test
    void list_delegatesAndWrapsPage() {
        VenueVO vo = new VenueVO();
        vo.setId(11L);
        PageResult<VenueVO> stub = PageResult.of(1L, 1, 10, List.of(vo));
        doReturn(stub).when(venueService).adminList(any());

        PageResult<VenueVO> data = controller.list(new VenueQueryRequest()).getData();
        assertEquals(1L, data.getTotal());
        assertEquals(11L, data.getList().get(0).getId());
    }

    @Test
    void get_delegates() {
        VenueDetailVO vo = new VenueDetailVO();
        vo.setId(7L);
        when(venueService.adminGet(7L)).thenReturn(vo);
        assertEquals(7L, controller.get(7L).getData().getId());
    }

    @Test
    void create_delegatesAndWrapsId() {
        when(venueService.create(any())).thenReturn(42L);
        Long id = ((Number) controller.create(new VenueCreateRequest()).getData().get("id")).longValue();
        assertEquals(42L, id);
    }

    @Test
    void update_delegates() {
        doNothing().when(venueService).update(anyLong(), any());
        controller.update(5L, new VenueCreateRequest());
        verify(venueService).update(eq(5L), any());
    }

    @Test
    void delete_delegates() {
        doNothing().when(venueService).delete(anyLong());
        controller.delete(9L);
        verify(venueService).delete(9L);
    }

    @Test
    void replaceBusinessHours_delegates() {
        VenueBusinessHourRequest req = new VenueBusinessHourRequest();
        VenueBusinessHourRequest.Item it = new VenueBusinessHourRequest.Item();
        it.setDayOfWeek(1);
        it.setOpenTime(LocalTime.of(9, 0));
        it.setCloseTime(LocalTime.of(22, 0));
        req.setHours(List.of(it));
        doNothing().when(venueService).replaceBusinessHours(anyLong(), any());
        controller.replaceBusinessHours(3L, req);
        verify(venueService).replaceBusinessHours(eq(3L), eq(req));
    }

    @Test
    void addContact_delegatesAndWrapsId() {
        VenueContactRequest req = new VenueContactRequest();
        req.setType("PHONE");
        req.setValue("12345678");
        when(venueService.addContact(anyLong(), any())).thenReturn(77L);
        Long id = ((Number) controller.addContact(3L, req).getData().get("id")).longValue();
        assertEquals(77L, id);
    }

    @Test
    void updateContact_delegatesByContactId() {
        VenueContactRequest req = new VenueContactRequest();
        req.setType("PHONE");
        req.setValue("x");
        doNothing().when(venueService).updateContact(anyLong(), any());
        controller.updateContact(88L, req);
        verify(venueService).updateContact(eq(88L), eq(req));
    }

    @Test
    void deleteContact_delegatesByContactId() {
        doNothing().when(venueService).deleteContact(anyLong());
        controller.deleteContact(88L);
        verify(venueService).deleteContact(88L);
    }

    @Test
    void createResult_codeIsZero() {
        when(venueService.create(any())).thenReturn(1L);
        Map<String, Object> data = controller.create(new VenueCreateRequest()).getData();
        assertEquals(1L, ((Number) data.get("id")).longValue());
    }
}
