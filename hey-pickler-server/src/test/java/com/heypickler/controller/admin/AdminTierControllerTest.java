package com.heypickler.controller.admin;

import com.heypickler.common.exception.BizException;
import com.heypickler.dto.admin.TierItemUpdateRequest;
import com.heypickler.service.TierConfigService;
import com.heypickler.vo.TierConfigVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AdminTierController 委派 + track 校验测试。
 * <p>
 * STAR/PARTY 通过委派；非法 track（如 OTHER）抛 PARAM_ERROR 且不触达 service。
 */
@ExtendWith(MockitoExtension.class)
class AdminTierControllerTest {

    @InjectMocks
    AdminTierController controller;
    @Mock
    TierConfigService tierConfigService;

    @Test
    void get_starTrack_delegates() {
        when(tierConfigService.getByTrack("STAR")).thenReturn(List.of());
        assertEquals(0, controller.get("STAR").getData().size());
        verify(tierConfigService).getByTrack("STAR");
    }

    @Test
    void get_partyTrack_delegates() {
        when(tierConfigService.getByTrack("PARTY")).thenReturn(List.of());
        assertEquals(0, controller.get("PARTY").getData().size());
        verify(tierConfigService).getByTrack("PARTY");
    }

    @Test
    void get_invalidTrack_throwsParamError() {
        assertThrows(BizException.class, () -> controller.get("OTHER"));
        verifyNoInteractions(tierConfigService);
    }

    @Test
    void get_invalidTrack_lowerCase_throws() {
        // track 严格大小写匹配，前端必须传大写
        assertThrows(BizException.class, () -> controller.get("star"));
        verifyNoInteractions(tierConfigService);
    }

    @Test
    void update_starTrack_delegates() {
        controller.update("STAR", List.of());
        verify(tierConfigService).updateTrack(eq("STAR"), anyList());
    }

    @Test
    void update_partyTrack_delegates() {
        controller.update("PARTY", List.of());
        verify(tierConfigService).updateTrack(eq("PARTY"), anyList());
    }

    @Test
    void update_invalidTrack_throwsParamError() {
        assertThrows(BizException.class, () -> controller.update("OTHER", List.of()));
        verifyNoInteractions(tierConfigService);
    }

    @Test
    void get_returnsMappedVO() {
        TierConfigVO vo = new TierConfigVO();
        vo.setTrack("STAR"); vo.setTierCode("BRONZE"); vo.setTierName("青铜");
        when(tierConfigService.getByTrack("STAR")).thenReturn(List.of(vo));
        List<TierConfigVO> data = controller.get("STAR").getData();
        assertEquals(1, data.size());
        assertEquals("青铜", data.get(0).getTierName());
    }
}
