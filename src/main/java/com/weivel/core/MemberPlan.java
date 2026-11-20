package com.weivel.core;

/**
 * How one member of a described struct, or one parameter of an operation, is written and read, worked out at the
 * bind so a call looks nothing up by name: its kind and offset, the struct it embeds or reaches, the stride of what it
 * reaches, where its count lies, and the release of a handle it holds.
 */
final class MemberPlan {
    static final long NO_COUNT = -1;

    final String name;
    final int kind;
    final int element;
    final long offset;
    final long countOffset;
    final long stride;
    final String handleType;
    final String releaseName;
    StructPlan target;
    ReleasePlan release;

    MemberPlan(String name, int kind, int element, long offset, long countOffset, long stride, String handleType,
               String releaseName) {
        this.name = name;
        this.kind = kind;
        this.element = element;
        this.offset = offset;
        this.countOffset = countOffset;
        this.stride = stride;
        this.handleType = handleType;
        this.releaseName = releaseName;
    }

    boolean isCounted() {
        return countOffset != NO_COUNT;
    }
}
