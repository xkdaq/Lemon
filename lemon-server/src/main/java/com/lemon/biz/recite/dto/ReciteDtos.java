package com.lemon.biz.recite.dto;

import java.util.List;

/**
 * 背诵模块的数据结构：导入文件格式与接口返回共用
 */
public final class ReciteDtos {

    private ReciteDtos() {
    }

    public record Option(String key, String content) {
    }

    /** 要背的点：问 + 答 */
    public record Card(String q, String a) {
    }

    /** 错误说法 + 错因 */
    public record Trap(String wrong, String why) {
    }

    /** 易混辨析表：每行 [名称, 区分要点] */
    public record Dist(String title, List<List<String>> rows) {
    }

    public record SetInfo(Integer no, String name) {
    }

    // ---------- 导入文件（JSON） ----------

    public record ImportData(String name, String intro, List<SetInfo> sets, List<ImportItem> items) {
    }

    /** type 可写 单选题 / 多选题 / 1 / 2 */
    public record ImportItem(Integer setNo, Integer no, String type, String subject, String topic, String qtype,
                             String stem, List<Option> options, String answer, String core,
                             List<Card> cards, List<Trap> traps, List<Dist> dists, String tip, String mnemonic) {
    }

    // ---------- 用户端返回 ----------

    /** 一套：题数，以及对照题库里的章节（有则可“去刷这套”） */
    public record SetVO(Integer no, String name, int count, Long chapterId) {
    }

    public record BookVO(Long id, String name, String intro, int itemCount, Long linkBankId, List<SetVO> sets) {
    }

    public record ItemVO(Long id, Integer setNo, Integer no, Integer type, String subject, String topic, String qtype,
                         String stem, List<Option> options, String answer, String core, List<Card> cards,
                         List<Trap> traps, List<Dist> dists, String tip, String mnemonic,
                         Long questionId, Long chapterId) {
    }
}
