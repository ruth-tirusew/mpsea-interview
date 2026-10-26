package com.mpsesa.interview.transfers.data;

import jakarta.validation.constraints.Size;

public record ReversalRequest(@Size(max = 255) String reason) {
}
