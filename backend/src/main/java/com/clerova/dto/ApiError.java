package com.clerova.dto;

public record ApiError (
    String error,
    String message
    ){
}
