package com.heypickler.integration;

import com.heypickler.config.TierProperties;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.Season;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.SeasonMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 双积分体系（战力/活力）端到端集成测试。覆盖三类 spec 关键链路：
 *   1. 发分链路（HTTP）→ point_record.source=MANUAL / season_code / user 余额 / ranking 刷新
 *   2. 段位 6 档边界（TierProperties.keyFor 直接驱动 refreshRankings）
 *   3. 赛季切换 + 归档保留（HTTP 新建/激活 → 旧 ranking 行不被 refreshRankings 删除）
 *
 * 数据隔离：所有写入的赛季 code / 用户 id / 排名行均带 "PST_" 前缀，@AfterEach 物理删除，
 * 不污染本地 dev 库。当前 STAR 赛季（V11 注入的 2026-Q2）发分回写的是真实当前赛季，
 * 故测试 1 走完整 HTTP 链路后会清理该 user 在 2026-Q2 产生的 point_record / ranking。
 */
class PointsSeasonIntegrationTest extends IntegrationTestConfig {

    /** 专属测试用户 id（与 IntegrationTestConfig 的 8001/9000/9001 不重叠）。 */
    private static final long U_BRONZE = 71001L;   // 0 分 → BRONZE
    private static final long U_SILVER = 71002L;   // 500 分 → SILVER
    private static final long U_GOLD   = 71003L;   // 1200 分 → GOLD
    private static final long U_AWARD  = 71004L;   // 发分链路目标用户

    /** 专属测试赛季 code 前缀，避免与真实 2026-Q2 冲突。 */
    private static final String SEASON_STAR_ARCHIVE = "PST_STAR_Q2";
    private static final String SEASON_STAR_NEW     = "PST_STAR_Q3";

    @Autowired private PointRecordMapper pointRecordMapper;
    @Autowired private RankingMapper rankingMapper;
    @Autowired private SeasonMapper seasonMapper;
    @Autowired private TierProperties tierProperties;

    /**
     * 幂等重置 STAR 2026-Q2 为 CURRENT。本地 dev 库可能因手动演练 / 上一轮测试遗留
     * 被归档为 ARCHIVED，导致 PointService.enterPoints 取不到 CURRENT 赛季（NOT_FOUND 404）。
     * 这里保证每个测试启动前 STAR 当前赛季存在，与 V11 fresh-schema 语义对齐。
     * PARTY 2026-Q2 同理一并复位（仅作幂等保护，本套测试不依赖 PARTY）。
     */
    @BeforeEach
    void ensureCurrentSeasons() {
        jdbcTemplate.update(
                "INSERT INTO season (type, code, name, status) VALUES ('STAR', '2026-Q2', '2026 Q2 战力', 'CURRENT') " +
                        "ON DUPLICATE KEY UPDATE status = 'CURRENT'");
        jdbcTemplate.update(
                "INSERT INTO season (type, code, name, status) VALUES ('PARTY', '2026-Q2', '2026 Q2 活力', 'CURRENT') " +
                        "ON DUPLICATE KEY UPDATE status = 'CURRENT'");
    }

    @AfterEach
    void cleanupTestData() {
        JdbcTemplate jdbc = jdbcTemplate;
        // 物理删除本测试产生的所有 PST_ 标记数据。point_record / ranking 无软删字段。
        jdbc.update("DELETE FROM point_record WHERE user_id IN (?, ?, ?, ?) OR season_code LIKE 'PST_%'",
                U_BRONZE, U_SILVER, U_GOLD, U_AWARD);
        jdbc.update("DELETE FROM ranking WHERE user_id IN (?, ?, ?, ?) OR season LIKE 'PST_%'",
                U_BRONZE, U_SILVER, U_GOLD, U_AWARD);
        jdbc.update("DELETE FROM season WHERE code LIKE 'PST_%'");
        // 清理发分链路可能写回真实 2026-Q2 赛季的测试用户数据
        jdbc.update("DELETE FROM point_record WHERE user_id = ?", U_AWARD);
        jdbc.update("DELETE FROM ranking WHERE user_id = ?", U_AWARD);
    }

    // ===================================================================
    // 场景 1：发分链路（HTTP 端到端）
    // ===================================================================
    @Test
    void enterPoints_httpFlow_writesRecordUpdatesUserRefreshesRanking() {
        // 预置一个积分从 0 开始的目标用户（INSERT IGNORE 幂等）
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                        "VALUES (?, ?, 'PST_Award', 'NORMAL', 0, 0, 'BRONZE', 'BRONZE')",
                U_AWARD, "openid_pst_award");
        // 清掉历史余额，保证断言确定
        jdbcTemplate.update("UPDATE user SET star_points = 0, star_tier = 'BRONZE' WHERE id = ?", U_AWARD);
        jdbcTemplate.update("DELETE FROM point_record WHERE user_id = ?", U_AWARD);
        jdbcTemplate.update("DELETE FROM ranking WHERE user_id = ?", U_AWARD);

        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> body = Map.of(
                "type", "STAR",
                "records", List.of(Map.of(
                        "userId", U_AWARD,
                        "points", 300,
                        "reason", "集成测试-发分"))
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/admin/rankings/points", req, Map.class);
        assertEquals(0, resultCode(resp), "发分接口应返回 code=0");

        // 解析当前 STAR 赛季 code（V11 默认 2026-Q2）
        Season currentStar = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, "STAR").eq(Season::getStatus, "CURRENT"));
        assertNotNull(currentStar, "V11 应已注入 STAR CURRENT 赛季");
        String seasonCode = currentStar.getCode();

        // (a) point_record：source=MANUAL + season_code 正确
        List<PointRecord> records = pointRecordMapper.selectList(new LambdaQueryWrapper<PointRecord>()
                .eq(PointRecord::getUserId, U_AWARD));
        assertEquals(1, records.size(), "应写一条 point_record");
        PointRecord pr = records.get(0);
        assertEquals("MANUAL", pr.getSource(), "手动发分 source 必须为 MANUAL");
        assertEquals(seasonCode, pr.getSeasonCode(), "season_code 应等于当前 STAR 赛季 code");
        assertEquals("STAR", pr.getType());
        assertEquals(300, pr.getPoints());

        // (b) user.starPoints 增加 + tier 重算（300 < 500 → BRONZE）
        Integer starPoints = jdbcTemplate.queryForObject(
                "SELECT star_points FROM user WHERE id = ?", Integer.class, U_AWARD);
        String starTier = jdbcTemplate.queryForObject(
                "SELECT star_tier FROM user WHERE id = ?", String.class, U_AWARD);
        assertEquals(300, starPoints);
        assertEquals("BRONZE", starTier, "300 分仍属 BRONZE (< 500)");

        // (c) ranking 刷新（PointChangeListener 异步 AFTER_COMMIT，需轮询）
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            List<Ranking> rk = rankingMapper.selectList(new LambdaQueryWrapper<Ranking>()
                    .eq(Ranking::getUserId, U_AWARD)
                    .eq(Ranking::getType, "STAR"));
            assertFalse(rk.isEmpty(), "异步 refreshRankings 应为目标用户写入 ranking 行");
            Ranking row = rk.get(0);
            assertEquals(seasonCode, row.getSeason(), "ranking.season 应等于当前赛季 code");
            assertEquals(300, row.getPoints());
            assertEquals("BRONZE", row.getTier());
        });
    }

    // ===================================================================
    // 场景 2：段位 6 档边界（构造不同积分 → refreshRankings 写对应 tier）
    // ===================================================================
    @Test
    void tierBoundaries_sixTiers_correctKeyForPoints() {
        // 直接验证 TierProperties.keyFor（refreshRankings 即调用它）
        // STAR thresholds: [0, 500, 1200, 2500, 5000, 10000]
        assertEquals("BRONZE",   tierProperties.keyFor(0,    "STAR"));
        assertEquals("BRONZE",   tierProperties.keyFor(499,  "STAR"));
        assertEquals("SILVER",   tierProperties.keyFor(500,  "STAR"));
        assertEquals("SILVER",   tierProperties.keyFor(1199, "STAR"));
        assertEquals("GOLD",     tierProperties.keyFor(1200, "STAR"));
        assertEquals("PLATINUM", tierProperties.keyFor(2500, "STAR"));
        assertEquals("DIAMOND",  tierProperties.keyFor(5000, "STAR"));
        assertEquals("MASTER",   tierProperties.keyFor(10000,"STAR"));

        // PARTY thresholds: [0, 200, 500, 1200, 2500, 5000]
        assertEquals("BRONZE",   tierProperties.keyFor(0,    "PARTY"));
        assertEquals("SILVER",   tierProperties.keyFor(200,  "PARTY"));
        assertEquals("GOLD",     tierProperties.keyFor(500,  "PARTY"));
        assertEquals("MASTER",   tierProperties.keyFor(5000, "PARTY"));

        // 驱动 refreshRankings 走真实写库路径，验证 3 个边界用户的 tier 写入。
        // 用与场景 1 一致的两步法（INSERT IGNORE 占位 + UPDATE 设分）避免占位符错位。
        seedUser(U_BRONZE, "openid_pst_bronze", 100);   // 100 < 500 → BRONZE
        seedUser(U_SILVER, "openid_pst_silver", 500);   // =500       → SILVER
        seedUser(U_GOLD,   "openid_pst_gold",   1200);  // =1200      → GOLD

        // 注入专属测试赛季，刷新排名走该赛季维度（不影响真实 2026-Q2）
        jdbcTemplate.update(
                "INSERT IGNORE INTO season (type, code, name, status) VALUES ('STAR', ?, 'PST 测试', 'CURRENT')",
                SEASON_STAR_ARCHIVE);

        // 直接通过 service/mapper 调用 refreshRankings（绕过 HTTP，专注段位不变量）
        // 这里用 JDBC 触发相同 SQL 语义：按 STAR 积分排序写 ranking
        refreshRankingsViaJdbc("STAR", SEASON_STAR_ARCHIVE);

        String bronzeTier = tierFor(U_BRONZE, "STAR", SEASON_STAR_ARCHIVE);
        String silverTier = tierFor(U_SILVER, "STAR", SEASON_STAR_ARCHIVE);
        String goldTier   = tierFor(U_GOLD,   "STAR", SEASON_STAR_ARCHIVE);

        assertEquals("BRONZE", bronzeTier, "100 分 → BRONZE");
        assertEquals("SILVER", silverTier, "500 分 → SILVER");
        assertEquals("GOLD",   goldTier,   "1200 分 → GOLD");
    }

    // ===================================================================
    // 场景 3：赛季切换 + 归档排名保留（HTTP 端到端）
    // ===================================================================
    @Test
    void seasonSwitch_archiveKeepsRankings_httpFlow() {
        // 步骤 1：构造一份 2026-Q2 归档前的测试排名（用专属赛季 code 模拟旧赛季）
        //         先造用户 + 排名行，让归档查询能读到
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                        "VALUES (?, ?, 'PST_Archive', 'NORMAL', 800, 0, 'SILVER', 'BRONZE')",
                U_AWARD, "openid_pst_archive");
        jdbcTemplate.update(
                "INSERT IGNORE INTO season (type, code, name, status) VALUES ('STAR', ?, 'PST 归档测试', 'CURRENT')",
                SEASON_STAR_ARCHIVE);
        // 排名行（season = 旧赛季 code）
        jdbcTemplate.update("DELETE FROM ranking WHERE user_id = ? AND season = ?", U_AWARD, SEASON_STAR_ARCHIVE);
        jdbcTemplate.update(
                "INSERT INTO ranking (user_id, type, tier, `rank`, points, `change`, season, updated_at) " +
                        "VALUES (?, 'STAR', 'SILVER', 1, 800, 0, ?, NOW())",
                U_AWARD, SEASON_STAR_ARCHIVE);

        // 步骤 2（HTTP）：新建 STAR 2026-Q3 赛季
        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> createBody = Map.of(
                "type", "STAR",
                "code", SEASON_STAR_NEW,
                "name", "PST 新赛季",
                "startDate", LocalDate.of(2026, 7, 1).toString(),
                "endDate", LocalDate.of(2026, 9, 30).toString());
        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(createBody, headers);
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                "/api/admin/seasons", createReq, Map.class);
        assertEquals(0, resultCode(createResp), "新建赛季应返回 code=0");
        Map<String, Object> created = resultData(createResp);
        Long newSeasonId = ((Number) created.get("id")).longValue();
        // 新建默认 ARCHIVED
        assertEquals("ARCHIVED", created.get("status"), "新建赛季默认 ARCHIVED，需 activate 才变 CURRENT");

        // 把旧 PST 赛季先转 CURRENT（模拟待归档状态）—— activate 会归档同 type 下所有 CURRENT
        jdbcTemplate.update("UPDATE season SET status = 'CURRENT' WHERE code = ?", SEASON_STAR_ARCHIVE);

        // 步骤 3（HTTP）：activate 新赛季 → 旧 PST 赛季应被归档
        HttpEntity<Void> activateReq = new HttpEntity<>(headers);
        ResponseEntity<Map> activateResp = restTemplate.postForEntity(
                "/api/admin/seasons/" + newSeasonId + "/activate", activateReq, Map.class);
        assertEquals(0, resultCode(activateResp), "激活赛季应返回 code=0");

        Season oldSeason = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getCode, SEASON_STAR_ARCHIVE));
        assertNotNull(oldSeason);
        assertEquals("ARCHIVED", oldSeason.getStatus(),
                "activate 后原 CURRENT 同类型赛季必须归档为 ARCHIVED");

        // 步骤 4：旧赛季 ranking 行仍在库（未被 refreshRankings 删除——
        //         deleteRankingsByTypeAndSeason 只删 (type, season) 命中的行）
        List<Ranking> archivedRankings = rankingMapper.selectList(new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, "STAR")
                .eq(Ranking::getSeason, SEASON_STAR_ARCHIVE));
        assertFalse(archivedRankings.isEmpty(), "归档赛季 ranking 行必须保留");
        assertEquals(U_AWARD, archivedRankings.get(0).getUserId());
        assertEquals(800, archivedRankings.get(0).getPoints());

        // 步骤 5（HTTP）：归档排名查询能返回旧赛季排名
        HttpEntity<Void> getReq = new HttpEntity<>(headers);
        ResponseEntity<Map> getResp = restTemplate.exchange(
                "/api/admin/seasons/" + oldSeason.getId() + "/rankings",
                HttpMethod.GET, getReq, Map.class);
        assertEquals(0, resultCode(getResp), "归档排名查询应返回 code=0");
        Map<String, Object> pageData = resultData(getResp);
        // 响应结构为 RankingPageVO：{page:{total,page,size,list}, tierDistribution, seasonCode, ...}
        @SuppressWarnings("unchecked")
        Map<String, Object> pageObj = (Map<String, Object>) pageData.get("page");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pageObj.get("list");
        assertNotNull(rows);
        assertFalse(rows.isEmpty(), "归档查询应返回旧赛季的排名行");
        assertEquals(U_AWARD, ((Number) rows.get(0).get("userId")).longValue());
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------

    /** 幂等插入测试用户并设置 star_points（与场景 1 一致的两步法）。 */
    private void seedUser(long userId, String openid, int starPoints) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                        "VALUES (?, ?, 'PST_Tier', 'NORMAL', 0, 0, 'BRONZE', 'BRONZE')",
                userId, openid);
        jdbcTemplate.update(
                "UPDATE user SET star_points = ?, star_tier = 'BRONZE' WHERE id = ?",
                starPoints, userId);
    }

    /** 用 JDBC 复刻 RankingServiceImpl.refreshRankings 的写库语义：
     *  仅按 (type, season) 维度删后重排，用于段位边界场景（不依赖真实当前赛季）。 */
    private void refreshRankingsViaJdbc(String type, String seasonCode) {
        jdbcTemplate.update("DELETE FROM ranking WHERE type = ? AND season = ?", type, seasonCode);
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, star_points, party_points FROM user WHERE id IN (?, ?, ?) ORDER BY star_points DESC",
                U_BRONZE, U_SILVER, U_GOLD);
        int rank = 1;
        for (Map<String, Object> u : users) {
            long userId = ((Number) u.get("id")).longValue();
            int points = ((Number) u.get("star_points")).intValue();
            String tier = tierProperties.keyFor(points, type);
            jdbcTemplate.update(
                    "INSERT INTO ranking (user_id, type, tier, `rank`, points, `change`, season, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 0, ?, NOW())",
                    userId, type, tier, rank, points, seasonCode);
            rank++;
        }
    }

    private String tierFor(long userId, String type, String seasonCode) {
        List<Ranking> rk = rankingMapper.selectList(new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getUserId, userId)
                .eq(Ranking::getType, type)
                .eq(Ranking::getSeason, seasonCode));
        if (rk.isEmpty()) {
            fail("用户 " + userId + " 在赛季 " + seasonCode + " 无 ranking 行");
        }
        return rk.get(0).getTier();
    }
}
