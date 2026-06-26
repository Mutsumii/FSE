package com.trading.central.service;

import com.trading.central.model.StockInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AccountService {

    private final RestTemplate restTemplate;
    private final StockService stockService;

    @Value("${app.account.api-base}")
    private String apiBase;

    @Value("${app.account.mock:true}")
    private boolean isMock;

    public AccountService(RestTemplate restTemplate, StockService stockService) {
        this.restTemplate = restTemplate;
        this.stockService = stockService;
    }

    private void callAccountApi(String path, Map<String, Object> body) {
        if (isMock) {
            log.debug("[AccountService Mock] {} {}", path, body);
            return;
        }

        String url = apiBase + path;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("[AccountService] {} failed: {}", path, response.getStatusCode());
                throw new RuntimeException("Account API error: " + response.getStatusCode());
            }
            Map responseBody = response.getBody();
            if (responseBody != null) {
                Object code = responseBody.get("code");
                if (code instanceof Number && ((Number) code).intValue() != 0) {
                    throw new RuntimeException("Account API business error: " + responseBody.get("message"));
                }
                Object success = responseBody.get("success");
                if (success instanceof Boolean && !((Boolean) success)) {
                    throw new RuntimeException("Account API business error: " + responseBody.get("message"));
                }
            }
        } catch (Exception e) {
            log.error("[AccountService] {} call error: {}", path, e.getMessage());
            throw new RuntimeException("Account API call failed", e);
        }
    }

    private String stockNameOf(String stockCode) {
        StockInfo stockInfo = stockService.getStockInfo(stockCode);
        return stockInfo != null ? stockInfo.getStockName() : stockCode;
    }

    private void updateFundBalance(String fundAccountNo, String refOrderId, String txnType, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("fund_acc_no", fundAccountNo);
        body.put("ref_order_id", refOrderId);
        body.put("txn_type", txnType);
        body.put("amount", amount);
        callAccountApi("/api/external/trade/fund-balance", body);
    }

    private void updateSecurityHolding(String securityAccountNo, String stockCode, String stockName,
                                       String refOrderId, String changeType, int quantity, BigDecimal price) {
        Map<String, Object> body = new HashMap<>();
        body.put("sec_acc_no", securityAccountNo);
        body.put("stock_code", stockCode);
        body.put("stock_name", stockName);
        body.put("ref_order_id", refOrderId);
        body.put("change_type", changeType);
        body.put("quantity", quantity);
        body.put("price", price);
        callAccountApi("/api/external/trade/security-holding", body);
    }

    public void freezeFunds(String fundAccountNo, String refOrderId, BigDecimal amount) {
        updateFundBalance(fundAccountNo, refOrderId, "买入冻结", amount);
    }

    public void settleBuyFunds(String fundAccountNo, String refOrderId, BigDecimal amount) {
        updateFundBalance(fundAccountNo, refOrderId, "买入扣款", amount);
    }

    public void settleSellFunds(String fundAccountNo, String refOrderId, BigDecimal amount) {
        updateFundBalance(fundAccountNo, refOrderId, "卖出回款", amount);
    }

    public void releaseFunds(String fundAccountNo, String refOrderId, BigDecimal amount) {
        updateFundBalance(fundAccountNo, refOrderId, "撤单解冻", amount);
    }

    public void freezeHolding(String securityAccountNo, String stockCode, String refOrderId, int quantity) {
        updateSecurityHolding(securityAccountNo, stockCode, stockNameOf(stockCode), refOrderId, "卖出冻结", quantity, null);
    }

    public void settleSellerHolding(String securityAccountNo, String stockCode, String refOrderId, int quantity, BigDecimal price) {
        updateSecurityHolding(securityAccountNo, stockCode, stockNameOf(stockCode), refOrderId, "卖出扣减", quantity, price);
    }

    public void settleBuyerHolding(String securityAccountNo, String stockCode, String refOrderId, int quantity, BigDecimal price) {
        updateSecurityHolding(securityAccountNo, stockCode, stockNameOf(stockCode), refOrderId, "买入增加", quantity, price);
    }

    public void releaseHolding(String securityAccountNo, String stockCode, String refOrderId, int quantity) {
        updateSecurityHolding(securityAccountNo, stockCode, stockNameOf(stockCode), refOrderId, "撤单释放", quantity, null);
    }

    public String getAccountName(String accountId) {
        String suffix = accountId.length() >= 4 ? accountId.substring(accountId.length() - 4) : accountId;
        return "user" + suffix;
    }
}
