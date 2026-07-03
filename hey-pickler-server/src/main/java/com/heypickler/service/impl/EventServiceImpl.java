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
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.EventService;
import com.heypickler.service.TeamService;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventParticipantVO;
import com.heypickler.vo.EventResultVO;
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
        Event event = requireEvent(eventId);

        if (Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED, "赛事已分组锁定，如需调整请先解锁");
        }
        if ("IN_PROGRESS".equals(event.getStatus()) || "COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事已开始比赛，无法报名");
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
            throw new BizException(ErrorCode.PARAM_ERROR, "单打赛事不能携带搭档或队伍");
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
            throw new BizException(ErrorCode.PARAM_ERROR, "双打/混打需指定搭档(partnerUserId)或确认队伍(teamId)");
        }
        maybeTransitionFull(eventId, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRegistration(Long userId, Long eventId) {
        Event event = requireEvent(eventId);

        if (Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED, "赛事已分组锁定，如需调整请先解锁");
        }
        if ("IN_PROGRESS".equals(event.getStatus()) || "COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事已开始比赛，无法取消报名");
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
    public PageResult<EventVO> adminListEvents(String type, String status, String keyword, String location, String startTime, String endTime, int page, int size) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Event::getTitle, keyword);
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

        wrapper.orderByDesc(Event::getEventTime);

        Page<Event> eventPage = eventMapper.selectPage(new Page<>(page, size), wrapper);

        List<EventVO> voList = convertAllToVO(eventPage.getRecords());

        return PageResult.of(eventPage.getTotal(), (int) eventPage.getCurrent(), size, voList);
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

        if ("CHECKED_IN".equals(status)) {
            if (!"REGISTERED".equals(oldStatus)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "只有已报名状态可以签到");
            }
            registration.setStatus("CHECKED_IN");
            registrationMapper.updateById(registration);
        } else if ("WITHDRAWN".equals(status)) {
            if ("WITHDRAWN".equals(oldStatus)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "该报名已取消");
            }
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
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的状态变更");
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
                    throw new BizException(ErrorCode.INSUFFICIENT_POINTS,
                            String.format("积分不足，需 %d 分，当前 %d 分", event.getMinPoints(), userPoints));
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

    /** Atomic capacity-guarded slot reservation. */
    private void reserveSlot(Long eventId, Event event) {
        int rows = eventMapper.update(null,
                new LambdaUpdateWrapper<Event>()
                        .eq(Event::getId, eventId)
                        .lt(Event::getCurrentParticipants, event.getMaxParticipants())
                        .setSql("current_participants = current_participants + 1"));
        if (rows == 0) {
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
}
