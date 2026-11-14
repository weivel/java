package com.weivel.core;

import java.nio.file.Path;
import java.util.Objects;

/** What loading the core's binary answered: the loaded binary, or why there is none. */
public interface CoreLoad {
    /**
     * The binary loaded, with every core function resolved.
     */
    final class Loaded implements CoreLoad {
        private final CoreBinary binary;

        /**
         * Makes the value.
         *
         * @param binary the loaded binary, which its caller closes
         */
        public Loaded(CoreBinary binary) {
            this.binary = binary;
        }

        /**
         * Answers {@code binary}.
         *
         * @return the loaded binary, which its caller closes
         */
        public CoreBinary binary() {
            return binary;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Loaded)) {
                return false;
            }
            Loaded that = (Loaded) other;
            return Objects.equals(binary, that.binary);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(binary);
            return hash;
        }

        @Override
        public String toString() {
            return "Loaded[binary=" + binary + "]";
        }
    }

    /**
     * The path names no library the platform's loader could open.
     */
    final class NotALibrary implements CoreLoad {
        private final Path path;
        private final String reason;

        /**
         * Makes the value.
         *
         * @param path the path the caller gave
         * @param reason what the loader said
         */
        public NotALibrary(Path path, String reason) {
            this.path = path;
            this.reason = reason;
        }

        /**
         * Answers {@code path}.
         *
         * @return the path the caller gave
         */
        public Path path() {
            return path;
        }

        /**
         * Answers {@code reason}.
         *
         * @return what the loader said
         */
        public String reason() {
            return reason;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof NotALibrary)) {
                return false;
            }
            NotALibrary that = (NotALibrary) other;
            return Objects.equals(path, that.path)
                    && Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(path);
            hash = 31 * hash + Objects.hashCode(reason);
            return hash;
        }

        @Override
        public String toString() {
            return "NotALibrary[path=" + path + ", reason=" + reason + "]";
        }
    }

    /**
     * The library opened but lacks a function of the core, so it is no core, or a core older than the layer.
     */
    final class MissingFunction implements CoreLoad {
        private final Path path;
        private final String function;

        /**
         * Makes the value.
         *
         * @param path the path the caller gave
         * @param function the symbol of the first core function, in the header's order, the library lacks
         */
        public MissingFunction(Path path, String function) {
            this.path = path;
            this.function = function;
        }

        /**
         * Answers {@code path}.
         *
         * @return the path the caller gave
         */
        public Path path() {
            return path;
        }

        /**
         * Answers {@code function}.
         *
         * @return the symbol of the first core function, in the header's order, the library lacks
         */
        public String function() {
            return function;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MissingFunction)) {
                return false;
            }
            MissingFunction that = (MissingFunction) other;
            return Objects.equals(path, that.path)
                    && Objects.equals(function, that.function);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(path);
            hash = 31 * hash + Objects.hashCode(function);
            return hash;
        }

        @Override
        public String toString() {
            return "MissingFunction[path=" + path + ", function=" + function + "]";
        }
    }
}
