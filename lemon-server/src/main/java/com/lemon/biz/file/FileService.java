package com.lemon.biz.file;

import com.lemon.common.BizException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传：只允许常见位图格式，按文件头（魔数）判断真实类型，不信任扩展名和 Content-Type；
 * 不允许 SVG（可内嵌脚本）。文件名用 UUID，按年/月分目录。
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Map<String, byte[][]> MAGIC = Map.of(
            "png", new byte[][]{{(byte) 0x89, 'P', 'N', 'G'}},
            "jpg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}},
            "gif", new byte[][]{{'G', 'I', 'F', '8'}},
            "webp", new byte[][]{{'R', 'I', 'F', 'F'}});

    private final FileStorage storage;

    @Value("${lemon.upload.max-size-mb:5}")
    private int maxSizeMb;

    public String uploadImage(MultipartFile file) {
        BizException.throwIf(file == null || file.isEmpty(), "请选择文件");
        BizException.throwIf(file.getSize() > maxSizeMb * 1024L * 1024L, "图片不能超过 " + maxSizeMb + "MB");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }
        String ext = detectImageType(bytes);
        BizException.throwIf(ext == null, "只支持 PNG / JPG / GIF / WEBP 图片");

        LocalDate today = LocalDate.now();
        String path = String.format(Locale.ROOT, "images/%d/%02d/%s.%s",
                today.getYear(), today.getMonthValue(), UUID.randomUUID().toString().replace("-", ""), ext);
        return storage.save(path, new ByteArrayInputStream(bytes), bytes.length);
    }

    /** 根据文件头识别图片类型，不认识返回 null */
    static String detectImageType(byte[] bytes) {
        for (Map.Entry<String, byte[][]> e : MAGIC.entrySet()) {
            for (byte[] magic : e.getValue()) {
                if (bytes.length >= magic.length && Arrays.equals(Arrays.copyOf(bytes, magic.length), magic)) {
                    if ("webp".equals(e.getKey())) {
                        // RIFF....WEBP
                        boolean isWebp = bytes.length >= 12 && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
                        if (!isWebp) {
                            continue;
                        }
                    }
                    return e.getKey();
                }
            }
        }
        return null;
    }
}
