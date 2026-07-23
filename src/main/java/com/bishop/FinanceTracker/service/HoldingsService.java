package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.domain.ShareTrade;
import com.bishop.FinanceTracker.model.domain.StockSplit;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import com.bishop.FinanceTracker.repository.ShareTradeRepository;
import com.bishop.FinanceTracker.repository.StockSplitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives share/ETF holdings from the trade log using the average-cost method.
 * Holdings are not stored — they are a pure function of trades + the latest price.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HoldingsService {

    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 4;
    private static final int UNIT_SCALE = 6;

    private final SecurityRepository securityRepository;
    private final ShareTradeRepository shareTradeRepository;
    private final SecurityPriceRepository securityPriceRepository;
    private final StockSplitRepository stockSplitRepository;

    /** One holding (in the security's native currency) for every household security that has trades. */
    public List<HoldingView> computeHoldings() {
        return computeHoldings(null);
    }

    /**
     * One holding (native currency) for every security with trades belonging to
     * {@code owner}. Pass {@code null} for the household portfolio (the owner's own
     * trades — unchanged existing behavior); pass "CHLOE"/"MILLIE" for a kid's
     * portfolio.
     */
    public List<HoldingView> computeHoldings(String owner) {
        List<HoldingView> result = new ArrayList<>();
        for (Security sec : securityRepository.findAll()) {
            List<ShareTrade> trades = owner == null
                    ? shareTradeRepository.findBySecurityIdAndOwnerIsNullOrderByTradeDateAscIdAsc(sec.getId())
                    : shareTradeRepository.findBySecurityIdAndOwnerOrderByTradeDateAscIdAsc(sec.getId(), owner);
            if (trades.isEmpty()) continue;
            List<StockSplit> splits = stockSplitRepository.findBySecurityIdOrderByExDateAscIdAsc(sec.getId());
            result.add(buildHolding(sec, trades, splits));
        }
        result.sort((a, b) -> a.getTicker().compareToIgnoreCase(b.getTicker()));
        return result;
    }

    // One step in the chronological replay: a trade, or a split (ratio only).
    private record Event(String date, boolean isSplit, ShareTrade trade, BigDecimal ratio) {}

    private HoldingView buildHolding(Security sec, List<ShareTrade> trades, List<StockSplit> splits) {
        BigDecimal qty = BigDecimal.ZERO;        // running position
        BigDecimal costBasis = BigDecimal.ZERO;  // total cost of the current position (incl. fees)
        BigDecimal realised = BigDecimal.ZERO;   // realised P/L from sells

        // Merge trades + splits into a single timeline. Same-date order: trades first,
        // then the split (so shares bought on the ex-date are split too).
        List<Event> events = new ArrayList<>();
        for (ShareTrade t : trades) events.add(new Event(t.getTradeDate(), false, t, null));
        for (StockSplit s : splits) events.add(new Event(s.getExDate(), true, null, s.getRatio()));
        events.sort(Comparator
                .comparing(Event::date)
                .thenComparing(e -> e.isSplit() ? 1 : 0));

        for (Event e : events) {
            if (e.isSplit()) {
                BigDecimal ratio = nz(e.ratio());
                // Multiply quantity by the ratio; cost basis is unchanged, so the
                // derived average cost (basis / qty) adjusts by 1/ratio automatically.
                if (ratio.signum() > 0) qty = qty.multiply(ratio);
                continue;
            }
            ShareTrade t = e.trade();
            BigDecimal tq = nz(t.getQuantity());
            BigDecimal tp = nz(t.getPrice());
            BigDecimal fee = nz(t.getFee());

            if ("SELL".equalsIgnoreCase(t.getSide())) {
                BigDecimal sellQty = tq.min(qty); // clamp an oversell to the held quantity
                BigDecimal avg = qty.signum() > 0
                        ? costBasis.divide(qty, UNIT_SCALE, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                BigDecimal costRemoved = avg.multiply(sellQty);
                BigDecimal proceeds = tp.multiply(sellQty).subtract(fee);
                realised = realised.add(proceeds.subtract(costRemoved));
                qty = qty.subtract(sellQty);
                costBasis = costBasis.subtract(costRemoved);
                if (qty.signum() <= 0) {
                    qty = BigDecimal.ZERO;
                    costBasis = BigDecimal.ZERO;
                }
            } else { // BUY (default)
                costBasis = costBasis.add(tp.multiply(tq)).add(fee);
                qty = qty.add(tq);
            }
        }

        BigDecimal avgCost = qty.signum() > 0
                ? costBasis.divide(qty, UNIT_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        SecurityPrice priceRow = securityPriceRepository
                .findFirstBySecurityIdOrderByAsOfDateDesc(sec.getId()).orElse(null);
        BigDecimal lastPrice = priceRow != null ? priceRow.getPrice() : null;
        String lastPriceDate = priceRow != null ? priceRow.getAsOfDate() : null;
        boolean priceIsEstimated = lastPrice == null;

        // No market price yet → value the position at average cost as a placeholder
        // (so it still contributes its cost basis to net worth, with 0 unrealised P/L)
        // until a price is entered or a live feed is plugged in.
        BigDecimal effectivePrice = priceIsEstimated ? avgCost : lastPrice;
        BigDecimal marketValue = effectivePrice.multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal costOfPosition = avgCost.multiply(qty);
        BigDecimal unrealised = marketValue.subtract(costOfPosition).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Double unrealisedPct = costOfPosition.signum() > 0
                ? unrealised.divide(costOfPosition, 6, RoundingMode.HALF_UP).doubleValue() * 100.0
                : null;

        return HoldingView.builder()
                .securityId(sec.getId())
                .ticker(sec.getTicker())
                .name(sec.getName())
                .exchange(sec.getExchange())
                .currency(sec.getCurrency())
                .quantity(qty.setScale(UNIT_SCALE, RoundingMode.HALF_UP))
                .avgCost(avgCost.setScale(PRICE_SCALE, RoundingMode.HALF_UP))
                .lastPrice(lastPrice)
                .lastPriceDate(lastPriceDate)
                .priceIsEstimated(priceIsEstimated)
                .marketValueNative(marketValue)
                .unrealisedPlNative(unrealised)
                .realisedPlNative(realised.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .unrealisedPlPct(unrealisedPct)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
