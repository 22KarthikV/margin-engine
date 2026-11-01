package com.karthikv.margin.domain;

/** Identifies one position: an account's holding in one instrument. */
public record PositionKey(String account, String instrument) {
}
