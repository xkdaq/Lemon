package com.lemon.controller.app;

import com.lemon.biz.recite.dto.ReciteDtos.BookVO;
import com.lemon.biz.recite.dto.ReciteDtos.ItemVO;
import com.lemon.biz.recite.service.ReciteService;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 背诵模块：免费开放。/recite/** 免登录（WebConfig 白名单），/recite-marks 需登录。
 */
@Tag(name = "用户端-背诵")
@RestController
@RequiredArgsConstructor
public class AppReciteController {

    private final ReciteService service;

    public record MarkReq(@NotNull Long bookId, @NotNull @Min(1) Integer setNo, @NotNull @Min(1) Integer no,
                          @NotNull @Min(0) Integer idx, boolean mastered) {
    }

    @Operation(summary = "背诵资料列表（含每套题数）")
    @GetMapping("/api/app/recite/books")
    public R<List<BookVO>> books() {
        return R.ok(service.books());
    }

    @Operation(summary = "背诵条目：不传 setNo 返回整份资料")
    @GetMapping("/api/app/recite/books/{bookId}/items")
    public R<List<ItemVO>> items(@PathVariable Long bookId, @RequestParam(required = false) Integer setNo) {
        return R.ok(service.items(bookId, setNo));
    }

    @Operation(summary = "我已掌握的错误说法（“套-题号-第几条”）")
    @GetMapping("/api/app/recite-marks")
    public R<List<String>> marks(@RequestParam Long bookId) {
        return R.ok(service.marks(StpAppUtil.userId(), bookId));
    }

    @Operation(summary = "标记 / 取消标记一条错误说法为已掌握")
    @PutMapping("/api/app/recite-marks")
    public R<Void> mark(@Valid @RequestBody MarkReq req) {
        service.mark(StpAppUtil.userId(), req.bookId(), req.setNo(), req.no(), req.idx(), req.mastered());
        return R.ok();
    }
}
