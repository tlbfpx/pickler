package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventParticipantVO;
import com.heypickler.vo.EventResultVO;
import com.heypickler.vo.EventSummaryVO;
import com.heypickler.vo.EventVO;
import com.heypickler.vo.RegistrationVO;

import java.util.List;

public interface EventService {
    PageResult<EventVO> listEvents(String type, String status, int page, int size);

    EventDetailVO getEventDetail(Long eventId, Long userId);

    void register(Long userId, Long eventId, RegisterRequest request);

    void cancelRegistration(Long userId, Long eventId);

    PageResult<EventVO> adminListEvents(String type, String status, String keyword, String location, String startTime, String endTime, int page, int size);

    EventVO getEventDetail(Long eventId);

    Long createEvent(EventCreateRequest request, Long adminId);

    void updateEvent(Long eventId, EventUpdateRequest request);

    void deleteEvent(Long eventId);

    List<EventParticipantVO> getParticipants(Long eventId);

    List<EventResultVO> getEventResults(Long eventId);

    PageResult<RegistrationVO> getRegistrations(Long eventId, String status, String matchType, int page, int size);

    void updateRegistrationStatus(Long eventId, Long registrationId, String status);

    /**
     * Loop-v13 — operational summary aggregating registrations / teams /
     * matches / fees for an event. Read-only, no side effects.
     * Throws {@code BizException(NOT_FOUND)} when eventId is missing or
     * soft-deleted.
     */
    EventSummaryVO getEventSummary(Long eventId);
}
