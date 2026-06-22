package com.stock.publish.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.publish.entity.LocalUserSubscription;
import com.stock.publish.mapper.LocalUserSubscriptionMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final LocalUserSubscriptionMapper subscriptionMapper;

    public AuthInterceptor(LocalUserSubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        UserContext.clear();

        // 集成版：从 X-Fund-Acc-No 读取资金账号（前端登录后由账户系统返回）
        // 生产环境需替换为 Token/Session 机制，本阶段为集成验证简化方案
        String fundAccNo = request.getHeader("X-Fund-Acc-No");

        if (fundAccNo == null || fundAccNo.isEmpty()) {
            UserContext.setRole(UserContext.UserRole.GUEST);
            return true;
        }

        // 查询本地订阅状态
        LambdaQueryWrapper<LocalUserSubscription> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LocalUserSubscription::getGlobalUserId, fundAccNo);
        LocalUserSubscription subscription = subscriptionMapper.selectOne(queryWrapper);

        if (subscription == null) {
            subscription = new LocalUserSubscription();
            subscription.setGlobalUserId(fundAccNo);
            subscription.setIsPremium(false);
            subscriptionMapper.insert(subscription);
        }

        if (Boolean.TRUE.equals(subscription.getIsPremium())) {
            UserContext.setRole(UserContext.UserRole.PREMIUM_VIP);
        } else {
            UserContext.setRole(UserContext.UserRole.STANDARD);
        }

        UserContext.setGlobalUserId(fundAccNo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
