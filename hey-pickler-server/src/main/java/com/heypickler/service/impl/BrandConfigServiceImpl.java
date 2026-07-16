package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.BrandUpdateRequest;
import com.heypickler.entity.BrandConfig;
import com.heypickler.mapper.BrandConfigMapper;
import com.heypickler.service.BrandConfigService;
import com.heypickler.service.ImageUrlValidator;
import com.heypickler.vo.BrandVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 品牌配置服务实现（brand_config 单行 id=1）。
 * <p>
 * 读：selectById(1)，seed 保证存在。
 * 写：强校验 appName 非空、primaryColor #RRGGBB、logoUrl 外链可达（ImageUrlValidator），
 * updateById patch（MyBatis-Plus NOT_NULL 策略：传 "" 可清空 slogan/logo，传 null 跳过）。
 * <p>
 * 不耦合 dict version：品牌低频，wxapp 每次 onLaunch 拉、admin 编辑后 refresh 即可。
 */
@Service
@RequiredArgsConstructor
public class BrandConfigServiceImpl implements BrandConfigService {

    private static final Long SINGLETON_ID = 1L;
    private static final String DEFAULT_PRIMARY_COLOR = "#4CAF50";
    private static final int APP_NAME_MAX = 64;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final BrandConfigMapper brandConfigMapper;
    private final ImageUrlValidator imageUrlValidator;

    @Override
    public BrandVO get() {
        return toVO(requireBrand());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BrandVO update(BrandUpdateRequest req) {
        requireBrand(); // 确认单行存在

        // 1. appName 非空 + 长度
        String appName = req.getAppName();
        if (appName == null || appName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "app 名称不能为空");
        }
        appName = appName.trim();
        if (appName.length() > APP_NAME_MAX) {
            throw new BizException(ErrorCode.PARAM_ERROR, "app 名称过长（≤" + APP_NAME_MAX + "）");
        }

        // 2. primaryColor：缺省回退默认；非空须 #RRGGBB
        String primary = (req.getPrimaryColor() == null || req.getPrimaryColor().isBlank())
                ? DEFAULT_PRIMARY_COLOR : req.getPrimaryColor().trim();
        if (!HEX_COLOR.matcher(primary).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "主题色格式非法（需 #RRGGBB）: " + req.getPrimaryColor());
        }

        // 3. logoUrl 非空时校验为可访问图片外链（同 BannerServiceImpl）
        if (req.getLogoUrl() != null && !req.getLogoUrl().isBlank()) {
            imageUrlValidator.validate(req.getLogoUrl().trim());
        }

        // 4. patch（slogan / logoUrl 传 "" 可清空；appName / primaryColor 始终写）
        BrandConfig patch = new BrandConfig();
        patch.setId(SINGLETON_ID);
        patch.setAppName(appName);
        patch.setSlogan(req.getSlogan());
        patch.setLogoUrl(req.getLogoUrl());
        patch.setPrimaryColor(primary);
        brandConfigMapper.updateById(patch);

        return get();
    }

    private BrandConfig requireBrand() {
        BrandConfig brand = brandConfigMapper.selectById(SINGLETON_ID);
        if (brand == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "品牌配置未初始化");
        }
        return brand;
    }

    private BrandVO toVO(BrandConfig b) {
        BrandVO vo = new BrandVO();
        vo.setAppName(b.getAppName());
        vo.setSlogan(b.getSlogan());
        vo.setLogoUrl(b.getLogoUrl());
        vo.setPrimaryColor(b.getPrimaryColor());
        return vo;
    }
}
