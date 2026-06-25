package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.entity.Team;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.TeamServiceImpl;
import com.heypickler.vo.TeamVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @InjectMocks TeamServiceImpl teamService;
    @Mock TeamMapper teamMapper;
    @Mock RegistrationMapper registrationMapper;
    @Mock UserMapper userMapper;
    @Mock EventMapper eventMapper;

    // ---------- createTeam ----------

    @Test
    void createTeam_captainInvites_createsPendingTeamAndCaptainReg() {
        // Neither captain (1) nor partner (2) is in any team of event 10.
        when(teamMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(eventMapper.selectById(10L)).thenReturn(eventWithFormat("DOUBLES"));
        when(teamMapper.insert(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });
        when(registrationMapper.insert(any(Registration.class))).thenAnswer(inv -> {
            ((Registration) inv.getArgument(0)).setId(500L);
            return 1;
        });

        Team result = teamService.createTeam(10L, 1L, 2L);

        ArgumentCaptor<Team> teamCap = ArgumentCaptor.forClass(Team.class);
        verify(teamMapper).insert(teamCap.capture());
        Team inserted = teamCap.getValue();
        assertEquals(10L, inserted.getEventId());
        assertEquals(1L, inserted.getMember1UserId());   // captain
        assertEquals(2L, inserted.getMember2UserId());   // partner
        assertEquals("PENDING", inserted.getStatus());

        ArgumentCaptor<Registration> regCap = ArgumentCaptor.forClass(Registration.class);
        verify(registrationMapper).insert(regCap.capture());
        Registration reg = regCap.getValue();
        assertEquals(1L, reg.getUserId());
        assertEquals(10L, reg.getEventId());
        assertEquals(99L, reg.getTeamId());
        assertEquals("REGISTERED", reg.getStatus());
        assertEquals("DOUBLES", reg.getMatchType()); // forced to event.format at creation

        assertEquals(99L, result.getId());
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void createTeam_captainAlreadyInAnotherTeam_throws() {
        Team existing = new Team();
        existing.setId(50L);
        existing.setEventId(10L);
        existing.setMember1UserId(1L);
        existing.setMember2UserId(9L);
        when(teamMapper.selectList(any())).thenReturn(List.of(existing));

        BizException ex = assertThrows(BizException.class,
                () -> teamService.createTeam(10L, 1L, 2L));
        assertEquals(ErrorCode.DUPLICATE_REGISTRATION.getCode(), ex.getCode());
        verify(teamMapper, never()).insert(any());
    }

    @Test
    void createTeam_partnerAlreadyInAnotherTeam_throws() {
        Team existing = new Team();
        existing.setId(50L);
        existing.setEventId(10L);
        existing.setMember1UserId(8L);
        existing.setMember2UserId(2L);
        when(teamMapper.selectList(any())).thenReturn(List.of(existing));

        BizException ex = assertThrows(BizException.class,
                () -> teamService.createTeam(10L, 1L, 2L));
        assertEquals(ErrorCode.DUPLICATE_REGISTRATION.getCode(), ex.getCode());
        verify(teamMapper, never()).insert(any());
    }

    @Test
    void createTeam_selfPartner_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> teamService.createTeam(10L, 1L, 1L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(teamMapper, never()).insert(any());
    }

    // ---------- confirmTeam ----------

    @Test
    void confirmTeam_partnerConfirms_pendingToConfirmedAndPartnerRegCreated() {
        Team pending = new Team();
        pending.setId(99L);
        pending.setEventId(10L);
        pending.setMember1UserId(1L);   // captain
        pending.setMember2UserId(2L);   // invited partner
        pending.setStatus("PENDING");
        when(teamMapper.selectById(99L)).thenReturn(pending);
        when(teamMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(eventMapper.selectById(10L)).thenReturn(eventWithFormat("DOUBLES"));
        when(registrationMapper.insert(any(Registration.class))).thenAnswer(inv -> {
            ((Registration) inv.getArgument(0)).setId(501L);
            return 1;
        });

        Team result = teamService.confirmTeam(99L, 2L);

        ArgumentCaptor<Team> teamCap = ArgumentCaptor.forClass(Team.class);
        verify(teamMapper).updateById(teamCap.capture());
        assertEquals("CONFIRMED", teamCap.getValue().getStatus());

        ArgumentCaptor<Registration> regCap = ArgumentCaptor.forClass(Registration.class);
        verify(registrationMapper).insert(regCap.capture());
        Registration reg = regCap.getValue();
        assertEquals(2L, reg.getUserId());
        assertEquals(10L, reg.getEventId());
        assertEquals(99L, reg.getTeamId());
        assertEquals("REGISTERED", reg.getStatus());
        assertEquals("DOUBLES", reg.getMatchType());

        assertEquals("CONFIRMED", result.getStatus());
    }

    @Test
    void confirmTeam_nonInvitee_throws() {
        Team pending = newTeam(99L, 10L, 1L, 2L, "PENDING");
        when(teamMapper.selectById(99L)).thenReturn(pending);

        BizException ex = assertThrows(BizException.class,
                () -> teamService.confirmTeam(99L, 999L));   // not the invited partner
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(teamMapper, never()).updateById(any());
    }

    @Test
    void confirmTeam_alreadyConfirmed_throws() {
        Team confirmed = newTeam(99L, 10L, 1L, 2L, "CONFIRMED");
        when(teamMapper.selectById(99L)).thenReturn(confirmed);

        BizException ex = assertThrows(BizException.class,
                () -> teamService.confirmTeam(99L, 2L));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
        verify(teamMapper, never()).updateById(any());
    }

    @Test
    void confirmTeam_partnerAlreadyInAnotherTeam_throws() {
        Team pending = newTeam(99L, 10L, 1L, 2L, "PENDING");
        when(teamMapper.selectById(99L)).thenReturn(pending);
        Team other = newTeam(80L, 10L, 7L, 2L, "CONFIRMED");
        when(teamMapper.selectList(any())).thenReturn(List.of(other));

        BizException ex = assertThrows(BizException.class,
                () -> teamService.confirmTeam(99L, 2L));
        assertEquals(ErrorCode.DUPLICATE_REGISTRATION.getCode(), ex.getCode());
        verify(teamMapper, never()).updateById(any());
    }

    @Test
    void confirmTeam_teamNotFound_throws() {
        when(teamMapper.selectById(99L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> teamService.confirmTeam(99L, 2L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- dissolve ----------

    @Test
    void dissolve_confirmedTeam_deletesTeamAndWithdrawsBothRegs() {
        Team confirmed = newTeam(99L, 10L, 1L, 2L, "CONFIRMED");
        when(teamMapper.selectById(99L)).thenReturn(confirmed);

        Registration r1 = new Registration();
        r1.setId(501L);
        r1.setUserId(1L);
        r1.setTeamId(99L);
        r1.setStatus("REGISTERED");
        Registration r2 = new Registration();
        r2.setId(502L);
        r2.setUserId(2L);
        r2.setTeamId(99L);
        r2.setStatus("REGISTERED");
        when(registrationMapper.selectList(any())).thenReturn(List.of(r1, r2));

        teamService.dissolve(99L);

        verify(teamMapper).deleteById(99L);
        // Both registrations updated to WITHDRAWN
        ArgumentCaptor<Registration> regCap = ArgumentCaptor.forClass(Registration.class);
        verify(registrationMapper, times(2)).updateById(regCap.capture());
        assertEquals("WITHDRAWN", regCap.getAllValues().get(0).getStatus());
        assertEquals("WITHDRAWN", regCap.getAllValues().get(1).getStatus());
    }

    @Test
    void dissolve_teamNotFound_throws() {
        when(teamMapper.selectById(99L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> teamService.dissolve(99L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- decline ----------

    @Test
    void decline_partnerRejects_deletesTeamAndWithdrawsCaptainReg() {
        Team pending = newTeam(99L, 10L, 1L, 2L, "PENDING");
        when(teamMapper.selectById(99L)).thenReturn(pending);

        Registration captainReg = new Registration();
        captainReg.setId(500L);
        captainReg.setUserId(1L);
        captainReg.setTeamId(99L);
        captainReg.setStatus("REGISTERED");
        when(registrationMapper.selectList(any())).thenReturn(List.of(captainReg));

        teamService.decline(99L, 2L);

        verify(teamMapper).deleteById(99L);
        ArgumentCaptor<Registration> regCap = ArgumentCaptor.forClass(Registration.class);
        verify(registrationMapper).updateById(regCap.capture());
        assertEquals("WITHDRAWN", regCap.getValue().getStatus());
        assertEquals(1L, regCap.getValue().getUserId());
    }

    @Test
    void decline_nonInvitee_throws() {
        Team pending = newTeam(99L, 10L, 1L, 2L, "PENDING");
        when(teamMapper.selectById(99L)).thenReturn(pending);

        BizException ex = assertThrows(BizException.class,
                () -> teamService.decline(99L, 999L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(teamMapper, never()).deleteById(anyLong());
    }

    // ---------- getMyTeam ----------

    @Test
    void getMyTeam_returnsTeamWhenUserIsMember() {
        Team t = newTeam(99L, 10L, 1L, 2L, "CONFIRMED");
        when(teamMapper.selectOne(any())).thenReturn(t);

        Team result = teamService.getMyTeam(10L, 2L);
        assertNotNull(result);
        assertEquals(99L, result.getId());
    }

    @Test
    void getMyTeam_returnsNullWhenNotMember() {
        when(teamMapper.selectOne(any())).thenReturn(null);
        assertNull(teamService.getMyTeam(10L, 999L));
    }

    // ---------- toVO ----------

    @Test
    void toVO_populatesMemberNames() {
        Team t = newTeam(99L, 10L, 1L, 2L, "CONFIRMED");
        t.setName("Alpha Squad");
        User u1 = new User(); u1.setId(1L); u1.setNickname("Alice");
        User u2 = new User(); u2.setId(2L); u2.setNickname("Bob");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u1, u2));

        TeamVO vo = teamService.toVO(t);

        assertEquals(99L, vo.getId());
        assertEquals(10L, vo.getEventId());
        assertEquals(1L, vo.getMember1UserId());
        assertEquals(2L, vo.getMember2UserId());
        assertEquals("Alice", vo.getMember1Name());
        assertEquals("Bob", vo.getMember2Name());
        assertEquals("Alpha Squad", vo.getName());
        assertEquals("CONFIRMED", vo.getStatus());
    }

    // ---------- helpers ----------

    private Team newTeam(Long id, Long eventId, Long m1, Long m2, String status) {
        Team t = new Team();
        t.setId(id);
        t.setEventId(eventId);
        t.setMember1UserId(m1);
        t.setMember2UserId(m2);
        t.setStatus(status);
        return t;
    }

    private Event eventWithFormat(String format) {
        Event e = new Event();
        e.setId(10L);
        e.setFormat(format);
        return e;
    }
}
