package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.enums.BanAction;
import com.heypickler.common.enums.UserStatus;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.util.AesUtil;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.admin.UserQueryRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.UserService;
import com.heypickler.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;
    private final PointRecordMapper pointRecordMapper;
    private final BanRecordMapper banRecordMapper;
    private final AesUtil aesUtil;

    @Override
    public UserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setCity(user.getCity());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setStarPoints(user.getStarPoints());
        vo.setPartyPoints(user.getPartyPoints());
        vo.setStarTier(user.getStarTier());
        vo.setPartyTier(user.getPartyTier());
        vo.setTotalEvents(Math.toIntExact(registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>().eq(Registration::getUserId, userId))));
        return vo;
    }

    @Override
    public void updateProfile(Long userId, UserUpdateRequest request) {
        User user = new User();
        user.setId(userId);
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getCity())) {
            user.setCity(request.getCity());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        userMapper.updateById(user);
    }

    @Override
    public PageResult<MyEventVO> getMyEvents(Long userId, String type, int page, int size) {
        // Query all registrations for the user
        LambdaQueryWrapper<Registration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(Registration::getUserId, userId);

        List<Registration> allRegs = registrationMapper.selectList(regWrapper);
        if (allRegs.isEmpty()) {
            return PageResult.of(0L, page, size, Collections.emptyList());
        }

        // Batch query events
        List<Long> eventIds = allRegs.stream()
                .map(Registration::getEventId)
                .distinct()
                .collect(Collectors.toList());

        List<Event> events = eventMapper.selectBatchIds(eventIds);
        Map<Long, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        // Filter by type if specified
        List<MyEventVO> allVos = allRegs.stream()
                .map(reg -> {
                    Event event = eventMap.get(reg.getEventId());
                    if (event == null) return null;
                    if (StringUtils.hasText(type) && !type.equals(event.getType())) return null;

                    MyEventVO vo = new MyEventVO();
                    vo.setId(event.getId());
                    vo.setTitle(event.getTitle());
                    vo.setType(event.getType());
                    vo.setBannerUrl(event.getBannerUrl());
                    vo.setEventTime(event.getEventTime());
                    vo.setLocation(event.getLocation());
                    vo.setStatus(com.heypickler.common.enums.EventStatus.valueOf(event.getStatus()));
                    vo.setRegistrationStatus(com.heypickler.common.enums.RegistrationStatus.valueOf(reg.getStatus()));
                    return vo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Manual pagination
        long total = allVos.size();
        int from = Math.min((page - 1) * size, allVos.size());
        int to = Math.min(from + size, allVos.size());
        List<MyEventVO> pageVos = allVos.subList(from, to);

        return PageResult.of(total, page, size, pageVos);
    }

    @Override
    public PageResult<PointRecordVO> getPointHistory(Long userId, String type, int page, int size) {
        LambdaQueryWrapper<PointRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointRecord::getUserId, userId);
        if (StringUtils.hasText(type)) {
            wrapper.eq(PointRecord::getType, type);
        }
        wrapper.orderByDesc(PointRecord::getCreatedAt);

        Page<PointRecord> pointPage = new Page<>(page, size);
        Page<PointRecord> pageResult = pointRecordMapper.selectPage(pointPage, wrapper);

        if (pageResult.getRecords().isEmpty()) {
            return PageResult.of(0L, page, size, Collections.emptyList());
        }

        // Batch query events
        List<Long> eventIds = pageResult.getRecords().stream()
                .map(PointRecord::getEventId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Event> eventMap;
        if (!eventIds.isEmpty()) {
            List<Event> events = eventMapper.selectBatchIds(eventIds);
            eventMap = events.stream()
                    .collect(Collectors.toMap(Event::getId, e -> e));
        } else {
            eventMap = new HashMap<>();
        }

        // Build VOs
        List<PointRecordVO> vos = pageResult.getRecords().stream()
                .map(record -> {
                    PointRecordVO vo = new PointRecordVO();
                    vo.setId(record.getId());
                    vo.setEventId(record.getEventId());
                    Event event = eventMap.get(record.getEventId());
                    vo.setEventTitle(event != null ? event.getTitle() : null);
                    vo.setType(record.getType());
                    vo.setPoints(record.getPoints());
                    vo.setReason(record.getReason());
                    vo.setCreatedAt(record.getCreatedAt());
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), page, size, vos);
    }

    @Override
    public PageResult<UserAdminVO> adminListUsers(UserQueryRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w.like(User::getNickname, request.getKeyword())
                    .or()
                    .like(User::getPhone, request.getKeyword()));
        }

        if (StringUtils.hasText(request.getCity())) {
            wrapper.eq(User::getCity, request.getCity());
        }

        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(User::getStatus, request.getStatus());
        }

        if (StringUtils.hasText(request.getStarTier())) {
            wrapper.eq(User::getStarTier, request.getStarTier());
        }

        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> userPage = new Page<>(request.getPage(), request.getSize());
        Page<User> pageResult = userMapper.selectPage(userPage, wrapper);

        List<UserAdminVO> vos = pageResult.getRecords().stream()
                .map(this::mapToAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), request.getPage(), request.getSize(), vos);
    }

    @Override
    public UserAdminVO adminGetUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return mapToAdminVO(user);
    }

    @Override
    public void adminUpdateUser(Long userId, UserUpdateRequest request) {
        User user = new User();
        user.setId(userId);
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getCity())) {
            user.setCity(request.getCity());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        userMapper.updateById(user);
    }

    @Override
    public void banUser(Long userId, Long operatorId, BanRequest request) {
        // Update user status
        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.BANNED.name());
        userMapper.updateById(user);

        // Create ban record
        BanRecord record = new BanRecord();
        record.setUserId(userId);
        record.setOperatorId(operatorId);
        record.setAction(BanAction.BAN.name());
        record.setReason(request.getReason());
        record.setBanUntil(request.getBanUntil());
        banRecordMapper.insert(record);
    }

    @Override
    public void unbanUser(Long userId, Long operatorId) {
        // Update user status
        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.NORMAL.name());
        userMapper.updateById(user);

        // Create unban record
        BanRecord record = new BanRecord();
        record.setUserId(userId);
        record.setOperatorId(operatorId);
        record.setAction(BanAction.UNBAN.name());
        record.setReason("解除封禁");
        banRecordMapper.insert(record);
    }

    private UserAdminVO mapToAdminVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setCity(user.getCity());
        vo.setPhone(safeDecrypt(user.getPhone()));
        vo.setStarPoints(user.getStarPoints());
        vo.setPartyPoints(user.getPartyPoints());
        vo.setStarTier(user.getStarTier());
        vo.setPartyTier(user.getPartyTier());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private String safeDecrypt(String value) {
        if (value == null) return null;
        try {
            return aesUtil.decrypt(value);
        } catch (Exception e) {
            return value;
        }
    }

    private String maskPhone(String encryptedPhone) {
        if (encryptedPhone == null) return null;
        String phone = safeDecrypt(encryptedPhone);
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return phone;
    }
}
