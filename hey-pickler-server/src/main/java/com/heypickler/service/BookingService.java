package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.vo.BookingAdminVO;
import com.heypickler.vo.BookingCreateResultVO;
import com.heypickler.vo.BookingVO;
import jakarta.servlet.http.HttpServletRequest;

public interface BookingService {
    BookingCreateResultVO create(HttpServletRequest req, BookingCreateRequest body);
    void cancelMine(HttpServletRequest req, Long bookingId);
    PageResult<BookingVO> listMine(HttpServletRequest req, String group, int page, int size);
    PageResult<BookingAdminVO> listAdmin(BookingQueryRequest q);
    BookingAdminVO getAdmin(Long id);
    void complete(Long id);
    void markNoShow(Long id);
    void forceCancel(Long id, BookingForceCancelRequest body);
}
