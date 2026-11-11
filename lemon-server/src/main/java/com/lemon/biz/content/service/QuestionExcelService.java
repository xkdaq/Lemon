package com.lemon.biz.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.biz.content.dto.QuestionReqs.QuestionQuery;
import com.lemon.biz.content.dto.QuestionReqs.QuestionSave;
import com.lemon.biz.content.dto.QuestionVO;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.BankType;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.CourseMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 题目 Excel 导入导出。
 * <p>
 * 列（按表头名识别，顺序无所谓，多余列忽略）：科目、章节、题型、题干、选项A~选项H、答案、解析、分值、难度、来源。
 * 导入时页面先选题库；若同时选了章节，则所有题目进该章节（忽略科目/章节列），
 * 否则按“科目 / 章节”名称归类，不存在的自动创建。
 * <p>
 * 导入是全有或全无：先逐行校验，只要有一行出错就一条都不保存，返回每行的错误原因。
 */
@Service
@RequiredArgsConstructor
public class QuestionExcelService {

    public static final int MAX_ROWS = 2000;
    private static final int MAX_EXPORT = 5000;
    private static final int MAX_ERRORS = 200;
    private static final int OPTION_COUNT = QuestionNormalizer.MAX_OPTIONS;

    static final String H_COURSE = "科目";
    static final String H_CHAPTER = "章节";
    static final String H_TYPE = "题型";
    static final String H_STEM = "题干";
    static final String H_ANSWER = "答案";
    static final String H_ANALYSIS = "解析";
    static final String H_SCORE = "分值";
    static final String H_DIFFICULTY = "难度";
    static final String H_SOURCE = "来源";
    static final String H_SECTION = "小节";

    private static final Map<String, Integer> TYPE_ALIASES = Map.ofEntries(
            Map.entry("单选", 1), Map.entry("单选题", 1), Map.entry("1", 1),
            Map.entry("多选", 2), Map.entry("多选题", 2), Map.entry("2", 2),
            Map.entry("判断", 3), Map.entry("判断题", 3), Map.entry("3", 3),
            Map.entry("主观", 4), Map.entry("主观题", 4), Map.entry("简答", 4), Map.entry("简答题", 4),
            Map.entry("论述", 4), Map.entry("论述题", 4), Map.entry("分析", 4), Map.entry("分析题", 4),
            Map.entry("材料分析题", 4), Map.entry("辨析", 4), Map.entry("辨析题", 4), Map.entry("4", 4),
            Map.entry("问答", 4), Map.entry("问答题", 4), Map.entry("材料", 4), Map.entry("材料题", 4),
            Map.entry("案例分析题", 4), Map.entry("名词解释", 4), Map.entry("填空题", 4));

    private static final Map<String, Integer> DIFFICULTY_ALIASES = Map.of(
            "简单", 1, "易", 1, "1", 1, "中等", 2, "中", 2, "2", 2, "困难", 3, "难", 3, "3", 3);

    private static final String[] DIFFICULTY_LABELS = {"", "简单", "中等", "困难"};

    /** 题干开头的题号：可先有 HTML 标签（如“<p>12.”），再是 数字 + . ． 、 */
    private static final java.util.regex.Pattern LEADING_NUMBER =
            java.util.regex.Pattern.compile("^\\s*((?:<[^>]+>\\s*)*)(\\d{1,3})\\s*[.．、]\\s*");

    /**
     * 计算每行题干开头应去掉的题号。同一组（章节 / 套卷）内按 1、2、3… 顺序匹配，
     * 材料下的小题等不带题号的行跳过不影响计数。至少一半的行匹配上才认为文件带题号，
     * 否则全部返回 null，避免把“1.5 倍……”这种本身以数字开头的题干误删。
     */
    static List<Integer> sequentialNumbers(List<String> stems, List<String> groups) {
        Map<String, Integer> expected = new HashMap<>();
        List<Integer> result = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < stems.size(); i++) {
            int next = expected.getOrDefault(groups.get(i), 1);
            var m = LEADING_NUMBER.matcher(stems.get(i));
            if (m.find() && Integer.parseInt(m.group(2)) == next) {
                result.add(next);
                expected.put(groups.get(i), next + 1);
                matched++;
            } else {
                result.add(null);
            }
        }
        if (matched * 2 < stems.size()) {
            return java.util.Collections.nCopies(stems.size(), null);
        }
        return result;
    }

    /** 去掉题干开头与给定题号一致的题号（保留前面的 HTML 标签）；题号为 null 或对不上时原样返回 */
    static String stripNumber(String stem, Integer questionNo) {
        if (questionNo == null) {
            return stem;
        }
        var m = LEADING_NUMBER.matcher(stem);
        return m.find() && Integer.parseInt(m.group(2)) == questionNo ? m.group(1) + stem.substring(m.end()) : stem;
    }

    private final QuestionAdminService questionAdminService;
    private final QuestionBankMapper bankMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;

    // ==================== 模板 ====================

    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = writeHeader(wb, "题目");
            String[][] samples = {
                    {"马原", "第一章 世界的物质性", "单选", "物质的唯一特性是", "客观实在性", "运动", "可知性", "绝对性",
                            "", "", "", "", "A", "物质的唯一特性是客观实在性。", "1", "简单", "2023 真题", "单选题"},
                    {"马原", "第一章 世界的物质性", "多选", "下列属于意识能动作用的有", "目的性", "创造性", "指导实践", "无条件性",
                            "", "", "", "", "ABC", "", "2", "中等", ""},
                    {"马原", "第一章 世界的物质性", "判断", "运动是物质的根本属性。", "", "", "", "",
                            "", "", "", "", "对", "", "", "", ""},
                    {"马原", "第二章 实践与认识", "主观", "论述实践与认识的辩证关系。", "", "", "", "",
                            "", "", "", "", "", "（1）实践决定认识……\n（2）认识反作用于实践……", "10", "困难", "", "论述题"},
            };
            for (int i = 0; i < samples.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < samples[i].length; c++) {
                    row.createCell(c).setCellValue(samples[i][c]);
                }
            }

            Sheet help = wb.createSheet("填写说明");
            String[] lines = {
                    "1. 第一行是表头，列名不要改；列的顺序可以调整，多余的列会被忽略。",
                    "   也兼容题库导出格式：“题目”“分数”“一级目录”分别当作“题干”“分值”“章节”；题干开头的题号（如“12.”）会自动去掉；内容已是 HTML 的会保留格式。",
                    "2. 题型：单选 / 多选 / 判断 / 主观（简答、论述、问答、分析、辨析都按主观题处理）。",
                    "   材料题：题型填“材料题”、只填题干（材料内容），后面紧跟的几道不带题号的主观题作为它的小题，材料会附在每道小题前面。",
                    "3. 选项：填在“选项A”~“选项H”，单选和多选至少 2 个；判断题可以不填，默认 A 正确 / B 错误。",
                    "4. 答案：单选填一个字母，如 A；多选填多个字母，如 ABD（A,B,D 也可以）；判断填 对/错 或 A/B；主观题不填。",
                    "5. 主观题的参考答案可以填在“答案”或“解析”里，两者都填时解析接在参考答案后面；都不填则作为背诵题（题干即背诵内容）。单元格内换行会保留为分段。",
                    "6. 分值：可不填，默认 0；难度：简单 / 中等 / 困难，可不填，默认中等。",
                    "7. 科目 / 章节：导入时如果在页面上选了章节，这两列会被忽略；",
                    "   否则科目型题库要填“科目”和“章节”，试卷型题库只看“章节”（即试卷名，“科目”列忽略），不存在的会自动创建；",
                    "   自动创建的试卷总分为导入题目分值之和，考试时长默认 180 分钟，可在后台修改。",
                    "8. 小节：章节 / 试卷下的分组，如“论述题”“材料分析题”，用户端可以按小节单独练习；不填则按题型（单选题 / 多选题 / 判断题 / 分析题）分组。题库导出的“二级目录”会当作小节。",
                    "9. 一次最多 " + MAX_ROWS + " 题。只要有一行出错，整份文件都不会导入，请按提示改好后重新上传。",
            };
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 120 * 256);
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== 导出 ====================

    public byte[] export(QuestionQuery query) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = writeHeader(wb, "题目");
            // 导出额外附带 ID / 题库 / 状态，方便核对；重新导入时这些列会被忽略
            Row header = sheet.getRow(0);
            int extra = header.getLastCellNum();
            String[] extraHeaders = {"ID", "题库", "状态"};
            for (int i = 0; i < extraHeaders.length; i++) {
                header.createCell(extra + i).setCellValue(extraHeaders[i]);
                header.getCell(extra + i).setCellStyle(header.getCell(0).getCellStyle());
            }

            int rowIdx = 1;
            query.setPageSize(500);
            for (long page = 1; rowIdx <= MAX_EXPORT; page++) {
                query.setPageNum(page);
                PageResult<QuestionVO> result = questionAdminService.page(query);
                for (QuestionVO q : result.getList()) {
                    writeQuestionRow(sheet.createRow(rowIdx++), q, extra);
                }
                if (result.getList().size() < 500) {
                    break;
                }
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeQuestionRow(Row row, QuestionVO q, int extraCol) {
        int c = 0;
        row.createCell(c++).setCellValue(Objects.toString(q.getCourseName(), ""));
        row.createCell(c++).setCellValue(Objects.toString(q.getChapterName(), ""));
        row.createCell(c++).setCellValue(QuestionType.of(q.getType()).getLabel().replace("题", ""));
        row.createCell(c++).setCellValue(HtmlUtil.htmlToText(q.getStem()));
        Map<String, String> opts = new HashMap<>();
        q.getOptions().forEach(o -> opts.put(o.key(), o.content()));
        for (int i = 0; i < OPTION_COUNT; i++) {
            row.createCell(c++).setCellValue(opts.getOrDefault(String.valueOf((char) ('A' + i)), ""));
        }
        row.createCell(c++).setCellValue(Objects.toString(q.getAnswer(), ""));
        row.createCell(c++).setCellValue(HtmlUtil.htmlToText(q.getAnalysis()));
        row.createCell(c++).setCellValue(q.getScore() == null ? "" : q.getScore().stripTrailingZeros().toPlainString());
        int d = q.getDifficulty() == null ? 2 : q.getDifficulty();
        row.createCell(c++).setCellValue(d >= 1 && d <= 3 ? DIFFICULTY_LABELS[d] : "");
        row.createCell(c++).setCellValue(Objects.toString(q.getSource(), ""));
        row.createCell(c).setCellValue(Objects.toString(q.getSection(), ""));
        row.createCell(extraCol).setCellValue(q.getId());
        row.createCell(extraCol + 1).setCellValue(Objects.toString(q.getBankName(), ""));
        row.createCell(extraCol + 2).setCellValue(q.getStatus() != null && q.getStatus() == Status.ON ? "上架" : "下架");
    }

    // ==================== 导入 ====================

    public record RowError(int row, String message) {
    }

    /**
     * @param total           数据行数
     * @param imported        成功导入题数（有错误时为 0）
     * @param createdCourses  自动新建的科目数
     * @param createdChapters 自动新建的章节数
     * @param errors          错误（最多返回 200 条）
     */
    public record ImportResult(int total, int imported, int createdCourses, int createdChapters, List<RowError> errors) {
    }

    /** 解析后的一行 */
    private record ParsedRow(int row, String courseName, String chapterName, QuestionSave save) {
    }

    @Transactional
    public ImportResult importQuestions(Long bankId, Long chapterId, InputStream in) {
        QuestionBank bank = BizException.notNull(bankMapper.selectById(bankId), "题库不存在");
        boolean courseBank = BankType.of(bank.getType()) == BankType.COURSE;
        if (chapterId != null) {
            Chapter chapter = BizException.notNull(chapterMapper.selectById(chapterId), "章节不存在");
            BizException.throwIf(!Objects.equals(chapter.getBankId(), bankId), "章节不属于该题库");
        }

        List<RowError> errors = new ArrayList<>();
        List<ParsedRow> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> cols = readHeader(sheet.getRow(sheet.getFirstRowNum()), fmt);
            for (String required : List.of(H_TYPE, H_STEM)) {
                BizException.throwIf(!cols.containsKey(required), "表头缺少“" + required + "”列，请使用导入模板");
            }

            // 先收集非空行
            List<Integer> excelRows = new ArrayList<>();
            List<CellReader> readers = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                CellReader cell = name -> {
                    Integer idx = cols.get(name);
                    if (row == null || idx == null) {
                        return "";
                    }
                    Cell c = row.getCell(idx);
                    return c == null ? "" : fmt.formatCellValue(c, evaluator).strip();
                };
                if (isBlankRow(cell, cols)) {
                    continue;
                }
                BizException.throwIf(readers.size() >= MAX_ROWS, "一次最多导入 " + MAX_ROWS + " 题，请拆分文件");
                excelRows.add(r + 1);
                readers.add(cell);
            }

            // 按章节（套卷）分组：页面上选了章节则整份文件一组
            List<String> groups = readers.stream()
                    .map(c -> chapterId != null ? "" : (courseBank ? c.get(H_COURSE) : "") + "/" + c.get(H_CHAPTER))
                    .toList();
            List<Integer> numbers = sequentialNumbers(readers.stream().map(c -> c.get(H_STEM)).toList(), groups);

            // 材料题：材料行本身不导入，内容附在它后面的小题（不带题号的主观题）题干前
            Map<String, String> materials = new HashMap<>();
            Map<String, Integer> unusedMaterialRow = new HashMap<>();
            for (int i = 0; i < readers.size(); i++) {
                CellReader cell = readers.get(i);
                int excelRow = excelRows.get(i);
                String group = groups.get(i);
                try {
                    if (isMaterialRow(cell)) {
                        String material = toHtml(stripNumber(cell.get(H_STEM), numbers.get(i)));
                        BizException.throwIf(material == null || HtmlUtil.isBlank(material), "材料题的材料内容不能为空");
                        failIfUnused(unusedMaterialRow.put(group, excelRow));
                        materials.put(group, material);
                        continue;
                    }
                    Integer type = TYPE_ALIASES.get(cell.get(H_TYPE).replace(" ", ""));
                    // 带题号的行是新的大题，不属于前面的材料；不带题号的主观题才是材料下的小题
                    boolean subQuestion = type != null && type == QuestionType.SUBJECTIVE.getCode()
                            && (numbers.get(i) == null) && materials.containsKey(group);
                    if (!subQuestion) {
                        failIfUnused(unusedMaterialRow.remove(group));
                        materials.remove(group);
                    } else {
                        unusedMaterialRow.remove(group);
                    }
                    rows.add(parseRow(excelRow, cell, chapterId != null, courseBank, numbers.get(i),
                            subQuestion ? materials.get(group) : null));
                } catch (BizException e) {
                    if (errors.size() < MAX_ERRORS) {
                        errors.add(new RowError(excelRow, e.getMessage()));
                    } else {
                        break;
                    }
                }
            }
            unusedMaterialRow.values().forEach(r -> errors.add(new RowError(r, "材料题后面没有小题")));
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw new BizException("无法读取文件，请上传 .xlsx 格式的 Excel");
        }

        int total = rows.size() + errors.size();
        BizException.throwIf(total == 0, "文件里没有题目");
        if (!errors.isEmpty()) {
            return new ImportResult(total, 0, 0, 0, errors);
        }

        // 校验全部通过：解析章节（按名称查找或创建），再批量写入
        ChapterResolver resolver = new ChapterResolver(bank);
        List<QuestionSave> saves = rows.stream().map(p -> {
            Long target = chapterId != null ? chapterId : resolver.resolve(p.courseName(), p.chapterName());
            QuestionSave s = p.save();
            return new QuestionSave(target, s.type(), s.stem(), s.options(), s.answer(), s.analysis(),
                    s.score(), s.difficulty(), s.source(), null, Status.ON, s.section());
        }).toList();
        int imported = questionAdminService.createBatch(saves);
        if (!courseBank) {
            fillNewPapers(resolver.createdChapterIds, saves);
        }
        return new ImportResult(total, imported, resolver.createdCourses, resolver.createdChapters, List.of());
    }

    /** 默认考试时长（分钟），导入时新建的试卷使用，之后可在后台修改 */
    private static final int DEFAULT_PAPER_MINUTES = 180;

    /** 导入时新建的试卷：总分取导入题目的分值之和，时长默认 180 分钟 */
    private void fillNewPapers(List<Long> chapterIds, List<QuestionSave> saves) {
        for (Long id : chapterIds) {
            BigDecimal sum = saves.stream().filter(q -> id.equals(q.chapterId()))
                    .map(q -> Objects.requireNonNullElse(q.score(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
            Chapter c = new Chapter();
            c.setId(id);
            c.setTotalScore(sum.setScale(0, java.math.RoundingMode.HALF_UP).intValue());
            c.setTimeLimit(DEFAULT_PAPER_MINUTES);
            chapterMapper.updateById(c);
        }
    }

    /** 材料行后紧跟的不是小题：材料没被任何小题使用，视为数据错误 */
    private static void failIfUnused(Integer materialRow) {
        if (materialRow != null) {
            throw new BizException("第 " + materialRow + " 行的材料题后面没有小题");
        }
    }

    /** 材料题：题型为“材料 / 材料题”，且没有答案和解析（只有材料内容） */
    private static boolean isMaterialRow(CellReader cell) {
        String type = cell.get(H_TYPE).replace(" ", "");
        return ("材料".equals(type) || "材料题".equals(type)) && cell.get(H_ANSWER).isEmpty() && cell.get(H_ANALYSIS).isEmpty();
    }

    private static final java.util.regex.Pattern HTML_TAG = java.util.regex.Pattern.compile(
            "<\\s*/?\\s*(p|br|span|strong|b|i|u|em|div|img|ul|ol|li|table|tr|td|sub|sup|blockquote)\\b[^<>]*>",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** 单元格内容转 HTML：已经是 HTML（题库导出常见）则原样保留（保存时统一白名单过滤），否则按纯文本分段 */
    static String toHtml(String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        return HTML_TAG.matcher(cell).find() ? cell.strip() : HtmlUtil.textToHtml(cell);
    }

    @FunctionalInterface
    private interface CellReader {
        String get(String header);
    }

    /**
     * @param questionNo 文件带题号时为本行应去掉的题号；不带题号时为 null
     * @param material   材料题下的小题：所属材料（HTML），会以引用块放在题干前
     */
    private ParsedRow parseRow(int excelRow, CellReader cell, boolean chapterFixed, boolean courseBank,
                               Integer questionNo, String material) {
        Integer type = TYPE_ALIASES.get(cell.get(H_TYPE).replace(" ", ""));
        if (type == null) {
            throw new BizException("题型“" + cell.get(H_TYPE) + "”无法识别，应为 单选 / 多选 / 判断 / 主观（问答、论述、材料等）");
        }
        // 去掉题干开头的题号（如“12.”“3、”），题号由用户端按顺序显示
        String stemHtml = toHtml(stripNumber(cell.get(H_STEM), questionNo));
        BizException.throwIf(stemHtml == null || HtmlUtil.isBlank(stemHtml), "题干不能为空");
        if (material != null) {
            stemHtml = "<blockquote>" + material + "</blockquote>" + stemHtml;
        }

        List<OptionItem> options = new ArrayList<>();
        for (int i = 0; i < OPTION_COUNT; i++) {
            String key = String.valueOf((char) ('A' + i));
            String content = cell.get("选项" + key);
            if (!content.isEmpty()) {
                // 选项只存纯文本
                options.add(new OptionItem(key, HTML_TAG.matcher(content).find() ? HtmlUtil.htmlToText(content) : content));
            }
        }

        String answer = cell.get(H_ANSWER);
        String analysisHtml = toHtml(cell.get(H_ANALYSIS));
        if (type == QuestionType.SUBJECTIVE.getCode()) {
            // 主观题：“答案”列是参考答案；同时有解析时接在参考答案后面
            String reference = toHtml(answer);
            if (reference != null && !HtmlUtil.isBlank(reference)) {
                analysisHtml = analysisHtml == null || HtmlUtil.isBlank(analysisHtml)
                        ? reference
                        : reference + "<p><strong>解析</strong></p>" + analysisHtml;
            }
            answer = null;
        }
        // 用和手工录入完全相同的规则校验，保证导入的数据一样合规
        QuestionNormalizer.normalize(type, options, answer, analysisHtml);

        // 试卷型题库没有科目，“科目”列忽略（题库供应商导出的“科目”通常是套卷系列名）
        String courseName = courseBank ? cell.get(H_COURSE) : "";
        String chapterName = cell.get(H_CHAPTER);
        if (!chapterFixed) {
            if (courseBank) {
                BizException.throwIf(courseName.isEmpty() || chapterName.isEmpty(),
                        "科目型题库请填写“科目”和“章节”（或在页面上直接选择章节）");
            } else {
                BizException.throwIf(chapterName.isEmpty(), "请填写“章节”（试卷名），或在页面上直接选择试卷");
            }
            BizException.throwIf(courseName.length() > 64, "科目名称不能超过 64 个字");
            BizException.throwIf(chapterName.length() > 128, "章节名称不能超过 128 个字");
        }

        QuestionSave save = new QuestionSave(null, type, stemHtml, options, answer,
                analysisHtml, parseScore(cell.get(H_SCORE)), parseDifficulty(cell.get(H_DIFFICULTY)),
                StringUtils.hasText(cell.get(H_SOURCE)) ? truncate(cell.get(H_SOURCE), 64) : null, null, Status.ON,
                sectionOf(type, cell.get(H_SECTION)));
        return new ParsedRow(excelRow, courseName, chapterName, save);
    }

    /** 小节：不填为 null（按题型分组）；客观题的笼统写法“选择题”换成具体题型，避免同一题库里“选择题”“单选题”混用 */
    static String sectionOf(int type, String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String section = truncate(raw.strip(), 32);
        if ("选择题".equals(section) && QuestionType.of(type).isAutoJudge()) {
            return QuestionType.of(type).getLabel();
        }
        return section;
    }

    private static BigDecimal parseScore(String raw) {
        if (raw.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal v = new BigDecimal(raw);
            BizException.throwIf(v.signum() < 0 || v.compareTo(new BigDecimal("999.9")) > 0, "分值应在 0~999.9 之间");
            return v;
        } catch (NumberFormatException e) {
            throw new BizException("分值“" + raw + "”不是数字");
        }
    }

    private static Integer parseDifficulty(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        Integer d = DIFFICULTY_ALIASES.get(raw);
        if (d == null) {
            throw new BizException("难度“" + raw + "”无法识别，应为 简单 / 中等 / 困难");
        }
        return d;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isBlankRow(CellReader cell, Map<String, Integer> cols) {
        return cols.keySet().stream().allMatch(h -> cell.get(h).isEmpty());
    }

    /** 常见的其他表头写法 → 模板表头（题库供应商导出的文件常用“题目”“分数”） */
    private static final Map<String, String> HEADER_ALIASES = Map.of(
            "题目", H_STEM, "题目内容", H_STEM, "分数", H_SCORE, "正确答案", H_ANSWER, "答案解析", H_ANALYSIS,
            "一级目录", H_CHAPTER, "二级目录", H_SECTION, "节", H_SECTION);

    /** 表头名 → 列下标；表头里的空格忽略；模板表头不存在时接受别名 */
    private static Map<String, Integer> readHeader(Row header, DataFormatter fmt) {
        BizException.throwIf(header == null, "第一行应为表头，请使用导入模板");
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (Cell c : header) {
            String name = fmt.formatCellValue(c).replaceAll("\\s", "");
            if (!name.isEmpty()) {
                cols.putIfAbsent(name, c.getColumnIndex());
            }
        }
        HEADER_ALIASES.forEach((alias, canonical) -> {
            if (!cols.containsKey(canonical) && cols.containsKey(alias)) {
                cols.put(canonical, cols.get(alias));
            }
        });
        return cols;
    }

    /** 按名称查找章节，找不到则在本题库下创建（科目同理），同一次导入内缓存结果 */
    private final class ChapterResolver {

        private final QuestionBank bank;
        private final Map<String, Long> courseIds = new HashMap<>();
        private final Map<String, Long> chapterIds = new HashMap<>();
        int createdCourses;
        int createdChapters;
        final List<Long> createdChapterIds = new ArrayList<>();

        ChapterResolver(QuestionBank bank) {
            this.bank = bank;
        }

        Long resolve(String courseName, String chapterName) {
            Long courseId = courseName.isEmpty() ? null : courseIds.computeIfAbsent(courseName, this::findOrCreateCourse);
            return chapterIds.computeIfAbsent(courseId + "/" + chapterName, k -> findOrCreateChapter(courseId, chapterName));
        }

        private Long findOrCreateCourse(String name) {
            Course existing = courseMapper.selectOne(Wrappers.<Course>lambdaQuery()
                    .eq(Course::getBankId, bank.getId()).eq(Course::getName, name).last("LIMIT 1"));
            if (existing != null) {
                return existing.getId();
            }
            Course c = new Course();
            c.setBankId(bank.getId());
            c.setName(name);
            c.setSort((int) (courseMapper.selectCount(Wrappers.<Course>lambdaQuery().eq(Course::getBankId, bank.getId())) + 1));
            c.setStatus(Status.ON);
            courseMapper.insert(c);
            createdCourses++;
            return c.getId();
        }

        private Long findOrCreateChapter(Long courseId, String name) {
            Chapter existing = chapterMapper.selectOne(Wrappers.<Chapter>lambdaQuery()
                    .eq(Chapter::getBankId, bank.getId())
                    .eq(courseId != null, Chapter::getCourseId, courseId)
                    .isNull(courseId == null, Chapter::getCourseId)
                    .eq(Chapter::getName, name).last("LIMIT 1"));
            if (existing != null) {
                return existing.getId();
            }
            Chapter c = new Chapter();
            c.setBankId(bank.getId());
            c.setCourseId(courseId);
            c.setName(name);
            c.setSort((int) (chapterMapper.selectCount(Wrappers.<Chapter>lambdaQuery()
                    .eq(Chapter::getBankId, bank.getId())
                    .eq(courseId != null, Chapter::getCourseId, courseId)) + 1));
            c.setStatus(Status.ON);
            c.setQuestionCount(0);
            chapterMapper.insert(c);
            createdChapters++;
            createdChapterIds.add(c.getId());
            return c.getId();
        }
    }

    // ==================== 工具 ====================

    /** 写表头：科目、章节、题型、题干、选项A~H、答案、解析、分值、难度、来源 */
    private static Sheet writeHeader(Workbook wb, String sheetName) {
        Sheet sheet = wb.createSheet(sheetName);
        List<String> headers = new ArrayList<>(List.of(H_COURSE, H_CHAPTER, H_TYPE, H_STEM));
        for (int i = 0; i < OPTION_COUNT; i++) {
            headers.add("选项" + (char) ('A' + i));
        }
        headers.addAll(List.of(H_ANSWER, H_ANALYSIS, H_SCORE, H_DIFFICULTY, H_SOURCE, H_SECTION));

        CellStyle style = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(style);
            int width = switch (headers.get(i)) {
                case H_STEM, H_ANALYSIS -> 50;
                case H_CHAPTER -> 24;
                default -> headers.get(i).startsWith("选项") ? 18 : 10;
            };
            sheet.setColumnWidth(i, width * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
