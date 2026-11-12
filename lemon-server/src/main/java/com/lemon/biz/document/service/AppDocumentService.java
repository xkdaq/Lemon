package com.lemon.biz.document.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.document.entity.AppDocument;
import com.lemon.biz.document.mapper.AppDocumentMapper;
import com.lemon.common.BizException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 协议文档：用户协议（agreement）、隐私政策（privacy）。用户端注册页、设置页展示，后台编辑。
 */
@Service
@RequiredArgsConstructor
public class AppDocumentService {

    public static final int MAX_CONTENT = 50000;

    private final AppDocumentMapper mapper;

    public List<AppDocument> list() {
        return mapper.selectList(Wrappers.<AppDocument>lambdaQuery().orderByAsc(AppDocument::getId));
    }

    public AppDocument get(String key) {
        return BizException.notNull(mapper.selectOne(
                Wrappers.<AppDocument>lambdaQuery().eq(AppDocument::getDocKey, key)), "文档不存在");
    }

    public void update(String key, String title, String content) {
        AppDocument d = get(key);
        String t = title == null ? "" : title.strip();
        String c = content == null ? "" : content.strip();
        BizException.throwIf(t.isEmpty() || t.length() > 64, "标题不能为空，最多 64 字");
        BizException.throwIf(c.isEmpty(), "内容不能为空");
        BizException.throwIf(c.length() > MAX_CONTENT, "内容最多 " + MAX_CONTENT + " 字");
        d.setTitle(t);
        d.setContent(c);
        mapper.updateById(d);
    }
}
