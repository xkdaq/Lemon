package com.lemon.biz.paper.service;

import com.lemon.biz.paper.dto.PaperRuleDtos.RuleItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 按规则抽题（纯计算，不访问数据库）。
 * <ul>
 *   <li>每种题型独立抽题，按规则里题型的顺序输出</li>
 *   <li>设置了难度比例时，按最大余数法把题数分到易 / 中 / 难三档，某档不够时用其余难度的题补足</li>
 *   <li>同一个种子、同一个题池，结果完全相同（打卡用活动 + 日期做种子，当天所有人题目一样）</li>
 * </ul>
 */
public final class PaperPicker {

    /** 候选题 */
    public record Candidate(long id, int type, int difficulty) {
    }

    private PaperPicker() {
    }

    public static List<Long> pick(List<Candidate> pool, List<RuleItem> items, List<Integer> ratio, long seed) {
        Random random = new Random(seed);
        Map<Integer, List<Candidate>> byType = pool.stream()
                .sorted(Comparator.comparingLong(Candidate::id))
                .collect(Collectors.groupingBy(Candidate::type));
        List<Long> result = new ArrayList<>();
        for (RuleItem item : items) {
            List<Candidate> candidates = new ArrayList<>(byType.getOrDefault(item.type(), List.of()));
            Collections.shuffle(candidates, random);
            result.addAll(pickOne(candidates, item.count(), ratio));
        }
        return result;
    }

    private static List<Long> pickOne(List<Candidate> shuffled, int count, List<Integer> ratio) {
        if (ratio == null || ratio.stream().mapToInt(Integer::intValue).sum() <= 0) {
            return shuffled.stream().limit(count).map(Candidate::id).toList();
        }
        int[] targets = split(count, ratio);
        List<Long> picked = new ArrayList<>();
        List<Candidate> rest = new ArrayList<>();
        int[] taken = new int[3];
        for (Candidate c : shuffled) {
            int level = level(c.difficulty());
            if (taken[level] < targets[level]) {
                taken[level]++;
                picked.add(c.id());
            } else {
                rest.add(c);
            }
        }
        // 某档难度的题不够：用剩下的题补足
        for (int i = 0; picked.size() < count && i < rest.size(); i++) {
            picked.add(rest.get(i).id());
        }
        return picked;
    }

    /** 最大余数法：把 count 按比例分成三档，合计恰好等于 count */
    static int[] split(int count, List<Integer> ratio) {
        int sum = ratio.stream().mapToInt(Integer::intValue).sum();
        int[] result = new int[3];
        double[] remainder = new double[3];
        int assigned = 0;
        for (int i = 0; i < 3; i++) {
            double exact = (double) count * ratio.get(i) / sum;
            result[i] = (int) Math.floor(exact);
            remainder[i] = exact - result[i];
            assigned += result[i];
        }
        while (assigned < count) {
            int best = 0;
            for (int i = 1; i < 3; i++) {
                if (remainder[i] > remainder[best]) {
                    best = i;
                }
            }
            result[best]++;
            remainder[best] = -1;
            assigned++;
        }
        return result;
    }

    /** 难度 1-3 → 下标 0-2，异常值按中等处理 */
    static int level(int difficulty) {
        return difficulty >= 1 && difficulty <= 3 ? difficulty - 1 : 1;
    }
}
