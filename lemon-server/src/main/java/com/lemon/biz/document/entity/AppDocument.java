package com.lemon.biz.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户端展示的协议文档（用户协议、隐私政策），纯文本，后台可编辑
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`app_document`")
public class AppDocument extends BaseEntity {

    /** agreement 用户协议 / privacy 隐私政策 */
    private String docKey;

    private String title;

    /** 纯文本，按换行分段 */
    private String content;
}
