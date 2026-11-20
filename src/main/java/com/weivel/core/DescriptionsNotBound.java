package com.weivel.core;

/** A native backend that binds no described definition yet, saying so, which refuses a described load. */
final class DescriptionsNotBound extends UnsupportedOperationException {
    private static final long serialVersionUID = 1L;

    DescriptionsNotBound(String detail) {
        super(detail);
    }
}
