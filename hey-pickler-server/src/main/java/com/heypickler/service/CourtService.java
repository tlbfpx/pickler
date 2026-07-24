package com.heypickler.service;

import com.heypickler.dto.admin.CourtCreateRequest;
import com.heypickler.dto.admin.CourtPricingBandBatchRequest;
import com.heypickler.vo.CourtPricingBandVO;
import com.heypickler.vo.CourtVO;

import java.util.List;

public interface CourtService {

    List<CourtVO> listByVenue(Long venueId);

    CourtVO get(Long id);

    Long create(CourtCreateRequest req);

    void update(Long id, CourtCreateRequest req);

    void delete(Long id);

    /** 读取场地的定价带列表（按 startTime 升序）。 */
    List<CourtPricingBandVO> listPricingBands(Long courtId);

    /** 校验 + 整批覆盖定价带。 */
    void replacePricingBands(Long courtId, CourtPricingBandBatchRequest req);

    /** 从指定场地复制价目。 */
    void copyPricingBands(Long targetCourtId, Long fromCourtId);
}
