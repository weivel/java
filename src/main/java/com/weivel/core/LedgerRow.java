package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * One plugin's row of the core's ledger, as {@code weivel_ledger} answers it: who the plugin is, where it is in its
 * life, and for a quarantined plugin why. The row's lists of relationships are not copied.
 */
public final class LedgerRow {
    private final long plugin;
    private final String importName;
    private final Status status;
    private final boolean verified;
    private final Optional<Refusal> reason;

    /**
     * Checks that every part is present.
     *
     * @param plugin the plugin's handle
     * @param importName its import name
     * @param status where it is in its life
     * @param verified whether its activation passed a verification since it was last opened
     * @param reason why it is quarantined; empty when it carries none
     */
    public LedgerRow(long plugin, String importName, Status status, boolean verified, Optional<Refusal> reason) {
        this.plugin = plugin;
        this.importName = Objects.requireNonNull(importName, "importName");
        this.status = Objects.requireNonNull(status, "status");
        this.verified = verified;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Answers {@code plugin}.
     *
     * @return the plugin's handle
     */
    public long plugin() {
        return plugin;
    }

    /**
     * Answers {@code importName}.
     *
     * @return its import name
     */
    public String importName() {
        return importName;
    }

    /**
     * Answers {@code status}.
     *
     * @return where it is in its life
     */
    public Status status() {
        return status;
    }

    /**
     * Answers {@code verified}.
     *
     * @return whether its activation passed a verification since it was last opened
     */
    public boolean verified() {
        return verified;
    }

    /**
     * Answers {@code reason}.
     *
     * @return for a quarantined plugin, the last reason its quarantine was given, else the refusal its verification
     *     answered, with the plugin's import name as its subject; empty for one carrying none
     */
    public Optional<Refusal> reason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LedgerRow)) {
            return false;
        }
        LedgerRow that = (LedgerRow) other;
        return plugin == that.plugin && importName.equals(that.importName) && status == that.status
                && verified == that.verified && reason.equals(that.reason);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(plugin);
        hash = 31 * hash + importName.hashCode();
        hash = 31 * hash + status.hashCode();
        hash = 31 * hash + Boolean.hashCode(verified);
        hash = 31 * hash + reason.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        return "LedgerRow[plugin=" + plugin + ", importName=" + importName + ", status=" + status + ", verified="
                + verified + ", reason=" + reason + "]";
    }

    /** Where a plugin is in its life, mirroring {@code weivel_plugin_status}. */
    public enum Status {
        /** Registered and never opened ({@code WEIVEL_PLUGIN_STATUS_REGISTERED}). */
        REGISTERED(1),
        /** Open ({@code WEIVEL_PLUGIN_STATUS_ACTIVE}). */
        ACTIVE(2),
        /** Quarantined until it is opened again ({@code WEIVEL_PLUGIN_STATUS_QUARANTINED}). */
        QUARANTINED(3),
        /** Released until it is forgotten ({@code WEIVEL_PLUGIN_STATUS_RELEASED}). */
        RELEASED(4);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        static Status of(int value) {
            for (Status status : values()) {
                if (status.value == value) {
                    return status;
                }
            }
            throw new IllegalStateException("the core answered a plugin status the layer does not know: " + value);
        }
    }
}
