CREATE TABLE backtest_run (
    id              UUID PRIMARY KEY,
    strategy        VARCHAR(64)     NOT NULL,
    symbol          VARCHAR(32)     NOT NULL,
    start_at        TIMESTAMPTZ     NOT NULL,
    end_at          TIMESTAMPTZ     NOT NULL,
    initial_cash    NUMERIC(28, 8)  NOT NULL,
    final_equity    NUMERIC(28, 8)  NOT NULL,
    config_json     JSONB           NOT NULL,
    metrics_json    JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_backtest_run_created_at ON backtest_run (created_at DESC);
CREATE INDEX idx_backtest_run_symbol ON backtest_run (symbol);

CREATE TABLE backtest_trade (
    id          BIGSERIAL PRIMARY KEY,
    run_id      UUID NOT NULL REFERENCES backtest_run (id) ON DELETE CASCADE,
    seq         INT NOT NULL,
    order_id    VARCHAR(64) NOT NULL,
    symbol      VARCHAR(32) NOT NULL,
    side        VARCHAR(8)  NOT NULL,
    quantity    NUMERIC(28, 12) NOT NULL,
    price       NUMERIC(28, 8)  NOT NULL,
    fee         NUMERIC(28, 8)  NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (run_id, seq)
);

CREATE INDEX idx_backtest_trade_run_id ON backtest_trade (run_id);

CREATE TABLE backtest_equity_point (
    id          BIGSERIAL PRIMARY KEY,
    run_id      UUID NOT NULL REFERENCES backtest_run (id) ON DELETE CASCADE,
    seq         INT NOT NULL,
    ts          TIMESTAMPTZ NOT NULL,
    equity      NUMERIC(28, 8) NOT NULL,
    UNIQUE (run_id, seq)
);

CREATE INDEX idx_backtest_equity_point_run_id ON backtest_equity_point (run_id);
