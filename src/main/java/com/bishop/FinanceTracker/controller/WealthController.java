package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.domain.FxRate;
import com.bishop.FinanceTracker.model.domain.NetWorthSnapshot;
import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.domain.ShareTrade;
import com.bishop.FinanceTracker.model.domain.StockSplit;
import com.bishop.FinanceTracker.model.domain.WealthItem;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import com.bishop.FinanceTracker.repository.FxRateRepository;
import com.bishop.FinanceTracker.repository.NetWorthSnapshotRepository;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import com.bishop.FinanceTracker.repository.ShareTradeRepository;
import com.bishop.FinanceTracker.repository.StockSplitRepository;
import com.bishop.FinanceTracker.repository.WealthItemRepository;
import com.bishop.FinanceTracker.service.FxService;
import com.bishop.FinanceTracker.service.PriceRefreshService;
import com.bishop.FinanceTracker.service.WealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Total Wealth / Net Worth tracker API. Manual entry in v1 (holdings, prices,
 * trades and the USD/AUD FX rate are all set by hand). See feature doc §3.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance/wealth")
public class WealthController {

    private static final String SUPER = "SUPER";
    private static final String SUPER_NAME = "Superannuation";

    private final WealthService wealthService;
    private final FxService fxService;
    private final PriceRefreshService priceRefreshService;
    private final WealthItemRepository wealthItemRepository;
    private final SecurityRepository securityRepository;
    private final ShareTradeRepository shareTradeRepository;
    private final StockSplitRepository stockSplitRepository;
    private final SecurityPriceRepository securityPriceRepository;
    private final FxRateRepository fxRateRepository;
    private final NetWorthSnapshotRepository snapshotRepository;

    // --- Summary -----------------------------------------------------------

    @GetMapping("/summary")
    public ResponseEntity<TotalWealthResponse> getSummary(
            @RequestParam(value = "currency", required = false) String currency) {
        return ResponseEntity.ok(wealthService.getSummary(currency));
    }

    // --- Wealth items (assets & liabilities) -------------------------------

    @GetMapping("/items")
    public ResponseEntity<List<WealthItem>> getItems() {
        return ResponseEntity.ok(wealthItemRepository.findByArchivedFalseOrderByAssetClassAscNameAsc());
    }

    @PostMapping("/items")
    public ResponseEntity<WealthItem> addItem(@RequestBody Map<String, Object> body) {
        String name = str(body, "name");
        String assetClass = str(body, "assetClass");
        String kind = str(body, "kind");
        if (isBlank(name) || isBlank(assetClass) || isBlank(kind)) {
            return ResponseEntity.badRequest().build();
        }
        long now = System.currentTimeMillis();
        WealthItem item = WealthItem.builder()
                .name(name.trim())
                .assetClass(assetClass.trim().toUpperCase())
                .kind(kind.trim().toUpperCase())
                .currency(ccyOr(body, "currency", "AUD"))
                .currentValue(dec(body, "currentValue"))
                .note(str(body, "note"))
                .archived(false)
                .createTime(now)
                .updateTime(now)
                .build();
        return ResponseEntity.ok(wealthItemRepository.save(item));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<WealthItem> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<WealthItem> opt = wealthItemRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        WealthItem item = opt.get();
        if (body.containsKey("name") && !isBlank(str(body, "name"))) item.setName(str(body, "name").trim());
        if (body.containsKey("assetClass")) item.setAssetClass(str(body, "assetClass").trim().toUpperCase());
        if (body.containsKey("kind")) item.setKind(str(body, "kind").trim().toUpperCase());
        if (body.containsKey("currency")) item.setCurrency(ccyOr(body, "currency", item.getCurrency()));
        if (body.containsKey("currentValue")) item.setCurrentValue(dec(body, "currentValue"));
        if (body.containsKey("note")) item.setNote(str(body, "note"));
        if (body.containsKey("archived")) item.setArchived(Boolean.parseBoolean(String.valueOf(body.get("archived"))));
        item.setUpdateTime(System.currentTimeMillis());
        return ResponseEntity.ok(wealthItemRepository.save(item));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        if (!wealthItemRepository.existsById(id)) return ResponseEntity.notFound().build();
        wealthItemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- Securities --------------------------------------------------------

    @GetMapping("/securities")
    public ResponseEntity<List<Security>> getSecurities() {
        return ResponseEntity.ok(securityRepository.findAllByOrderByTickerAsc());
    }

    @PostMapping("/securities")
    public ResponseEntity<Security> addSecurity(@RequestBody Map<String, Object> body) {
        String ticker = str(body, "ticker");
        if (isBlank(ticker)) return ResponseEntity.badRequest().build();
        String tickerNorm = ticker.trim().toUpperCase();
        String exchange = str(body, "exchange");
        String exchangeNorm = exchange == null ? "" : exchange.trim().toUpperCase();
        Optional<Security> existing =
                securityRepository.findByTickerIgnoreCaseAndExchangeIgnoreCase(tickerNorm, exchangeNorm);
        if (existing.isPresent()) return ResponseEntity.ok(existing.get());
        Security sec = Security.builder()
                .ticker(tickerNorm)
                .name(str(body, "name"))
                .exchange(exchangeNorm)
                .currency(ccyOr(body, "currency", "AUD"))
                .priceSource("MANUAL")
                .build();
        return ResponseEntity.ok(securityRepository.save(sec));
    }

    @PutMapping("/securities/{id}")
    public ResponseEntity<Security> updateSecurity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Security> opt = securityRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Security sec = opt.get();
        if (body.containsKey("name")) sec.setName(str(body, "name"));
        if (body.containsKey("currency")) sec.setCurrency(ccyOr(body, "currency", sec.getCurrency()));
        if (body.containsKey("exchange") && !isBlank(str(body, "exchange"))) {
            sec.setExchange(str(body, "exchange").trim().toUpperCase());
        }
        if (body.containsKey("priceSymbol")) {
            String ps = str(body, "priceSymbol");
            sec.setPriceSymbol(isBlank(ps) ? null : ps.trim());
        }
        return ResponseEntity.ok(securityRepository.save(sec));
    }

    // --- Trades ------------------------------------------------------------

    @GetMapping("/trades")
    public ResponseEntity<List<ShareTrade>> getTrades() {
        return ResponseEntity.ok(shareTradeRepository.findAllByOrderByTradeDateAscIdAsc());
    }

    @PostMapping("/trades")
    public ResponseEntity<ShareTrade> addTrade(@RequestBody Map<String, Object> body) {
        Long securityId = lng(body, "securityId");
        String side = str(body, "side");
        BigDecimal quantity = dec(body, "quantity");
        BigDecimal price = dec(body, "price");
        String tradeDate = str(body, "tradeDate");
        if (securityId == null || isBlank(side) || quantity == null || price == null || isBlank(tradeDate)) {
            return ResponseEntity.badRequest().build();
        }
        if (!securityRepository.existsById(securityId)) return ResponseEntity.badRequest().build();
        String s = side.trim().toUpperCase();
        if (!"BUY".equals(s) && !"SELL".equals(s)) return ResponseEntity.badRequest().build();
        BigDecimal fee = dec(body, "fee");
        ShareTrade trade = ShareTrade.builder()
                .securityId(securityId)
                .side(s)
                .quantity(quantity)
                .price(price)
                .fee(fee == null ? BigDecimal.ZERO : fee)
                .tradeDate(tradeDate.trim())
                .note(str(body, "note"))
                .createTime(System.currentTimeMillis())
                .build();
        return ResponseEntity.ok(shareTradeRepository.save(trade));
    }

    @DeleteMapping("/trades/{id}")
    public ResponseEntity<Void> deleteTrade(@PathVariable Long id) {
        if (!shareTradeRepository.existsById(id)) return ResponseEntity.notFound().build();
        shareTradeRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- Stock splits ------------------------------------------------------

    @GetMapping("/splits")
    public ResponseEntity<List<StockSplit>> getSplits() {
        return ResponseEntity.ok(stockSplitRepository.findAllByOrderByExDateAscIdAsc());
    }

    @PostMapping("/splits")
    public ResponseEntity<StockSplit> addSplit(@RequestBody Map<String, Object> body) {
        Long securityId = lng(body, "securityId");
        BigDecimal ratio = dec(body, "ratio");
        String exDate = str(body, "exDate");
        if (securityId == null || ratio == null || ratio.signum() <= 0 || isBlank(exDate)) {
            return ResponseEntity.badRequest().build();
        }
        if (!securityRepository.existsById(securityId)) return ResponseEntity.badRequest().build();
        StockSplit split = StockSplit.builder()
                .securityId(securityId)
                .exDate(exDate.trim())
                .ratio(ratio)
                .note(str(body, "note"))
                .createTime(System.currentTimeMillis())
                .build();
        return ResponseEntity.ok(stockSplitRepository.save(split));
    }

    @DeleteMapping("/splits/{id}")
    public ResponseEntity<Void> deleteSplit(@PathVariable Long id) {
        if (!stockSplitRepository.existsById(id)) return ResponseEntity.notFound().build();
        stockSplitRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- Prices ------------------------------------------------------------

    @PostMapping("/prices")
    public ResponseEntity<SecurityPrice> setPrice(@RequestBody Map<String, Object> body) {
        Long securityId = lng(body, "securityId");
        BigDecimal price = dec(body, "price");
        if (securityId == null || price == null) return ResponseEntity.badRequest().build();
        if (!securityRepository.existsById(securityId)) return ResponseEntity.badRequest().build();
        String asOf = str(body, "asOfDate");
        if (isBlank(asOf)) asOf = LocalDate.now().toString();
        SecurityPrice row = securityPriceRepository
                .findBySecurityIdAndAsOfDate(securityId, asOf).orElseGet(SecurityPrice::new);
        row.setSecurityId(securityId);
        row.setAsOfDate(asOf);
        row.setPrice(price);
        row.setSource("MANUAL");
        return ResponseEntity.ok(securityPriceRepository.save(row));
    }

    @PostMapping("/prices/refresh")
    public ResponseEntity<List<PriceRefreshService.RefreshResult>> refreshPrices() {
        return ResponseEntity.ok(priceRefreshService.refreshNow());
    }

    @GetMapping("/prices/quote")
    public ResponseEntity<Map<String, Object>> quote(@RequestParam Long securityId) {
        Optional<Security> sec = securityRepository.findById(securityId);
        if (sec.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> body = new LinkedHashMap<>();
        priceRefreshService.quote(sec.get()).ifPresent(q -> {
            body.put("price", q.price());
            body.put("currency", q.currency());
            body.put("asOf", q.asOfDate());
        });
        return ResponseEntity.ok(body); // empty body => symbol could not be priced
    }

    // --- FX rates ----------------------------------------------------------

    @PostMapping("/fx-rates")
    public ResponseEntity<FxRate> setFxRate(@RequestBody Map<String, Object> body) {
        BigDecimal rate = dec(body, "rate");
        if (rate == null || rate.signum() <= 0) return ResponseEntity.badRequest().build();
        String base = ccyOr(body, "baseCcy", "USD");
        String quote = ccyOr(body, "quoteCcy", "AUD");
        String asOf = str(body, "asOfDate");
        if (isBlank(asOf)) asOf = LocalDate.now().toString();
        FxRate row = fxRateRepository
                .findByBaseCcyAndQuoteCcyAndAsOfDate(base, quote, asOf).orElseGet(FxRate::new);
        row.setBaseCcy(base);
        row.setQuoteCcy(quote);
        row.setAsOfDate(asOf);
        row.setRate(rate);
        row.setSource("MANUAL");
        return ResponseEntity.ok(fxRateRepository.save(row));
    }

    @GetMapping("/fx-rates/latest")
    public ResponseEntity<Map<String, Object>> latestFxRate() {
        Optional<BigDecimal> rate = fxService.latestUsdAud();
        Map<String, Object> body = new LinkedHashMap<>();
        if (rate.isPresent()) {
            body.put("baseCcy", "USD");
            body.put("quoteCcy", "AUD");
            body.put("rate", rate.get());
        }
        return ResponseEntity.ok(body);
    }

    // --- Snapshots ---------------------------------------------------------

    @GetMapping("/snapshots")
    public ResponseEntity<List<NetWorthSnapshot>> getSnapshots() {
        return ResponseEntity.ok(snapshotRepository.findAllByOrderByAsOfDateAsc());
    }

    @PostMapping("/snapshots/run")
    public ResponseEntity<NetWorthSnapshot> runSnapshot() {
        return ResponseEntity.ok(wealthService.runSnapshot());
    }

    // --- Super balance (linked to the §1 Super Forecast) -------------------

    @GetMapping("/super-balance")
    public ResponseEntity<Map<String, Object>> getSuperBalance() {
        Map<String, Object> body = new LinkedHashMap<>();
        wealthItemRepository.findFirstByAssetClassAndArchivedFalse(SUPER).ifPresent(it -> {
            body.put("id", it.getId());
            body.put("value", it.getCurrentValue() == null ? BigDecimal.ZERO : it.getCurrentValue());
            body.put("currency", it.getCurrency());
            body.put("updateTime", it.getUpdateTime() == null ? 0L : it.getUpdateTime());
        });
        return ResponseEntity.ok(body);
    }

    @PutMapping("/super-balance")
    public ResponseEntity<WealthItem> setSuperBalance(@RequestBody Map<String, Object> body) {
        BigDecimal value = dec(body, "value");
        if (value == null) return ResponseEntity.badRequest().build();
        long now = System.currentTimeMillis();
        WealthItem item = wealthItemRepository.findFirstByAssetClassAndArchivedFalse(SUPER)
                .orElseGet(() -> WealthItem.builder()
                        .name(SUPER_NAME).assetClass(SUPER).kind("ASSET")
                        .archived(false).createTime(now).build());
        item.setName(isBlank(item.getName()) ? SUPER_NAME : item.getName());
        item.setAssetClass(SUPER);
        item.setKind("ASSET");
        item.setCurrency(ccyOr(body, "currency", item.getCurrency() == null ? "AUD" : item.getCurrency()));
        item.setCurrentValue(value);
        item.setUpdateTime(now);
        return ResponseEntity.ok(wealthItemRepository.save(item));
    }

    // --- Body-parsing helpers ----------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String ccyOr(Map<String, Object> body, String key, String def) {
        String v = str(body, key);
        if (isBlank(v)) return def;
        String u = v.trim().toUpperCase();
        return ("USD".equals(u) || "AUD".equals(u)) ? u : def;
    }

    private static BigDecimal dec(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long lng(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
