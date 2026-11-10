package com.lemon.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询参数基类，具体查询条件继承此类。
 */
@Data
public class PageQuery {

    @Min(1)
    private long pageNum = 1;

    @Min(1)
    @Max(200)
    private long pageSize = 20;

    public <T> Page<T> toPage() {
        return Page.of(pageNum, pageSize);
    }
}
