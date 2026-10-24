package com.asp.platform.stock.api;

import jakarta.validation.groups.Default;

/// Validation group of PUT requests: every default constraint plus `version`, which updates must send.
interface OnUpdate extends Default {
}
