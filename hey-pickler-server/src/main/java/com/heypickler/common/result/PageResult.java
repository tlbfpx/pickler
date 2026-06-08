package com.heypickler.common.result;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private int page;
    private int size;
    private List<T> list;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> list) {
        PageResult<T> pr = new PageResult<>();
        pr.setTotal(total);
        pr.setPage(page);
        pr.setSize(size);
        pr.setList(list);
        return pr;
    }
}
