package com.klinekit.data;

import com.klinekit.domain.Candle;

import java.util.List;

public interface CandleProvider {

    List<Candle> load();
}
