package com.asp.platform.shop.api;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.asp.platform.shop.domain.OrderProductException;

/// Shop-specific error mappings: a product of a new order that cannot be sold (`product-not-listed`,
/// `insufficient-stock`) names the product, so the page can mark its line. Ahead of the global handler, which maps
/// every other conflict without it.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ShopExceptionHandler {

    @ExceptionHandler(OrderProductException.class)
    ProblemDetail orderProduct(OrderProductException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setType(URI.create("/problems/" + e.code()));
        problem.setProperty("code", e.code());
        problem.setProperty("productId", e.productId());
        return problem;
    }
}
