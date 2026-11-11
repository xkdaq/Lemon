package com.lemon.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.access.service.AccessService;
import com.lemon.biz.access.service.ActivationService;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.common.R;
import com.lemon.common.Status;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-模块激活")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppAccessController {

    private final AccessService accessService;
    private final ActivationService activationService;
    private final ExamModuleMapper moduleMapper;

    /** free：免费模块，不需要激活（active 恒为 true，expireAt 为空） */
    public record ModuleAccess(Long moduleId, String name,
                               @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expireAt, boolean active,
                               boolean free) {
    }

    /** trialQuestions：未激活模块每章可试做的题数；没登录时 modules 全部未激活 */
    public record AccessResp(int trialQuestions, List<ModuleAccess> modules) {
    }

    public record ActivateReq(@NotBlank(message = "请输入激活码") @Size(max = 40) String code) {
    }

    @Operation(summary = "各模块的激活状态（未登录也可调用，全部显示未激活）")
    @GetMapping("/access")
    public R<AccessResp> access() {
        Long userId = StpAppUtil.userIdOrNull();
        Map<Long, LocalDateTime> active = userId == null ? Map.of() : accessService.activeModules(userId);
        List<ModuleAccess> modules = moduleMapper.selectList(Wrappers.<ExamModule>lambdaQuery()
                        .eq(ExamModule::getStatus, Status.ON).orderByAsc(ExamModule::getSort).orderByAsc(ExamModule::getId))
                .stream().map(m -> {
                    boolean free = m.getFree() != null && m.getFree() == 1;
                    return new ModuleAccess(m.getId(), m.getName(), free ? null : active.get(m.getId()),
                            free || active.containsKey(m.getId()), free);
                })
                .toList();
        return R.ok(new AccessResp(accessService.trialQuestions(), modules));
    }

    @Operation(summary = "使用激活码激活模块（同一模块没到期时顺延）")
    @PostMapping("/activate")
    public R<ActivationService.Activated> activate(@Valid @RequestBody ActivateReq req) {
        return R.ok(activationService.redeem(StpAppUtil.userId(), req.code()));
    }
}
