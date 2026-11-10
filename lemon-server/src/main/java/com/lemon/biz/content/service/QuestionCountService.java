package com.lemon.biz.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.QuestionCountMapper;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 题目增删改、上下架、移动，以及章节上下架后，调用这里重算冗余题量。
 * 直接按 COUNT 重算而不是 +1/-1，避免并发或漏调导致计数漂移。
 */
@Service
@RequiredArgsConstructor
public class QuestionCountService {

    private final QuestionCountMapper countMapper;
    private final ChapterMapper chapterMapper;

    /** 重算这些章节及其所属题库的题量 */
    public void refreshChapters(Collection<Long> chapterIds) {
        Set<Long> ids = chapterIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        countMapper.refreshChapters(ids);
        // 注意：逻辑删除的章节查不出来，删除章节时由调用方直接 refreshBanks
        Set<Long> bankIds = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                        .select(Chapter::getBankId).in(Chapter::getId, ids))
                .stream().map(Chapter::getBankId).collect(Collectors.toSet());
        refreshBanks(bankIds);
    }

    public void refreshBanks(Collection<Long> bankIds) {
        if (!bankIds.isEmpty()) {
            countMapper.refreshBanks(bankIds);
        }
    }
}
