package com.heypickler.dto.app;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 报名请求。
 *
 * 单打：仅传 matchType（由服务端强制等于 event.format）。
 * 双打/混打：
 *   - 队长发起建队 → 传 partnerUserId（matchType 可省略，服务端强制为 event.format）
 *   - 队友确认入队 → 传 teamId（matchType 可省略）
 *
 * matchType/partnerId 字段保留以兼容历史数据与旧前端，新报名以 event.format 为准。
 */
@Data
public class RegisterRequest {

    @Pattern(regexp = "^(SINGLES|DOUBLES|MIXED)$", message = "无效的比赛类型")
    private String matchType;

    /** 旧字段：双打搭档 id，保留兼容。新报名请用 partnerUserId。 */
    private Long partnerId;

    /** 双打/混打：队长指定的队友 userId（发起建队）。 */
    private Long partnerUserId;

    /** 双打/混打：队友确认入队时携带的 teamId。 */
    private Long teamId;
}
