package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.enums.EventFormat;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.util.StatusTransitionValidator;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.Event;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.MatchMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.EventService;
import com.heypickler.service.TeamService;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventParticipantVO;
import com.heypickler.vo.EventSummaryVO;
import com.heypickler.vo.EventResultVO;
import com.heypickler.vo.BulkCheckInResult;
import com.heypickler.vo.EventVO;
import com.heypickler.vo.RegistrationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;
    private final PointRecordMapper pointRecordMapper;
    private final AdminUserMapper adminUserMapper;
    private final TeamMapper teamMapper;
    private final MatchMapper matchMapper;
    private final TeamService teamService;

    @Override
    public PageResult<EventVO> listEvents(String type, String status, int page, int size) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .isNull(Event::getDeletedAt);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Event::getType, type);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Event::getStatus, status);
        }

        wrapper.orderByDesc(Event::getEventTime);

        Page<Event> eventPage = eventMapper.selectPage(new Page<>(page, size), wrapper);

        List<EventVO> voList = convertAllToVO(eventPage.getRecords());

        return PageResult.of(eventPage.getTotal(), (int) eventPage.getCurrent(), size, voList);
    }

    @Override
    public EventDetailVO getEventDetail(Long eventId, Long userId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        EventDetailVO vo = convertToDetailVO(event);

        if (userId != null) {
            Registration registration = registrationMapper.selectOne(
                    new LambdaQueryWrapper<Registration>()
                            .eq(Registration::getUserId, userId)
                            .eq(Registration::getEventId, eventId)
                            .eq(Registration::getStatus, "REGISTERED")
            );
            if (registration != null) {
                vo.setMyRegistrationStatus("REGISTERED");
            }
        }

        return vo;
    }

    @Override
    public EventVO getEventDetail(Long eventId) {
        return convertToVO(requireEvent(eventId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, Long eventId, RegisterRequest request) {
        // 1) read-only guards — pre-flight checks before opening a write transaction.
        Event event = requireEvent(eventId);

        if (Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED, "赛事已分组锁定。如需调整请在「对阵/比赛」Tab 点「解锁并清空」");
        }
        if ("IN_PROGRESS".equals(event.getStatus()) || "COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事已开始比赛，无法报名；如需代签到请使用「报名」Tab 的签到按钮");
        }
        if (!"OPEN".equals(event.getStatus())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }
        if (LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }

        String format = resolveFormat(event);
        if (EventFormat.SINGLES.name().equals(format)) {
            registerSingles(userId, eventId, event, request);
        } else {
            registerTeam(userId, eventId, event, request);
        }
    }

    /**
     * Single-player registration: one registration row, matchType forced to SINGLES,
     * partner/team payload rejected.
     */
    private void registerSingles(Long userId, Long eventId, Event event, RegisterRequest request) {
        if (request.getPartnerId() != null || request.getPartnerUserId() != null || request.getTeamId() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单打赛事不能携带搭档或队伍；如需双打请选择「DOUBLES」或「MIXED」比赛形式");
        }
        checkPointsEligibility(userId, event);
        guardDuplicate(userId, eventId);
        reserveSlot(eventId, event);

        Registration registration = new Registration();
        registration.setUserId(userId);
        registration.setEventId(eventId);
        registration.setMatchType(EventFormat.SINGLES.name());
        registration.setStatus("REGISTERED");
        registrationMapper.insert(registration);

        maybeTransitionFull(eventId, event);
    }

    /**
     * Doubles/mixed registration, branch by intent:
     *   partnerUserId present -> captain initiates a PENDING team (createTeam)
     *   teamId present        -> invited partner confirms (confirmTeam)
     * The chosen TeamService op inserts the member's registration (with
     * matchType = event.format set at creation); this method only gates
     * capacity/dedup/eligibility.
     */
    private void registerTeam(Long userId, Long eventId, Event event, RegisterRequest request) {
        if (request.getTeamId() != null) {
            guardDuplicate(userId, eventId);
            reserveSlot(eventId, event);
            teamService.confirmTeam(request.getTeamId(), userId);
        } else if (request.getPartnerUserId() != null) {
            checkPointsEligibility(userId, event);
            guardDuplicate(userId, eventId);
            reserveSlot(eventId, event);
            teamService.createTeam(eventId, userId, request.getPartnerUserId());
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "双打/混打需指定搭档(partnerUserId)或确认队伍(teamId)；请先在「报名」Tab 完成建队再报名");
        }
        maybeTransitionFull(eventId, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRegistration(Long userId, Long eventId) {
        Event event = requireEvent(eventId);

        if (Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED, "赛事已分组锁定。如需调整请在「对阵/比赛」Tab 点「解锁并清空」");
        }
        if ("IN_PROGRESS".equals(event.getStatus()) || "COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事已开始比赛，无法取消报名；如需代签到或代录入请使用「报名」/「对阵/比赛」Tab");
        }
        if (LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }

        Registration registration = registrationMapper.selectOne(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .eq(Registration::getEventId, eventId)
                        .eq(Registration::getStatus, "REGISTERED"));

        if (registration == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到有效的报名记录");
        }

        if (registration.getTeamId() != null) {
            // Doubles/mixed: any member withdrawing dissolves the whole team.
            // Count active team registrations first (1 for PENDING, 2 for CONFIRMED),
            // then dissolve (which withdraws them all) and release that many slots.
            List<Registration> teamRegs = registrationMapper.selectList(
                    new LambdaQueryWrapper<Registration>()
                            .eq(Registration::getTeamId, registration.getTeamId())
                            .eq(Registration::getStatus, "REGISTERED"));
            teamService.dissolve(registration.getTeamId());
            for (Registration ignored : teamRegs) {
                releaseSlot(eventId);
            }
        } else {
            registration.setStatus("WITHDRAWN");
            registrationMapper.updateById(registration);
            releaseSlot(eventId);
        }

        maybeTransitionOpen(eventId, event);
    }

    @Override
    public PageResult<EventVO> adminListEvents(String type, String status, String keyword, String location, String startTime, String endTime, String sort, int page, int size) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();

        // Loop-v16A — multi-keyword search: split by whitespace, each token
        // matches (title OR description), all tokens must match (AND).
        if (keyword != null && !keyword.isEmpty()) {
            String[] tokens = keyword.trim().split("\\s+");
            if (tokens.length > 0 && !tokens[0].isEmpty()) {
                wrapper.and(w -> {
                    for (String t : tokens) {
                        w.or().like(Event::getTitle, t)
                         .or().like(Event::getDescription, t);
                    }
                });
            }
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Event::getType, type);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Event::getStatus, status);
        }
        if (location != null && !location.isEmpty()) {
            wrapper.like(Event::getLocation, location);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(Event::getEventTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(Event::getEventTime, endTime);
        }

        // Loop-v16A — sort: default event_time desc. Allowed: *_asc / *_desc.
        applySort(wrapper, sort);

        Page<Event> eventPage = eventMapper.selectPage(new Page<>(page, size), wrapper);

        List<EventVO> voList = convertAllToVO(eventPage.getRecords());

        return PageResult.of(eventPage.getTotal(), (int) eventPage.getCurrent(), size, voList);
    }

    /**
     * Loop-v16A — apply a sort clause to the wrapper. Supported values:
     *   event_time_desc (default), event_time_asc, created_at_desc,
     *   created_at_asc, current_participants_desc, current_participants_asc.
     * Unknown values fall back to the default (event_time desc).
     */
    private void applySort(LambdaQueryWrapper<Event> wrapper, String sort) {
        if (sort == null || sort.isEmpty()) {
            wrapper.orderByDesc(Event::getEventTime);
            return;
        }
        boolean asc = sort.endsWith("_asc");
        String field = asc
                ? sort.substring(0, sort.length() - 4)
                : sort.endsWith("_desc")
                    ? sort.substring(0, sort.length() - 5)
                    : sort;
        switch (field) {
            case "created_at":
                wrapper.orderBy(true, asc, Event::getCreatedAt);
                break;
            case "current_participants":
                wrapper.orderBy(true, asc, Event::getCurrentParticipants);
                break;
            case "event_time":
            default:
                wrapper.orderBy(true, asc, Event::getEventTime);
                break;
        }
    }

    @Override
    public Long createEvent(EventCreateRequest request, Long adminId) {
        Event event = new Event();
        BeanUtils.copyProperties(request, event);
        event.setCreatedBy(adminId);
        event.setCurrentParticipants(0);
        if (request.getStatus() == null) {
            event.setStatus("DRAFT");
        }
        if (request.getFee() == null) {
            event.setFee(java.math.BigDecimal.ZERO);
        }
        eventMapper.insert(event);
        return event.getId();
    }

    @Override
    public void updateEvent(Long eventId, EventUpdateRequest request) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        if (request.getStatus() != null && !request.getStatus().equals(event.getStatus())) {
            if (!StatusTransitionValidator.canTransit(event.getStatus(), request.getStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
        }

        Event updateEntity = new Event();
        BeanUtils.copyProperties(request, updateEntity);
        updateEntity.setId(eventId);
        eventMapper.updateById(updateEntity);
    }

    @Override
    public void deleteEvent(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        eventMapper.deleteById(eventId);
    }

    @Override
    public List<EventParticipantVO> getParticipants(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        List<Registration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getEventId, eventId)
                        .eq(Registration::getStatus, "REGISTERED"));

        if (registrations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = registrations.stream()
                .map(Registration::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return registrations.stream().map(reg -> {
            EventParticipantVO vo = new EventParticipantVO();
            vo.setUserId(reg.getUserId());
            vo.setMatchType(reg.getMatchType());
            vo.setRegistrationStatus(reg.getStatus());
            User user = userMap.get(reg.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<EventResultVO> getEventResults(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        List<Registration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getEventId, eventId)
                        .in(Registration::getStatus, "REGISTERED", "CHECKED_IN"));

        if (registrations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = registrations.stream()
                .map(Registration::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<PointRecord> records = pointRecordMapper.selectList(
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getEventId, eventId));

        Map<Long, Integer> pointsByUser = records.stream()
                .collect(Collectors.groupingBy(
                        PointRecord::getUserId,
                        Collectors.summingInt(PointRecord::getPoints)));

        List<EventResultVO> results = registrations.stream().map(reg -> {
            EventResultVO vo = new EventResultVO();
            vo.setUserId(reg.getUserId());
            vo.setMatchType(reg.getMatchType());
            User user = userMap.get(reg.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }
            vo.setPoints(pointsByUser.getOrDefault(reg.getUserId(), 0));
            return vo;
        }).collect(Collectors.toList());

        results.sort(Comparator.comparingInt(EventResultVO::getPoints).reversed());

        int rank = 0;
        Integer prevPoints = null;
        for (EventResultVO vo : results) {
            if (prevPoints == null || !prevPoints.equals(vo.getPoints())) {
                rank++;
                prevPoints = vo.getPoints();
            }
            vo.setRank(rank);
        }

        return results;
    }

    @Override
    public PageResult<RegistrationVO> getRegistrations(Long eventId, String status, String matchType, int page, int size) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<Registration>()
                .eq(Registration::getEventId, eventId)
                .ne(Registration::getStatus, "WITHDRAWN");

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Registration::getStatus, status);
        }
        if (matchType != null && !matchType.isEmpty()) {
            wrapper.eq(Registration::getMatchType, matchType);
        }

        wrapper.orderByDesc(Registration::getCreatedAt);

        Page<Registration> regPage = registrationMapper.selectPage(new Page<>(page, size), wrapper);

        if (regPage.getRecords().isEmpty()) {
            return PageResult.of(0, page, size, Collections.emptyList());
        }

        // Batch load user data
        Set<Long> userIds = new HashSet<>();
        for (Registration reg : regPage.getRecords()) {
            userIds.add(reg.getUserId());
            if (reg.getPartnerId() != null) {
                userIds.add(reg.getPartnerId());
            }
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<RegistrationVO> voList = regPage.getRecords().stream().map(reg -> {
            RegistrationVO vo = new RegistrationVO();
            vo.setId(reg.getId());
            vo.setUserId(reg.getUserId());
            vo.setMatchType(reg.getMatchType());
            vo.setPartnerId(reg.getPartnerId());
            vo.setStatus(reg.getStatus());
            vo.setCreatedAt(reg.getCreatedAt());

            User user = userMap.get(reg.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }

            if (reg.getPartnerId() != null) {
                User partner = userMap.get(reg.getPartnerId());
                if (partner != null) {
                    vo.setPartnerNickname(partner.getNickname());
                }
            }

            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(regPage.getTotal(), (int) regPage.getCurrent(), size, voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRegistrationStatus(Long eventId, Long registrationId, String status) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        Registration registration = registrationMapper.selectById(registrationId);
        if (registration == null || !registration.getEventId().equals(eventId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "报名记录不存在");
        }

        String oldStatus = registration.getStatus();

        // Loop-v2 D10 — registration state-machine gate. Centralizes the
        // transition rules so future statuses (PAUSED, REFUNDED...) cannot be
        // silently shipped past an if/else chain. Throws INVALID_STATUS_TRANSITION
        // (1006) for unknown source/transition pairs.
        if (!StatusTransitionValidator.canRegistrationTransit(oldStatus, status)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "报名状态不允许从 " + oldStatus + " 变更为 " + status
                            + "；允许的目标：" + StatusTransitionValidator.getAllowedRegistrationTargets(oldStatus));
        }

        if ("CHECKED_IN".equals(status)) {
            registration.setStatus("CHECKED_IN");
            registrationMapper.updateById(registration);
        } else if ("WITHDRAWN".equals(status)) {
            registration.setStatus("WITHDRAWN");
            registrationMapper.updateById(registration);

            // Decrease participant count
            eventMapper.update(null,
                    new LambdaUpdateWrapper<Event>()
                            .eq(Event::getId, eventId)
                            .gt(Event::getCurrentParticipants, 0)
                            .setSql("current_participants = current_participants - 1"));

            // Auto transition: FULL → OPEN
            if ("FULL".equals(event.getStatus()) && event.getMaxParticipants() != null) {
                Event updated = eventMapper.selectById(eventId);
                if (updated.getCurrentParticipants() < event.getMaxParticipants()) {
                    eventMapper.update(null,
                            new LambdaUpdateWrapper<Event>()
                                    .eq(Event::getId, eventId)
                                    .eq(Event::getStatus, "FULL")
                                    .set(Event::getStatus, "OPEN"));
                }
            }
        }
    }

    // ---------- registration helpers ----------

    private Event requireEvent(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return event;
    }

    private String resolveFormat(Event event) {
        return event.getFormat() != null ? event.getFormat() : EventFormat.SINGLES.name();
    }

    private void checkPointsEligibility(Long userId, Event event) {
        if (event.getMinPoints() != null && event.getMinPoints() > 0) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                int userPoints = "STAR".equals(event.getType()) ? user.getStarPoints() : user.getPartyPoints();
                if (userPoints < event.getMinPoints()) {
                    String track = "STAR".equals(event.getType()) ? "战力" : "活力";
                    throw new BizException(ErrorCode.INSUFFICIENT_POINTS,
                            String.format("%s积分不足，无法报名该赛事（需 %d %s，当前 %d 分）；请先参加其他 %s 赛事积累积分",
                                    track, event.getMinPoints(), track, userPoints, track));
                }
            }
        }
    }

    private void guardDuplicate(Long userId, Long eventId) {
        Registration existing = registrationMapper.selectOne(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .eq(Registration::getEventId, eventId));
        if (existing != null && !"WITHDRAWN".equals(existing.getStatus())) {
            throw new BizException(ErrorCode.DUPLICATE_REGISTRATION);
        }
    }

    /**
     * Atomic capacity + grouping-lock guarded slot reservation (D4 fix).
     *
     * <p>Why the WHERE includes {@code grouping_locked = false}:
     * the lock flag is independently flipped by {@code GroupingService.lock}
     * (Spec 1). Without including it in the slot UPDATE, there is a TOCTOU
     * window between {@code register()} checking the flag in memory and
     * flipping the slot counter. Folding the lock check into the same UPDATE
     * makes "locked OR full → reject" atomic at the storage layer.
     *
     * <p>On 0 rows updated, we distinguish "full" vs "locked" with one extra
     * SELECT — strictly cheaper than the previous report's two-step read.
     */
    private void reserveSlot(Long eventId, Event event) {
        int rows = eventMapper.update(null,
                new LambdaUpdateWrapper<Event>()
                        .eq(Event::getId, eventId)
                        // No-lock guard — must be evaluated inside the slot UPDATE.
                        .and(w -> w.isNull(Event::getGroupingLocked)
                                .or().eq(Event::getGroupingLocked, false))
                        .lt(Event::getCurrentParticipants, event.getMaxParticipants())
                        .setSql("current_participants = current_participants + 1"));
        if (rows == 0) {
            Event fresh = eventMapper.selectById(eventId);
            if (fresh != null && Boolean.TRUE.equals(fresh.getGroupingLocked())) {
                throw new BizException(ErrorCode.REGISTRATION_CLOSED,
                        "赛事已分组锁定。如需调整请在「对阵/比赛」Tab 点「解锁并清空」");
            }
            throw new BizException(ErrorCode.REGISTRATION_FULL);
        }
    }

    private void releaseSlot(Long eventId) {
        eventMapper.update(null,
                new LambdaUpdateWrapper<Event>()
                        .eq(Event::getId, eventId)
                        .gt(Event::getCurrentParticipants, 0)
                        .setSql("current_participants = current_participants - 1"));
    }

    private void maybeTransitionFull(Long eventId, Event event) {
        if ("OPEN".equals(event.getStatus()) && event.getMaxParticipants() != null) {
            Event updated = eventMapper.selectById(eventId);
            if (updated.getCurrentParticipants() >= event.getMaxParticipants()) {
                eventMapper.update(null,
                        new LambdaUpdateWrapper<Event>()
                                .eq(Event::getId, eventId)
                                .eq(Event::getStatus, "OPEN")
                                .set(Event::getStatus, "FULL"));
            }
        }
    }

    private void maybeTransitionOpen(Long eventId, Event event) {
        if ("FULL".equals(event.getStatus()) && event.getMaxParticipants() != null) {
            Event updated = eventMapper.selectById(eventId);
            if (updated.getCurrentParticipants() < event.getMaxParticipants()) {
                eventMapper.update(null,
                        new LambdaUpdateWrapper<Event>()
                                .eq(Event::getId, eventId)
                                .eq(Event::getStatus, "FULL")
                                .set(Event::getStatus, "OPEN"));
            }
        }
    }

    private EventVO convertToVO(Event event) {
        EventVO vo = new EventVO();
        BeanUtils.copyProperties(event, vo);
        if (event.getCreatedBy() != null) {
            vo.setCreatedByUsername(loadOrganizerName(event.getCreatedBy()));
        }
        return vo;
    }

    /**
     * Convert a list of events to VOs, batch-loading admin usernames for
     * {@code createdBy} to avoid N+1. Used by the paginated list endpoints
     * (admin list, app list) which can include many events created by the
     * same admin.
     */
    private List<EventVO> convertAllToVO(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> adminIds = events.stream()
                .map(Event::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> organizerCache = adminIds.isEmpty()
                ? Collections.emptyMap()
                : adminUserMapper.selectBatchIds(adminIds).stream()
                        .filter(a -> a.getId() != null && a.getUsername() != null)
                        .collect(Collectors.toMap(AdminUser::getId, AdminUser::getUsername, (a, b) -> a));
        return events.stream()
                .map(e -> {
                    EventVO vo = new EventVO();
                    BeanUtils.copyProperties(e, vo);
                    if (e.getCreatedBy() != null) {
                        vo.setCreatedByUsername(organizerCache.get(e.getCreatedBy()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * Single-event organizer lookup. Used by {@link #getEventDetail(Long)} /
     * {@link #convertToDetailVO(Event)} paths where a batched fetch would
     * over-fetch. Safe to call repeatedly — one row per admin id.
     */
    private String loadOrganizerName(Long adminId) {
        if (adminId == null) return null;
        AdminUser admin = adminUserMapper.selectById(adminId);
        return admin != null ? admin.getUsername() : null;
    }

    private EventDetailVO convertToDetailVO(Event event) {
        EventDetailVO vo = new EventDetailVO();
        BeanUtils.copyProperties(event, vo);
        return vo;
    }

    // ──────────────── Loop-v13 — getEventSummary ────────────────

    @Override
    public EventSummaryVO getEventSummary(Long eventId) {
        Event event = requireEvent(eventId);
        EventSummaryVO vo = new EventSummaryVO();
        vo.setEventId(event.getId());
        vo.setTitle(event.getTitle());
        vo.setType(event.getType());
        vo.setStatus(event.getStatus());
        vo.setEventTime(event.getEventTime());
        vo.setMaxParticipants(event.getMaxParticipants());
        vo.setCurrentParticipants(event.getCurrentParticipants() == null ? 0 : event.getCurrentParticipants());
        int max = event.getMaxParticipants() == null ? 0 : event.getMaxParticipants();
        vo.setFillRate(max > 0 ? (double) vo.getCurrentParticipants() / max : 0.0);

        vo.setRegistration(buildRegistrationCounts(eventId));
        vo.setTeams(buildTeamCounts(eventId));
        vo.setMatches(buildMatchCounts(eventId));
        vo.setFees(buildFeeSummary(event, vo.getRegistration().getRegistered()));
        vo.setTransitionableStatuses(
                new java.util.ArrayList<>(
                        com.heypickler.common.util.StatusTransitionValidator.getAllowedTargets(event.getStatus())));
        return vo;
    }

    private EventSummaryVO.RegistrationCountVO buildRegistrationCounts(Long eventId) {
        EventSummaryVO.RegistrationCountVO rc = new EventSummaryVO.RegistrationCountVO();
        int registered = 0, checkedIn = 0, withdrawn = 0;
        for (java.util.Map<String, Object> row : registrationMapper.countByEventGroupedByStatus(eventId)) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("REGISTERED".equals(status)) registered = (int) cnt;
            else if ("CHECKED_IN".equals(status)) checkedIn = (int) cnt;
            else if ("WITHDRAWN".equals(status)) withdrawn = (int) cnt;
        }
        rc.setRegistered(registered);
        rc.setCheckedIn(checkedIn);
        rc.setWithdrawn(withdrawn);
        rc.setCheckInRate(registered > 0 ? (double) checkedIn / registered : 0.0);
        return rc;
    }

    private EventSummaryVO.TeamCountVO buildTeamCounts(Long eventId) {
        EventSummaryVO.TeamCountVO tc = new EventSummaryVO.TeamCountVO();
        tc.setPending(0);
        tc.setConfirmed(0);
        for (java.util.Map<String, Object> row : teamMapper.countByEventGroupedByStatus(eventId)) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("PENDING".equals(status)) tc.setPending((int) cnt);
            else if ("CONFIRMED".equals(status)) tc.setConfirmed((int) cnt);
        }
        return tc;
    }

    private EventSummaryVO.MatchCountVO buildMatchCounts(Long eventId) {
        EventSummaryVO.MatchCountVO mc = new EventSummaryVO.MatchCountVO();
        mc.setScheduled(0);
        mc.setInProgress(0);
        mc.setCompleted(0);
        for (java.util.Map<String, Object> row : matchMapper.countByEventGroupedByStatus(eventId)) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("SCHEDULED".equals(status)) mc.setScheduled((int) cnt);
            else if ("IN_PROGRESS".equals(status)) mc.setInProgress((int) cnt);
            else if ("COMPLETED".equals(status)) mc.setCompleted((int) cnt);
        }
        return mc;
    }

    private EventSummaryVO.FeeSummaryVO buildFeeSummary(Event event, int activeRegistrations) {
        EventSummaryVO.FeeSummaryVO fee = new EventSummaryVO.FeeSummaryVO();
        long feeYuan = event.getFee() == null ? 0L : event.getFee().longValue();
        fee.setTotalCollected(feeYuan * activeRegistrations);
        fee.setCurrency("CNY");
        return fee;
    }

    // ──────────────── Loop-v14 — bulk check-in ────────────────

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public com.heypickler.vo.BulkCheckInResult bulkCheckIn(Long eventId, java.util.List<Long> registrationIds) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            throw new com.heypickler.common.exception.BizException(
                    com.heypickler.common.exception.ErrorCode.PARAM_ERROR,
                    "registrationIds 不能为空");
        }
        if (registrationIds.size() > 200) {
            throw new com.heypickler.common.exception.BizException(
                    com.heypickler.common.exception.ErrorCode.PARAM_ERROR,
                    "registrationIds 单次最多 200 条，当前 " + registrationIds.size());
        }
        for (Long id : registrationIds) {
            if (id == null || id <= 0) {
                throw new com.heypickler.common.exception.BizException(
                        com.heypickler.common.exception.ErrorCode.PARAM_ERROR,
                        "registrationIds 含非法 id: " + id);
            }
        }
        requireEvent(eventId);

        java.util.List<Long> notFound = new java.util.ArrayList<>();
        java.util.List<Long> withdrawn = new java.util.ArrayList<>();
        java.util.List<Long> toUpdate = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();

        // One SELECT to get current state
        for (Registration r : registrationMapper.findByEventAndIds(eventId, registrationIds)) {
            if (r == null || r.getId() == null) continue;
            if (!seen.add(r.getId())) continue;
            String status = r.getStatus();
            if ("WITHDRAWN".equals(status)) {
                withdrawn.add(r.getId());
            } else if ("REGISTERED".equals(status)) {
                toUpdate.add(r.getId());
            }
            // CHECKED_IN → alreadyCheckedIn (not exposed in v1 result)
        }
        // Ids not present in result → notFound
        for (Long id : registrationIds) {
            if (!seen.contains(id)) {
                notFound.add(id);
            }
        }

        int updated = 0;
        if (!toUpdate.isEmpty()) {
            updated = registrationMapper.updateStatusToCheckedIn(toUpdate);
        }

        com.heypickler.vo.BulkCheckInResult out = new com.heypickler.vo.BulkCheckInResult();
        out.setEventId(eventId);
        out.setRequested(registrationIds.size());
        out.setUpdated(updated);
        com.heypickler.vo.BulkCheckInResult.Skipped sk = new com.heypickler.vo.BulkCheckInResult.Skipped();
        sk.setNotFound(notFound);
        sk.setWithdrawn(withdrawn);
        out.setSkipped(sk);
        out.setUpdatedRegistrationIds(toUpdate);
        return out;
    }
}
