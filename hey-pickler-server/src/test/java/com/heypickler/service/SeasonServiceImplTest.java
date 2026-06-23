package com.heypickler.service;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.SeasonCreateRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.SeasonServiceImpl;
import com.heypickler.vo.RankingVO;
import com.heypickler.vo.SeasonVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeasonServiceImplTest {

    @InjectMocks
    SeasonServiceImpl service;

    @Mock
    SeasonMapper seasonMapper;
    @Mock
    RankingMapper rankingMapper;
    @Mock
    UserMapper userMapper;

    @Test
    void activate_archivesOldCurrent_andSetsNew_forSameType() {
        // 目标赛季（同 type=STAR，原 ARCHIVED）
        Season target = new Season();
        target.setId(2L);
        target.setType("STAR");
        target.setCode("2026-Q3");
        target.setStatus("ARCHIVED");
        when(seasonMapper.selectById(2L)).thenReturn(target);
        // 同 type 当前 CURRENT
        Season current = new Season();
        current.setId(1L);
        current.setType("STAR");
        current.setStatus("CURRENT");
        when(seasonMapper.selectList(any())).thenReturn(List.of(current));
        when(seasonMapper.updateById(any())).thenReturn(1);

        service.activate(2L);

        // 旧 CURRENT(1) → ARCHIVED；target(2) → CURRENT
        ArgumentCaptor<Season> cap = ArgumentCaptor.forClass(Season.class);
        verify(seasonMapper, atLeast(2)).updateById(cap.capture());
        List<Season> updated = cap.getAllValues();
        assertTrue(updated.stream().anyMatch(s -> s.getId().equals(1L) && "ARCHIVED".equals(s.getStatus())));
        assertTrue(updated.stream().anyMatch(s -> s.getId().equals(2L) && "CURRENT".equals(s.getStatus())));
    }

    @Test
    void activate_rejectsIfTargetNotFound() {
        when(seasonMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.activate(99L));
    }

    @Test
    void create_insertsNewSeasonWithDefaultArchivedStatus() {
        SeasonCreateRequest req = new SeasonCreateRequest();
        req.setType("STAR");
        req.setCode("2026-Q4");
        req.setName("2026 第四季");
        req.setStartDate(LocalDate.of(2026, 10, 1));
        req.setEndDate(LocalDate.of(2026, 12, 31));

        when(seasonMapper.insert(any(Season.class))).thenAnswer(inv -> {
            Season s = inv.getArgument(0);
            s.setId(10L);
            return 1;
        });

        SeasonVO vo = service.create(req);

        assertEquals(10L, vo.getId());
        assertEquals("ARCHIVED", vo.getStatus());
        ArgumentCaptor<Season> cap = ArgumentCaptor.forClass(Season.class);
        verify(seasonMapper).insert(cap.capture());
        Season inserted = cap.getValue();
        assertEquals("STAR", inserted.getType());
        assertEquals("2026-Q4", inserted.getCode());
        assertEquals("ARCHIVED", inserted.getStatus());
    }

    @Test
    void getRankings_readsDbBySeasonCode_noCache() {
        Season season = new Season();
        season.setId(5L);
        season.setType("PARTY");
        season.setCode("2026-P1");
        when(seasonMapper.selectById(5L)).thenReturn(season);

        Ranking r = new Ranking();
        r.setUserId(7L);
        r.setType("PARTY");
        r.setSeason("2026-P1");
        r.setRank(1);
        r.setPoints(100);
        r.setTier("GOLD");
        r.setChange(0);
        when(rankingMapper.selectList(any())).thenReturn(List.of(r));

        User u = new User();
        u.setId(7L);
        u.setNickname("alice");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u));

        RankingQuery query = new RankingQuery();
        query.setType("PARTY");
        query.setPage(1);
        query.setSize(20);

        PageResult<RankingVO> page = service.getRankings(5L, query);

        assertEquals(1, page.getList().size());
        assertEquals("alice", page.getList().get(0).getNickname());
        // 走 DB：未经过滤条件 tier/season 校验只需保证按 type+season 查询
        verify(rankingMapper).selectList(any());
    }
}
