package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// An empty photo or a caption that is too long. 400 `validation` with the field in `errors`, as Bean Validation
/// reports the other fields of the request.
public class InvalidPhotoInputException extends InvalidInputException {

    public InvalidPhotoInputException(String field, String message) {
        super("validation", field, message);
    }
}
