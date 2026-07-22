package com.heypickler.service;

import com.heypickler.vo.SlotVO;

import java.time.LocalDate;
import java.util.List;

public interface SlotService {

    List<SlotVO> getCourtSlots(Long courtId, LocalDate date);
}
