package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import com.heypickler.entity.Team;
import com.heypickler.service.TeamService;
import com.heypickler.vo.TeamVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin team management — let operators create / confirm / decline / dissolve
 * doubles & mixed teams on behalf of users. Mirrors the app-side flow but
 * acts as a privileged user: any user can be the captain or the invited
 * partner, and admin can confirm/decline a PENDING team on the partner's
 * behalf by passing the partner's userId explicitly.
 *
 * Business rules (delegated to TeamServiceImpl where possible):
 *  - event must be OPEN/FULL, not groupingLocked, not past deadline
 *  - captain != partner
 *  - neither user already in a team for this event
 *  - PENDING + non-invitee confirm = FORBIDDEN
 *  - CONFIRMED team dissolution = delete team + withdraw both registrations
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "管理后台-队伍管理")
public class AdminTeamController {

    private final TeamService teamService;

    @GetMapping("/events/{eventId}/teams")
    @Operation(summary = "列出赛事全部队伍（含 PENDING + CONFIRMED）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<List<TeamVO>> listByEvent(@PathVariable Long eventId) {
        return Result.ok(teamService.listByEventId(eventId));
    }

    @PostMapping("/events/{eventId}/teams")
    @Operation(summary = "管理员代用户建队（captain 邀请 partner）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<TeamVO> create(
            @PathVariable Long eventId,
            @RequestBody CreateTeamRequest req) {
        if (req == null || req.getCaptainUserId() == null || req.getPartnerUserId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "队长与搭档不能为空");
        }
        Team team = teamService.createTeam(eventId, req.getCaptainUserId(), req.getPartnerUserId());
        return Result.ok(teamService.toVO(team));
    }

    @PostMapping("/teams/{teamId}/confirm")
    @Operation(summary = "管理员代受邀队友确认入队（body 传 partner 的 userId）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<TeamVO> confirm(
            @PathVariable Long teamId,
            @RequestBody ConfirmTeamRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userId 不能为空");
        }
        Team team = teamService.confirmTeam(teamId, req.getUserId());
        return Result.ok(teamService.toVO(team));
    }

    @PostMapping("/teams/{teamId}/decline")
    @Operation(summary = "管理员代受邀队友拒绝邀请（body 传 partner 的 userId）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> decline(
            @PathVariable Long teamId,
            @RequestBody ConfirmTeamRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userId 不能为空");
        }
        teamService.decline(teamId, req.getUserId());
        return Result.ok();
    }

    @DeleteMapping("/teams/{teamId}")
    @Operation(summary = "解散队伍（CONFIRMED 删队+撤双报名；PENDING 走 decline 路径）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> dissolve(@PathVariable Long teamId) {
        teamService.dissolve(teamId);
        return Result.ok();
    }

    // ---------- request bodies ----------

    @Data
    public static class CreateTeamRequest {
        /** captain (member1) user id */
        @NotNull private Long captainUserId;
        /** invited partner (member2) user id */
        @NotNull private Long partnerUserId;
        /** optional display name */
        private String name;
    }

    @Data
    public static class ConfirmTeamRequest {
        /** the user id the admin is acting for (must equal team.member2UserId) */
        @NotNull private Long userId;
    }
}
