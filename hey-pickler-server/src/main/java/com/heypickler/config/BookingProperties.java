package com.heypickler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component                                          // 不可或缺!P1 PlacementProperties 也用 @Component + project 无 @EnableConfigurationProperties Scan
@ConfigurationProperties(prefix = "hey-pickler.booking")
public class BookingProperties {
    /** 用户取消截止前多少小时;默认 2。 */
    private Integer cancelDeadlineHours = 2;
    /** 用户未来时段 CONFIRMED 预约上限;默认 5。 */
    private Integer maxConcurrent = 5;
    /** 预约 slot_end 后多少小时仍未结→转 COMPLETED;默认 2。 */
    private Integer completeGraceHours = 2;
    /** 调度周期 ISO 8601 duration;默认 PT5M。 */
    private String completeCadence = "PT5M";
    /** 一次扫描最大批大小;默认 200。 */
    private Integer completeBatchSize = 200;
    /** scheduler 首次启动延迟秒;默认 30。 */
    private Integer initialDelaySeconds = 30;
}