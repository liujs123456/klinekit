ALTER TABLE backtest_equity_point
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'STRATEGY';

CREATE INDEX idx_backtest_equity_point_run_id_kind
    ON backtest_equity_point (run_id, kind);
