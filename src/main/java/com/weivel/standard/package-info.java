/**
 * Optional: Java's access to the standard's defaults, through the same foreign function interface. {@link
 * com.weivel.standard.StandardBinary} loads the standard's binary from a path its caller gives, and {@link
 * com.weivel.standard.Standard} reads JSON manifests into manifest values, discovers plugins laid out on disk, reads
 * library declarations, and answers identities, the mirror list, SHA-256 and the service ids, all as the standard
 * answers them; what it finds feeds the host's registration and library loading. {@link com.weivel.standard.Config},
 * {@link com.weivel.standard.Secrets} and {@link com.weivel.standard.Paths} are the standard's service definitions
 * translated into Java. A host never needs this package.
 */
package com.weivel.standard;
