package com.heypickler.service;

import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.vo.BannerVO;

import java.util.List;

public interface BannerService {
    List<BannerVO> listEnabledBanners();
    List<BannerVO> adminListAll();
    Long createBanner(BannerCreateRequest request);
    void updateBanner(Long id, BannerCreateRequest request);
    void deleteBanner(Long id);
}
