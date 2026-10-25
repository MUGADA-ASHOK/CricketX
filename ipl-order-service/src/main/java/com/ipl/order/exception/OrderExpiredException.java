package com.ipl.order.exception;

public class OrderExpiredException extends RuntimeException {
    public OrderExpiredException(String message) {
        super(message);
    }
}
