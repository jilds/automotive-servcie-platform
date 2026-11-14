package com.asp.platform.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record NoteRequest(@NotBlank @Size(max = 2000) String body) {
}
