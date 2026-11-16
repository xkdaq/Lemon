package com.lemon.biz.recite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.recite.dto.ReciteDtos.BookVO;
import com.lemon.biz.recite.dto.ReciteDtos.Card;
import com.lemon.biz.recite.dto.ReciteDtos.Dist;
import com.lemon.biz.recite.dto.ReciteDtos.ItemVO;
import com.lemon.biz.recite.dto.ReciteDtos.Option;
import com.lemon.biz.recite.dto.ReciteDtos.SetInfo;
import com.lemon.biz.recite.dto.ReciteDtos.SetVO;
import com.lemon.biz.recite.dto.ReciteDtos.Trap;
import com.lemon.biz.recite.entity.ReciteBook;
import com.lemon.biz.recite.entity.ReciteItem;
import com.lemon.biz.recite.entity.ReciteTrapMark;
import com.lemon.biz.recite.mapper.ReciteBookMapper;
import com.lemon.biz.recite.mapper.ReciteItemMapper;
import com.lemon.biz.recite.mapper.ReciteTrapMarkMapper;
import com.lemon.common.BizException;
import com.lemon.common.JsonUtil;
import com.lemon.common.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

/**
 * 背诵模块（用户端）：免费开放，浏览不需要登录；“错误说法已掌握”标记需要登录。
 */
@Service
@RequiredArgsConstructor
public class ReciteService {

    static final TypeReference<List<SetInfo>> SETS_TYPE = new TypeReference<>() {
    };
    static final TypeReference<List<Option>> OPTIONS_TYPE = new TypeReference<>() {
    };
    static final TypeReference<List<Card>> CARDS_TYPE = new TypeReference<>() {
    };
    static final TypeReference<List<Trap>> TRAPS_TYPE = new TypeReference<>() {
    };
    static final TypeReference<List<Dist>> DISTS_TYPE = new TypeReference<>() {
    };

    private final ReciteBookMapper bookMapper;
    private final ReciteItemMapper itemMapper;
    private final ReciteTrapMarkMapper markMapper;

    /** 已上架的背诵资料，带每套题数 */
    public List<BookVO> books() {
        List<ReciteBook> books = bookMapper.selectList(Wrappers.<ReciteBook>lambdaQuery()
                .eq(ReciteBook::getStatus, Status.ON)
                .orderByAsc(ReciteBook::getSort).orderByAsc(ReciteBook::getId));
        if (books.isEmpty()) {
            return List.of();
        }
        List<ReciteItem> items = itemMapper.selectList(Wrappers.<ReciteItem>lambdaQuery()
                .select(ReciteItem::getBookId, ReciteItem::getSetNo, ReciteItem::getChapterId)
                .in(ReciteItem::getBookId, books.stream().map(ReciteBook::getId).toList()));
        Map<Long, Map<Integer, List<ReciteItem>>> grouped = items.stream().collect(Collectors.groupingBy(
                ReciteItem::getBookId, Collectors.groupingBy(ReciteItem::getSetNo)));
        return books.stream().map(b -> toVO(b, grouped.getOrDefault(b.getId(), Map.of()))).toList();
    }

    static BookVO toVO(ReciteBook b, Map<Integer, List<ReciteItem>> bySet) {
        List<SetInfo> sets = parse(b.getSetList(), SETS_TYPE);
        Map<Integer, String> names = new LinkedHashMap<>();
        sets.forEach(s -> names.put(s.no(), s.name()));
        bySet.keySet().stream().sorted().forEach(n -> names.putIfAbsent(n, "第" + n + "套"));
        List<SetVO> vos = new ArrayList<>();
        names.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            List<ReciteItem> list = bySet.getOrDefault(e.getKey(), List.of());
            vos.add(new SetVO(e.getKey(), e.getValue(), list.size(), mainChapter(list)));
        });
        return new BookVO(b.getId(), b.getName(), b.getIntro(), b.getItemCount(), b.getLinkBankId(), vos);
    }

    /** 一套题对照到的章节：取出现最多的那个 */
    private static Long mainChapter(List<ReciteItem> list) {
        return list.stream().map(ReciteItem::getChapterId).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** 某份资料的条目，setNo 为空时返回全部（按套、题号排序） */
    public List<ItemVO> items(Long bookId, Integer setNo) {
        ReciteBook book = bookMapper.selectById(bookId);
        BizException.throwIf(book == null || book.getStatus() != Status.ON, "背诵资料不存在或已下架");
        return itemMapper.selectList(Wrappers.<ReciteItem>lambdaQuery()
                        .eq(ReciteItem::getBookId, bookId)
                        .eq(setNo != null, ReciteItem::getSetNo, setNo))
                .stream()
                .sorted(Comparator.comparing(ReciteItem::getSetNo).thenComparing(ReciteItem::getNo))
                .map(ReciteService::toVO).toList();
    }

    static ItemVO toVO(ReciteItem i) {
        return new ItemVO(i.getId(), i.getSetNo(), i.getNo(), i.getType(), i.getSubject(), i.getTopic(), i.getQtype(),
                i.getStem(), parse(i.getOptions(), OPTIONS_TYPE), i.getAnswer(), i.getCore(),
                parse(i.getCards(), CARDS_TYPE), parse(i.getTraps(), TRAPS_TYPE), parse(i.getDists(), DISTS_TYPE),
                i.getTip(), i.getMnemonic(), i.getQuestionId(), i.getChapterId());
    }

    static <T> List<T> parse(String json, TypeReference<List<T>> type) {
        List<T> list = JsonUtil.parse(json, type);
        return list == null ? List.of() : list;
    }

    /** 我在某份资料里已掌握的错误说法，格式 "套-题号-第几条" */
    public List<String> marks(Long userId, Long bookId) {
        return markMapper.selectList(Wrappers.<ReciteTrapMark>lambdaQuery()
                        .eq(ReciteTrapMark::getUserId, userId)
                        .eq(ReciteTrapMark::getBookId, bookId))
                .stream().map(m -> m.getSetNo() + "-" + m.getNo() + "-" + m.getIdx()).toList();
    }

    public void mark(Long userId, Long bookId, int setNo, int no, int idx, boolean mastered) {
        if (mastered) {
            ReciteTrapMark m = new ReciteTrapMark();
            m.setUserId(userId);
            m.setBookId(bookId);
            m.setSetNo(setNo);
            m.setNo(no);
            m.setIdx(idx);
            try {
                markMapper.insert(m);
            } catch (DuplicateKeyException ignored) {
                // 已经标记过
            }
        } else {
            markMapper.delete(Wrappers.<ReciteTrapMark>lambdaQuery()
                    .eq(ReciteTrapMark::getUserId, userId)
                    .eq(ReciteTrapMark::getBookId, bookId)
                    .eq(ReciteTrapMark::getSetNo, setNo)
                    .eq(ReciteTrapMark::getNo, no)
                    .eq(ReciteTrapMark::getIdx, idx));
        }
    }
}
