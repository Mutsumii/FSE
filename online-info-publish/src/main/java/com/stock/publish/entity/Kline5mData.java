package com.stock.publish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("kline_5m_data")
public class Kline5mData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stockCode;
    private LocalDateTime periodStartTime;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private Long volume;
}
