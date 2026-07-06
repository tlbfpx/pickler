package com.heypickler.common.aspect;

import com.heypickler.service.RankingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Loop-v4 D21 — covers the new {@link RankingWarmupRunner} which was 0%
 * covered after the loop-v2 merge. Also covers D22's per-type timeout
 * path: when a refresh throws, runner still logs and continues.
 */
@ExtendWith(MockitoExtension.class)
class RankingWarmupRunnerTest {

    @Mock
    RankingService rankingService;

    @Test
    void warmsBothTypesOnHappyPath() {
        RankingWarmupRunner runner = new RankingWarmupRunner(rankingService);
        runner.run(mockArgs());
        verify(rankingService).refreshRankings("STAR");
        verify(rankingService).refreshRankings("PARTY");
    }

    @Test
    void continuesWhenOneTypeThrows_noFailingBoot() {
        doThrow(new RuntimeException("missing CURRENT season"))
                .when(rankingService).refreshRankings("STAR");

        RankingWarmupRunner runner = new RankingWarmupRunner(rankingService);
        // Should not throw — boot must succeed even if a refresh fails.
        runner.run(mockArgs());

        verify(rankingService).refreshRankings("STAR");   // first call throws
        verify(rankingService, times(1)).refreshRankings("PARTY");  // still attempted
    }

    @Test
    void freshRunner_doesNotCallRepositoryDuringConstruction() {
        // Construction is parameter-only; this guards against accidental
        // eager-init side-effects in future refactors.
        new RankingWarmupRunner(rankingService);
        verify(rankingService, never()).refreshRankings(anyString());
    }

    private static ApplicationArguments mockArgs() {
        return org.mockito.Mockito.mock(ApplicationArguments.class);
    }
}
