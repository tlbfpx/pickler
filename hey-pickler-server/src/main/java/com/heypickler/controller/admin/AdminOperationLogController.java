package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.dto.OperationLogQuery;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.OperationLog;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.service.OperationLogService;
import com.heypickler.vo.OperationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/operation-logs")
@RequiredArgsConstructor
@Tag(name = "管理端-操作日志")
public class AdminOperationLogController {

    private final OperationLogService operationLogService;
    private final AdminUserMapper adminUserMapper;

    @GetMapping
    @Operation(summary = "操作日志列表（分页 + 过滤）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<OperationLogVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {

        OperationLogQuery query = new OperationLogQuery(
                operatorId, module, action, status, startTime, endTime);
        IPage<OperationLog> result = operationLogService.page(query, page, size);

        List<OperationLog> records = result.getRecords();
        if (records.isEmpty()) {
            return Result.ok(PageResult.of(result.getTotal(), page, size, List.of()));
        }

        // Batch-load operator names so the UI doesn't have to resolve admin ids itself
        List<Long> operatorIds = records.stream()
                .map(OperationLog::getOperatorId).distinct().filter(java.util.Objects::nonNull).toList();
        Map<Long, String> operatorNames = operatorIds.isEmpty()
                ? Map.of()
                : adminUserMapper.selectBatchIds(operatorIds).stream()
                    .collect(Collectors.toMap(AdminUser::getId, AdminUser::getUsername));

        List<OperationLogVO> vos = records.stream().map(log -> {
            OperationLogVO vo = new OperationLogVO();
            vo.setId(log.getId());
            vo.setOperatorId(log.getOperatorId());
            vo.setOperatorName(log.getOperatorId() == null ? null
                    : operatorNames.get(log.getOperatorId()));
            vo.setOperatorRole(log.getOperatorRole());
            vo.setMethod(log.getMethod());
            vo.setModule(log.getModule());
            vo.setAction(log.getAction());
            vo.setTargetType(log.getTargetType());
            vo.setTargetId(log.getTargetId());
            vo.setPath(log.getPath());
            vo.setParams(log.getParams());
            vo.setStatus(log.getStatus());
            vo.setErrorCode(log.getErrorCode());
            vo.setErrorMsg(log.getErrorMsg());
            vo.setIp(log.getIp());
            vo.setUserAgent(log.getUserAgent());
            vo.setLatencyMs(log.getLatencyMs());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList();

        return Result.ok(PageResult.of(result.getTotal(), page, size, vos));
    }
}
