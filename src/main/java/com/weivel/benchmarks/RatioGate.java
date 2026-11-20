package com.weivel.benchmarks;

/** A gate on how many times a bare C call's cost a call through weivel may take, named as the kit names it. */
public enum RatioGate {
    /** {@code weivel_get_service} through the wrapper over the same function of the core's table called bare. */
    CONTROL_CALL("control-call"),
    /** A C library's function through the host's service over the same function called bare. */
    LIBRARY_CALL("library-call"),
    /**
     * An operation of the standard's described fixture through the bound object a consumer resolved over the same
     * operation through a binding of the fixture's structs written by hand.
     */
    DESCRIBED_CALL("described-call");

    private final String kitName;

    RatioGate(String kitName) {
        this.kitName = kitName;
    }

    /**
     * Answers {@code kitName}.
     *
     * @return the gate's name in the kit's budget files
     */
    public String kitName() {
        return kitName;
    }

    /**
     * The gate's name for one backend, as {@code benchmarks/java.txt} carries it.
     *
     * @param backend the backend, {@code ffm} or {@code jna}
     * @return the name, the kit's followed by a hyphen and the backend
     */
    public String kitNameFor(String backend) {
        return kitName + "-" + backend;
    }
}
