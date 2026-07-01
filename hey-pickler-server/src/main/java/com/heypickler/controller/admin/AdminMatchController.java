package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.ScoreSubmissionRequest;
import com.heypickler.entity.Match;
import com.heypickler.service.MatchService;
import com.heypickler.vo.MatchVO;
import com.heypickler.vo.StandingVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMatchController {

    private final MatchService matchService;

    @PostMapping("/events/{eventId}/matches/generate")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<MatchVO>> generate(@PathVariable Long eventId) {
        List<Match> matches = matchService.generate(eventId);
        return Result.ok(matches.stream()
                .map(matchService::toVO)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/events/{eventId}/matches")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<List<MatchVO>>> listEventMatches(@PathVariable Long eventId) {
        return Result.ok(matchService.listEventMatches(eventId));
    }

    @GetMapping("/events/{eventId}/standings")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<List<StandingVO>>> standings(@PathVariable Long eventId) {
        return Result.ok(matchService.standings(eventId));
    }

    @PostMapping("/matches/{matchId}/reset")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> reset(@PathVariable Long matchId) {
        matchService.reset(matchId);
        return Result.ok();
    }

    @PostMapping("/matches/{matchId}/score")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> adminScore(
            @PathVariable Long matchId,
            @Valid @RequestBody ScoreSubmissionRequest req) {
        List<Match.GameScore> games = req.getGames().stream()
                .map(g -> {
                    Match.GameScore m = new Match.GameScore();
                    m.setGame(g.getGame());
                    m.setA(g.getA());
                    m.setB(g.getB());
                    return m;
                }).collect(java.util.stream.Collectors.toList());
        matchService.submitScore(matchId, null, games, true);
        return Result.ok();
    }

    @PostMapping("/events/{eventId}/complete")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> complete(@PathVariable Long eventId) {
        matchService.complete(eventId);
        return Result.ok();
    }
}