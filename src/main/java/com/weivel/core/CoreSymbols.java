package com.weivel.core;

/** The core's symbols by name, for a seam that must tell them apart from another library's without calling one. */
final class CoreSymbols {
    private CoreSymbols() {
    }

    static boolean isCoreSymbol(String symbol) {
        for (CoreFunction function : CoreFunction.values()) {
            if (function.symbol().equals(symbol)) {
                return true;
            }
        }
        return false;
    }
}
