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
        // 1. 解析赛事（若给出）→ type / 标题
        String resolvedType = type;
        String eventTitle = null;
        if (eventId != null && eventId > 0) {
            Event event = eventMapper.selectById(eventId);
            if (event == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "赛事不存在");
            }
            resolvedType = event.getType();
            eventTitle = event.getTitle();
        }

        // 2. 取当前赛季 → seasonCode
        Season season = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, resolvedType)
                .eq(Season::getStatus, "CURRENT"));
        if (season == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前赛季不存在: " + resolvedType);
        }
        String seasonCode = season.getCode();

        // 3. 批量加载用户
        List<Long> userIds = records.stream()
                .map(PointEntry::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = batchLoadUsers(userIds);

        // 4. 逐条发分：插 point_record → 累加余额 → 重算 tier → 更新 user
        for (PointEntry item : records) {
            User user = userMap.get(item.getUserId());
            if (user == null) continue;

            PointRecord record = new PointRecord();
            record.setUserId(item.getUserId());
            record.setEventId(eventId != null && eventId > 0 ? eventId : null);
            record.setType(resolvedType);
            record.setPoints(item.getPoints());
            record.setReason(eventTitle != null
                    ? "[" + eventTitle + "] " + item.getReason()
                    : item.getReason());
            record.setSource(source.name());
            record.setSeasonCode(seasonCode);
            record.setOperatorId(operatorId);
            pointRecordMapper.insert(record);

            int newPoints;
            if ("STAR".equals(resolvedType)) {
                int current = user.getStarPoints() != null ? user.getStarPoints() : 0;
                newPoints = Math.max(0, current + item.getPoints());
                user.setStarPoints(newPoints);
                user.setStarTier(tierProperties.keyFor(newPoints, resolvedType));
            } else {
                int current = user.getPartyPoints() != null ? user.getPartyPoints() : 0;
                newPoints = Math.max(0, current + item.getPoints());
                user.setPartyPoints(newPoints);
                user.setPartyTier(tierProperties.keyFor(newPoints, resolvedType));
            }
            userMapper.updateById(user);
        }

        // 5. 发布赛季维度事件（携 seasonCode）
        eventPublisher.publishEvent(new PointChangeListener.PointChangeEvent(resolvedType, seasonCode));
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
