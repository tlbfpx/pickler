package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.entity.Banner;
import com.heypickler.mapper.BannerMapper;
import com.heypickler.service.BannerService;
import com.heypickler.service.ImageUrlValidator;
import com.heypickler.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;
    private final ImageUrlValidator imageUrlValidator;

    @Override
    public List<BannerVO> listEnabledBanners() {
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Banner::getStatus, "ACTIVE");
        wrapper.orderByAsc(Banner::getSortOrder);
        List<Banner> banners = bannerMapper.selectList(wrapper);
        return banners.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<BannerVO> adminListAll() {
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Banner::getSortOrder);
        List<Banner> banners = bannerMapper.selectList(wrapper);
        return banners.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Long createBanner(BannerCreateRequest request) {
        imageUrlValidator.validate(request.getImageUrl());
        Banner banner = new Banner();
        banner.setImageUrl(request.getImageUrl());
        banner.setLinkUrl(request.getLinkUrl());
        banner.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        banner.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        bannerMapper.insert(banner);
        return banner.getId();
    }

    @Override
    public void updateBanner(Long id, BannerCreateRequest request) {
        imageUrlValidator.validate(request.getImageUrl());
        Banner banner = new Banner();
        banner.setId(id);
        banner.setImageUrl(request.getImageUrl());
        banner.setLinkUrl(request.getLinkUrl());
        banner.setSortOrder(request.getSortOrder());
        banner.setStatus(request.getStatus());
        bannerMapper.updateById(banner);
    }

    @Override
    public void deleteBanner(Long id) {
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        bannerMapper.deleteById(id);
    }

    private BannerVO toVO(Banner banner) {
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setImageUrl(banner.getImageUrl());
        vo.setLinkUrl(banner.getLinkUrl());
        vo.setSortOrder(banner.getSortOrder());
        vo.setStatus(banner.getStatus());
        return vo;
    }
}
