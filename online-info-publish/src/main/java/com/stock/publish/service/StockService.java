package com.stock.publish.service;

import com.stock.publish.entity.SyncStockInfo;

import java.util.List;

public interface StockService {
    List<SyncStockInfo> search(String keyword);
    SyncStockInfo getByCode(String stockCode);
    void syncFromCentralSystem();
}
