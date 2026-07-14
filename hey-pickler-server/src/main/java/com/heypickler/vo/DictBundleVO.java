package com.heypickler.vo;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
public class DictBundleVO {
    private long version;
    /** dictCode → items */
    private Map<String, List<SysDictItemVO>> dicts;
}
