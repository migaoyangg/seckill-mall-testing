package com.seckill.mall.common;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private Long total;
    private List<T> records;
    private Integer page;
    private Integer size;
    private Integer pages;

    public PageResult() {}

    public PageResult(Long total, List<T> records, Integer page, Integer size) {
        this.total = total;
        this.records = records;
        this.page = page;
        this.size = size;
        this.pages = (int) Math.ceil((double) total / size);
    }
}
