package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
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
    }

    @Override
    public PageResult<EventVO> adminListEvents(String type, String status, int page, int size) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();

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

        Event updateEntity = new Event();
        BeanUtils.copyProperties(request, updateEntity);
        updateEntity.setId(eventId);
        eventMapper.updateById(updateEntity);
    }

    @Override
    public void deleteEvent(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        Event updateEvent = new Event();
        updateEvent.setId(eventId);
        updateEvent.setDeletedAt(LocalDateTime.now());
        eventMapper.updateById(updateEvent);
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
