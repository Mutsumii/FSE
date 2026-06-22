package com.stock.publish.controller;

import com.stock.publish.calculation.KLineAggregator;
import com.stock.publish.calculation.TopTraderEngine;
import com.stock.publish.dto.ApiResponse;
import com.stock.publish.dto.KLineDTO;
import com.stock.publish.dto.QuoteDTO;
import com.stock.publish.interceptor.UserContext;
import com.stock.publish.service.MarketService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/market")
public class MarketController {

    private final MarketService marketService;
    private final KLineAggregator kLineAggregator;
    private final TopTraderEngine topTraderEngine;

    public MarketController(MarketService marketService, KLineAggregator kLineAggregator,
                            TopTraderEngine topTraderEngine) {
        this.marketService = marketService;
        this.kLineAggregator = kLineAggregator;
        this.topTraderEngine = topTraderEngine;
    }

    @GetMapping("/quote/{stockCode}")
    public ApiResponse<QuoteDTO> quote(@PathVariable String stockCode) {
        QuoteDTO quote = marketService.getQuote(stockCode);
        if (quote == null) {
            return ApiResponse.fail(404, "股票未找到");
        }
        return ApiResponse.ok(quote);
    }

    @GetMapping("/kline")
    public ApiResponse<?> kline(@RequestParam String stockCode,
                                @RequestParam(defaultValue = "1D") String period) {
        UserContext.UserRole role = UserContext.getRole();

        if (role == UserContext.UserRole.GUEST) {
            return ApiResponse.fail(403, "游客无权查看K线图");
        }
        // STANDARD 可看常用尺度，VIP 解锁 1W/1M/1Y
        Set<String> standardPeriods = Set.of("5M", "15M", "30M", "1H", "1D");
        if (role == UserContext.UserRole.STANDARD && !standardPeriods.contains(period)) {
            return ApiResponse.fail(403, "升级VIP解锁周/月/年K线");
        }

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusYears(1);
        List<KLineDTO> data = kLineAggregator.getKLineData(stockCode, period, start, end);

        // STANDARD 保留 MA5/MA10，清除 MACD（VIP 解锁）
        if (role == UserContext.UserRole.STANDARD) {
            for (KLineDTO k : data) {
                k.setDif(null);
                k.setDea(null);
                k.setMacdBar(null);
            }
        }
        return ApiResponse.ok(data);
    }
}
