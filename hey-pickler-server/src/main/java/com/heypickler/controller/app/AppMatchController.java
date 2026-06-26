package com.heypickler.controller.app;

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
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppMatchController {

    private final MatchService matchService;

    @GetMapping("/events/{eventId}/matches")
    public Result<List<MatchVO>> listMyMatches(
            @PathVariable Long eventId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(matchService.listMyMatches(eventId, userId));
    }

    @GetMapping("/events/{eventId}/standings")
    public Result<List<List<StandingVO>>> standings(@PathVariable Long eventId) {
        return Result.ok(matchService.standings(eventId));
    }

    @PostMapping("/matches/{matchId}/score")
    public Result<Void> submitScore(
            @PathVariable Long matchId,
            HttpServletRequest request,
            @Valid @RequestBody ScoreSubmissionRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        List<Match.GameScore> games = req.getGames().stream()
                .map(g -> {
                    Match.GameScore m = new Match.GameScore();
                    m.setGame(g.getGame());
                    m.setA(g.getA());
                    m.setB(g.getB());
                    return m;
                }).collect(java.util.stream.Collectors.toList());
        matchService.submitScore(matchId, userId, games, false);
        return Result.ok();
    }
}