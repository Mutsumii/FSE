package com.stock.publish.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.stock.publish.entity.LocalUserSubscription;
import com.stock.publish.mapper.LocalUserSubscriptionMapper;
import com.stock.publish.service.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final LocalUserSubscriptionMapper subscriptionMapper;

    public UserServiceImpl(LocalUserSubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }

    @Override
    public void upgradeToVip(String globalUserId) {
        // 更新用户记录，将 is_premium 置为 true 并更新 upgrade_time
        UpdateWrapper<LocalUserSubscription> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("global_user_id", globalUserId)
                .set("is_premium", true)
                .set("upgrade_time", LocalDateTime.now());

        int rows = subscriptionMapper.update(null, updateWrapper);

        // 理论上 AuthInterceptor 已经保障了记录一定存在，这里做个兜底处理
        if (rows == 0) {
            LocalUserSubscription subscription = new LocalUserSubscription();
            subscription.setGlobalUserId(globalUserId);
            subscription.setIsPremium(true);
            subscription.setUpgradeTime(LocalDateTime.now());
            subscriptionMapper.insert(subscription);
        }
    }
}
