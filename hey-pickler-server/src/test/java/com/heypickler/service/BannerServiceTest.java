package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.entity.Banner;
import com.heypickler.mapper.BannerMapper;
import com.heypickler.service.impl.BannerServiceImpl;
import com.heypickler.vo.BannerVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock
    private BannerMapper bannerMapper;

    @Mock
    private ImageUrlValidator imageUrlValidator;

    @InjectMocks
    private BannerServiceImpl bannerService;

    private Banner banner1;
    private Banner banner2;
    private Banner bannerDisabled;

    @BeforeEach
    void setUp() {
        banner1 = new Banner();
        banner1.setId(1L);
        banner1.setImageUrl("https://example.com/banner1.jpg");
        banner1.setLinkUrl("https://example.com/link1");
        banner1.setSortOrder(1);
        banner1.setStatus("ACTIVE");

        banner2 = new Banner();
        banner2.setId(2L);
        banner2.setImageUrl("https://example.com/banner2.jpg");
        banner2.setLinkUrl("https://example.com/link2");
        banner2.setSortOrder(2);
        banner2.setStatus("ACTIVE");

        bannerDisabled = new Banner();
        bannerDisabled.setId(3L);
        bannerDisabled.setImageUrl("https://example.com/banner3.jpg");
        bannerDisabled.setLinkUrl("https://example.com/link3");
        bannerDisabled.setSortOrder(3);
        bannerDisabled.setStatus("DISABLED");
    }

    @Test
    void listEnabledBanners_ShouldReturnOnlyEnabledBanners() {
        // Given
        List<Banner> enabledBanners = Arrays.asList(banner1, banner2);
        when(bannerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(enabledBanners);

        // When
        List<BannerVO> result = bannerService.listEnabledBanners();

        // Then
        assertEquals(2, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        assertEquals("ACTIVE", result.get(1).getStatus());
        verify(bannerMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void adminListAll_ShouldReturnAllBannersOrderedBySortOrder() {
        // Given
        List<Banner> allBanners = Arrays.asList(banner1, banner2, bannerDisabled);
        when(bannerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(allBanners);

        // When
        List<BannerVO> result = bannerService.adminListAll();

        // Then
        assertEquals(3, result.size());
        verify(bannerMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void createBanner_WithAllFields_ShouldInsertBanner() {
        // Given
        BannerCreateRequest request = new BannerCreateRequest();
        request.setImageUrl("https://example.com/new.jpg");
        request.setLinkUrl("https://example.com/newlink");
        request.setSortOrder(10);
        request.setStatus("ACTIVE");

        doNothing().when(imageUrlValidator).validate("https://example.com/new.jpg");
        when(bannerMapper.insert(any(Banner.class))).thenAnswer(invocation -> {
            Banner banner = invocation.getArgument(0);
            banner.setId(100L);
            return 1;
        });

        // When
        Long id = bannerService.createBanner(request);

        // Then
        assertEquals(100L, id);
        verify(bannerMapper).insert(argThat(banner ->
            banner.getImageUrl().equals("https://example.com/new.jpg") &&
            banner.getLinkUrl().equals("https://example.com/newlink") &&
            banner.getSortOrder() == 10 &&
            banner.getStatus().equals("ACTIVE")
        ));
    }

    @Test
    void createBanner_WithDefaults_ShouldSetDefaultValues() {
        // Given
        BannerCreateRequest request = new BannerCreateRequest();
        request.setImageUrl("https://example.com/new.jpg");

        doNothing().when(imageUrlValidator).validate("https://example.com/new.jpg");
        when(bannerMapper.insert(any(Banner.class))).thenAnswer(invocation -> {
            Banner banner = invocation.getArgument(0);
            banner.setId(100L);
            return 1;
        });

        // When
        Long id = bannerService.createBanner(request);

        // Then
        assertEquals(100L, id);
        verify(bannerMapper).insert(argThat(banner ->
            banner.getImageUrl().equals("https://example.com/new.jpg") &&
            banner.getSortOrder() == 0 &&
            banner.getStatus().equals("ACTIVE")
        ));
    }

    @Test
    void createBanner_WhenImageUrlUnreachable_ShouldThrowAndNotInsert() {
        // Given
        BannerCreateRequest request = new BannerCreateRequest();
        request.setImageUrl("https://test.com/banner.jpg");
        request.setSortOrder(5);
        request.setStatus("ACTIVE");

        doThrow(new BizException(ErrorCode.PARAM_ERROR, "图片地址校验失败"))
            .when(imageUrlValidator).validate("https://test.com/banner.jpg");

        // When & Then
        BizException ex = assertThrows(BizException.class,
            () -> bannerService.createBanner(request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(bannerMapper, never()).insert(any(Banner.class));
    }

    @Test
    void updateBanner_WithAllFields_ShouldUpdateBanner() {
        // Given
        Long bannerId = 1L;
        BannerCreateRequest request = new BannerCreateRequest();
        request.setImageUrl("https://example.com/updated.jpg");
        request.setLinkUrl("https://example.com/updatedlink");
        request.setSortOrder(20);
        request.setStatus("DISABLED");

        doNothing().when(imageUrlValidator).validate("https://example.com/updated.jpg");
        when(bannerMapper.updateById(any(Banner.class))).thenReturn(1);

        // When
        bannerService.updateBanner(bannerId, request);

        // Then
        verify(bannerMapper).updateById(argThat(banner ->
            banner.getId().equals(bannerId) &&
            banner.getImageUrl().equals("https://example.com/updated.jpg") &&
            banner.getLinkUrl().equals("https://example.com/updatedlink") &&
            banner.getSortOrder() == 20 &&
            banner.getStatus().equals("DISABLED")
        ));
    }

    @Test
    void updateBanner_WithPartialFields_ShouldUpdateOnlyProvidedFields() {
        // Given
        Long bannerId = 1L;
        BannerCreateRequest request = new BannerCreateRequest();
        request.setImageUrl("https://example.com/updated.jpg");
        request.setSortOrder(30);

        doNothing().when(imageUrlValidator).validate("https://example.com/updated.jpg");
        when(bannerMapper.updateById(any(Banner.class))).thenReturn(1);

        // When
        bannerService.updateBanner(bannerId, request);

        // Then
        verify(bannerMapper).updateById(argThat(banner ->
            banner.getId().equals(bannerId) &&
            banner.getImageUrl().equals("https://example.com/updated.jpg") &&
            banner.getSortOrder() == 30
        ));
    }

    @Test
    void updateBanner_WhenImageUrlUnreachable_ShouldThrowAndNotUpdate() {
        // Given
        Long bannerId = 1L;
        BannerCreateRequest request = new BannerCreateRequest();
        request.setImageUrl("https://test.com/banner.jpg");
        request.setSortOrder(50);
        request.setStatus("ACTIVE");

        doThrow(new BizException(ErrorCode.PARAM_ERROR, "图片地址校验失败"))
            .when(imageUrlValidator).validate("https://test.com/banner.jpg");

        // When & Then
        BizException ex = assertThrows(BizException.class,
            () -> bannerService.updateBanner(bannerId, request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(bannerMapper, never()).updateById(any(Banner.class));
    }

    @Test
    void deleteBanner_WithExistingId_ShouldDeleteBanner() {
        // Given
        Long bannerId = 1L;
        when(bannerMapper.selectById(bannerId)).thenReturn(banner1);
        when(bannerMapper.deleteById(bannerId)).thenReturn(1);

        // When
        bannerService.deleteBanner(bannerId);

        // Then
        verify(bannerMapper).selectById(bannerId);
        verify(bannerMapper).deleteById(bannerId);
    }

    @Test
    void deleteBanner_WithNonExistingId_ShouldThrowException() {
        // Given
        Long bannerId = 999L;
        when(bannerMapper.selectById(bannerId)).thenReturn(null);

        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            bannerService.deleteBanner(bannerId);
        });

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(bannerMapper).selectById(bannerId);
        verify(bannerMapper, never()).deleteById(bannerId);
    }
}
