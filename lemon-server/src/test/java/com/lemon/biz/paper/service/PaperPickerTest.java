package com.lemon.biz.paper.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lemon.biz.paper.dto.PaperRuleDtos.RuleItem;
import com.lemon.biz.paper.service.PaperPicker.Candidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaperPickerTest {

    /** 每种题型、每档难度各 n 道题 */
    private static List<Candidate> pool(int n) {
        List<Candidate> pool = new ArrayList<>();
        long id = 1;
        for (int type = 1; type <= 4; type++) {
            for (int d = 1; d <= 3; d++) {
                for (int i = 0; i < n; i++) {
                    pool.add(new Candidate(id++, type, d));
                }
            }
        }
        return pool;
    }

    @Test
    void picksRequestedCountsInRuleOrder() {
        List<Candidate> pool = pool(10);
        Map<Long, Candidate> byId = pool.stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
        List<Long> ids = PaperPicker.pick(pool, List.of(new RuleItem(2, 5), new RuleItem(1, 3)), null, 42);

        assertThat(ids).hasSize(8).doesNotHaveDuplicates();
        assertThat(ids.subList(0, 5)).allMatch(id -> byId.get(id).type() == 2);
        assertThat(ids.subList(5, 8)).allMatch(id -> byId.get(id).type() == 1);
    }

    @Test
    void sameSeedSameResult_differentSeedDifferentResult() {
        List<Candidate> pool = pool(20);
        List<RuleItem> items = List.of(new RuleItem(1, 10));
        assertThat(PaperPicker.pick(pool, items, null, 7)).isEqualTo(PaperPicker.pick(new ArrayList<>(pool), items, null, 7));
        assertThat(PaperPicker.pick(pool, items, null, 7)).isNotEqualTo(PaperPicker.pick(pool, items, null, 8));
    }

    @Test
    void followsDifficultyRatio() {
        List<Candidate> pool = pool(20);
        Map<Long, Candidate> byId = pool.stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
        List<Long> ids = PaperPicker.pick(pool, List.of(new RuleItem(1, 10)), List.of(30, 40, 30), 1);

        Map<Integer, Long> byDifficulty = ids.stream()
                .collect(Collectors.groupingBy(id -> byId.get(id).difficulty(), Collectors.counting()));
        assertThat(byDifficulty).containsEntry(1, 3L).containsEntry(2, 4L).containsEntry(3, 3L);
    }

    @Test
    void fillsShortDifficultyFromOthers() {
        // 难题只有 1 道，要 5 道难题：用其他难度补足总数
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            pool.add(new Candidate(i, 1, 1));
        }
        pool.add(new Candidate(99, 1, 3));
        List<Long> ids = PaperPicker.pick(pool, List.of(new RuleItem(1, 5)), List.of(0, 0, 100), 3);
        assertThat(ids).hasSize(5).contains(99L);
        assertThat(new HashSet<>(ids)).hasSize(5);
    }

    @Test
    void notEnoughQuestions_returnsWhatExists() {
        List<Long> ids = PaperPicker.pick(pool(1), List.of(new RuleItem(4, 10)), null, 5);
        assertThat(ids).hasSize(3);
    }

    @Test
    void splitUsesLargestRemainder() {
        assertThat(PaperPicker.split(10, List.of(30, 40, 30))).containsExactly(3, 4, 3);
        assertThat(PaperPicker.split(7, List.of(33, 33, 34))).containsExactly(2, 2, 3);
        assertThat(PaperPicker.split(1, List.of(50, 50, 0))).containsExactly(1, 0, 0);
    }
}
