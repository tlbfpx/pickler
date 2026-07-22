package com.heypickler.common.util;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.entity.CourtPricingBand;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 定价带有效集非重叠校验(spec §5.5)。纯逻辑,无 mapper 依赖。
 *
 * <p>有效集规则:
 * <ul>
 *   <li>工作日集 = {WEEKDAY} ∪ {ALL}</li>
 *   <li>周末集   = {WEEKEND} ∪ {ALL}</li>
 * </ul>
 * 同一有效集内任意两条 band 半开区间 [start,end) 不得重叠——即 ALL 不得与 WEEKDAY/WEEKEND 重叠。
 *
 * <p>标注 {@code @Component} 以便 {@code CourtServiceImpl} 通过 {@code @RequiredArgsConstructor} 注入。
 */
@Component
public class PricingBandValidator {

    public void validate(List<CourtPricingBand> bands) {
        if (bands == null || bands.isEmpty()) return;
        validateEffectiveSet(bands, "工作日", "WEEKDAY", "ALL");
        validateEffectiveSet(bands, "周末", "WEEKEND", "ALL");
    }

    private void validateEffectiveSet(List<CourtPricingBand> bands, String label, String... dayTypes) {
        List<CourtPricingBand> eff = bands.stream()
                .filter(b -> Arrays.asList(dayTypes).contains(b.getDayType()))
                .sorted(Comparator.comparing(CourtPricingBand::getStartTime))
                .toList();
        for (int i = 0; i < eff.size(); i++) {
            for (int j = i + 1; j < eff.size(); j++) {
                if (overlaps(eff.get(i), eff.get(j))) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            label + "定价带时间段重叠：" + eff.get(i).getStartTime() + "-" + eff.get(i).getEndTime()
                                    + " 与 " + eff.get(j).getStartTime() + "-" + eff.get(j).getEndTime());
                }
            }
        }
    }

    /** 半开区间重叠:a.start &lt; b.end && b.start &lt; a.end */
    private boolean overlaps(CourtPricingBand a, CourtPricingBand b) {
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }
}
