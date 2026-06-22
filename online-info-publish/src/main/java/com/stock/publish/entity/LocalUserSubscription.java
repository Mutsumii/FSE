package com.stock.publish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("local_user_subscription")
public class LocalUserSubscription {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String globalUserId;
    private Boolean isPremium;
    private LocalDateTime upgradeTime;
}
