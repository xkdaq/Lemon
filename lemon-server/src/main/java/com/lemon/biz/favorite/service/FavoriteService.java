package com.lemon.biz.favorite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.favorite.entity.UserFavorite;
import com.lemon.biz.favorite.mapper.UserFavoriteMapper;
import com.lemon.common.BizException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 收藏。M3 只提供做题页的收藏 / 取消；收藏集列表与收藏练习在 M4。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteMapper mapper;
    private final QuestionMapper questionMapper;

    public void add(Long userId, Long questionId) {
        Question q = BizException.notNull(questionMapper.selectById(questionId), "题目不存在");
        UserFavorite f = new UserFavorite();
        f.setUserId(userId);
        f.setQuestionId(q.getId());
        f.setModuleId(q.getModuleId());
        f.setBankId(q.getBankId());
        f.setChapterId(q.getChapterId());
        try {
            mapper.insert(f);
        } catch (DuplicateKeyException e) {
            // 已收藏，幂等
        }
    }

    public void remove(Long userId, Long questionId) {
        mapper.delete(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId).eq(UserFavorite::getQuestionId, questionId));
    }

    /** 这些题目中已收藏的 id */
    public Set<Long> favoritedIn(Long userId, Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(Wrappers.<UserFavorite>lambdaQuery()
                        .select(UserFavorite::getQuestionId)
                        .eq(UserFavorite::getUserId, userId).in(UserFavorite::getQuestionId, questionIds))
                .stream().map(UserFavorite::getQuestionId).collect(Collectors.toSet());
    }
}
