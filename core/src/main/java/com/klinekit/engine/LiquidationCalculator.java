package com.klinekit.engine;

import com.klinekit.domain.Direction;
import com.klinekit.domain.Position;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Liquidation price for an isolated-margin perpetual swap.
 *
 * For LONG  positions: liq = entry * (1 - 1/L + maintenanceMargin)
 * For SHORT positions: liq = entry * (1 + 1/L - maintenanceMargin)
 *
 * `L` is leverage; maintenance margin is expressed as a decimal (e.g. 0.005 = 0.5%
 * — close to OKX's BTC-USDT-SWAP tier-1 default).
 *
 * <p>Notes:
 * <ul>
 *   <li>This is the standard isolated-margin formula and ignores the funding-rate
 *       leg of the cash flow, which is handled separately by {@link FundingRateSim}.</li>
 *   <li>Cross-margin liquidation involves the entire account equity, which we don't
 *       model here — every klinekit perp backtest treats positions as isolated.</li>
 * </ul>
 */
public final class LiquidationCalculator {

    public static final BigDecimal DEFAULT_MAINTENANCE_MARGIN = new BigDecimal("0.005");
    private static final MathContext MC = MathContext.DECIMAL64;

    private LiquidationCalculator() {}

    public static BigDecimal liquidationPrice(Position p) {
        return liquidationPrice(p, DEFAULT_MAINTENANCE_MARGIN);
    }

    public static BigDecimal liquidationPrice(Position p, BigDecimal maintenanceMargin) {
        if (p.isFlat()) {
            throw new IllegalArgumentException("cannot compute liq price on a flat position");
        }
        if (p.leverage().compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("leverage must be > 1 for a perp position");
        }
        BigDecimal invL = BigDecimal.ONE.divide(p.leverage(), MC);
        BigDecimal factor = (p.direction() == Direction.LONG)
                ? BigDecimal.ONE.subtract(invL).add(maintenanceMargin)
                : BigDecimal.ONE.add(invL).subtract(maintenanceMargin);
        return p.avgCost().multiply(factor);
    }

    /** Returns true if the candle's low/high crossed liq price for this perp position. */
    public static boolean wasLiquidated(Position p, BigDecimal candleHigh, BigDecimal candleLow) {
        if (p.isFlat() || p.leverage().compareTo(BigDecimal.ONE) <= 0) return false;
        BigDecimal liq = liquidationPrice(p);
        return p.direction() == Direction.LONG
                ? candleLow.compareTo(liq) <= 0
                : candleHigh.compareTo(liq) >= 0;
    }
}
