package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.VenueBusinessHourRequest;
import com.heypickler.dto.admin.VenueContactRequest;
import com.heypickler.dto.admin.VenueCreateRequest;
import com.heypickler.dto.admin.VenueQueryRequest;
import com.heypickler.entity.Court;
import com.heypickler.entity.Venue;
import com.heypickler.entity.VenueBusinessHour;
import com.heypickler.entity.VenueContact;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.VenueBusinessHourMapper;
import com.heypickler.mapper.VenueContactMapper;
import com.heypickler.mapper.VenueMapper;
import com.heypickler.service.VenueService;
import com.heypickler.vo.CourtVO;
import com.heypickler.vo.VenueContactVO;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueMapper venueMapper;
    private final VenueBusinessHourMapper businessHourMapper;
    private final VenueContactMapper contactMapper;
    private final CourtMapper courtMapper;

    @Override
    public PageResult<VenueVO> adminList(VenueQueryRequest req) {
        LambdaQueryWrapper<Venue> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getKeyword())) {
            w.and(x -> x.like(Venue::getName, req.getKeyword())
                    .or().like(Venue::getAddress, req.getKeyword()));
        }
        if (StringUtils.hasText(req.getStatus())) {
            w.eq(Venue::getStatus, req.getStatus());
        }
        w.orderByDesc(Venue::getCreatedAt);
        Page<Venue> p = venueMapper.selectPage(new Page<>(req.getPage(), req.getSize()), w);
        List<VenueVO> vos = p.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), req.getPage(), req.getSize(), vos);
    }

    @Override
    public VenueDetailVO adminGet(Long id) {
        Venue v = mustExist(id);
        return toDetailVO(v);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(VenueCreateRequest req) {
        Venue v = new Venue();
        applyCreate(v, req);
        if (v.getStatus() == null) v.setStatus("ACTIVE");
        if (v.getBookingLeadDays() == null) v.setBookingLeadDays(14);
        venueMapper.insert(v);
        return v.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VenueCreateRequest req) {
        Venue v = mustExist(id);
        applyCreate(v, req);
        venueMapper.updateById(v);
    }

    @Override
    public void delete(Long id) {
        mustExist(id);
        venueMapper.deleteById(id); // @TableLogic 软删
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceBusinessHours(Long id, VenueBusinessHourRequest req) {
        mustExist(id);
        businessHourMapper.delete(new LambdaQueryWrapper<VenueBusinessHour>()
                .eq(VenueBusinessHour::getVenueId, id));
        for (VenueBusinessHourRequest.Item it : req.getHours()) {
            VenueBusinessHour bh = new VenueBusinessHour();
            bh.setVenueId(id);
            bh.setDayOfWeek(it.getDayOfWeek());
            bh.setOpenTime(it.getOpenTime());
            bh.setCloseTime(it.getCloseTime());
            businessHourMapper.insert(bh);
        }
    }

    @Override
    public Long addContact(Long venueId, VenueContactRequest req) {
        mustExist(venueId);
        VenueContact c = new VenueContact();
        c.setVenueId(venueId);
        c.setType(req.getType());
        c.setValue(req.getValue());
        c.setLabel(req.getLabel());
        c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        contactMapper.insert(c);
        return c.getId();
    }

    @Override
    public void updateContact(Long contactId, VenueContactRequest req) {
        VenueContact c = contactMapper.selectById(contactId);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND);
        c.setType(req.getType());
        c.setValue(req.getValue());
        c.setLabel(req.getLabel());
        c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        contactMapper.updateById(c);
    }

    @Override
    public void deleteContact(Long contactId) {
        if (contactMapper.selectById(contactId) == null) throw new BizException(ErrorCode.NOT_FOUND);
        contactMapper.deleteById(contactId);
    }

    @Override
    public PageResult<VenueVO> appList(VenueQueryRequest req) {
        LambdaQueryWrapper<Venue> w = new LambdaQueryWrapper<Venue>()
                .eq(Venue::getStatus, "ACTIVE");
        if (StringUtils.hasText(req.getKeyword())) {
            w.and(x -> x.like(Venue::getName, req.getKeyword())
                    .or().like(Venue::getAddress, req.getKeyword()));
        }
        w.orderByDesc(Venue::getCreatedAt);
        Page<Venue> p = venueMapper.selectPage(new Page<>(req.getPage(), req.getSize()), w);
        List<VenueVO> vos = p.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), req.getPage(), req.getSize(), vos);
    }

    @Override
    public VenueDetailVO appGet(Long id) {
        Venue v = venueMapper.selectById(id);
        if (v == null) throw new BizException(ErrorCode.VENUE_NOT_FOUND);
        return toDetailVO(v);
    }

    // ---- helpers ----

    private Venue mustExist(Long id) {
        Venue v = venueMapper.selectById(id);
        if (v == null) throw new BizException(ErrorCode.VENUE_NOT_FOUND);
        return v;
    }

    private void applyCreate(Venue v, VenueCreateRequest req) {
        v.setName(req.getName());
        v.setAddress(req.getAddress());
        v.setLatitude(req.getLatitude());
        v.setLongitude(req.getLongitude());
        v.setCoverUrl(req.getCoverUrl());
        v.setDescription(req.getDescription());
        if (req.getStatus() != null) v.setStatus(req.getStatus());
        if (req.getBookingLeadDays() != null) v.setBookingLeadDays(req.getBookingLeadDays());
    }

    private VenueVO toListVO(Venue v) {
        VenueVO vo = new VenueVO();
        copyBase(v, vo);
        vo.setContacts(loadContacts(v.getId()));
        return vo;
    }

    private VenueDetailVO toDetailVO(Venue v) {
        VenueDetailVO vo = new VenueDetailVO();
        copyBase(v, vo);
        vo.setContacts(loadContacts(v.getId()));
        vo.setBusinessHours(businessHourMapper.selectList(
                        new LambdaQueryWrapper<VenueBusinessHour>()
                                .eq(VenueBusinessHour::getVenueId, v.getId())
                                .orderByAsc(VenueBusinessHour::getDayOfWeek))
                .stream().map(bh -> {
                    VenueDetailVO.BusinessHourVO b = new VenueDetailVO.BusinessHourVO();
                    b.setDayOfWeek(bh.getDayOfWeek());
                    b.setOpenTime(bh.getOpenTime());
                    b.setCloseTime(bh.getCloseTime());
                    return b;
                }).collect(Collectors.toList()));
        vo.setCourts(courtMapper.selectList(
                        new LambdaQueryWrapper<Court>()
                                .eq(Court::getVenueId, v.getId())
                                .orderByAsc(Court::getSortOrder))
                .stream().map(this::toCourtVO).collect(Collectors.toList()));
        return vo;
    }

    private void copyBase(Venue v, VenueVO vo) {
        vo.setId(v.getId());
        vo.setName(v.getName());
        vo.setAddress(v.getAddress());
        vo.setLatitude(v.getLatitude());
        vo.setLongitude(v.getLongitude());
        vo.setCoverUrl(v.getCoverUrl());
        vo.setDescription(v.getDescription());
        vo.setStatus(v.getStatus());
        vo.setBookingLeadDays(v.getBookingLeadDays());
    }

    private List<VenueContactVO> loadContacts(Long venueId) {
        return contactMapper.selectList(new LambdaQueryWrapper<VenueContact>()
                        .eq(VenueContact::getVenueId, venueId)
                        .orderByAsc(VenueContact::getSortOrder))
                .stream().map(c -> {
                    VenueContactVO vo = new VenueContactVO();
                    vo.setId(c.getId());
                    vo.setType(c.getType());
                    vo.setValue(c.getValue());
                    vo.setLabel(c.getLabel());
                    vo.setSortOrder(c.getSortOrder());
                    return vo;
                }).collect(Collectors.toList());
    }

    private CourtVO toCourtVO(Court c) {
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
