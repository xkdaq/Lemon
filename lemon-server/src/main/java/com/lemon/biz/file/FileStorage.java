package com.lemon.biz.file;

import java.io.InputStream;

/**
 * 文件存储抽象。开发期用 {@link LocalFileStorage}；上线换腾讯云 COS / 阿里云 OSS 时新增实现并替换 Bean 即可。
 */
public interface FileStorage {

    /**
     * 保存文件
     *
     * @param relativePath 相对路径，如 2026/11/xxx.png
     * @return 可直接访问的 URL
     */
    String save(String relativePath, InputStream in, long size);
}
