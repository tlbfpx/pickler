package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventParticipantVO;
import com.heypickler.vo.EventVO;

import java.util.List;

public interface EventService {
    PageResult<EventVO> listEvents(String type, String status, int page, int size);

    EventDetailVO getEventDetail(Long eventId, Long userId);

    void register(Long userId, Long eventId, RegisterRequest request);

    void cancelRegistration(Long userId, Long eventId);

    PageResult<EventVO> adminListEvents(String type, String status, int page, int size);

    Long createEvent(EventCreateRequest request, Long adminId);

    void updateEvent(Long eventId, EventUpdateRequest request);

    void deleteEvent(Long eventId);

    List<EventParticipantVO> getParticipants(Long eventId);
}
