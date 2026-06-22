package com.stock.publish.calculation;

import com.stock.publish.dto.TopTraderDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 主力资金计算引擎 (Top Trader Engine)
 */
@Component
public class TopTraderEngine {

    private final StringRedisTemplate redisTemplate;

    public TopTraderEngine(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取当前日期的后缀，格式为 yyyyMMdd (例如：20260501)
     * 用于按天对 Redis Key 进行数据隔离
     */
    private String getDateSuffix() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 累加买卖双方的交易量
     * 业务场景：每当接收到中央交易系统的一条成交 tick 时调用。
     *
     * @param stockCode    股票代码
     * @param buyAccountId 买方资金账户ID
     * @param sellAccountId 卖方资金账户ID
     * @param quantity     本笔成交数量
     */
    public void accumulate(String stockCode, String buyAccountId,
                           String sellAccountId, long quantity) {
        String date = getDateSuffix();
        // 构造 Redis Key： top_buyer:{stockCode}:{date} 和 top_seller:{stockCode}:{date}
        String buyKey = "top_buyer:" + stockCode + ":" + date;
        String sellKey = "top_seller:" + stockCode + ":" + date;

        // 利用 Redis 单线程的特性，使用 Hash 结构的 HINCRBY 命令进行原子累加
        // 即使高并发下也不会发生数据覆盖（Lost Update）问题
        redisTemplate.opsForHash().increment(buyKey, buyAccountId, quantity);
        redisTemplate.opsForHash().increment(sellKey, sellAccountId, quantity);
    }

    /**
     * 获取当日买入量最大的主力账户 (Top 1)
     */
    public TopTraderDTO getTopBuyer(String stockCode) {
        String key = "top_buyer:" + stockCode + ":" + getDateSuffix();
        return findTopTraderFromHash(key);
    }

    /**
     * 获取当日卖出量最大的主力账户 (Top 1)
     */
    public TopTraderDTO getTopSeller(String stockCode) {
        String key = "top_seller:" + stockCode + ":" + getDateSuffix();
        return findTopTraderFromHash(key);
    }

    /**
     * 从指定的 Redis Hash 中遍历寻找 Value (交易量) 最大的 Entry。
     * 性能说明：单只股票当日的活跃账户数通常在可控范围内，
     * 在应用层内存中做 O(N) 遍历比在 Redis 中维护复杂的 ZSet 写入成本更低。
     */
    private TopTraderDTO findTopTraderFromHash(String key) {
        // 取出该股票当日所有的买/卖账户及其累加交易量
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null; // 当日无交易数据时安全返回
        }

        String topAccount = null;
        long maxQuantity = -1;

        // 内存中遍历寻找 Max 值
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            long currentQty = Long.parseLong(entry.getValue().toString());
            if (currentQty > maxQuantity) {
                maxQuantity = currentQty;
                topAccount = entry.getKey().toString();
            }
        }
        
        // 封装 DTO 返回给控制层
        TopTraderDTO dto = new TopTraderDTO();
        dto.setAccount(topAccount); 
        dto.setQty(maxQuantity);
        return dto;
    }
}