package com.lemon.biz.system.dto;

import com.lemon.common.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

public final class AdminReqs {

    private AdminReqs() {
    }

    public record AdminCreate(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "只能是 3-32 位字母、数字、下划线") String username,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password,
            @Size(max = 32) String nickname,
            @NotEmpty List<String> roles) {
    }

    public record AdminUpdate(
            @Size(max = 32) String nickname,
            @NotNull @Min(0) @Max(1) Integer status,
            @NotEmpty List<String> roles) {
    }

    public record PasswordReset(@NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password) {
    }

    public record PasswordChange(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String newPassword) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AdminQuery extends PageQuery {
        /** 匹配用户名或昵称 */
        private String keyword;
    }
}
