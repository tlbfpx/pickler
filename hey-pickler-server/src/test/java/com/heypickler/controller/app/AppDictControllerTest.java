package com.heypickler.controller.app;

import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppDictControllerTest {

    @InjectMocks AppDictController controller;
    @Mock DictService dictService;

    @Test
    void bundle_delegatesAndReturnsData() {
        DictBundleVO vo = new DictBundleVO();
        when(dictService.getBundle()).thenReturn(vo);
        assertSame(vo, controller.bundle().getData());
        assertEquals(0, controller.bundle().getCode());
    }
}
