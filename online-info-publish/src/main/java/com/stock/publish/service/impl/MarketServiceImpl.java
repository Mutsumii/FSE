package com.stock.publish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.publish.calculation.TopTraderEngine;
import com.stock.publish.dto.QuoteDTO;
import com.stock.publish.entity.Kline5mData;
import com.stock.publish.entity.SyncStockInfo;
import com.stock.publish.interceptor.UserContext;
import com.stock.publish.mapper.Kline5mDataMapper;
import com.stock.publish.mapper.SyncStockInfoMapper;
import com.stock.publish.service.MarketService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@Service
public class MarketServiceImpl implements MarketService {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final SyncStockInfoMapper stockInfoMapper;
    private final Kline5mDataMapper kline5mDataMapper;
    private final TopTraderEngine topTraderEngine;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${subsystems.central-trade.base-url:http://localhost:8082}")
    private String centralTradeBaseUrl;
    @Value("${subsystems.central-trade.snapshot-path:/api/central-trading/market/snapshot}")
    private String snapshotPath;

    private static final List<String> MOCK_STOCKS = List.of(
            "000001", "000002", "000858", "600000", "600016",
            "600036", "600519", "600900", "601398", "601988");

    public MarketServiceImpl(StringRedisTemplate redisTemplate,
                             RedissonClient redissonClient,
                             SyncStockInfoMapper stockInfoMapper,
                             Kline5mDataMapper kline5mDataMapper,
                             TopTraderEngine topTraderEngine,
                             ObjectMapper objectMapper,
                             RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.stockInfoMapper = stockInfoMapper;
        this.kline5mDataMapper = kline5mDataMapper;
        this.topTraderEngine = topTraderEngine;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    // 角色屏蔽方法
    private QuoteDTO maskByRole(QuoteDTO quote) {
        UserContext.UserRole role = UserContext.getRole();
        if (role == UserContext.UserRole.GUEST) {
            quote.setTopBuyer(null);
            quote.setTopSeller(null);
            quote.setBidPrice(null);
            quote.setAskPrice(null);
            quote.setBidVolume(null);
            quote.setAskVolume(null);
        }
        return quote;
    }

    private QuoteDTO buildQuote(String stockCode) {
        SyncStockInfo info = stockInfoMapper.selectById(stockCode);
        if (info == null) {
            return null;
        }

        // 用最近一根5分钟K线的收盘价作为 lastPrice；没有K线时退化为昨收
        BigDecimal lastPrice = info.getYesterdayClose();
        Kline5mData latest = kline5mDataMapper.selectOne(
                new LambdaQueryWrapper<Kline5mData>()
                        .eq(Kline5mData::getStockCode, stockCode)
                        .orderByDesc(Kline5mData::getPeriodStartTime)
                        .last("LIMIT 1"));
        if (latest != null && latest.getClosePrice() != null) {
            lastPrice = latest.getClosePrice();
        }

        // 涨跌幅：(lastPrice - yesterdayClose) / yesterdayClose * 100
        String changeRate = "0.00%";
        BigDecimal yClose = info.getYesterdayClose();
        if (yClose != null && yClose.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal rate = lastPrice.subtract(yClose)
                    .divide(yClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            changeRate = (rate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                    + rate.setScale(2, RoundingMode.HALF_UP) + "%";
        }

        QuoteDTO dto = new QuoteDTO();
        dto.setStockCode(info.getStockCode());
        dto.setStockName(info.getStockName());
        dto.setLastPrice(lastPrice);
        dto.setYesterdayClose(info.getYesterdayClose());
        dto.setChangeRate(changeRate);
        dto.setStatus(info.getStatus());
        try {
            dto.setTopBuyer(topTraderEngine.getTopBuyer(stockCode));
            dto.setTopSeller(topTraderEngine.getTopSeller(stockCode));
        } catch (UnsupportedOperationException e) {
            // B3 尚未实现，主力数据暂缺
        }
        return dto;
    }

    @Override
    public QuoteDTO getQuote(String stockCode) {
        // DONE: 1. 读 Redis "quote:{stockCode}"
        // DONE: 2. Cache Miss → Redisson 分布式锁 → 查库回填
        // DONE: 3. 根据 UserContext.getRole() 屏蔽敏感字段（GUEST 不返回主力/盘口）
        String cacheKey = "quote:" + stockCode;
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return maskByRole(objectMapper.readValue(cachedJson, QuoteDTO.class));
            }

            RLock lock = redissonClient.getLock("lock:quote:" + stockCode);
            try {
                if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                    try {
                        // double check：可能别的线程已经回填了
                        cachedJson = redisTemplate.opsForValue().get(cacheKey);
                        if (cachedJson != null) {
                            return maskByRole(objectMapper.readValue(cachedJson, QuoteDTO.class));
                        }
                        // 构建并回填
                        QuoteDTO quote = buildQuote(stockCode);
                        String json = objectMapper.writeValueAsString(quote);
                        redisTemplate.opsForValue().set(cacheKey, json, 5, TimeUnit.SECONDS);
                        return maskByRole(quote);
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 没抢到锁，等 100ms 再读一次缓存
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return maskByRole(objectMapper.readValue(cachedJson, QuoteDTO.class));
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON处理失败，stockCode: " + stockCode, e);
        }
    }

     private record TransactionRecord(
            String stockCode,
            LocalDateTime timestamp,
            String buyerAccount,
            String sellerAccount,
            BigDecimal price,
            long quantity
    ) {}

    @Scheduled(cron = "*/5 * * * * *")
    @Override
    public void refreshQuotes() {
        for (String code : MOCK_STOCKS) {
            try {
                fetchAndCacheQuote(code);
            } catch (Exception e) {
                // 中央交易系统未就绪时静默跳过，保留 Redis 中上次数据
            }
        }
    }

    private void fetchAndCacheQuote(String code) {
        String url = centralTradeBaseUrl + snapshotPath + "/" + code;
        ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
        if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) return;

        Map<String, Object> body = resp.getBody();
        SyncStockInfo info = stockInfoMapper.selectById(code);
        if (info == null) return;

        // 最新成交价：取 recentTrades 第一条
        BigDecimal lastPrice = info.getYesterdayClose();
        Object tradesObj = body.get("recentTrades");
        if (tradesObj instanceof List && !((List<?>) tradesObj).isEmpty()) {
            Map<String, Object> firstTrade = (Map<String, Object>) ((List<?>) tradesObj).get(0);
            Object dealPrice = firstTrade.get("dealPrice");
            if (dealPrice != null) {
                lastPrice = new BigDecimal(dealPrice.toString());
            }

            // 主力累加 & 推 tick
            LocalDateTime now = LocalDateTime.now();
            for (Object t : (List<?>) tradesObj) {
                Map<String, Object> trade = (Map<String, Object>) t;
                String buyer = String.valueOf(trade.getOrDefault("buyerName", ""));
                String seller = String.valueOf(trade.getOrDefault("sellerName", ""));
                BigDecimal price = new BigDecimal(trade.getOrDefault("dealPrice", "0").toString());
                long qty = Long.parseLong(trade.getOrDefault("dealQuantity", "0").toString());
                try {
                    topTraderEngine.accumulate(code, buyer, seller, qty);
                } catch (UnsupportedOperationException ignored) {}
                TransactionRecord tr = new TransactionRecord(code, now, buyer, seller, price, qty);
                try {
                    redisTemplate.opsForList().rightPush("tick:" + code,
                            objectMapper.writeValueAsString(tr));
                } catch (JsonProcessingException ignored) {}
            }
        }

        // 涨跌幅
        BigDecimal yClose = info.getYesterdayClose();
        BigDecimal rate = lastPrice.subtract(yClose)
                .divide(yClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        String changeRate = (rate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + rate + "%";

        // 盘口（中央交易系统真实数据）
        Object bidP = body.get("bidPrice");
        Object askP = body.get("askPrice");
        Object bidV = body.get("bidVolume");
        Object askV = body.get("askVolume");

        QuoteDTO quote = buildQuote(code);
        quote.setLastPrice(lastPrice);
        quote.setChangeRate(changeRate);
        quote.setBidPrice(bidP != null ? new BigDecimal(bidP.toString()) : null);
        quote.setAskPrice(askP != null ? new BigDecimal(askP.toString()) : null);
        quote.setBidVolume(bidV != null ? Long.parseLong(bidV.toString()) : null);
        quote.setAskVolume(askV != null ? Long.parseLong(askV.toString()) : null);

        try {
            redisTemplate.opsForValue().set("quote:" + code,
                    objectMapper.writeValueAsString(quote), 5, TimeUnit.SECONDS);
        } catch (JsonProcessingException ignored) {}
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void aggregate5mKline() {
        // DONE: 1. 从 Redis "tick:{stockCode}" 取出过去5分钟的所有 tick
        // DONE: 2. 聚合为 OHLCV
        // DONE: 3. 写入 kline_5m_data 表
        LocalDateTime periodStart = LocalDateTime.now()
                .minusMinutes(5)
                .truncatedTo(ChronoUnit.MINUTES);

        for (String code : MOCK_STOCKS) {
            String tickKey = "tick:" + code;
            List<String> tickJsons = redisTemplate.opsForList().range(tickKey, 0, -1);
            if (tickJsons == null || tickJsons.isEmpty()) continue;

            // JSON → TransactionRecord
            List<TransactionRecord> ticks = new ArrayList<>();
            for (String json : tickJsons) {
                try {
                    ticks.add(objectMapper.readValue(json, TransactionRecord.class));
                } catch (JsonProcessingException ignored) {}
            }
            if (ticks.isEmpty()) continue;

            // 聚合 OHLCV
            BigDecimal open = ticks.get(0).price();
            BigDecimal close = ticks.get(ticks.size() - 1).price();
            BigDecimal high = ticks.stream()
                    .map(TransactionRecord::price)
                    .max(BigDecimal::compareTo).orElse(open);
            BigDecimal low = ticks.stream()
                    .map(TransactionRecord::price)
                    .min(BigDecimal::compareTo).orElse(open);
            long volume = ticks.stream().mapToLong(TransactionRecord::quantity).sum();

            // 写入 kline_5m_data
            Kline5mData kline = new Kline5mData();
            kline.setStockCode(code);
            kline.setPeriodStartTime(periodStart);
            kline.setOpenPrice(open);
            kline.setClosePrice(close);
            kline.setHighPrice(high);
            kline.setLowPrice(low);
            kline.setVolume(volume);
            try {
                kline5mDataMapper.insert(kline);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 该时段已有记录（例如重启后重复聚合），忽略
            }

            // 清空已消费的 tick
            redisTemplate.delete(tickKey);
        }
    }
}
