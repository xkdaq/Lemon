package com.lemon.biz.collection;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.access.service.AccessService;
import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.CourseMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.practice.dto.PracticeDtos.Scope;
import com.lemon.biz.wrong.entity.UserWrongQuestion;
import com.lemon.biz.wrong.mapper.UserWrongQuestionMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.JsonUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

/**
 * 错题集按题库导出 Word：按 科目 → 章节 分节，题目连续编号。
 * 答案解析可以跟在每题后面，也可以集中放在文末（打印出来重做时不会先看到答案）。
 */
@Service
@RequiredArgsConstructor
public class WrongExportService {

    /** 一次最多导出的题数，避免文件过大 */
    static final int MAX_QUESTIONS = 2000;
    private static final String FONT = "宋体";
    private static final java.util.regex.Pattern LEADING_NO = java.util.regex.Pattern.compile("^\\d{1,3}\\s*[.．、]\\s*");
    private static final TypeReference<List<OptionItem>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private final AccessService accessService;
    private final UserWrongQuestionMapper wrongMapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankMapper bankMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    /** 导出结果 */
    public record ExportFile(String filename, byte[] content) {
    }

    /**
     * @param answersAtEnd true：答案解析集中放在文末；false：跟在每题后面
     */
    public ExportFile export(Long userId, Long bankId, boolean answersAtEnd) {
        QuestionBank bank = BizException.notNull(bankMapper.selectById(bankId), "题库不存在");
        accessService.requireAccess(userId, bank.getModuleId());
        List<UserWrongQuestion> wrongs = wrongMapper.selectList(
                CollectionService.wrongScope(userId, new Scope(null, bankId, null, null)));
        BizException.throwIf(wrongs.isEmpty(), "这个题库里没有错题");
        BizException.throwIf(wrongs.size() > MAX_QUESTIONS, "错题太多，一次最多导出 " + MAX_QUESTIONS + " 题，请按章节练习后再导出");

        Map<Long, Question> questions = questionMapper.selectByIds(wrongs.stream().map(UserWrongQuestion::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, Chapter> chapters = chapterMapper.selectByIds(questions.values().stream().map(Question::getChapterId)
                        .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Chapter::getId, Function.identity()));
        List<Long> courseIds = chapters.values().stream().map(Chapter::getCourseId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of() : courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<Long, UserWrongQuestion> wrongByQ = wrongs.stream()
                .collect(Collectors.toMap(UserWrongQuestion::getQuestionId, Function.identity(), (a, b) -> a));

        // 按 科目排序 → 章节排序 → 题目排序 排列，和做题时的顺序一致
        Comparator<Question> order = Comparator
                .comparing((Question q) -> courseSort(courses, chapters.get(q.getChapterId())))
                .thenComparing(q -> sortOf(chapters.get(q.getChapterId())))
                .thenComparing(q -> Objects.requireNonNullElse(q.getSort(), 0))
                .thenComparing(Question::getId);
        List<Question> ordered = questions.values().stream().sorted(order).toList();
        boolean multiCourse = ordered.stream().map(q -> courseOf(chapters.get(q.getChapterId()))).distinct().count() > 1;

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            run(title, bank.getName() + " · 错题", 18, true);
            XWPFParagraph sub = doc.createParagraph();
            sub.setAlignment(ParagraphAlignment.CENTER);
            run(sub, "共 " + ordered.size() + " 题 · 导出于 " + LocalDate.now(), 10, false).setColor("808080");

            Long lastCourse = -1L;
            Long lastChapter = -1L;
            List<Question> numbered = new ArrayList<>();
            for (Question q : ordered) {
                Chapter ch = chapters.get(q.getChapterId());
                Long courseId = courseOf(ch);
                if (multiCourse && !Objects.equals(courseId, lastCourse)) {
                    Course c = courseId == null ? null : courses.get(courseId);
                    heading(doc, c == null ? "其他" : c.getName(), 15);
                    lastCourse = courseId;
                }
                if (!Objects.equals(q.getChapterId(), lastChapter)) {
                    heading(doc, ch == null ? "（章节已删除）" : ch.getName(), 13);
                    lastChapter = q.getChapterId();
                }
                numbered.add(q);
                writeQuestion(doc, numbered.size(), q);
                if (!answersAtEnd) {
                    writeAnswer(doc, null, q, wrongByQ.get(q.getId()));
                }
            }
            if (answersAtEnd) {
                doc.createParagraph().setPageBreak(true);
                heading(doc, "答案与解析", 15);
                for (int i = 0; i < numbered.size(); i++) {
                    writeAnswer(doc, i + 1, numbered.get(i), wrongByQ.get(numbered.get(i).getId()));
                }
            }
            doc.write(out);
            return new ExportFile(safeName(bank.getName()) + "-错题.docx", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeQuestion(XWPFDocument doc, int no, Question q) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(160);
        run(p, no + ". ", 11, true);
        run(p, "【" + typeLabel(q) + "】", 11, false).setColor("B4432F");
        // 老题库的题干开头常带原来的题号（如“5.”），导出时重新编号，去掉原题号
        lines(p, LEADING_NO.matcher(HtmlUtil.htmlToText(q.getStem()).strip()).replaceFirst(""), 11, false);
        List<OptionItem> options = JsonUtil.parse(q.getOptions(), OPTIONS_TYPE);
        if (options != null) {
            for (OptionItem o : options) {
                XWPFParagraph op = doc.createParagraph();
                op.setIndentationLeft(360);
                run(op, o.key() + ". " + Objects.requireNonNullElse(o.content(), ""), 11, false);
            }
        }
    }

    /**
     * @param no 文末答案区的题号；跟在题后时为 null
     */
    private void writeAnswer(XWPFDocument doc, Integer no, Question q, UserWrongQuestion w) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(no == null ? 360 : 0);
        p.setSpacingBefore(no == null ? 40 : 120);
        if (no != null) {
            run(p, no + ". ", 10.5, true);
        }
        boolean subjective = q.getType() == QuestionType.SUBJECTIVE.getCode();
        if (!subjective) {
            run(p, "答案：", 10.5, true);
            run(p, Objects.requireNonNullElse(q.getAnswer(), "") + "    ", 10.5, false).setColor("2F855A");
            if (w != null && w.getLastWrongAnswer() != null && !w.getLastWrongAnswer().isBlank()) {
                run(p, "我的答案：", 10.5, true);
                run(p, w.getLastWrongAnswer() + "    ", 10.5, false).setColor("C2410C");
            }
        }
        if (w != null) {
            run(p, "答错 " + w.getWrongCount() + " 次", 10.5, false).setColor("808080");
        }
        String analysis = q.getAnalysis() == null ? "" : HtmlUtil.htmlToText(q.getAnalysis()).strip();
        if (!analysis.isEmpty()) {
            XWPFParagraph ap = doc.createParagraph();
            ap.setIndentationLeft(360);
            run(ap, subjective ? "参考答案：" : "解析：", 10.5, true);
            lines(ap, analysis, 10.5, false);
        } else if (subjective) {
            XWPFParagraph ap = doc.createParagraph();
            ap.setIndentationLeft(360);
            run(ap, "背诵题：熟读题干内容", 10.5, false).setColor("808080");
        }
    }

    private static void heading(XWPFDocument doc, String text, double size) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(320);
        p.setSpacingAfter(80);
        run(p, text, size, true);
    }

    /** 多行文本：保留换行 */
    private static void lines(XWPFParagraph p, String text, double size, boolean bold) {
        String[] parts = text.split("\n");
        for (int i = 0; i < parts.length; i++) {
            XWPFRun r = run(p, parts[i], size, bold);
            if (i < parts.length - 1) {
                r.addBreak();
            }
        }
    }

    private static XWPFRun run(XWPFParagraph p, String text, double size, boolean bold) {
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(size);
        r.setBold(bold);
        r.setFontFamily(FONT);
        return r;
    }

    private static String typeLabel(Question q) {
        try {
            return QuestionType.of(q.getType()).getLabel();
        } catch (BizException e) {
            return "题目";
        }
    }

    private static Long courseOf(Chapter c) {
        return c == null ? null : c.getCourseId();
    }

    private static int courseSort(Map<Long, Course> courses, Chapter c) {
        Course course = c == null || c.getCourseId() == null ? null : courses.get(c.getCourseId());
        return course == null || course.getSort() == null ? Integer.MAX_VALUE : course.getSort();
    }

    private static int sortOf(Chapter c) {
        return c == null || c.getSort() == null ? Integer.MAX_VALUE : c.getSort();
    }

    /** 文件名去掉不合法字符 */
    static String safeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "");
    }
}
