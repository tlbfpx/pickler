package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.enums.PointSource;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.config.TierProperties;
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
    private final TierProperties tierProperties;
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
            throw new BizException(ErrorCode.PARAM_ERROR, "积分类型不能为空");
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
        pointRecordMapper.insert(record);

        if ("STAR".equals(target.type())) {
            int current = user.getStarPoints() != null ? user.getStarPoints() : 0;
            int newPoints = Math.max(0, current + points);
            user.setStarPoints(newPoints);
            user.setStarTier(tierProperties.keyFor(newPoints, target.type()));
        } else {
            int current = user.getPartyPoints() != null ? user.getPartyPoints() : 0;
            int newPoints = Math.max(0, current + points);
            user.setPartyPoints(newPoints);
            user.setPartyTier(tierProperties.keyFor(newPoints, target.type()));
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