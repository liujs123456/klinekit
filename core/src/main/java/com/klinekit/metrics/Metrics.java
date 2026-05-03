package com.klinekit.metrics;

import com.klinekit.domain.EquityPoint;
import com.klinekit.domain.Side;
import com.klinekit.domain.Trade;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Metrics {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Metrics() {}

    public static MetricSet compute(List<EquityPoint> curve, List<Trade> trades, BigDecimal initialCash) {
        BigDecimal totalReturnPct = totalReturnPct(curve, initialCash);
        BigDecimal sharpe = sharpe(curve);
        BigDecimal maxDdPct = maxDrawdownPct(curve);
        BigDecimal winRate = winRatePct(trades);
        return new MetricSet(totalReturnPct, sharpe, maxDdPct, winRate, trades.size());
    }

    static BigDecimal totalReturnPct(List<EquityPoint> curve, BigDecimal initialCash) {
        if (curve.isEmpty() || initialCash.signum() == 0) return BigDecimal.ZERO;
        BigDecimal last = curve.getLast().equity();
        return last.subtract(initialCash)
                .divide(initialCash, MC)
                .multiply(HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal sharpe(List<EquityPoint> curve) {
        if (curve.size() < 2) return BigDecimal.ZERO;

        List<Double> rets = new ArrayList<>(curve.size() - 1);
        double avgPeriodSec = 0.0;
        for (int i = 1; i < curve.size(); i++) {
            BigDecimal prev = curve.get(i - 1).equity();
            BigDecimal cur = curve.get(i).equity();
            if (prev.signum() <= 0) continue;
            double r = cur.divide(prev, MC).doubleValue() - 1.0;
            rets.add(r);
            avgPeriodSec += Duration.between(curve.get(i - 1).timestamp(), curve.get(i).timestamp()).toSeconds();
        }
        if (rets.size() < 2) return BigDecimal.ZERO;
        avgPeriodSec /= rets.size();

        double mean = 0.0;
        for (double r : rets) mean += r;
        mean /= rets.size();

        double sq = 0.0;
        for (double r : rets) sq += (r - mean) * (r - mean);
        double std = Math.sqrt(sq / (rets.size() - 1));
        if (std == 0.0) return BigDecimal.ZERO;

        double secPerYear = 365.25 * 24 * 60 * 60;
        double periodsPerYear = avgPeriodSec > 0 ? secPerYear / avgPeriodSec : 252.0;

        double sharpe = (mean / std) * Math.sqrt(periodsPerYear);
        if (Double.isNaN(sharpe) || Double.isInfinite(sharpe)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal maxDrawdownPct(List<EquityPoint> curve) {
        if (curve.isEmpty()) return BigDecimal.ZERO;
        BigDecimal peak = curve.getFirst().equity();
        BigDecimal maxDd = BigDecimal.ZERO;
        for (EquityPoint p : curve) {
            if (p.equity().compareTo(peak) > 0) peak = p.equity();
            if (peak.signum() <= 0) continue;
            BigDecimal dd = peak.subtract(p.equity()).divide(peak, MC);
            if (dd.compareTo(maxDd) > 0) maxDd = dd;
        }
        return maxDd.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal winRatePct(List<Trade> trades) {
        if (trades.isEmpty()) return BigDecimal.ZERO;
        Map<String, BigDecimal> totalQty = new HashMap<>();
        Map<String, BigDecimal> totalCost = new HashMap<>();
        int wins = 0;
        int closed = 0;
        for (Trade t : trades) {
            String sym = t.symbol();
            BigDecimal qty = totalQty.getOrDefault(sym, BigDecimal.ZERO);
            BigDecimal cost = totalCost.getOrDefault(sym, BigDecimal.ZERO);
            if (t.side() == Side.BUY) {
                BigDecimal newQty = qty.add(t.quantity());
                BigDecimal newCost = cost.add(t.price().multiply(t.quantity()));
                totalQty.put(sym, newQty);
                totalCost.put(sym, newCost);
            } else {
                BigDecimal avgCost = qty.signum() == 0
                        ? BigDecimal.ZERO
                        : cost.divide(qty, MC);
                BigDecimal pnl = t.price().subtract(avgCost).multiply(t.quantity()).subtract(t.fee());
                closed++;
                if (pnl.signum() > 0) wins++;
                BigDecimal sellRatio = qty.signum() == 0 ? BigDecimal.ZERO : t.quantity().divide(qty, MC);
                BigDecimal newQty = qty.subtract(t.quantity());
                BigDecimal newCost = cost.subtract(cost.multiply(sellRatio));
                if (newQty.signum() <= 0) {
                    totalQty.put(sym, BigDecimal.ZERO);
                    totalCost.put(sym, BigDecimal.ZERO);
                } else {
                    totalQty.put(sym, newQty);
                    totalCost.put(sym, newCost);
                }
            }
        }
        if (closed == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(wins)
                .divide(BigDecimal.valueOf(closed), MC)
                .multiply(HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
