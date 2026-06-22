package com.stock.publish.service;

import com.stock.publish.dto.QuoteDTO;

public interface MarketService {
    QuoteDTO getQuote(String stockCode);
    void refreshQuotes();
}
