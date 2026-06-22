package com.stock.publish.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KLineDTO {
    private String time;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
    private BigDecimal ma5;
    private BigDecimal ma10;
    private BigDecimal dif;
    private BigDecimal dea;
    private BigDecimal macdBar;
}
