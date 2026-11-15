package com.weivel.benchmarks;

/** One measured path, handed the call's index; what it answers is kept, so no call can be dropped as unread. */
@FunctionalInterface
interface MeasuredPath {
    long call(int index);
}
