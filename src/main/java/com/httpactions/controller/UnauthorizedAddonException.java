package com.httpactions.controller;

public class UnauthorizedAddonException extends RuntimeException {

    public UnauthorizedAddonException(String message) {
        super(message);
    }
}
