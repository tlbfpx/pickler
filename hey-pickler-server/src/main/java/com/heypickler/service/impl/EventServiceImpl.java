package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.util.StatusTransitionValidator;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.EventService;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventParticipantVO;
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

        List<EventVO> voList = eventPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

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
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, Long eventId, RegisterRequest request) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        if (!"OPEN".equals(event.getStatus())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }

        if (LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }

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

        Registration existingRegistration = registrationMapper.selectOne(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .eq(Registration::getEventId, eventId)
        );

        if (existingRegistration != null && !"WITHDRAWN".equals(existingRegistration.getStatus())) {
            throw new BizException(ErrorCode.DUPLICATE_REGISTRATION);
        }

        String matchType = request.getMatchType();
        if (("DOUBLES".equals(matchType) || "MIXED".equals(matchType)) && request.getPartnerId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "双打和混双需要指定搭档");
        }

        int rows = eventMapper.update(null,
                new LambdaUpdateWrapper<Event>()
                        .eq(Event::getId, eventId)
                        .lt(Event::getCurrentParticipants, event.getMaxParticipants())
                        .setSql("current_participants = current_participants + 1"));

        if (rows == 0) {
            throw new BizException(ErrorCode.REGISTRATION_FULL);
        }

        Registration registration = new Registration();
        registration.setUserId(userId);
        registration.setEventId(eventId);
        registration.setMatchType(matchType);
        registration.setPartnerId(request.getPartnerId());
        registration.setStatus("REGISTERED");
        registrationMapper.insert(registration);

        // Auto transition: OPEN → FULL when capacity reached
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRegistration(Long userId, Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        if (LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }

        Registration registration = registrationMapper.selectOne(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .eq(Registration::getEventId, eventId)
                        .eq(Registration::getStatus, "REGISTERED")
        );

        if (registration == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到有效的报名记录");
        }

        registration.setStatus("WITHDRAWN");
        registrationMapper.updateById(registration);

        eventMapper.update(null,
                new LambdaUpdateWrapper<Event>()
                        .eq(Event::getId, eventId)
                        .gt(Event::getCurrentParticipants, 0)
                        .setSql("current_participants = current_participants - 1"));

        // Auto transition: FULL → OPEN when participants drop below capacity
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

        List<EventVO> voList = eventPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

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

    private EventVO convertToVO(Event event) {
        EventVO vo = new EventVO();
        BeanUtils.copyProperties(event, vo);
        return vo;
    }

    private EventDetailVO convertToDetailVO(Event event) {
        EventDetailVO vo = new EventDetailVO();
        BeanUtils.copyProperties(event, vo);
        return vo;
    }
}
