package com.lemon.biz.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.operation.entity.Notice;
import com.lemon.biz.operation.mapper.NoticeMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper mapper;

    public record NoticeSave(
            @NotBlank @Size(max = 128) String title,
            @Size(max = 5000) String content,
            Integer sort,
            @Min(0) @Max(1) Integer status) {
    }

    public PageResult<Notice> page(PageQuery q) {
        return PageResult.of(mapper.selectPage(q.toPage(), Wrappers.<Notice>lambdaQuery()
                .orderByAsc(Notice::getSort).orderByDesc(Notice::getId)));
    }

    public Long create(NoticeSave req) {
        Notice n = new Notice();
        copy(req, n);
        mapper.insert(n);
        return n.getId();
    }

    public void update(Long id, NoticeSave req) {
        Notice n = BizException.notNull(mapper.selectById(id), "公告不存在");
        copy(req, n);
        mapper.updateById(n);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private static void copy(NoticeSave req, Notice n) {
        n.setTitle(req.title().trim());
        n.setContent(req.content());
        n.setSort(Objects.requireNonNullElse(req.sort(), 0));
        n.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }
}
