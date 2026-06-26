package edu.zju.se.management.model;

import java.math.BigDecimal;

public class ReviewRequest {
    public String reviewId;
    public String orderId;
    public String accountId;
    public String fundAccountNo;
    public String securityAccountNo;
    public String idCardNo;
    public String userName;
    public String stockCode;
    public String stockName;
    public String side;
    public String direction;
    public BigDecimal price;
    public int quantity;
    public BigDecimal amount;
    public String clientTime;
}
