package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.domain.ShareTrade;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import com.bishop.FinanceTracker.repository.ShareTradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

    /** One holding (in the security's native currency) for every security that has trades. */
    public List<HoldingView> computeHoldings() {
        List<HoldingView> result = new ArrayList<>();
        for (Security sec : securityRepository.findAll()) {
            List<ShareTrade> trades = shareTradeRepository.findBySecurityIdOrderByTradeDateAscIdAsc(sec.getId());
            if (trades.isEmpty()) continue;
            result.add(buildHolding(sec, trades));
        }
        result.sort((a, b) -> a.getTicker().compareToIgnoreCase(b.getTicker()));
        return result;
    }

    private HoldingView buildHolding(Security sec, List<ShareTrade> trades) {
        BigDecimal qty = BigDecimal.ZERO;        // running position
        BigDecimal costBasis = BigDecimal.ZERO;  // total cost of the current position (incl. fees)
        BigDecimal realised = BigDecimal.ZERO;   // realised P/L from sells

        for (ShareTrade t : trades) {
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

        BigDecimal marketValue = lastPrice != null
                ? lastPrice.multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal costOfPosition = avgCost.multiply(qty);
        BigDecimal unrealised = lastPrice != null
                ? marketValue.subtract(costOfPosition).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Double unrealisedPct = (lastPrice != null && costOfPosition.signum() > 0)
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
