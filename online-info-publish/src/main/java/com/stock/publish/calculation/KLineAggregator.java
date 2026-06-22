package com.stock.publish.calculation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.publish.dto.KLineDTO;
import com.stock.publish.entity.Kline5mData;
import com.stock.publish.mapper.Kline5mDataMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * K线数据聚合器与技术指标引擎
 * 负责将底层的 5 分钟 (5M) K线数据向上降维聚合成 1H、1D 等长周期图表，并叠加 MA 与 MACD 指标。
 */
@Component
public class KLineAggregator {

    private final Kline5mDataMapper kline5mDataMapper;

    public KLineAggregator(Kline5mDataMapper kline5mDataMapper) {
        this.kline5mDataMapper = kline5mDataMapper;
    }

    /**
     * 获取指定周期、指定时间段内的 K 线图全量数据（包含财务指标）
     *
     * @param stockCode 股票代码
     * @param period    周期枚举 (5M, 1H, 1D, 1M, 1Y)
     * @param start     查询起始时间
     * @param end       查询结束时间
     * @return 包含 OHLC 和 MA/MACD 数据的 DTO 列表，供 ECharts 直接渲染
     */
    public List<KLineDTO> getKLineData(String stockCode, String period,
                                        LocalDateTime start, LocalDateTime end) {
        
        // 1. 数据装载：使用 MyBatis-Plus 条件构造器查出底层基准表 (5M) 的数据，按时间正序排列
        List<Kline5mData> entities = kline5mDataMapper.selectList(
                new LambdaQueryWrapper<Kline5mData>()
                        .eq(Kline5mData::getStockCode, stockCode)
                        .between(Kline5mData::getPeriodStartTime, start, end)
                        .orderByAsc(Kline5mData::getPeriodStartTime)
        );

        if (entities == null || entities.isEmpty()) return new ArrayList<>();

        // 2. 实体转换：Entity -> DTO，剥离与数据库强耦合的字段，保留金融计算属性
        // 格式化时间为规范的 yyyy-MM-dd HH:mm 
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<KLineDTO> baseData = entities.stream().map(e -> {
            KLineDTO dto = new KLineDTO();
            if (e.getPeriodStartTime() != null) {
                dto.setTime(e.getPeriodStartTime().format(formatter));
            }
            dto.setOpen(e.getOpenPrice());
            dto.setClose(e.getClosePrice());
            dto.setHigh(e.getHighPrice());
            dto.setLow(e.getLowPrice());
            dto.setVolume(e.getVolume());
            return dto;
        }).collect(Collectors.toList());

        List<KLineDTO> aggregatedData;
        
        // 3. 确定聚合步长 (Chunk Size)
        int chunkSize = 1;
        switch (period.toUpperCase()) {
            case "5M":  chunkSize = 1; break;
            case "15M": chunkSize = 3; break;   // 15分钟 / 5分钟 = 3条
            case "30M": chunkSize = 6; break;   // 30分钟 / 5分钟 = 6条
            case "1H":  chunkSize = 12; break;  // 60分钟 / 5分钟 = 12条
            case "1D":  chunkSize = 48; break;  // 4小时交易 / 5分钟 = 48条
            case "1W":  chunkSize = 48 * 5; break;  // 5个交易日
            case "1M":  chunkSize = 48 * 22; break; // 约22个交易日
            case "1Y":  chunkSize = 48 * 250; break; // 约250个交易日
            default: throw new IllegalArgumentException("Unsupported period: " + period);
        }

        // 4. 执行 OHLC 降维聚合
        aggregatedData = aggregateByChunk(baseData, chunkSize);

        // 5. 追加计算技术指标 (依赖已按时间排序的降维数据)
        computeMA(aggregatedData);
        computeMACD(aggregatedData);

        return aggregatedData;
    }

    /**
     * 核心聚合算法：将 N 条小周期 K 线合成 1 条大周期 K 线
     */
    private List<KLineDTO> aggregateByChunk(List<KLineDTO> data, int chunkSize) {
        if (chunkSize == 1) return data; // 5M 无需聚合，直接返回
        
        List<KLineDTO> result = new ArrayList<>();
        // 步长切割
        for (int i = 0; i < data.size(); i += chunkSize) {
            int endIdx = Math.min(i + chunkSize, data.size());
            List<KLineDTO> chunk = data.subList(i, endIdx);
            
            KLineDTO merged = new KLineDTO();
            // 时间锚点：取周期的起始时间
            merged.setTime(chunk.get(0).getTime());
            // 开盘价 (Open)：取周期内第一条数据的开盘价
            merged.setOpen(chunk.get(0).getOpen());
            // 收盘价 (Close)：取周期内最后一条数据的收盘价
            merged.setClose(chunk.get(chunk.size() - 1).getClose());
            
            BigDecimal high = chunk.get(0).getHigh();
            BigDecimal low = chunk.get(0).getLow();
            long vol = 0;
            
            // 遍历周期内所有原子数据，寻找极值并累加成交量
            for (KLineDTO k : chunk) {
                // BigDecimal 比较大小：compareTo > 0 表示大于， < 0 表示小于
                if (k.getHigh().compareTo(high) > 0) high = k.getHigh(); // 最高价取 Max
                if (k.getLow().compareTo(low) < 0) low = k.getLow();     // 最低价取 Min
                vol += (k.getVolume() != null ? k.getVolume() : 0);      // 成交量求和
            }
            
            merged.setHigh(high);
            merged.setLow(low);
            merged.setVolume(vol);
            result.add(merged);
        }
        return result;
    }

    /**
     * 计算移动平均线 (Moving Average)
     * 分别计算 MA5 (白线) 和 MA10 (黄线)
     */
    private void computeMA(List<KLineDTO> data) {
        // 使用滑动窗口累加算法。为方便除法运算，计算时暂转为 double，结果封装回 BigDecimal 防失真
        for (int i = 0; i < data.size(); i++) {
            // 当数据积累满 5 天时，开始计算 MA5
            if (i >= 4) {
                double sum5 = 0;
                for (int j = i - 4; j <= i; j++) sum5 += data.get(j).getClose().doubleValue();
                data.get(i).setMa5(BigDecimal.valueOf(sum5 / 5.0));
            }
            // 当数据积累满 10 天时，开始计算 MA10
            if (i >= 9) {
                double sum10 = 0;
                for (int j = i - 9; j <= i; j++) sum10 += data.get(j).getClose().doubleValue();
                data.get(i).setMa10(BigDecimal.valueOf(sum10 / 10.0));
            }
        }
    }

    /**
     * 计算平滑异同移动平均线 (MACD)
     * 严格遵循规格书 6.5 节给定的递推平滑公式体系
     */
    private void computeMACD(List<KLineDTO> data) {
        if (data.isEmpty()) return;

        // 初始值设定：第一天的 EMA12 和 EMA26 默认为第一天的收盘价
        double prevEMA12 = data.get(0).getClose().doubleValue();
        double prevEMA26 = data.get(0).getClose().doubleValue();
        double prevDEA = 0.0;

        for (int i = 0; i < data.size(); i++) {
            KLineDTO curr = data.get(i);
            // 数学约束：MACD 指标的第一天全为 0
            if (i == 0) {
                curr.setDif(BigDecimal.ZERO);
                curr.setDea(BigDecimal.ZERO);
                curr.setMacdBar(BigDecimal.ZERO);
                continue;
            }

            double close = curr.getClose().doubleValue();
            
            // 1. 计算 12日和 26日指数移动平均值 (平滑系数分别为 2/13 和 2/27)
            double ema12 = (close - prevEMA12) * (2.0 / 13.0) + prevEMA12;
            double ema26 = (close - prevEMA26) * (2.0 / 27.0) + prevEMA26;
            
            // 2. 计算离差值 DIF (快线)
            double dif = ema12 - ema26;
            
            // 3. 计算离差平均值 DEA (慢线，平滑系数为 2/10)
            double dea = (dif - prevDEA) * (2.0 / 10.0) + prevDEA;
            
            // 4. 计算 MACD 红绿柱 (柱状图 = 2 * (DIF - DEA))
            double macdBar = 2.0 * (dif - dea);

            // 封装结果
            curr.setDif(BigDecimal.valueOf(dif));
            curr.setDea(BigDecimal.valueOf(dea));
            curr.setMacdBar(BigDecimal.valueOf(macdBar));

            // 将当日结果置为前一日结果，进行下一轮递推
            prevEMA12 = ema12;
            prevEMA26 = ema26;
            prevDEA = dea;
        }
    }
}