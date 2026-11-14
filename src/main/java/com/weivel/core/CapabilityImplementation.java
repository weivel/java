package com.weivel.core;

/**
 * One implementation of a capability, as {@code weivel_all_capabilities} answers it.
 *
 * @param implementation the handle its provider was answered
 * @param provider the providing plugin
 */
public record CapabilityImplementation(long implementation, long provider) {
}
