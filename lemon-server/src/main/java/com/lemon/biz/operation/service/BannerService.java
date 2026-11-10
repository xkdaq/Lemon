package com.lemon.biz.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.operation.entity.Banner;
import com.lemon.biz.operation.mapper.BannerMapper;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 首页 Banner。link 可为空、站内页面（/pages/...）或 https 外链（仅 H5 可打开）。
 */
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper mapper;

    public record BannerSave(@NotBlank @Size(max = 255) String image, @Size(max = 255) String link,
                             Integer sort, @Min(0) @Max(1) Integer status) {
    }

    public List<Banner> list() {
        return mapper.selectList(Wrappers.<Banner>lambdaQuery().orderByAsc(Banner::getSort).orderByDesc(Banner::getId));
    }

    /** 用户端首页：上架的，最多 5 张 */
    public List<Banner> active() {
        return mapper.selectList(Wrappers.<Banner>lambdaQuery()
                .eq(Banner::getStatus, Status.ON).isNull(Banner::getModuleId)
                .orderByAsc(Banner::getSort).orderByDesc(Banner::getId).last("LIMIT 5"));
    }

    public Long create(BannerSave req) {
        Banner b = new Banner();
        copy(req, b);
        mapper.insert(b);
        return b.getId();
    }

    public void update(Long id, BannerSave req) {
        Banner b = BizException.notNull(mapper.selectById(id), "Banner 不存在");
        copy(req, b);
        mapper.updateById(b);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private static void copy(BannerSave req, Banner b) {
        String link = req.link() == null ? null : req.link().strip();
        BizException.throwIf(link != null && !link.isEmpty() && !link.startsWith("/pages/") && !link.startsWith("https://"),
                "跳转链接只能是站内页面（/pages/ 开头）或 https 链接");
        String image = req.image().strip();
        BizException.throwIf(!image.startsWith("/uploads/") && !image.startsWith("https://"), "图片地址不合法，请通过上传获取");
        b.setImage(image);
        b.setLink(link == null || link.isEmpty() ? null : link);
        b.setSort(Objects.requireNonNullElse(req.sort(), 0));
        b.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }
}
