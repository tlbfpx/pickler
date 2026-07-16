package com.heypickler.service;

import com.heypickler.dto.admin.BrandUpdateRequest;
import com.heypickler.vo.BrandVO;

/**
 * 品牌配置服务（读 brand_config 单行 id=1；管理端更新）。
 * <p>
 * 读：返回当前品牌配置（app / admin 共用）。
 * 写：校验 appName 非空、primaryColor #RRGGBB、logoUrl 外链可达后 patch id=1。
 */
public interface BrandConfigService {

    /** 读取品牌配置（单行 id=1）。 */
    BrandVO get();

    /**
     * 更新品牌配置（强校验后 patch id=1，返回最新值）。
     * <ul>
     *   <li>appName 非空（≤64）</li>
     *   <li>primaryColor 缺省回退 #4CAF50，非空须匹配 ^#[0-9A-Fa-f]{6}$</li>
     *   <li>logoUrl 非空时走 ImageUrlValidator（HEAD + image/* content-type）</li>
     * </ul>
     */
    BrandVO update(BrandUpdateRequest req);
}
