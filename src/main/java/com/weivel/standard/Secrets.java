package com.weivel.standard;

import java.util.Optional;

/**
 * The secrets a host keeps, served under the standard's secrets service id, translated from {@code
 * weivel_standard_secrets}: any bytes, so a secret that is text is stored as its UTF-8. A provider is safe for
 * concurrent calls from any thread, and every call completes before it returns.
 *
 * @implNote As for {@link Config}: no {@code size}, no {@code context}, and no releases. A provider should overwrite
 * a secret's bytes once it no longer needs them, which the header asks before a free.
 */
public interface Secrets {
    /**
     * Removes one secret.
     *
     * @param name the secret's name
     * @throws ServiceRefusal when the secrets cannot be written; removing one that is not there succeeds
     */
    void remove(String name) throws ServiceRefusal;

    /**
     * One secret.
     *
     * @param name the secret's name
     * @return its bytes, a copy the caller owns; empty when the name holds no secret
     * @throws ServiceRefusal when the secrets cannot be read
     */
    Optional<byte[]> get(String name) throws ServiceRefusal;

    /**
     * Stores one secret, replacing any value it had.
     *
     * @param name the secret's name
     * @param bytes the secret, read during the call only
     * @throws ServiceRefusal when the provider will not store it or cannot write
     */
    void set(String name, byte[] bytes) throws ServiceRefusal;
}
