package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.config.BookingProperties;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.BookingService;
import com.heypickler.vo.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration MIN_LEAD = Duration.ofMinutes(30);

    private final BookingMapper bookingMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final CourtMapper courtMapper;
    private final VenueMapper venueMapper;
    private final VenueBusinessHourMapper businessHourMapper;
    private final CourtPricingBandMapper bandMapper;
    private final com.heypickler.mapper.UserMapper userMapper;   // 必需:list 需要 userNickname/phone
    private final SlotCalculator calculator;
    private final PricingBandValidator validator;
    private final StringRedisTemplate stringRedis;
    private final BookingProperties props;
    private final Clock clock;

    public BookingServiceImpl(BookingMapper bookingMapper, BookingSlotMapper bookingSlotMapper,
                             CourtMapper courtMapper, VenueMapper venueMapper,
                             VenueBusinessHourMapper businessHourMapper, CourtPricingBandMapper bandMapper,
                             com.heypickler.mapper.UserMapper userMapper,
                             SlotCalculator calculator, PricingBandValidator validator,
                             StringRedisTemplate stringRedis,
                             BookingProperties props,
                             @Qualifier("clock") Clock clock) {
        this.bookingMapper = bookingMapper;
        this.bookingSlotMapper = bookingSlotMapper;
        this.courtMapper = courtMapper;
        this.venueMapper = venueMapper;
        this.businessHourMapper = businessHourMapper;
        this.bandMapper = bandMapper;
        this.userMapper = userMapper;
        this.calculator = calculator;
        this.validator = validator;
        this.stringRedis = stringRedis;
        this.props = props;
        this.clock = clock;
    }

    // ====== create ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingCreateResultVO create(HttpServletRequest httpReq, BookingCreateRequest body) {
        Long userId = ((Number) httpReq.getAttribute("userId")).longValue();
        Court court = courtMapper.selectById(body.getCourtId());
        if (court == null) throw new BizException(ErrorCode.COURT_NOT_FOUND);
        if (!"OPEN".equals(court.getStatus())) throw new BizException(ErrorCode.COURT_NOT_AVAILABLE);

        Venue venue = venueMapper.selectById(court.getVenueId());
        int leadDays = venue != null && venue.getBookingLeadDays() != null ? venue.getBookingLeadDays() : 14;

        // user concurrent cap
        long current = bookingMapper.selectCount(new LambdaQueryWrapper<Booking>()
                .eq(Booking::getUserId, userId)
                .eq(Booking::getStatus, "CONFIRMED")
                .gt(Booking::getSlotStart, LocalDateTime.now(clock)));
        if (current >= props.getMaxConcurrent()) {
            throw new BizException(ErrorCode.USER_BOOKING_LIMIT_EXCEEDED);
        }

        // gather pricing+business hour
        LocalDate date = body.getSlotStart().toLocalDate();
        int schemaDow = date.getDayOfWeek().getValue() % 7;
        VenueBusinessHour bh = businessHourMapper.selectOne(new LambdaQueryWrapper<VenueBusinessHour>()
                .eq(VenueBusinessHour::getVenueId, court.getVenueId())
                .eq(VenueBusinessHour::getDayOfWeek, schemaDow));
        String dayType = (schemaDow == 0 || schemaDow == 6) ? "WEEKEND" : "WEEKDAY";
        List<CourtPricingBand> effBands = bandMapper.selectList(new LambdaQueryWrapper<CourtPricingBand>()
                .eq(CourtPricingBand::getCourtId, court.getId())
                .in(CourtPricingBand::getDayType, dayType, "ALL"));
        Set<LocalDateTime> occupied = bookingSlotMapper.selectList(new LambdaQueryWrapper<BookingSlot>()
                .eq(BookingSlot::getCourtId, court.getId())
                .between(BookingSlot::getSlotStart, date.atStartOfDay(), date.atTime(LocalTime.MAX)))
                .stream().map(BookingSlot::getSlotStart).collect(Collectors.toSet());

        // 逐格独立校验 + 多 band 求和 + 整单拒绝 (复用 P1 SlotCalculator)
        List<SlotCalculator.SlotRange> ranges = calculator.generate(
                bh != null ? bh.getOpenTime() : null,
                bh != null ? bh.getCloseTime() : null,
                court.getSlotMinutes(),
                effBands, occupied,
                date, LocalDateTime.now(clock), leadDays);

        // 构造每个 slot 格并验证:每格必须 available + 在 lead window 内
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime latestStart = now.plusDays(leadDays);
        LocalDateTime earliestStart = now.plus(MIN_LEAD);
        BigDecimal totalPrice = BigDecimal.ZERO;
        List<LocalDateTime> myStarts = new ArrayList<>();
        for (int i = 0; i < body.getSlotsCount(); i++) {
            LocalDateTime t = body.getSlotStart().plusMinutes((long) i * court.getSlotMinutes());
            if (t.isBefore(earliestStart) || !t.isBefore(latestStart)) {
                throw new BizException(ErrorCode.BOOKING_WINDOW_EXCEEDED);
            }
            SlotCalculator.SlotRange r = ranges.stream()
                    .filter(x -> x.start().equals(t)).findFirst().orElse(null);
            if (r == null || !r.available() || r.price() == null) {
                throw new BizException(ErrorCode.SLOT_NOT_BOOKABLE);
            }
            totalPrice = totalPrice.add(r.price());
            myStarts.add(t);
        }
        // also validate no gaps;the first slot must be on the user's start;others step by slotMinutes
        for (int i = 1; i < myStarts.size(); i++) {
            if (!myStarts.get(i).equals(myStarts.get(i - 1).plusMinutes(court.getSlotMinutes()))) {
                throw new BizException(ErrorCode.SLOT_NOT_BOOKABLE);
            }
        }

        // booking_no = BK + yyyyMMdd + "-" + INCR (本地日)
        String dateKey = LocalDate.now(clock).format(YMD);
        Long seq = stringRedis.opsForValue().increment(RedisKey.bookingSeq(dateKey));
        long safeSeq = seq == null ? 1 : seq;
        String bookingNo = "BK" + dateKey + "-" + String.format("%04d", safeSeq);

        Booking booking = new Booking();
        booking.setBookingNo(bookingNo);
        booking.setUserId(userId);
        booking.setVenueId(court.getVenueId());
        booking.setCourtId(court.getId());
        booking.setSlotDate(date);
        booking.setSlotStart(body.getSlotStart());
        booking.setSlotEnd(body.getSlotStart().plusMinutes((long) body.getSlotsCount() * court.getSlotMinutes()));
        booking.setSlotsCount(body.getSlotsCount());
        booking.setPriceSnapshot(totalPrice);
        booking.setStatus("CONFIRMED");

        try {
            bookingMapper.insert(booking);
            for (LocalDateTime t : myStarts) {
                BookingSlot bs = new BookingSlot();
                bs.setBookingId(booking.getId());
                bs.setCourtId(court.getId());
                bs.setSlotStart(t);
                bookingSlotMapper.insert(bs);   // 任一撞 UNIQUE → 整事务回滚 → 抛 SLOT_ALREADY_TAKEN
            }
        } catch (DataIntegrityViolationException e) {
            // 让 GlobalExceptionHandler 翻译;这里重新抛以便事务回滚 + 错误码翻译
            throw e;
        }

        BookingCreateResultVO vo = new BookingCreateResultVO();
        vo.setId(booking.getId()); vo.setBookingNo(bookingNo);
        vo.setVenueId(court.getVenueId()); vo.setCourtId(court.getId());
        vo.setSlotDate(date); vo.setSlotStart(booking.getSlotStart()); vo.setSlotEnd(booking.getSlotEnd());
        vo.setSlotsCount(booking.getSlotsCount()); vo.setPriceSnapshot(totalPrice);
        vo.setStatus("CONFIRMED");
        return vo;
    }

    // ====== cancel (CAS + 删 slot) ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelMine(HttpServletRequest httpReq, Long bookingId) {
        Long userId = ((Number) httpReq.getAttribute("userId")).longValue();
        Booking b = bookingMapper.selectById(bookingId);
        if (b == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        if (!b.getUserId().equals(userId)) throw new BizException(ErrorCode.FORBIDDEN);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(b.getSlotStart().minusHours(props.getCancelDeadlineHours()))) {
            throw new BizException(ErrorCode.CANCEL_DEADLINE_PASSED);
        }
        // CAS
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, bookingId)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .eq(Booking::getUserId, userId)
                        .set(Booking::getStatus, "CANCELLED")
                        .set(Booking::getCancelledAt, now)
                        .set(Booking::getUpdatedAt, now));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
        // 删 slot 行释放 UNIQUE
        bookingSlotMapper.delete(new LambdaQueryWrapper<BookingSlot>().eq(BookingSlot::getBookingId, bookingId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceCancel(Long bookingId, BookingForceCancelRequest body) {
        // 区分存在性 vs CAS(spec §9):不存在的 id→BOOKING_NOT_FOUND,存在但 CAS=0→INVALID_STATUS_TRANSITION
        Booking exists = bookingMapper.selectById(bookingId);
        if (exists == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        // 单一 CAS:状态推进 + cancel_reason + cancelled_at 在同一 UPDATE
        String reason = "ADMIN:" + (body == null || body.getReason() == null ? "" : body.getReason());
        LocalDateTime now = LocalDateTime.now(clock);
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, bookingId)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .set(Booking::getStatus, "CANCELLED")
                        .set(Booking::getCancelledAt, now)
                        .set(Booking::getCancelReason, reason));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);   // 早抛,避免误删 slot
        bookingSlotMapper.delete(new LambdaQueryWrapper<BookingSlot>().eq(BookingSlot::getBookingId, bookingId));
    }

    // ====== terminal transitions ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        if (bookingMapper.selectById(id) == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, id)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .set(Booking::getStatus, "COMPLETED"));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoShow(Long id) {
        if (bookingMapper.selectById(id) == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, id)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .set(Booking::getStatus, "NO_SHOW"));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ====== list ======

    @Override
    public PageResult<BookingVO> listMine(HttpServletRequest httpReq, String group, int page, int size) {
        if (group == null || (!group.equalsIgnoreCase("upcoming") && !group.equalsIgnoreCase("history"))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "group must be one of: upcoming, history");
        }
        Long userId = ((Number) httpReq.getAttribute("userId")).longValue();
        LocalDateTime now = LocalDateTime.now(clock);
        LambdaQueryWrapper<Booking> w = new LambdaQueryWrapper<Booking>().eq(Booking::getUserId, userId);
        if ("upcoming".equalsIgnoreCase(group)) {
            w.eq(Booking::getStatus, "CONFIRMED").ge(Booking::getSlotStart, now);
        } else { // history
            // 双层 lambda(或(状态非 CONFIRMED, 或 slot_start < now)) — 避免"非 CONFIRMED 确已出席完"的订单漏掉
            w.and(wq -> wq.ne(Booking::getStatus, "CONFIRMED").or(wq2 -> wq2.lt(Booking::getSlotStart, now)));
        }
        w.orderByDesc(Booking::getSlotStart);
        Page<Booking> pg = bookingMapper.selectPage(new Page<>(page, size), w);
        List<BookingVO> vos = enrichMine(pg.getRecords());
        return PageResult.of(pg.getTotal(), page, size, vos);
    }

    @Override
    public PageResult<BookingAdminVO> listAdmin(BookingQueryRequest q) {
        LambdaQueryWrapper<Booking> w = new LambdaQueryWrapper<>();
        if (q.getVenueId() != null) w.eq(Booking::getVenueId, q.getVenueId());
        if (q.getCourtId() != null) w.eq(Booking::getCourtId, q.getCourtId());
        if (q.getDateFrom() != null) w.ge(Booking::getSlotDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(Booking::getSlotDate, q.getDateTo());
        if (q.getStatus() != null && !q.getStatus().isEmpty()) w.eq(Booking::getStatus, q.getStatus());
        if (q.getKeyword() != null && !q.getKeyword().isEmpty()) {
            String kw = q.getKeyword();
            // 关键字 = bookingNo 模糊 OR 纯数字 userId 等值;非数字 kw 不会抛 NumberFormatException
            w.and(x -> {
                x.like(Booking::getBookingNo, kw);
                try { long uid = Long.parseLong(kw); x.or().eq(Booking::getUserId, uid); }
                catch (NumberFormatException ignore) { /* 纯文本关键字:只看 bookingNo */ }
            });
        }
        w.orderByDesc(Booking::getSlotStart);
        Page<Booking> pg = bookingMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        List<BookingAdminVO> vos = enrich(pg.getRecords());
        return PageResult.of(pg.getTotal(), q.getPage(), q.getSize(), vos);
    }

    @Override
    public BookingAdminVO getAdmin(Long id) {
        Booking b = bookingMapper.selectById(id);
        if (b == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        return enrich(Collections.singletonList(b)).get(0);
    }

    // ====== enrichment (避免 N+1:一次 list -> 一次 set 查 user/venue/court) ======

    private List<BookingAdminVO> enrich(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();
        Set<Long> userIds  = bookings.stream().map(Booking::getUserId).collect(Collectors.toSet());
        Set<Long> venueIds = bookings.stream().map(Booking::getVenueId).collect(Collectors.toSet());
        Set<Long> courtIds = bookings.stream().map(Booking::getCourtId).collect(Collectors.toSet());

        // 一次性取 user,投影 nickname + phone(spec §8.1 要求批量加载,避免 N+1)
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);
        Map<Long, String>  userNames  = users.stream()
                .collect(Collectors.toMap(User::getId, u -> Optional.ofNullable(u.getNickname()).orElse("用户" + u.getId())));
        Map<Long, String>  userPhones = users.stream()
                .collect(Collectors.toMap(User::getId, u -> Optional.ofNullable(u.getPhone()).orElse("")));
        Map<Long, String>  venueNames = venueIds.isEmpty() ? Map.of() :
                venueMapper.selectBatchIds(venueIds).stream()
                        .collect(Collectors.toMap(Venue::getId, Venue::getName));
        Map<Long, Court>   courtMap   = courtIds.isEmpty() ? Map.of() :
                courtMapper.selectBatchIds(courtIds).stream().collect(Collectors.toMap(Court::getId, c -> c));

        return bookings.stream().map(b -> {
            BookingAdminVO v = toAdminVO(b);
            v.setUserNickname(userNames.get(b.getUserId()));
            v.setUserPhone(userPhones.get(b.getUserId()));
            v.setVenueName(venueNames.get(b.getVenueId()));
            Court c = courtMap.get(b.getCourtId());
            if (c != null) { v.setCourtName(c.getName()); v.setCourtType(c.getCourtType()); }
            return v;
        }).collect(Collectors.toList());
    }

    private List<BookingVO> enrichMine(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();
        Set<Long> courtIds = bookings.stream().map(Booking::getCourtId).collect(Collectors.toSet());
        Set<Long> venueIds = bookings.stream().map(Booking::getVenueId).collect(Collectors.toSet());
        Map<Long, Court>  courtMap = courtIds.isEmpty() ? Map.of() :
                courtMapper.selectBatchIds(courtIds).stream().collect(Collectors.toMap(Court::getId, c -> c));
        Map<Long, String> venueNames = venueIds.isEmpty() ? Map.of() :
                venueMapper.selectBatchIds(venueIds).stream()
                        .collect(Collectors.toMap(Venue::getId, Venue::getName));
        return bookings.stream().map(b -> {
            BookingVO v = toVO(b);
            v.setVenueName(venueNames.get(b.getVenueId()));
            Court c = courtMap.get(b.getCourtId());
            if (c != null) v.setCourtName(c.getName());
            return v;
        }).collect(Collectors.toList());
    }

    // ====== mappers ======

    private BookingVO toVO(Booking b) {
        BookingVO v = new BookingVO();
        v.setId(b.getId()); v.setBookingNo(b.getBookingNo());
        v.setCourtId(b.getCourtId()); v.setVenueId(b.getVenueId());
        v.setSlotDate(b.getSlotDate()); v.setSlotStart(b.getSlotStart()); v.setSlotEnd(b.getSlotEnd());
        v.setSlotsCount(b.getSlotsCount()); v.setPriceSnapshot(b.getPriceSnapshot()); v.setStatus(b.getStatus());
        v.setCancelReason(b.getCancelReason()); v.setCancelledAt(b.getCancelledAt());
        v.setCreatedAt(b.getCreatedAt());
        return v;
    }

    private BookingAdminVO toAdminVO(Booking b) {
        BookingAdminVO v = new BookingAdminVO();
        v.setId(b.getId()); v.setBookingNo(b.getBookingNo());
        v.setUserId(b.getUserId()); v.setVenueId(b.getVenueId());
        v.setCourtId(b.getCourtId());
        v.setSlotDate(b.getSlotDate()); v.setSlotStart(b.getSlotStart()); v.setSlotEnd(b.getSlotEnd());
        v.setSlotsCount(b.getSlotsCount()); v.setPriceSnapshot(b.getPriceSnapshot()); v.setStatus(b.getStatus());
        v.setCancelReason(b.getCancelReason()); v.setCancelledAt(b.getCancelledAt());
        v.setCreatedAt(b.getCreatedAt());
        return v;
    }
}
