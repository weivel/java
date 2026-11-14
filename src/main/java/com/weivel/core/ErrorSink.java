package com.weivel.core;

/**
 * Where failures in code the layer calls, and defects of the layer itself, go: never to the caller of the call whose
 * drain set them off. It is called outside the layer lock, so it may call into the core. What it throws is written to
 * the standard error output.
 */
@FunctionalInterface
public interface ErrorSink {
    /** The sink a core has until its caller replaces it: one line per failure to the standard error output. */
    ErrorSink STANDARD_ERROR = failure -> System.err.println("weivel: " + failure.text() + " (" + failure.kind()
            + failure.cause().map(cause -> ": " + cause).orElse("") + ")");

    /**
     * Is told one failure.
     *
     * @param failure what failed
     */
    void report(Failure failure);
}
