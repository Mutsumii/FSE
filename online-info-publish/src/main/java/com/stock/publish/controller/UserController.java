package com.stock.publish.controller;

import com.stock.publish.dto.ApiResponse;
import com.stock.publish.interceptor.UserContext;
import com.stock.publish.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserRoleInfo> me() {
        UserContext.UserRole role = UserContext.getRole();
        return ApiResponse.ok(new UserRoleInfo(
                UserContext.getGlobalUserId(),
                role.name(),
                role == UserContext.UserRole.PREMIUM_VIP));
    }

    public record UserRoleInfo(String globalUserId, String role, boolean isPremium) {}

    @PostMapping("/upgrade")
    public ApiResponse<Void> upgrade() {
        // 获取目前用户角色
        UserContext.UserRole role = UserContext.getRole();

        // 校验操作权限
        if (role == UserContext.UserRole.GUEST) {
            return ApiResponse.fail(403, "游客无法升级，请先登录");
        }
        if (role == UserContext.UserRole.PREMIUM_VIP) {
            return ApiResponse.fail(400, "您已经是VIP，无需重复升级");
        }

        // 获取全局用户ID并执行升级
        String globalUserId = UserContext.getGlobalUserId();
        if (globalUserId == null) {
            return ApiResponse.fail(401, "未能获取到有效的用户信息");
        }

        userService.upgradeToVip(globalUserId);
        return ApiResponse.ok(null);
    }
}
