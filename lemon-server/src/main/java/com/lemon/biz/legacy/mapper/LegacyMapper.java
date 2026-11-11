package com.lemon.biz.legacy.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 老系统错题恢复。这三张表是一次性导入的迁移数据，不走实体 CRUD，直接写 SQL。
 */
@Mapper
public interface LegacyMapper {

    /** 老用户（含认领状态） */
    record LegacyUser(Long oldUserId, String nickname, Integer wrongTotal, Integer matched, Long claimedBy) {
    }

    @Select("""
            SELECT u.old_user_id, u.nickname, u.wrong_total, u.matched, u.claimed_by
            FROM legacy_claim_key k JOIN legacy_user u ON u.old_user_id = k.old_user_id
            WHERE k.claim_key = #{key}
            """)
    LegacyUser findByKey(@Param("key") String key);

    @Select("SELECT old_user_id, nickname, wrong_total, matched, claimed_by FROM legacy_user WHERE old_user_id = #{id} FOR UPDATE")
    LegacyUser lockUser(@Param("id") Long oldUserId);

    /** 现在还能恢复的题数（题目没被删除；认领时以此为准） */
    @Select("""
            SELECT COUNT(*) FROM legacy_wrong lw JOIN question q ON q.id = lw.question_id AND q.deleted = 0
            WHERE lw.old_user_id = #{id}
            """)
    int countRestorable(@Param("id") Long oldUserId);

    /** 恢复进错题集：已在错题集的题累加错误次数并重新放回 */
    @Insert("""
            INSERT INTO user_wrong_question
              (user_id, question_id, module_id, bank_id, chapter_id, wrong_count, right_streak, last_wrong_answer, last_wrong_at, status)
            SELECT #{userId}, q.id, q.module_id, q.bank_id, q.chapter_id, lw.wrong_count, 0, lw.last_answer, NOW(), 1
            FROM legacy_wrong lw JOIN question q ON q.id = lw.question_id AND q.deleted = 0
            WHERE lw.old_user_id = #{oldUserId}
            ON DUPLICATE KEY UPDATE wrong_count = user_wrong_question.wrong_count + VALUES(wrong_count),
              right_streak = 0, status = 1
            """)
    int restore(@Param("userId") Long userId, @Param("oldUserId") Long oldUserId);

    @Update("UPDATE legacy_user SET claimed_by = #{userId}, claimed_at = NOW() WHERE old_user_id = #{id} AND claimed_by IS NULL")
    int markClaimed(@Param("id") Long oldUserId, @Param("userId") Long userId);
}
