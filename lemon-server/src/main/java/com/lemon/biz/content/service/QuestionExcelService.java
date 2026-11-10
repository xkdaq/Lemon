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

    private static final Map<String, Integer> TYPE_ALIASES = Map.ofEntries(
            Map.entry("单选", 1), Map.entry("单选题", 1), Map.entry("1", 1),
            Map.entry("多选", 2), Map.entry("多选题", 2), Map.entry("2", 2),
            Map.entry("判断", 3), Map.entry("判断题", 3), Map.entry("3", 3),
            Map.entry("主观", 4), Map.entry("主观题", 4), Map.entry("简答", 4), Map.entry("简答题", 4),
            Map.entry("论述", 4), Map.entry("论述题", 4), Map.entry("分析", 4), Map.entry("分析题", 4),
            Map.entry("材料分析题", 4), Map.entry("辨析", 4), Map.entry("辨析题", 4), Map.entry("4", 4));

    private static final Map<String, Integer> DIFFICULTY_ALIASES = Map.of(
            "简单", 1, "易", 1, "1", 1, "中等", 2, "中", 2, "2", 2, "困难", 3, "难", 3, "3", 3);

    private static final String[] DIFFICULTY_LABELS = {"", "简单", "中等", "困难"};

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
                            "", "", "", "", "A", "物质的唯一特性是客观实在性。", "1", "简单", "2023 真题"},
                    {"马原", "第一章 世界的物质性", "多选", "下列属于意识能动作用的有", "目的性", "创造性", "指导实践", "无条件性",
                            "", "", "", "", "ABC", "", "2", "中等", ""},
                    {"马原", "第一章 世界的物质性", "判断", "运动是物质的根本属性。", "", "", "", "",
                            "", "", "", "", "对", "", "", "", ""},
                    {"马原", "第二章 实践与认识", "主观", "论述实践与认识的辩证关系。", "", "", "", "",
                            "", "", "", "", "", "（1）实践决定认识……\n（2）认识反作用于实践……", "10", "困难", ""},
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
                    "2. 题型：单选 / 多选 / 判断 / 主观（简答、论述、分析、辨析都按主观题处理）。",
                    "3. 选项：填在“选项A”~“选项H”，单选和多选至少 2 个；判断题可以不填，默认 A 正确 / B 错误。",
                    "4. 答案：单选填一个字母，如 A；多选填多个字母，如 ABD（A,B,D 也可以）；判断填 对/错 或 A/B；主观题不填。",
                    "5. 解析：主观题的参考答案填在解析里（必填）；单元格内换行会保留为分段。",
                    "6. 分值：可不填，默认 0；难度：简单 / 中等 / 困难，可不填，默认中等。",
                    "7. 科目 / 章节：导入时如果在页面上选了章节，这两列会被忽略；",
                    "   否则科目型题库要填“科目”和“章节”，试卷型题库只填“章节”（即试卷名），不存在的会自动创建。",
                    "8. 一次最多 " + MAX_ROWS + " 题。只要有一行出错，整份文件都不会导入，请按提示改好后重新上传。",
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
        row.createCell(c).setCellValue(Objects.toString(q.getSource(), ""));
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
                BizException.throwIf(rows.size() + errors.size() >= MAX_ROWS, "一次最多导入 " + MAX_ROWS + " 题，请拆分文件");
                int excelRow = r + 1;
                try {
                    rows.add(parseRow(excelRow, cell, chapterId != null, courseBank));
                } catch (BizException e) {
                    if (errors.size() < MAX_ERRORS) {
                        errors.add(new RowError(excelRow, e.getMessage()));
                    } else {
                        break;
                    }
                }
            }
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
                    s.score(), s.difficulty(), s.source(), null, Status.ON);
        }).toList();
        int imported = questionAdminService.createBatch(saves);
        return new ImportResult(total, imported, resolver.createdCourses, resolver.createdChapters, List.of());
    }

    @FunctionalInterface
    private interface CellReader {
        String get(String header);
    }

    private ParsedRow parseRow(int excelRow, CellReader cell, boolean chapterFixed, boolean courseBank) {
        Integer type = TYPE_ALIASES.get(cell.get(H_TYPE).replace(" ", ""));
        if (type == null) {
            throw new BizException("题型“" + cell.get(H_TYPE) + "”无法识别，应为 单选 / 多选 / 判断 / 主观");
        }
        String stem = cell.get(H_STEM);
        BizException.throwIf(stem.isEmpty(), "题干不能为空");

        List<OptionItem> options = new ArrayList<>();
        for (int i = 0; i < OPTION_COUNT; i++) {
            String key = String.valueOf((char) ('A' + i));
            String content = cell.get("选项" + key);
            if (!content.isEmpty()) {
                options.add(new OptionItem(key, content));
            }
        }
        String analysisHtml = HtmlUtil.textToHtml(cell.get(H_ANALYSIS));
        // 用和手工录入完全相同的规则校验，保证导入的数据一样合规
        QuestionNormalizer.normalize(type, options, cell.get(H_ANSWER), analysisHtml);

        String courseName = cell.get(H_COURSE);
        String chapterName = cell.get(H_CHAPTER);
        if (!chapterFixed) {
            if (courseBank) {
                BizException.throwIf(courseName.isEmpty() || chapterName.isEmpty(),
                        "科目型题库请填写“科目”和“章节”（或在页面上直接选择章节）");
            } else {
                BizException.throwIf(!courseName.isEmpty(), "试卷型题库没有科目，请清空“科目”列");
                BizException.throwIf(chapterName.isEmpty(), "请填写“章节”（试卷名），或在页面上直接选择试卷");
            }
            BizException.throwIf(courseName.length() > 64, "科目名称不能超过 64 个字");
            BizException.throwIf(chapterName.length() > 128, "章节名称不能超过 128 个字");
        }

        QuestionSave save = new QuestionSave(null, type, HtmlUtil.textToHtml(stem), options, cell.get(H_ANSWER),
                analysisHtml, parseScore(cell.get(H_SCORE)), parseDifficulty(cell.get(H_DIFFICULTY)),
                StringUtils.hasText(cell.get(H_SOURCE)) ? truncate(cell.get(H_SOURCE), 64) : null, null, Status.ON);
        return new ParsedRow(excelRow, courseName, chapterName, save);
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

    /** 表头名 → 列下标；表头里的空格忽略 */
    private static Map<String, Integer> readHeader(Row header, DataFormatter fmt) {
        BizException.throwIf(header == null, "第一行应为表头，请使用导入模板");
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (Cell c : header) {
            String name = fmt.formatCellValue(c).replaceAll("\\s", "");
            if (!name.isEmpty()) {
                cols.putIfAbsent(name, c.getColumnIndex());
            }
        }
        return cols;
    }

    /** 按名称查找章节，找不到则在本题库下创建（科目同理），同一次导入内缓存结果 */
    private final class ChapterResolver {

        private final QuestionBank bank;
        private final Map<String, Long> courseIds = new HashMap<>();
        private final Map<String, Long> chapterIds = new HashMap<>();
        int createdCourses;
        int createdChapters;

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
        headers.addAll(List.of(H_ANSWER, H_ANALYSIS, H_SCORE, H_DIFFICULTY, H_SOURCE));

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
