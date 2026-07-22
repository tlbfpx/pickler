package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.dto.admin.CourtCreateRequest;
import com.heypickler.dto.admin.CourtPricingBandBatchRequest;
import com.heypickler.dto.admin.CourtPricingBandRequest;
import com.heypickler.entity.Court;
import com.heypickler.entity.CourtPricingBand;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.CourtPricingBandMapper;
import com.heypickler.service.CourtService;
import com.heypickler.vo.CourtVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

    private final CourtMapper courtMapper;
    private final CourtPricingBandMapper bandMapper;
    private final PricingBandValidator validator;

    @Override
    public List<CourtVO> listByVenue(Long venueId) {
        return courtMapper.selectList(new LambdaQueryWrapper<Court>()
                        .eq(Court::getVenueId, venueId)
                        .orderByAsc(Court::getSortOrder))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public CourtVO get(Long id) {
        return toVO(mustExist(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CourtCreateRequest req) {
        Court c = new Court();
        c.setVenueId(req.getVenueId()); // venueId 仅 create 设置;update 忽略(不跨场馆搬移,见 CourtCreateRequest)
        apply(req, c);
        if (c.getVenueId() == null) throw new BizException(ErrorCode.PARAM_ERROR, "venueId 不能为空");
        if (c.getSlotMinutes() == null) c.setSlotMinutes(60);
        if (c.getStatus() == null) c.setStatus("OPEN");
        if (c.getCourtType() == null) c.setCourtType("INDOOR");
        courtMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CourtCreateRequest req) {
        Court c = mustExist(id);
        apply(req, c);
        courtMapper.updateById(c);
    }

    @Override
    public void delete(Long id) {
        mustExist(id);
        courtMapper.deleteById(id); // 软删; name_key 释放
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replacePricingBands(Long courtId, CourtPricingBandBatchRequest req) {
        mustExist(courtId);
        List<CourtPricingBand> bands = req.getBands().stream().map(this::toEntity).collect(Collectors.toList());
        validator.validate(bands); // 先校验,通过后再清旧写新
        bandMapper.delete(new LambdaQueryWrapper<CourtPricingBand>().eq(CourtPricingBand::getCourtId, courtId));
        for (CourtPricingBand b : bands) {
            b.setCourtId(courtId);
            bandMapper.insert(b);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyPricingBands(Long targetCourtId, Long fromCourtId) {
        mustExist(targetCourtId);
        List<CourtPricingBand> src = bandMapper.selectList(
                new LambdaQueryWrapper<CourtPricingBand>().eq(CourtPricingBand::getCourtId, fromCourtId));
        validator.validate(src);
        bandMapper.delete(new LambdaQueryWrapper<CourtPricingBand>().eq(CourtPricingBand::getCourtId, targetCourtId));
        for (CourtPricingBand b : src) {
            CourtPricingBand nb = new CourtPricingBand();
            nb.setDayType(b.getDayType());
            nb.setStartTime(b.getStartTime());
            nb.setEndTime(b.getEndTime());
            nb.setPrice(b.getPrice());
            nb.setCourtId(targetCourtId);
            bandMapper.insert(nb);
        }
    }

    private Court mustExist(Long id) {
        Court c = courtMapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.COURT_NOT_FOUND);
        return c;
    }

    private void apply(CourtCreateRequest req, Court c) {
        // venueId 不在 apply 内设置:create 显式赋值,update 保持原值
        c.setName(req.getName());
        if (req.getCourtType() != null) c.setCourtType(req.getCourtType());
        if (req.getSlotMinutes() != null) c.setSlotMinutes(req.getSlotMinutes());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        if (req.getSortOrder() != null) c.setSortOrder(req.getSortOrder());
    }

    private CourtPricingBand toEntity(CourtPricingBandRequest r) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType(r.getDayType());
        b.setStartTime(r.getStartTime());
        b.setEndTime(r.getEndTime());
        b.setPrice(r.getPrice());
        return b;
    }

    private CourtVO toVO(Court c) {
        CourtVO vo = new CourtVO();
        vo.setId(c.getId());
        vo.setVenueId(c.getVenueId());
        vo.setName(c.getName());
        vo.setCourtType(c.getCourtType());
        vo.setSlotMinutes(c.getSlotMinutes());
        vo.setStatus(c.getStatus());
        vo.setSortOrder(c.getSortOrder());
        return vo;
    }
}
