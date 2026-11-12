package com.lemon.biz.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.operation.entity.Feedback;
import com.lemon.biz.operation.mapper.FeedbackMapper;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.PageResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 题目纠错与意见反馈。用户提交 → 管理端处理并回复 → 用户在“我的反馈”看到回复。
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    public static final int TYPE_CORRECTION = 1;
    public static final int TYPE_SUGGESTION = 2;
    private static final int PENDING = 0;
    private static final int HANDLED = 1;
    /** 防刷：每人每天最多提交条数 */
    private static final int DAILY_LIMIT = 20;

    private final FeedbackMapper mapper;
    private final QuestionMapper questionMapper;
    private final AppUserMapper userMapper;

    public record SubmitReq(@NotNull @Min(1) @Max(2) Integer type, Long questionId,
                            @NotBlank @Size(max = 1000) String content) {
    }

    public record HandleReq(@Size(max = 500) String reply, @NotNull @Min(0) @Max(1) Integer status) {
    }

    /**
     * @param stemText 纠错题目的题干摘要
     * @param user     提交人（手机号 / 昵称），仅管理端返回
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedbackVO(Long id, Integer type, Long questionId, String stemText, String content,
                             Integer status, String reply, String user,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime createdAt,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime updatedAt) {
    }

    // ==================== 用户端 ====================

    public Long submit(Long userId, SubmitReq req) {
        long today = mapper.selectCount(Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getUserId, userId).ge(Feedback::getCreatedAt, LocalDate.now().atStartOfDay()));
        BizException.throwIf(today >= DAILY_LIMIT, "今天提交的反馈太多了，明天再来吧");
        if (req.type() == TYPE_CORRECTION) {
            BizException.throwIf(req.questionId() == null, "请指明要纠错的题目");
            BizException.notNull(questionMapper.selectById(req.questionId()), "题目不存在");
        }
        Feedback f = new Feedback();
        f.setUserId(userId);
        f.setType(req.type());
        f.setQuestionId(req.type() == TYPE_CORRECTION ? req.questionId() : null);
        f.setContent(req.content().strip());
        f.setStatus(PENDING);
        mapper.insert(f);
        return f.getId();
    }

    public PageResult<FeedbackVO> mine(Long userId, long pageNum, long pageSize) {
        Page<Feedback> page = mapper.selectPage(Page.of(pageNum, pageSize), Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getUserId, userId).orderByDesc(Feedback::getId));
        return toVO(page, false);
    }

    // ==================== 管理端 ====================

    public PageResult<FeedbackVO> page(Integer type, Integer status, long pageNum, long pageSize) {
        Page<Feedback> page = mapper.selectPage(Page.of(pageNum, pageSize), Wrappers.<Feedback>lambdaQuery()
                .eq(type != null, Feedback::getType, type)
                .eq(status != null, Feedback::getStatus, status)
                .orderByAsc(Feedback::getStatus).orderByDesc(Feedback::getId));
        return toVO(page, true);
    }

    public void handle(Long id, HandleReq req) {
        Feedback f = BizException.notNull(mapper.selectById(id), "反馈不存在");
        f.setReply(req.reply() == null || req.reply().isBlank() ? null : req.reply().strip());
        f.setStatus(req.status() == HANDLED ? HANDLED : PENDING);
        mapper.updateById(f);
    }

    private PageResult<FeedbackVO> toVO(Page<Feedback> page, boolean withUser) {
        var records = page.getRecords();
        var qIds = records.stream().map(Feedback::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Question> questions = qIds.isEmpty() ? Map.of()
                : questionMapper.selectByIds(qIds).stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        var uIds = records.stream().map(Feedback::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AppUser> users = !withUser || uIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(uIds).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return PageResult.of(page, f -> {
            Question q = f.getQuestionId() == null ? null : questions.get(f.getQuestionId());
            String stem = f.getQuestionId() == null ? null : q == null ? "该题已被删除" : preview(q.getStem());
            AppUser u = f.getUserId() == null ? null : users.get(f.getUserId());
            String user = u == null ? null : Objects.toString(u.getNickname(), "") + (u.getPhone() == null ? "" : "（" + u.getPhone() + "）");
            return new FeedbackVO(f.getId(), f.getType(), f.getQuestionId(), stem, f.getContent(), f.getStatus(),
                    f.getReply(), withUser ? user : null, f.getCreatedAt(), f.getUpdatedAt());
        });
    }

    private static String preview(String html) {
        String t = HtmlUtil.htmlToText(html).replaceAll("\\s+", " ").strip();
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }
}
