package com.heypickler.controller.admin;

import com.heypickler.entity.Team;
import com.heypickler.service.TeamService;
import com.heypickler.vo.TeamInviteVO;
import com.heypickler.vo.TeamVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Loop-v10 — moves AdminTeamController from 0% to ~80%+.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminTeamControllerTest {

    @Mock private TeamService teamService;
    @InjectMocks private AdminTeamController controller;

    @Test
    void listByEvent_delegates() {
        doReturn(List.of()).when(teamService).listByEventId(7L);
        assertEquals(0, controller.listByEvent(7L).getData().size());
    }

    @Test
    void create_nullRequest_throws() {
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.create(7L, null));
    }

    @Test
    void create_missingCaptain_throws() {
        AdminTeamController.CreateTeamRequest req = new AdminTeamController.CreateTeamRequest();
        req.setPartnerUserId(2L);
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.create(7L, req));
    }

    @Test
    void create_missingPartner_throws() {
        AdminTeamController.CreateTeamRequest req = new AdminTeamController.CreateTeamRequest();
        req.setCaptainUserId(1L);
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.create(7L, req));
    }

    @Test
    void create_fullRequest_delegatesToService() {
        AdminTeamController.CreateTeamRequest req = new AdminTeamController.CreateTeamRequest();
        req.setCaptainUserId(1L);
        req.setPartnerUserId(2L);
        Team t = new Team();
        doReturn(t).when(teamService).createTeam(anyLong(), anyLong(), anyLong());
        doReturn(new TeamVO()).when(teamService).toVO(t);
        controller.create(7L, req);
    }

    @Test
    void confirm_nullRequest_throws() {
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.confirm(7L, null));
    }

    @Test
    void confirm_missingUserId_throws() {
        AdminTeamController.ConfirmTeamRequest req = new AdminTeamController.ConfirmTeamRequest();
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.confirm(7L, req));
    }

    @Test
    void confirm_validRequest_delegatesToService() {
        AdminTeamController.ConfirmTeamRequest req = new AdminTeamController.ConfirmTeamRequest();
        req.setUserId(2L);
        Team t = new Team();
        doReturn(t).when(teamService).confirmTeam(anyLong(), anyLong());
        doReturn(new TeamVO()).when(teamService).toVO(t);
        controller.confirm(7L, req);
    }

    @Test
    void decline_nullRequest_throws() {
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.decline(7L, null));
    }

    @Test
    void decline_validRequest_delegatesToService() {
        AdminTeamController.ConfirmTeamRequest req = new AdminTeamController.ConfirmTeamRequest();
        req.setUserId(2L);
        controller.decline(7L, req);
    }

    @Test
    void dissolve_delegatesToService() {
        controller.dissolve(7L);
    }

    @Test
    void invite_returnsVO() {
        doReturn(new TeamInviteVO()).when(teamService).buildInvite(7L);
        assertEquals(TeamInviteVO.class, controller.invite(7L).getData().getClass());
    }

    @Test
    void invite_nullVO_throws() {
        when(teamService.buildInvite(7L)).thenReturn(null);
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> controller.invite(7L));
    }
}
