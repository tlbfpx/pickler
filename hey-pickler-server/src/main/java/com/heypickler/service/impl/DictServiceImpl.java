package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.entity.SysDict;
import com.heypickler.entity.SysDictItem;
import com.heypickler.mapper.SysDictItemMapper;
import com.heypickler.mapper.SysDictMapper;
import com.heypickler.service.DictCacheService;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import com.heypickler.vo.SysDictItemVO;
import com.heypickler.vo.SysDictVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper itemMapper;
    private final DictCacheService cacheService;

    @Override
    public List<SysDictVO> listDicts() {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                        .orderByAsc(SysDict::getId))
                .stream().map(this::toDictVO).collect(Collectors.toList());
    }

    @Override
    public List<SysDictItemVO> listItems(String dictCode) {
        // 字典表小（< 50 行），直接查 DB；对象缓存见 RedisKey.dictVersion 说明（YAGNI）
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictCode, dictCode)
                .orderByAsc(SysDictItem::getSort))
                .stream().map(this::toItemVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateItems(String dictCode, List<DictItemUpdateRequest> items) {
        for (DictItemUpdateRequest req : items) {
            // @Valid 对 List 元素不级联，service 兜底校验 itemKey 非空
            if (req.getItemKey() == null || req.getItemKey().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "itemKey 不能为空");
            }
            SysDictItem existing = itemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                    .eq(SysDictItem::getDictCode, dictCode)
                    .eq(SysDictItem::getItemKey, req.getItemKey()));
            if (existing == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在: " + req.getItemKey());
            }
            // 铁律：只改 label/color/sort/status，item_key 永不回写
            SysDictItem patch = new SysDictItem();
            patch.setId(existing.getId());
            patch.setItemLabel(req.getItemLabel());
            patch.setItemColor(req.getItemColor());
            patch.setSort(req.getSort());
            patch.setStatus(req.getStatus());
            itemMapper.updateById(patch);
        }
        cacheService.incrementVersion();
    }

    @Override
    public DictBundleVO getBundle() {
        List<SysDict> dicts = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getStatus, "ENABLED")
                .orderByAsc(SysDict::getId));
        Map<String, List<SysDictItemVO>> map = new LinkedHashMap<>();
        for (SysDict d : dicts) {
            map.put(d.getDictCode(), listItems(d.getDictCode()));
        }
        DictBundleVO bundle = new DictBundleVO();
        bundle.setVersion(cacheService.getVersion());
        bundle.setDicts(map);
        return bundle;
    }

    @Override
    public long getVersion() {
        return cacheService.getVersion();
    }

    private SysDictVO toDictVO(SysDict d) {
        SysDictVO vo = new SysDictVO();
        vo.setDictCode(d.getDictCode());
        vo.setDictName(d.getDictName());
        vo.setDescription(d.getDescription());
        vo.setStatus(d.getStatus());
        return vo;
    }

    private SysDictItemVO toItemVO(SysDictItem it) {
        SysDictItemVO vo = new SysDictItemVO();
        vo.setItemKey(it.getItemKey());
        vo.setItemLabel(it.getItemLabel());
        vo.setItemColor(it.getItemColor());
        vo.setSort(it.getSort());
        vo.setStatus(it.getStatus());
        vo.setExtraJson(it.getExtraJson());
        return vo;
    }
}
