package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.KidPortfolioSnapshot;
import com.bishop.FinanceTracker.model.json.WealthReview;
import com.bishop.FinanceTracker.model.json.WealthReview.*;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.model.wealth.SnapshotView;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import com.bishop.FinanceTracker.repository.KidPortfolioSnapshotRepository;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bishop.FinanceTracker.service.WealthService.BASE_CCY;
import static com.bishop.FinanceTracker.service.WealthService.scale;

/**
 * Builds the wealth half of the month-in-review: net-worth change and its
 * waterfall, asset-class moves, holding movers, trend, and the kids' portfolios.
 * Reuses {@link WealthService} for current/snapshot figures and
 * {@link HoldingsService} (as-of valuation) for month-boundary portfolio values.
 */
@Service
@RequiredArgsConstructor
public class WealthReviewService {

    static final int TREND_MONTHS = 24;
    private static final BigDecimal ZERO = scale(BigDecimal.ZERO);

    private final WealthService wealthService;
    private final HoldingsService holdingsService;
    private final KidPortfolioSnapshotRepository kidSnapshotRepository;

    /** A portfolio's values across the month (AUD) plus per-holding moves. */
    private record Positions(BigDecimal opening, BigDecimal closing, BigDecimal contributions,
                             List<HoldingMove> moves) {
    }

    /** @param savings the month's income minus spend, from the spending review */
    public WealthReview review(YearMonth month, BigDecimal savings) {
        TotalWealthResponse summary = wealthService.getSummary(BASE_CCY);
        boolean[] fxMissing = {summary.isFxMissing()};
        Point opening = closingPoint(month.minusMonths(1), summary);
        Point closing = closingPoint(month, summary);
        Positions household = positions(null, month, fxMissing);

        BigDecimal change = opening == null || closing == null ? null
                : closing.netWorth().subtract(opening.netWorth());
        BigDecimal changePercent = change == null || opening.netWorth().signum() == 0 ? null
                : change.multiply(BigDecimal.valueOf(100)).divide(opening.netWorth().abs(), 1, RoundingMode.HALF_UP);

        List<KidReview> kids = WealthService.KID_OWNERS.stream()
                .map(owner -> kidReview(owner, month, fxMissing))
                .toList();

        return new WealthReview(opening, closing, change, changePercent,
                waterfall(opening, closing, savings, household.contributions()),
                allocation(opening, closing),
                household.moves(),
                trend(month, m -> Optional.ofNullable(closingPoint(m, summary))
                        .map(p -> new TrendPoint(m.toString(), p.netWorth(), p.breakdown()))
                        .orElse(null)),
                kids,
                fxMissing[0]);
    }

    /** Month-end figures: live for the in-progress month, else that month's closing snapshot. */
    private static Point closingPoint(YearMonth month, TotalWealthResponse summary) {
        YearMonth current = YearMonth.now(DateUtil.APP_ZONE);
        if (month.equals(current)) {
            Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
            summary.getAllocation().forEach(a -> breakdown.put(a.getAssetClass(), a.getValue()));
            return new Point(summary.getAsOf(), summary.getNetWorth(), summary.getTotalAssets(),
                    summary.getTotalLiabilities(), breakdown);
        }
        if (month.isAfter(current)) return null;
        return closingRow(summary.getSnapshots(), SnapshotView::getAsOfDate, month)
                .map(s -> new Point(s.getAsOfDate(), s.getNetWorth(), s.getTotalAssets(),
                        s.getTotalLiabilities(), s.getBreakdown()))
                .orElse(null);
    }

    /**
     * A month's closing row: the latest dated after the 1st of {@code month}, up to
     * and including the 1st of the next (when the monthly snapshot cron runs).
     * Dates are yyyy-MM-dd (LocalDate#toString). If the cron misses the 1st and no
     * snapshot falls in the window, that month has no close.
     */
    static <T> Optional<T> closingRow(List<T> rowsByDateAsc, Function<T, String> isoDate, YearMonth month) {
        String after = month.atDay(1).toString();
        String upTo = month.plusMonths(1).atDay(1).toString();
        T found = null;
        for (T row : rowsByDateAsc) {
            String date = isoDate.apply(row);
            if (date.compareTo(after) > 0 && date.compareTo(upTo) <= 0) found = row;
        }
        return Optional.ofNullable(found);
    }

    private static Waterfall waterfall(Point opening, Point closing, BigDecimal savings, BigDecimal contributions) {
        if (opening == null || closing == null || opening.breakdown() == null || closing.breakdown() == null) {
            return null;
        }
        BigDecimal growth = classChange(opening, closing, WealthService.SHARES).subtract(contributions);
        BigDecimal equity = classChange(opening, closing, WealthService.OPTIONS);
        BigDecimal other = closing.netWorth().subtract(opening.netWorth())
                .subtract(savings).subtract(growth).subtract(equity);
        return new Waterfall(opening.netWorth(), savings, growth, equity, other, closing.netWorth());
    }

    private static BigDecimal classChange(Point opening, Point closing, String assetClass) {
        return closing.breakdown().getOrDefault(assetClass, BigDecimal.ZERO)
                .subtract(opening.breakdown().getOrDefault(assetClass, BigDecimal.ZERO));
    }

    /** One row per asset class at either end; an end without a breakdown reads as null. */
    private static List<ClassChange> allocation(Point opening, Point closing) {
        Map<String, BigDecimal> open = opening == null ? null : opening.breakdown();
        Map<String, BigDecimal> close = closing == null ? null : closing.breakdown();
        Set<String> classes = new TreeSet<>();
        if (open != null) classes.addAll(open.keySet());
        if (close != null) classes.addAll(close.keySet());
        return classes.stream()
                .map(c -> {
                    BigDecimal o = open == null ? null : open.getOrDefault(c, ZERO);
                    BigDecimal n = close == null ? null : close.getOrDefault(c, ZERO);
                    return new ClassChange(c, o, n, o == null || n == null ? null : n.subtract(o));
                })
                .sorted(Comparator.comparing((ClassChange c) -> Objects.requireNonNullElse(c.closing(), BigDecimal.ZERO))
                        .reversed())
                .toList();
    }

    private KidReview kidReview(String owner, YearMonth month, boolean[] fxMissing) {
        Positions p = positions(owner, month, fxMissing);
        List<KidPortfolioSnapshot> snapshots = kidSnapshotRepository.findAllByOwnerOrderByAsOfDateAsc(owner);
        // The review month itself uses the replayed close, so trend and totals agree.
        List<TrendPoint> trend = trend(month, m -> m.equals(month)
                ? new TrendPoint(m.toString(), p.closing(), null)
                : closingRow(snapshots, KidPortfolioSnapshot::getAsOfDate, m)
                        .map(s -> new TrendPoint(m.toString(), scale(wealthService.convert(s.getPortfolioValue(),
                                Objects.requireNonNullElse(s.getBaseCcy(), BASE_CCY), BASE_CCY, fxMissing)), null))
                        .orElse(null));
        return new KidReview(owner, p.opening(), p.closing(), p.contributions(),
                p.closing().subtract(p.opening()).subtract(p.contributions()), p.moves(), trend);
    }

    /**
     * Values {@code owner}'s holdings (null = household) at both month ends with the
     * same replay the Wealth page uses, and splits each holding's change between
     * market movement and money put in by trades during the month.
     */
    private Positions positions(String owner, YearMonth month, boolean[] fxMissing) {
        Map<Long, HoldingView> open = holdingsService.computeHoldings(owner, month.minusMonths(1).atEndOfMonth())
                .stream().collect(Collectors.toMap(HoldingView::getSecurityId, Function.identity()));

        BigDecimal openTotal = BigDecimal.ZERO;
        BigDecimal closeTotal = BigDecimal.ZERO;
        BigDecimal inTotal = BigDecimal.ZERO;
        List<HoldingMove> moves = new ArrayList<>();
        // Every security traded on or before month end is in the closing list (even if sold out).
        for (HoldingView c : holdingsService.computeHoldings(owner, month.atEndOfMonth())) {
            HoldingView o = open.get(c.getSecurityId());
            BigDecimal openValue = o == null ? BigDecimal.ZERO : o.getMarketValueNative();
            BigDecimal closeValue = c.getMarketValueNative();
            // Net money put in by the month's trades: a buy adds its cost to the basis; a sell
            // removes basis and books (proceeds - basis) as realised, so basis change minus
            // realised change is exactly buys - sell proceeds, consistent with the replay.
            BigDecimal in = c.getCostBasisNative().subtract(o == null ? BigDecimal.ZERO : o.getCostBasisNative())
                    .subtract(c.getRealisedPlNative().subtract(o == null ? BigDecimal.ZERO : o.getRealisedPlNative()));
            if (openValue.signum() == 0 && closeValue.signum() == 0 && in.signum() == 0) continue;

            BigDecimal gain = closeValue.subtract(openValue).subtract(in);
            BigDecimal invested = openValue.add(in.max(BigDecimal.ZERO));
            BigDecimal returnPercent = invested.signum() > 0
                    ? gain.multiply(BigDecimal.valueOf(100)).divide(invested, 1, RoundingMode.HALF_UP)
                    : null;
            BigDecimal closeAud = aud(closeValue, c.getCurrency(), fxMissing);
            openTotal = openTotal.add(aud(openValue, c.getCurrency(), fxMissing));
            closeTotal = closeTotal.add(closeAud);
            inTotal = inTotal.add(aud(in, c.getCurrency(), fxMissing));
            moves.add(new HoldingMove(c.getTicker(), c.getName(), closeAud, aud(gain, c.getCurrency(), fxMissing),
                    returnPercent, c.isPriceIsEstimated() || (o != null && o.isPriceIsEstimated())));
        }
        moves.sort(Comparator.comparing(HoldingMove::gain).reversed());
        return new Positions(openTotal, closeTotal, inTotal, moves);
    }

    private BigDecimal aud(BigDecimal amount, String currency, boolean[] fxMissing) {
        return scale(wealthService.convert(amount, currency, BASE_CCY, fxMissing));
    }

    /** Up to {@link #TREND_MONTHS} month-end points ending at {@code month}; null points are dropped. */
    private static List<TrendPoint> trend(YearMonth month, Function<YearMonth, TrendPoint> pointFor) {
        return Stream.iterate(month.minusMonths(TREND_MONTHS - 1), m -> m.plusMonths(1))
                .limit(TREND_MONTHS)
                .map(pointFor)
                .filter(Objects::nonNull)
                .toList();
    }
}
