package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.VenueBusinessHourRequest;
import com.heypickler.dto.admin.VenueContactRequest;
import com.heypickler.dto.admin.VenueCreateRequest;
import com.heypickler.dto.admin.VenueQueryRequest;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;

public interface VenueService {

    PageResult<VenueVO> adminList(VenueQueryRequest req);

    VenueDetailVO adminGet(Long id);

    Long create(VenueCreateRequest req);

    void update(Long id, VenueCreateRequest req);

    void delete(Long id);

    void replaceBusinessHours(Long id, VenueBusinessHourRequest req);

    // contacts
    Long addContact(Long venueId, VenueContactRequest req);

    void updateContact(Long contactId, VenueContactRequest req);

    void deleteContact(Long contactId);

    // app
    PageResult<VenueVO> appList(VenueQueryRequest req);

    VenueDetailVO appGet(Long id);
}
