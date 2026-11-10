package com.lemon.biz.practice.event;

/**
 * 做题会话已结束（交卷 / 完成练习 / 考试超时自动交卷）。
 * 在结束会话的同一个事务里同步发布，监听方的写操作和会话状态一起提交或回滚。
 */
public record PracticeFinishedEvent(Long sessionId, Long userId, int mode, int total, int answeredCount,
                                    int correctCount) {
}
