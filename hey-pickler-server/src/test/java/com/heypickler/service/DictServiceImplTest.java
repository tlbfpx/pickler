package com.heypickler.service;

import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.entity.SysDict;
import com.heypickler.entity.SysDictItem;
import com.heypickler.mapper.SysDictItemMapper;
import com.heypickler.mapper.SysDictMapper;
import com.heypickler.service.impl.DictServiceImpl;
import com.heypickler.vo.DictBundleVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictServiceImplTest {

    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        // Each assistant's namespace locks after first initTableInfo, so use a fresh
        // assistant per mapper namespace.
        MapperBuilderAssistant itemAssistant = new MapperBuilderAssistant(cfg, "");
        itemAssistant.setCurrentNamespace("com.heypickler.mapper.SysDictItemMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(itemAssistant, SysDictItem.class);

        MapperBuilderAssistant dictAssistant = new MapperBuilderAssistant(cfg, "");
        dictAssistant.setCurrentNamespace("com.heypickler.mapper.SysDictMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(dictAssistant, SysDict.class);
    }

    @InjectMocks DictServiceImpl service;
    @Mock SysDictMapper dictMapper;
    @Mock SysDictItemMapper itemMapper;
    @Mock DictCacheService cacheService;

    @Test
    void listDicts_returnsAllDicts() {
        SysDict d = new SysDict(); d.setDictCode("event_type"); d.setDictName("赛事类型");
        when(dictMapper.selectList(any())).thenReturn(List.of(d));
        assertEquals(1, service.listDicts().size());
    }

    @Test
    void listItems_readsFromDb() {
        SysDictItem item = new SysDictItem();
        item.setDictCode("event_type"); item.setItemKey("STAR");
        item.setItemLabel("竞技赛事"); item.setSort(0); item.setStatus("ENABLED");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        var vos = service.listItems("event_type");

        assertEquals(1, vos.size());
        assertEquals("STAR", vos.get(0).getItemKey());
    }

    @Test
    void updateItems_onlyMutatesLabelColorSortStatus_keyNeverWritten() {
        SysDictItem existing = new SysDictItem();
        existing.setId(1L); existing.setDictCode("event_type");
        existing.setItemKey("STAR"); existing.setItemLabel("竞技赛事");
        when(itemMapper.selectOne(any())).thenReturn(existing);

        DictItemUpdateRequest req = new DictItemUpdateRequest();
        req.setItemKey("STAR"); req.setItemLabel("竞技"); req.setItemColor("#123456");
        req.setSort(5); req.setStatus("DISABLED");

        service.updateItems("event_type", List.of(req));

        ArgumentCaptor<SysDictItem> cap = ArgumentCaptor.forClass(SysDictItem.class);
        verify(itemMapper).updateById(cap.capture());
        SysDictItem saved = cap.getValue();
        assertEquals("竞技", saved.getItemLabel());   // 可改
        assertEquals("#123456", saved.getItemColor());
        assertEquals(5, saved.getSort());
        assertEquals("DISABLED", saved.getStatus());
        assertNull(saved.getItemKey());                 // 铁律：item_key 不得回写（防回归）
        verify(cacheService).incrementVersion();
    }

    @Test
    void updateItems_blankKey_throwsParamError() {
        // @Valid 对 List 元素不级联，service 兜底校验 itemKey 非空
        DictItemUpdateRequest req = new DictItemUpdateRequest();
        req.setItemKey("   ");
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> service.updateItems("event_type", List.of(req)));
        verifyNoInteractions(itemMapper);
    }

    @Test
    void updateItems_unknownKey_throwsNotFound() {
        when(itemMapper.selectOne(any())).thenReturn(null);
        DictItemUpdateRequest req = new DictItemUpdateRequest();
        req.setItemKey("NOPE");
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> service.updateItems("event_type", List.of(req)));
    }

    @Test
    void getBundle_aggregatesAllDictsAndVersion() {
        SysDict d = new SysDict(); d.setDictCode("event_type"); d.setDictName("赛事类型"); d.setStatus("ENABLED");
        when(dictMapper.selectList(any())).thenReturn(List.of(d));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getVersion()).thenReturn(3L);

        DictBundleVO bundle = service.getBundle();

        assertEquals(3L, bundle.getVersion());
        assertTrue(bundle.getDicts().containsKey("event_type"));
    }
}
