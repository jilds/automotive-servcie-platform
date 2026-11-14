package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// The instalment data of an INSTALMENTS payment is wrong or missing. 400.
public class InvalidInstalmentException extends InvalidInputException {

    public InvalidInstalmentException(String code, String field, String message) {
        super(code, field, message);
    }
}
