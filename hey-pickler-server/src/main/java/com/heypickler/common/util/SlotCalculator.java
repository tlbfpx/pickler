package com.heypickler.common.util;

import com.heypickler.entity.CourtPricingBand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 时段生成/定价/可用性纯算法(spec §6.1)。不查库,全部入参注入,便于表驱动测试。
 *
 * <p>算法:
 * <ul>
 *   <li>锚定 openTime,半开 [t, t+slot);末尾不足整格的丢弃。</li>
 *   <li>下界:slotStart &lt; now+30min 跳过;上界:slotStart &gt;= now+leadDays·24h 停。</li>
 *   <li>价格查命中 band(半开区间,specific 优先 ALL)。</li>
 *   <li>available = 有 band 命中 && 未被 occupied 占用。</li>
 *   <li>跨午夜守卫:endT 回绕(LocalTime 无日期,&lt;=t)立刻停——v1 不支持跨夜营业。</li>
 * </ul>
 *
 * <p>标注 {@code @Component} 以便 {@code SlotServiceImpl} 通过 {@code @RequiredArgsConstructor} 注入。
 */
@Component
public class SlotCalculator {

    public record SlotRange(LocalDateTime start, LocalDateTime end, boolean available, BigDecimal price) {}

    private static final Duration MIN_LEAD = Duration.ofMinutes(30);

    public List<SlotRange> generate(LocalTime openTime, LocalTime closeTime, int slotMinutes,
                                    List<CourtPricingBand> effBands, Set<LocalDateTime> occupied,
                                    LocalDate date, LocalDateTime now, int bookingLeadDays) {
        List<SlotRange> out = new ArrayList<>();
        if (openTime == null || closeTime == null || slotMinutes <= 0) return out;

        LocalDateTime earliestStart = now.plus(MIN_LEAD);
        LocalDateTime windowEnd = now.plusDays(bookingLeadDays);

        LocalTime t = openTime;
        while (true) {
            LocalTime endT = t.plusMinutes(slotMinutes);
            if (!endT.isAfter(t)) break;          // 跨午夜回绕(LocalTime 无日期)→ 停;v1 不支持跨夜营业
            if (endT.isAfter(closeTime)) break;   // 半开,末尾不足丢弃

            LocalDateTime slotStart = LocalDateTime.of(date, t);
            if (!slotStart.isBefore(earliestStart)) {         // >= now+30min
                if (!slotStart.isBefore(windowEnd)) break;    // 达到/超过 now+leadDays 停
                CourtPricingBand band = matchBand(effBands, t);
                boolean available = band != null && !occupied.contains(slotStart);
                out.add(new SlotRange(slotStart, slotStart.plusMinutes(slotMinutes),
                        available, band != null ? band.getPrice() : null));
            }
            t = endT;
        }
        return out;
    }

    /**
     * 返回 t 落入半开 [start,end) 的唯一 band;有效集保存时已去重,至多 1 个命中。
     * 防御:若多命中,specific(WEEKDAY/WEEKEND)优先 ALL。
     */
    private CourtPricingBand matchBand(List<CourtPricingBand> effBands, LocalTime t) {
        CourtPricingBand hit = null;
        for (CourtPricingBand b : effBands) {
            if (!t.isBefore(b.getStartTime()) && t.isBefore(b.getEndTime())) {
                if (hit == null) {
                    hit = b;
                } else if (!"ALL".equals(b.getDayType()) && "ALL".equals(hit.getDayType())) {
                    hit = b; // specific 优先 ALL
                }
            }
        }
        return hit;
    }
}
