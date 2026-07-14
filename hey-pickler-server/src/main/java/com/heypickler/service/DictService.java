package com.heypickler.service;

import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.vo.DictBundleVO;
import com.heypickler.vo.SysDictItemVO;
import com.heypickler.vo.SysDictVO;
import java.util.List;

public interface DictService {
    List<SysDictVO> listDicts();
    List<SysDictItemVO> listItems(String dictCode);
    /** 批量更新某字典的 items（仅 label/color/sort/status 可改；item_key 不可改） */
    void updateItems(String dictCode, List<DictItemUpdateRequest> items);
    /** 聚合 bundle：全部字典 + items（带缓存） */
    DictBundleVO getBundle();
    /** 全局版本号 */
    long getVersion();
}
