package com.lemon.biz.note.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.type.TypeReference;
import com.lemon.biz.access.service.AccessService;
import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.file.FileService;
import com.lemon.biz.note.entity.QuestionNote;
import com.lemon.biz.note.mapper.QuestionNoteMapper;
import com.lemon.biz.risk.service.RiskGuardService;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.JsonUtil;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 题目笔记（私密，只有自己看得到）：每人每题一条，纯文字 + 最多 6 张图片。
 * 图片先单独上传（存到 uploads/notes/），保存笔记时只接受本站 notes 目录下的图片地址。
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    public static final int MAX_CONTENT = 2000;
    public static final int MAX_IMAGES = 6;
    /** 每人每天最多上传的笔记图片数 */
    private static final int DAILY_UPLOADS = 100;
    private static final int STEM_PREVIEW = 80;
    private static final TypeReference<List<String>> IMAGES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<OptionItem>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private final QuestionNoteMapper mapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankMapper bankMapper;
    private final ChapterMapper chapterMapper;
    private final AccessService accessService;
    private final FileService fileService;
    private final StringRedisTemplate redis;
    private final RiskGuardService riskGuard;

    @Value("${lemon.upload.url-prefix}")
    private String urlPrefix;

    public record NoteVO(String content, List<String> images,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime updatedAt) {
    }

    /** 我的笔记列表的一条 */
    public record NoteItem(Long questionId, String stemText, String bankName, String chapterName,
                           String content, List<String> images, boolean available,
                           @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime updatedAt) {
    }

    /** 笔记详情：题目 + 笔记；模块未激活时不带答案和解析 */
    public record NoteDetail(Long questionId, Integer type, String stem, List<OptionItem> options, BigDecimal score,
                             String answer, String analysis, String bankName, String chapterName, NoteVO note) {
    }

    // ==================== 查询 ====================

    /** 这些题目里自己的笔记：题目 id → 笔记 */
    public Map<Long, NoteVO> notesIn(Long userId, Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(Wrappers.<QuestionNote>lambdaQuery()
                        .eq(QuestionNote::getUserId, userId).in(QuestionNote::getQuestionId, questionIds))
                .stream().collect(Collectors.toMap(QuestionNote::getQuestionId, NoteService::toVO, (a, b) -> a));
    }

    public NoteDetail detail(Long userId, Long questionId) {
        Question q = BizException.notNull(questionMapper.selectById(questionId), "题目不存在");
        riskGuard.onQuestionsViewed(userId, List.of(questionId));
        QuestionNote n = find(userId, questionId);
        boolean reveal = accessService.accessibleModules(userId).contains(q.getModuleId());
        QuestionBank bank = bankMapper.selectById(q.getBankId());
        Chapter chapter = chapterMapper.selectById(q.getChapterId());
        List<OptionItem> options = JsonUtil.parse(q.getOptions(), OPTIONS_TYPE);
        return new NoteDetail(q.getId(), q.getType(), q.getStem(), options == null ? List.of() : options, q.getScore(),
                reveal ? q.getAnswer() : null, reveal ? q.getAnalysis() : null,
                bank == null ? null : bank.getName(), chapter == null ? null : chapter.getName(),
                n == null ? null : toVO(n));
    }

    /** 我的笔记：最近修改的在前；可按模块筛选 */
    public PageResult<NoteItem> page(Long userId, Long moduleId, long pageNum, long pageSize) {
        var page = mapper.selectPage(Page.of(pageNum, pageSize), Wrappers.<QuestionNote>lambdaQuery()
                .eq(QuestionNote::getUserId, userId)
                .eq(moduleId != null, QuestionNote::getModuleId, moduleId)
                .orderByDesc(QuestionNote::getUpdatedAt).orderByDesc(QuestionNote::getId));
        List<QuestionNote> rows = page.getRecords();
        Map<Long, Question> questions = rows.isEmpty() ? Map.of() : byId(questionMapper.selectByIds(
                rows.stream().map(QuestionNote::getQuestionId).toList()), Question::getId);
        Map<Long, QuestionBank> banks = rows.isEmpty() ? Map.of() : byId(bankMapper.selectByIds(
                rows.stream().map(QuestionNote::getBankId).collect(Collectors.toSet())), QuestionBank::getId);
        Map<Long, Chapter> chapters = rows.isEmpty() ? Map.of() : byId(chapterMapper.selectByIds(
                rows.stream().map(QuestionNote::getChapterId).collect(Collectors.toSet())), Chapter::getId);
        List<NoteItem> list = rows.stream().map(n -> {
            Question q = questions.get(n.getQuestionId());
            QuestionBank b = banks.get(n.getBankId());
            Chapter c = chapters.get(n.getChapterId());
            return new NoteItem(n.getQuestionId(), q == null ? "该题已被删除" : preview(q.getStem()),
                    b == null ? null : b.getName(), c == null ? null : c.getName(),
                    n.getContent(), images(n), q != null && q.getStatus() == Status.ON, n.getUpdatedAt());
        }).toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================== 保存 / 删除 ====================

    /** 保存（新建或覆盖）；文字和图片都为空时删除笔记，返回 null */
    public NoteVO save(Long userId, Long questionId, String rawContent, List<String> rawImages) {
        String content = rawContent == null ? "" : rawContent.strip();
        List<String> images = rawImages == null ? List.of() : rawImages.stream().filter(s -> s != null && !s.isBlank()).toList();
        BizException.throwIf(content.length() > MAX_CONTENT, "笔记最多 " + MAX_CONTENT + " 字");
        BizException.throwIf(images.size() > MAX_IMAGES, "最多 " + MAX_IMAGES + " 张图片");
        Pattern allowed = Pattern.compile("^" + Pattern.quote(urlPrefix) + "/notes/\\d{4}/\\d{2}/[0-9a-f]{32}\\.(png|jpg|gif|webp)$");
        BizException.throwIf(images.stream().anyMatch(u -> !allowed.matcher(u).matches()), "图片地址不正确，请重新上传");
        if (content.isEmpty() && images.isEmpty()) {
            delete(userId, questionId);
            return null;
        }
        Question q = BizException.notNull(questionMapper.selectById(questionId), "题目不存在");
        QuestionNote n = find(userId, questionId);
        if (n == null) {
            n = new QuestionNote();
            n.setUserId(userId);
            n.setQuestionId(q.getId());
            n.setModuleId(q.getModuleId());
            n.setBankId(q.getBankId());
            n.setChapterId(q.getChapterId());
            n.setContent(content);
            n.setImages(JsonUtil.toJson(images));
            try {
                mapper.insert(n);
            } catch (DuplicateKeyException e) {
                // 并发保存：另一次请求已经建好，改成更新
                return save(userId, questionId, content, images);
            }
        } else {
            n.setContent(content);
            n.setImages(JsonUtil.toJson(images));
            mapper.updateById(n);
        }
        return toVO(find(userId, questionId));
    }

    public void delete(Long userId, Long questionId) {
        mapper.delete(Wrappers.<QuestionNote>lambdaQuery()
                .eq(QuestionNote::getUserId, userId).eq(QuestionNote::getQuestionId, questionId));
    }

    /** 上传笔记图片（每人每天限量），返回访问地址 */
    public String uploadImage(Long userId, MultipartFile file) {
        String key = "note:upload:" + userId + ":" + LocalDate.now();
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) {
            redis.expire(key, Duration.ofDays(1));
        }
        BizException.throwIf(n != null && n > DAILY_UPLOADS, "今天上传的图片太多了，明天再试");
        return fileService.uploadImage(file, "notes");
    }

    // ==================== 工具 ====================

    private QuestionNote find(Long userId, Long questionId) {
        return mapper.selectOne(Wrappers.<QuestionNote>lambdaQuery()
                .eq(QuestionNote::getUserId, userId).eq(QuestionNote::getQuestionId, questionId));
    }

    private static NoteVO toVO(QuestionNote n) {
        return new NoteVO(n.getContent() == null ? "" : n.getContent(), images(n), n.getUpdatedAt());
    }

    private static List<String> images(QuestionNote n) {
        List<String> list = n.getImages() == null ? null : JsonUtil.parse(n.getImages(), IMAGES_TYPE);
        return list == null ? List.of() : list;
    }

    private static String preview(String html) {
        String text = HtmlUtil.htmlToText(html).replaceAll("\\s+", " ").strip();
        return text.length() > STEM_PREVIEW ? text.substring(0, STEM_PREVIEW) + "…" : text;
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
