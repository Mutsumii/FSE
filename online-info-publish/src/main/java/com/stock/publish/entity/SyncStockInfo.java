package com.stock.publish.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sync_stock_info")
public class SyncStockInfo {
    @TableId
    private String stockCode;
    private String stockName;
    private Integer stockType;
    private BigDecimal yesterdayClose;
    private BigDecimal limitRate;
    private Integer status;
    private String pinyinAbbr;
}
