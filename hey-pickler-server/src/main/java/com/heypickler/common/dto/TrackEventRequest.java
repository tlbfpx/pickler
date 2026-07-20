package com.heypickler.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Loop-v19 Dashboard Phase 2 R3 — 小程序自定义事件上报请求体。
 *
 * <p>{@code POST /api/app/track/event} 接收。name 必填（≤64 字符），
 * props 可选（≤2 KB）。did 由小程序 wx.getStorageSync('did') 生成，
 * 服务端用它关联 access_log（userId 已由 AppAuthFilter 自动绑定）。
 */
@Data
public class TrackEventRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 事件属性，size 限制在 controller 层校验（@Size 不能直接作用 Map）。 */
    private Map<String, Object> props;

    /** 客户端时间戳（ms），仅供排查用，不参与服务端逻辑。 */
    private Long ts;

    /** 小程序持久化 device id（wx.getStorageSync('did')）。 */
    private String did;
}