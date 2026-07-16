package com.heypickler.service;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.BrandUpdateRequest;
import com.heypickler.entity.BrandConfig;
import com.heypickler.mapper.BrandConfigMapper;
import com.heypickler.service.impl.BrandConfigServiceImpl;
import com.heypickler.vo.BrandVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BrandConfigServiceImpl 单测：get 读 seed、update 强校验（appName/hex/logo）+ patch。
 * mapper / imageUrlValidator 均 mock，无 Spring 容器；selectById 不走 LambdaQueryWrapper，无需预热 TableInfo。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandConfigServiceImplTest {

    @Mock
    private BrandConfigMapper brandConfigMapper;
    @Mock
    private ImageUrlValidator imageUrlValidator;

    @InjectMocks
    private BrandConfigServiceImpl brandConfigService;

    private BrandConfig seed() {
        BrandConfig b = new BrandConfig();
        b.setId(1L);
        b.setAppName("Hey Pickler");
        b.setSlogan("匹克球赛事活动管理平台");
        b.setLogoUrl(null);
        b.setPrimaryColor("#4CAF50");
        return b;
    }

    private BrandUpdateRequest req(String name, String slogan, String logo, String color) {
        BrandUpdateRequest r = new BrandUpdateRequest();
        r.setAppName(name);
        r.setSlogan(slogan);
        r.setLogoUrl(logo);
        r.setPrimaryColor(color);
        return r;
    }

    @Test
    void get_returnsSeedBrand() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        BrandVO vo = brandConfigService.get();
        assertEquals("Hey Pickler", vo.getAppName());
        assertEquals("#4CAF50", vo.getPrimaryColor());
        assertEquals("匹克球赛事活动管理平台", vo.getSlogan());
        assertNull(vo.getLogoUrl());
    }

    @Test
    void get_notInitialized_throwsNotFound() {
        when(brandConfigMapper.selectById(1L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> brandConfigService.get());
        assertTrue(ex.getMessage().contains("品牌配置未初始化"));
    }

    @Test
    void update_valid_persistsAndValidatesLogo() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());

        BrandVO vo = brandConfigService.update(req("新品牌", "新口号", "https://cdn.example.com/logo.png", "#123456"));

        verify(imageUrlValidator).validate("https://cdn.example.com/logo.png");
        verify(brandConfigMapper).updateById(argThat(b ->
                b.getId() != null && b.getId() == 1L
                        && "新品牌".equals(b.getAppName())
                        && "#123456".equals(b.getPrimaryColor())
                        && "https://cdn.example.com/logo.png".equals(b.getLogoUrl())));
        assertNotNull(vo);
    }

    @Test
    void update_blankAppName_throws() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        assertThrows(BizException.class, () -> brandConfigService.update(req("   ", null, null, "#4CAF50")));
        verify(brandConfigMapper, never()).updateById(any());
    }

    @Test
    void update_appNameTooLong_throws() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        assertThrows(BizException.class, () -> brandConfigService.update(req("x".repeat(65), null, null, "#4CAF50")));
        verify(brandConfigMapper, never()).updateById(any());
    }

    @Test
    void update_badHex_throws() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        assertThrows(BizException.class, () -> brandConfigService.update(req("X", null, null, "#xyz123")));
        verify(brandConfigMapper, never()).updateById(any());
    }

    @Test
    void update_invalidLogoUrl_propagates() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        doThrow(new BizException(ErrorCode.PARAM_ERROR, "图片不可达"))
                .when(imageUrlValidator).validate(anyString());
        assertThrows(BizException.class,
                () -> brandConfigService.update(req("X", null, "https://bad/x.png", "#4CAF50")));
        verify(brandConfigMapper, never()).updateById(any());
    }

    @Test
    void update_emptyLogoUrl_skipsValidation() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        brandConfigService.update(req("X", "", "", "#4CAF50"));
        verify(imageUrlValidator, never()).validate(anyString());
        verify(brandConfigMapper).updateById(argThat(b -> "".equals(b.getLogoUrl()) && "".equals(b.getSlogan())));
    }

    @Test
    void update_blankPrimary_fallsBackToDefault() {
        when(brandConfigMapper.selectById(1L)).thenReturn(seed());
        brandConfigService.update(req("X", null, null, "   "));
        verify(brandConfigMapper).updateById(argThat(b -> "#4CAF50".equals(b.getPrimaryColor())));
    }
}
