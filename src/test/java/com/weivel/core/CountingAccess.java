package com.weivel.core;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** The selected backend, with every native allocation and free made through it counted. */
final class CountingAccess implements NativeAccess {
    private final NativeAccess backend = NativeBackends.current();
    private final AtomicLong allocations = new AtomicLong();
    private final AtomicLong frees = new AtomicLong();

    long allocations() {
        return allocations.get();
    }

    long frees() {
        return frees.get();
    }

    @Override
    public String name() {
        return backend.name();
    }

    @Override
    public long addressSize() {
        return backend.addressSize();
    }

    @Override
    public OpenLibrary open(Path path) {
        return backend.open(path);
    }

    @Override
    public long allocate(long size) {
        allocations.incrementAndGet();
        return backend.allocate(size);
    }

    @Override
    public void free(long address) {
        frees.incrementAndGet();
        backend.free(address);
    }

    @Override
    public int getInt(long address, long offset) {
        return backend.getInt(address, offset);
    }

    @Override
    public void putInt(long address, long offset, int value) {
        backend.putInt(address, offset, value);
    }

    @Override
    public long getLong(long address, long offset) {
        return backend.getLong(address, offset);
    }

    @Override
    public void putLong(long address, long offset, long value) {
        backend.putLong(address, offset, value);
    }

    @Override
    public double getDouble(long address, long offset) {
        return backend.getDouble(address, offset);
    }

    @Override
    public void putDouble(long address, long offset, double value) {
        backend.putDouble(address, offset, value);
    }

    @Override
    public byte[] readBytes(long address, long offset, int length) {
        return backend.readBytes(address, offset, length);
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes) {
        backend.writeBytes(address, offset, bytes);
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes, int from, int length) {
        backend.writeBytes(address, offset, bytes, from, length);
    }

    @Override
    public void clear(long address, long offset, long length) {
        backend.clear(address, offset, length);
    }

    @Override
    public String readText(long address) {
        return backend.readText(address);
    }

    @Override
    public long addressOf(ByteBuffer direct) {
        return backend.addressOf(direct);
    }

    @Override
    public void keepReachable(Object object) {
        backend.keepReachable(object);
    }
}
