package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/teams")
@RequiredArgsConstructor
@Tag(name = "小程序-队伍")
public class AppTeamController {

    private final TeamService teamService;

    @PostMapping("/{teamId}/decline")
    @Operation(summary = "队友拒绝入队邀请（删队 + 撤队长报名）")
    public Result<Void> decline(
            @PathVariable Long teamId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        teamService.decline(teamId, userId);
        return Result.ok();
    }
}
