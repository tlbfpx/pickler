package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.entity.BookingSlot;
import com.heypickler.entity.Court;
import com.heypickler.entity.CourtPricingBand;
import com.heypickler.entity.Venue;
import com.heypickler.entity.VenueBusinessHour;
import com.heypickler.mapper.BookingSlotMapper;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.CourtPricingBandMapper;
import com.heypickler.mapper.VenueBusinessHourMapper;
import com.heypickler.mapper.VenueMapper;
import com.heypickler.service.SlotService;
import com.heypickler.vo.SlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final CourtMapper courtMapper;
    private final VenueMapper venueMapper;
    private final VenueBusinessHourMapper businessHourMapper;
    private final CourtPricingBandMapper bandMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final SlotCalculator calculator;
    private final Clock clock;

    @Override
    public List<SlotVO> getCourtSlots(Long courtId, LocalDate date) {
        Court court = courtMapper.selectById(courtId);
        if (court == null) throw new BizException(ErrorCode.COURT_NOT_FOUND);
        if (!"OPEN".equals(court.getStatus())) throw new BizException(ErrorCode.COURT_NOT_AVAILABLE);

        Venue venue = venueMapper.selectById(court.getVenueId());
        int leadDays = (venue != null && venue.getBookingLeadDays() != null) ? venue.getBookingLeadDays() : 14;

        // LocalDate dow(Mon=1..Sun=7) → schema(0=Sun..6=Sat): getValue()%7
        int schemaDow = date.getDayOfWeek().getValue() % 7;
        VenueBusinessHour bh = businessHourMapper.selectOne(new LambdaQueryWrapper<VenueBusinessHour>()
                .eq(VenueBusinessHour::getVenueId, court.getVenueId())
                .eq(VenueBusinessHour::getDayOfWeek, schemaDow));
        LocalTime open = bh != null ? bh.getOpenTime() : null;
        LocalTime close = bh != null ? bh.getCloseTime() : null;

        String dayType = (schemaDow == 0 || schemaDow == 6) ? "WEEKEND" : "WEEKDAY";
        List<CourtPricingBand> effBands = bandMapper.selectList(new LambdaQueryWrapper<CourtPricingBand>()
                .eq(CourtPricingBand::getCourtId, courtId)
                .in(CourtPricingBand::getDayType, dayType, "ALL"));

        Set<LocalDateTime> occupied = bookingSlotMapper.selectList(new LambdaQueryWrapper<BookingSlot>()
                        .eq(BookingSlot::getCourtId, courtId)
                        .between(BookingSlot::getSlotStart, date.atStartOfDay(), date.atTime(LocalTime.MAX)))
                .stream().map(BookingSlot::getSlotStart).collect(Collectors.toSet());

        return calculator.generate(open, close, court.getSlotMinutes(), effBands, occupied,
                        date, LocalDateTime.now(clock), leadDays).stream().map(r -> {
                    SlotVO vo = new SlotVO();
                    vo.setStart(r.start());
                    vo.setEnd(r.end());
                    vo.setAvailable(r.available());
                    vo.setPrice(r.price());
                    return vo;
                }).collect(Collectors.toList());
    }
}
