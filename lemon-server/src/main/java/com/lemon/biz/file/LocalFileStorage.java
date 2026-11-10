package com.lemon.biz.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地磁盘存储：文件写到 lemon.upload.dir，由 WebConfig 把 /uploads/** 映射为静态资源。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String urlPrefix;

    public LocalFileStorage(@Value("${lemon.upload.dir}") String dir,
                            @Value("${lemon.upload.url-prefix}") String urlPrefix) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
    }

    public Path root() {
        return root;
    }

    @Override
    public String save(String relativePath, InputStream in, long size) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法路径：" + relativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存文件失败", e);
        }
        return urlPrefix + "/" + relativePath;
    }
}
