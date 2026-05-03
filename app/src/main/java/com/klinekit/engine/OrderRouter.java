package com.klinekit.engine;

import com.klinekit.domain.Order;
import com.klinekit.domain.Trade;

public interface OrderRouter {

    Trade submit(Order order);
}
