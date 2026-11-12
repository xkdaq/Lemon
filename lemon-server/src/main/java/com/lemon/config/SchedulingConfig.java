package com.lemon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 定时任务（每日汇总推送等） */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
