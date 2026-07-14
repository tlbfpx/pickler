package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.enums.PointSource;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.TierResolver;
import com.heypickler.entity.Event;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.listener.PointChangeListener;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.PointService;
import com.heypickler.service.PointWallet;
import com.heypickler.service.dto.PointEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService, PointWallet {

    private final EventMapper eventMapper;
    private final PointRecordMapper pointRecordMapper;
    private final UserMapper userMapper;
    private final SeasonMapper seasonMapper;
    private final TierResolver tierResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void enterPoints(Long eventId, String type, List<PointEntry> records,
                            PointSource source, Long operatorId) {
        if (records == null || records.isEmpty()) return;

        ResolvedTarget target = resolveTarget(eventId, type);
        Map<Long, User> userMap = batchLoadUsers(records.stream()
                .map(PointEntry::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        // enterPoints requires a current season — fail-fast.
        String seasonCode = requireCurrentSeasonCode(target.type());

        for (PointEntry item : records) {
            User user = userMap.get(item.getUserId());
            if (user == null) continue;
            String reason = target.title() != null
                    ? "[" + target.title() + "] " + item.getReason()
                    : item.getReason();
            writeRecord(eventId, user, target, seasonCode, source.name(),
                    item.getPoints(), reason, operatorId);
        }
        eventPublisher.publishEvent(new PointChangeListener.PointChangeEvent(target.type(), seasonCode));
    }

    @Override
    @Transactional
    public void issuePlacement(Long eventId, Long userId, int points, String reason) {
        if (eventId == null || userId == null) return;
        ResolvedTarget target = resolveTarget(eventId, null);
        User user = userMapper.selectById(userId);
        if (user == null) return;
        // Placement tolerates a missing current season — rows still written.
        String seasonCode = tryCurrentSeasonCode(target.type());
        writeRecord(eventId, user, target, seasonCode, PointSource.PLACEMENT.name(),
                points, reason, null);
        eventPublisher.publishEvent(new PointChangeListener.PointChangeEvent(target.type(), seasonCode));
    }

    @Override
    @Transactional
    public void revertPointRecord(Long recordId, Long operatorId) {
        PointRecord original = pointRecordMapper.selectById(recordId);
        if (original == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "积分记录不存在: " + recordId);
        }
        String src = original.getSource();
        if (!PointSource.MANUAL.name().equals(src) && !PointSource.ADJUST.name().equals(src)) {
            // PLACEMENT / REGISTRATION / CHECK_IN / REDEEM 不可撤销
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "仅支持撤销手动录入(MANUAL)与纠错(ADJUST)记录，当前来源=" + src);
        }
        // 跨赛季撤销：补偿行必须落在当前赛季，与原记录同赛季才有意义
        String currentCode = requireCurrentSeasonCode(original.getType());
        if (!currentCode.equals(original.getSeasonCode())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "原始记录不属于当前赛季，无法撤销");
        }

        User user = userMapper.selectById(original.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 补偿行：负分、ADJUST 来源、审计可追溯的原因。writeRecord 会钳制余额≥0、重算段位、存库。
        ResolvedTarget target = new ResolvedTarget(original.getType(), null);
        String reason = "撤销 #" + original.getId() + ": " + original.getReason();
        writeRecord(original.getEventId(), user, target, currentCode,
                PointSource.ADJUST.name(), -original.getPoints(), reason, operatorId);

        eventPublisher.publishEvent(new PointChangeListener.PointChangeEvent(target.type(), currentCode));
    }

    // ---------- internals ----------

    private record ResolvedTarget(String type, String title) {}

    private ResolvedTarget resolveTarget(Long eventId, String typeOverride) {
        String resolvedType = typeOverride;
        String title = null;
        if (eventId != null && eventId > 0) {
            Event event = eventMapper.selectById(eventId);
            if (event == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "赛事不存在");
            }
            resolvedType = event.getType();
            title = event.getTitle();
        }
        if (resolvedType == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "积分类型不能为空；请指定 STAR(战力) 或 PARTY(活力)");
        }
        return new ResolvedTarget(resolvedType, title);
    }

    /** Required-season lookup for enterPoints. */
    private String requireCurrentSeasonCode(String type) {
        Season season = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, type)
                .eq(Season::getStatus, "CURRENT"));
        if (season == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前赛季不存在: " + type);
        }
        return season.getCode();
    }

    /** Optional-season lookup for placement (null if missing). */
    private String tryCurrentSeasonCode(String type) {
        Season season = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, type)
                .eq(Season::getStatus, "CURRENT"));
        return season == null ? null : season.getCode();
    }

    private void writeRecord(Long eventId, User user, ResolvedTarget target,
                             String seasonCode, String sourceName, int points, String reason,
                             Long operatorId) {
        PointRecord record = new PointRecord();
        record.setUserId(user.getId());
        record.setEventId(eventId != null && eventId > 0 ? eventId : null);
        record.setType(target.type());
        record.setPoints(points);
        record.setReason(reason);
        record.setSource(sourceName);
        record.setSeasonCode(seasonCode);
        record.setOperatorId(operatorId);

        // V17 幂等兜底：MySQL functional unique index
        //   uk_event_user_when_placement (
        //     (CASE WHEN source='PLACEMENT' THEN CONCAT(event_id,'-',user_id) ELSE NULL END)
        //   )
        // 命中时该 INSERT 被拒，捕获并跳过即可。MySQL InnoDB 中 UNIQUE 违反
        // 仅回滚本语句、事务可继续；此处不重抛是为了防止 Spring 将外层事务
        // 标记为 rollback-only。
        // 在 InnoDB 中，UNIQUE 违反只回滚该语句，整个事务可继续；捕获并跳过即可。
        // 此处不重抛是为了避免 Spring 把外层事务标记为 rollback-only。
        try {
            pointRecordMapper.insert(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("point_record duplicate ignored (idempotent): event={}, user={}, source={}, season={}",
                    eventId, user.getId(), sourceName, seasonCode);
            return; // 跳出，跳过 userPoints 累加避免双重加和
        }

        if ("STAR".equals(target.type())) {
            int current = user.getStarPoints() != null ? user.getStarPoints() : 0;
            int newPoints = Math.max(0, current + points);
            user.setStarPoints(newPoints);
            user.setStarTier(tierResolver.keyFor(newPoints, target.type()));
        } else {
            int current = user.getPartyPoints() != null ? user.getPartyPoints() : 0;
            int newPoints = Math.max(0, current + points);
            user.setPartyPoints(newPoints);
            user.setPartyTier(tierResolver.keyFor(newPoints, target.type()));
        }
        userMapper.updateById(user);
    }

    @Override
    public int getBalance(Long userId, String type) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return "PARTY".equals(type) ? u.getPartyPoints() : u.getStarPoints();
    }

    private Map<Long, User> batchLoadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return java.util.Collections.emptyMap();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
}