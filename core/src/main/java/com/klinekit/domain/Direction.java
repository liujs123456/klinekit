package com.klinekit.domain;

public enum Direction {
    LONG,
    SHORT;

    public int sign() {
        return this == LONG ? 1 : -1;
    }
}
