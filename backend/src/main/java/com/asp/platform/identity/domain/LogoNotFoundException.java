package com.asp.platform.identity.domain;

import com.asp.platform.shared.NotFoundException;

/// The workshop has no logo; the UI shows its default brand mark.
public class LogoNotFoundException extends NotFoundException {

    public LogoNotFoundException() {
        super("logo-not-found", "This workshop has no logo");
    }
}
