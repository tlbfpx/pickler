package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDictControllerTest {

    @InjectMocks AdminDictController controller;
    @Mock DictService dictService;

    @Test
    void listDicts_delegatesAndWraps() {
        when(dictService.listDicts()).thenReturn(List.of());
        assertEquals(0, controller.listDicts().getData().size());
    }

    @Test
    void listItems_delegatesByCode() {
        when(dictService.listItems("event_type")).thenReturn(List.of());
        controller.listItems("event_type");
        verify(dictService).listItems("event_type");
    }

    @Test
    void updateItems_delegates() {
        controller.updateItems("event_type", List.of());
        verify(dictService).updateItems(eq("event_type"), anyList());
    }

    @Test
    void bundle_delegates() {
        DictBundleVO vo = new DictBundleVO();
        when(dictService.getBundle()).thenReturn(vo);
        assertSame(vo, controller.bundle().getData());
    }

    @Test
    void version_returnsVersionMap() {
        when(dictService.getVersion()).thenReturn(5L);
        Result<Map<String, Long>> r = controller.version();
        assertEquals(0, r.getCode());
        assertEquals(5L, r.getData().get("version"));
    }
}
