package com.lemon.biz.recite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.recite.dto.ReciteDtos.BookVO;
import com.lemon.biz.recite.dto.ReciteDtos.ImportData;
import com.lemon.biz.recite.dto.ReciteDtos.ImportItem;
import com.lemon.biz.recite.dto.ReciteDtos.SetInfo;
import com.lemon.biz.recite.entity.ReciteBook;
import com.lemon.biz.recite.entity.ReciteItem;
import com.lemon.biz.recite.mapper.ReciteBookMapper;
import com.lemon.biz.recite.mapper.ReciteItemMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.JsonUtil;
import com.lemon.common.Status;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * 背诵资料管理：导入 JSON（新建或覆盖）、改名 / 上下架、删除。
 * 只读引用题库：导入时可选一个题库，按题干对照出每道题在题库里的题目和章节，不修改题库任何数据。
 */
@Service
@RequiredArgsConstructor
public class ReciteAdminService {

    private static final int MAX_ITEMS = 5000;

    private final ReciteBookMapper bookMapper;
    private final ReciteItemMapper itemMapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankMapper bankMapper;

    public record AdminBookVO(Long id, String name, String intro, Integer sort, Integer status, Integer itemCount,
                              Long linkBankId, String linkBankName, Integer linkedCount,
                              List<com.lemon.biz.recite.dto.ReciteDtos.SetVO> sets, String updatedAt) {
    }

    public record BookUpdate(String name, String intro, Integer sort, Integer status) {
    }

    /** 导入结果：没对照上题库的题列出来，方便核对 */
    public record ImportResult(Long bookId, int itemCount, int linkedCount, List<String> unlinked) {
    }

    public List<AdminBookVO> list() {
        List<ReciteBook> books = bookMapper.selectList(Wrappers.<ReciteBook>lambdaQuery()
                .orderByAsc(ReciteBook::getSort).orderByAsc(ReciteBook::getId));
        if (books.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Integer, List<ReciteItem>>> grouped = itemMapper.selectList(Wrappers.<ReciteItem>lambdaQuery()
                        .select(ReciteItem::getBookId, ReciteItem::getSetNo, ReciteItem::getChapterId)
                        .in(ReciteItem::getBookId, books.stream().map(ReciteBook::getId).toList()))
                .stream().collect(Collectors.groupingBy(ReciteItem::getBookId, Collectors.groupingBy(ReciteItem::getSetNo)));
        Set<Long> bankIds = books.stream().map(ReciteBook::getLinkBankId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> bankNames = bankIds.isEmpty() ? Map.of() : bankMapper.selectByIds(bankIds).stream()
                .collect(Collectors.toMap(QuestionBank::getId, QuestionBank::getName));
        return books.stream().map(b -> {
            BookVO vo = ReciteService.toVO(b, grouped.getOrDefault(b.getId(), Map.of()));
            return new AdminBookVO(b.getId(), b.getName(), b.getIntro(), b.getSort(), b.getStatus(), b.getItemCount(),
                    b.getLinkBankId(), bankNames.get(b.getLinkBankId()), b.getLinkedCount(), vo.sets(),
                    b.getUpdatedAt() == null ? null : b.getUpdatedAt().toString().replace('T', ' ').substring(0, 16));
        }).toList();
    }

    /**
     * 导入一份背诵资料。bookId 为空时新建；不为空时覆盖该资料的全部条目（用户的掌握标记按 套+题号 保留）。
     * linkBankId 可选：对照的题库，只读。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importBook(byte[] content, Long bookId, Long linkBankId) {
        ImportData data;
        try {
            data = JsonUtil.parse(new String(content, StandardCharsets.UTF_8), new TypeReference<ImportData>() {
            });
        } catch (Exception e) {
            throw new BizException("文件不是有效的背诵资料 JSON：" + e.getMessage());
        }
        BizException.throwIf(data == null || data.items() == null || data.items().isEmpty(), "文件里没有条目");
        BizException.throwIf(data.items().size() > MAX_ITEMS, "条目太多（最多 " + MAX_ITEMS + " 条）");
        List<ReciteItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < data.items().size(); i++) {
            ImportItem it = data.items().get(i);
            String where = "第 " + (i + 1) + " 条";
            Integer type = parseType(it.type());
            if (it.setNo() == null || it.setNo() < 1 || it.no() == null || it.no() < 1) {
                errors.add(where + "：套号、题号必须是正整数");
                continue;
            }
            where = "第" + it.setNo() + "套第" + it.no() + "题";
            if (!seen.add(it.setNo() + "-" + it.no())) {
                errors.add(where + "：重复");
            }
            if (type == null) {
                errors.add(where + "：题型只能是单选题或多选题");
            }
            if (it.stem() == null || it.stem().isBlank()) {
                errors.add(where + "：题干为空");
            }
            ReciteItem r = new ReciteItem();
            r.setSetNo(it.setNo());
            r.setNo(it.no());
            r.setType(type);
            r.setSubject(cut(it.subject(), 16));
            r.setTopic(cut(it.topic(), 128));
            r.setQtype(cut(it.qtype(), 16));
            r.setStem(it.stem() == null ? "" : it.stem().strip());
            r.setOptions(JsonUtil.toJson(it.options() == null ? List.of() : it.options()));
            r.setAnswer(cut(it.answer(), 16));
            r.setCore(cut(it.core(), 255));
            r.setCards(JsonUtil.toJson(it.cards() == null ? List.of() : it.cards()));
            r.setTraps(JsonUtil.toJson(it.traps() == null ? List.of() : it.traps()));
            r.setDists(JsonUtil.toJson(it.dists() == null ? List.of() : it.dists()));
            r.setTip(cut(it.tip(), 500));
            r.setMnemonic(cut(it.mnemonic(), 255));
            items.add(r);
        }
        if (!errors.isEmpty()) {
            throw new BizException("导入失败，共 " + errors.size() + " 处错误：" + String.join("；", errors.subList(0, Math.min(10, errors.size()))));
        }

        ReciteBook book;
        if (bookId != null) {
            book = BizException.notNull(bookMapper.selectById(bookId), "背诵资料不存在");
        } else {
            BizException.throwIf(data.name() == null || data.name().isBlank(), "文件里缺少资料名称 name");
            book = new ReciteBook();
            book.setStatus(Status.ON);
            book.setSort(0);
        }
        if (data.name() != null && !data.name().isBlank()) {
            book.setName(cut(data.name().strip(), 64));
        }
        if (data.intro() != null) {
            book.setIntro(cut(data.intro().strip(), 255));
        }
        List<SetInfo> sets = data.sets() == null ? List.of() : data.sets();
        book.setSetList(JsonUtil.toJson(sets));
        book.setItemCount(items.size());

        // 只读对照题库
        List<String> unlinked = new ArrayList<>();
        int linked = 0;
        if (linkBankId != null) {
            BizException.notNull(bankMapper.selectById(linkBankId), "对照的题库不存在");
            Map<String, Question> byStem = stemIndex(linkBankId);
            for (ReciteItem r : items) {
                Question q = byStem.get(norm(r.getStem()));
                if (q != null) {
                    r.setQuestionId(q.getId());
                    r.setChapterId(q.getChapterId());
                    linked++;
                } else {
                    unlinked.add("第" + r.getSetNo() + "套第" + r.getNo() + "题");
                }
            }
        }
        book.setLinkBankId(linkBankId);
        book.setLinkedCount(linked);
        if (book.getId() == null) {
            bookMapper.insert(book);
        } else {
            bookMapper.updateById(book);
            itemMapper.delete(Wrappers.<ReciteItem>lambdaQuery().eq(ReciteItem::getBookId, book.getId()));
        }
        for (ReciteItem r : items) {
            r.setBookId(book.getId());
            itemMapper.insert(r);
        }
        return new ImportResult(book.getId(), items.size(), linked, unlinked);
    }

    /** 题库里客观题的题干索引；同一题干出现多次的不参与对照，避免对错 */
    private Map<String, Question> stemIndex(Long bankId) {
        List<Question> qs = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .select(Question::getId, Question::getChapterId, Question::getStem)
                .eq(Question::getBankId, bankId)
                .in(Question::getType, 1, 2));
        Map<String, Question> map = new HashMap<>();
        Set<String> dup = new HashSet<>();
        for (Question q : qs) {
            String k = norm(HtmlUtil.htmlToText(q.getStem()));
            if (k.isEmpty()) {
                continue;
            }
            if (map.putIfAbsent(k, q) != null) {
                dup.add(k);
            }
        }
        dup.forEach(map::remove);
        return map;
    }

    /** 只保留文字和数字，忽略空白、标点、全半角差异 */
    static String norm(String s) {
        if (s == null) {
            return "";
        }
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Integer parseType(String t) {
        if (t == null) {
            return null;
        }
        return switch (t.strip()) {
            case "1", "单选", "单选题" -> 1;
            case "2", "多选", "多选题" -> 2;
            default -> null;
        };
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        s = s.strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public void update(Long id, BookUpdate req) {
        ReciteBook book = BizException.notNull(bookMapper.selectById(id), "背诵资料不存在");
        if (req.name() != null) {
            BizException.throwIf(req.name().isBlank(), "名称不能为空");
            book.setName(cut(req.name(), 64));
        }
        if (req.intro() != null) {
            book.setIntro(cut(req.intro(), 255));
        }
        if (req.sort() != null) {
            book.setSort(req.sort());
        }
        if (req.status() != null) {
            BizException.throwIf(req.status() != Status.ON && req.status() != Status.OFF, "状态不正确");
            book.setStatus(req.status());
        }
        bookMapper.updateById(book);
    }

    /** 删除资料（逻辑删除）并清空条目；用户的掌握标记保留，不影响其他数据 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizException.notNull(bookMapper.selectById(id), "背诵资料不存在");
        bookMapper.deleteById(id);
        itemMapper.delete(Wrappers.<ReciteItem>lambdaQuery().eq(ReciteItem::getBookId, id));
    }
}
