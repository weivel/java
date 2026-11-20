package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A service's description as Java values, copied once at the load from the {@code weivel_standard_description} its
 * library answered, after the standard's check passed it: the definition's structs and enums, and each function
 * pointer of the definition with its parameters. Kinds are {@link DescribedKind}'s numbers.
 */
public final class Description {
    /** {@code WEIVEL_STANDARD_DESCRIBED_INPUT}: a parameter read during the call only. */
    public static final int INPUT = 1;
    /** {@code WEIVEL_STANDARD_DESCRIBED_ANSWER}: the consumer's empty answer, filled on OK alone. */
    public static final int ANSWER = 2;
    /** {@code WEIVEL_STANDARD_DESCRIBED_REFUSAL}: the consumer's empty refusal, filled on REFUSED. */
    public static final int REFUSAL = 3;
    /** {@code WEIVEL_STANDARD_DESCRIBED_STATUS}: an operation answering a {@code weivel_status}. */
    public static final int STATUS = 1;
    /** {@code WEIVEL_STANDARD_DESCRIBED_NOTHING}: a release, answering nothing. */
    public static final int NOTHING = 2;

    private final String definition;
    private final List<Type> types;
    private final List<Enumeration> enums;
    private final List<Operation> operations;

    /**
     * Makes the value.
     *
     * @param definition the name of the struct among {@code types} the definition is
     * @param types every described struct
     * @param enums every described enum
     * @param operations every function pointer of the definition, in the struct's order
     */
    public Description(String definition, List<Type> types, List<Enumeration> enums, List<Operation> operations) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.types = copied(types);
        this.enums = copied(enums);
        this.operations = copied(operations);
    }

    /**
     * Answers {@code definition}.
     *
     * @return the name of the struct the definition is
     */
    public String definition() {
        return definition;
    }

    /**
     * Answers {@code types}.
     *
     * @return every described struct
     */
    public List<Type> types() {
        return types;
    }

    /**
     * Answers {@code enums}.
     *
     * @return every described enum
     */
    public List<Enumeration> enums() {
        return enums;
    }

    /**
     * Answers {@code operations}.
     *
     * @return every function pointer of the definition, operations and releases alike
     */
    public List<Operation> operations() {
        return operations;
    }

    /**
     * The struct of that name.
     *
     * @param name a struct's name
     * @return the struct; empty when the description holds none of that name
     */
    public Optional<Type> type(String name) {
        for (Type type : types) {
            if (type.name().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * The function pointer of that name.
     *
     * @param name an operation's or a release's name
     * @return it; empty when the description holds none of that name
     */
    public Optional<Operation> operation(String name) {
        for (Operation operation : operations) {
            if (operation.name().equals(name)) {
                return Optional.of(operation);
            }
        }
        return Optional.empty();
    }

    private static <T> List<T> copied(List<T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** One described struct: its {@code sizeof}, where its first shape ends, and its members. */
    public static final class Type {
        private final String name;
        private final long size;
        private final long firstShape;
        private final List<Member> members;

        /**
         * Makes the value.
         *
         * @param name its typedef name
         * @param size its {@code sizeof}
         * @param firstShape the offset just past the last field of its first shape
         * @param members its members, a definition's data members only
         */
        public Type(String name, long size, long firstShape, List<Member> members) {
            this.name = Objects.requireNonNull(name, "name");
            this.size = size;
            this.firstShape = firstShape;
            this.members = copied(members);
        }

        /**
         * Answers {@code name}.
         *
         * @return its typedef name
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code size}.
         *
         * @return its {@code sizeof}
         */
        public long size() {
            return size;
        }

        /**
         * Answers {@code firstShape}.
         *
         * @return where its first shape ends
         */
        public long firstShape() {
            return firstShape;
        }

        /**
         * Answers {@code members}.
         *
         * @return its members, in order
         */
        public List<Member> members() {
            return members;
        }
    }

    /** One member of a described struct. */
    public static final class Member {
        private final String name;
        private final long offset;
        private final long size;
        private final int kind;
        private final int element;
        private final Optional<String> type;
        private final Optional<String> count;
        private final Optional<String> release;

        /**
         * Makes the value.
         *
         * @param name its field name
         * @param offset its {@code offsetof}
         * @param size its {@code sizeof}
         * @param kind a {@link DescribedKind}
         * @param element for a pointer or an array, the kind of what it reaches; else 0
         * @param type the struct, enum or opaque type it names, when its kind names one
         * @param count the member counting it, for texts, bytes and an array
         * @param release the release freeing it, for a handle
         */
        public Member(String name, long offset, long size, int kind, int element, Optional<String> type,
                      Optional<String> count, Optional<String> release) {
            this.name = Objects.requireNonNull(name, "name");
            this.offset = offset;
            this.size = size;
            this.kind = kind;
            this.element = element;
            this.type = Objects.requireNonNull(type, "type");
            this.count = Objects.requireNonNull(count, "count");
            this.release = Objects.requireNonNull(release, "release");
        }

        /**
         * Answers {@code name}.
         *
         * @return its field name
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code offset}.
         *
         * @return its {@code offsetof}
         */
        public long offset() {
            return offset;
        }

        /**
         * Answers {@code size}.
         *
         * @return its {@code sizeof}
         */
        public long size() {
            return size;
        }

        /**
         * Answers {@code kind}.
         *
         * @return a {@link DescribedKind}
         */
        public int kind() {
            return kind;
        }

        /**
         * Answers {@code element}.
         *
         * @return the kind a pointer or an array reaches; 0 otherwise
         */
        public int element() {
            return element;
        }

        /**
         * Answers {@code type}.
         *
         * @return the type it names
         */
        public Optional<String> type() {
            return type;
        }

        /**
         * Answers {@code count}.
         *
         * @return the member counting it
         */
        public Optional<String> count() {
            return count;
        }

        /**
         * Answers {@code release}.
         *
         * @return the release freeing it
         */
        public Optional<String> release() {
            return release;
        }
    }

    /** One described enum, whose values a U32 member or parameter naming it holds. */
    public static final class Enumeration {
        private final String name;
        private final List<Entry> entries;

        /**
         * Makes the value.
         *
         * @param name its typedef name
         * @param entries its named values
         */
        public Enumeration(String name, List<Entry> entries) {
            this.name = Objects.requireNonNull(name, "name");
            this.entries = copied(entries);
        }

        /**
         * Answers {@code name}.
         *
         * @return its typedef name
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code entries}.
         *
         * @return its named values, in order
         */
        public List<Entry> entries() {
            return entries;
        }
    }

    /** One named value of an enum. */
    public static final class Entry {
        private final String name;
        private final long number;

        /**
         * Makes the value.
         *
         * @param name its name
         * @param number its number, a {@code uint32_t}
         */
        public Entry(String name, long number) {
            this.name = Objects.requireNonNull(name, "name");
            this.number = number;
        }

        /**
         * Answers {@code name}.
         *
         * @return its name
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code number}.
         *
         * @return its number
         */
        public long number() {
            return number;
        }
    }

    /** One function pointer of the definition: an operation answering a status, or a release answering nothing. */
    public static final class Operation {
        private final String name;
        private final long offset;
        private final int returns;
        private final List<Parameter> parameters;

        /**
         * Makes the value.
         *
         * @param name its field name
         * @param offset its {@code offsetof} in the definition's struct
         * @param returns {@link #STATUS} or {@link #NOTHING}
         * @param parameters its parameters, in the prototype's order, the context first
         */
        public Operation(String name, long offset, int returns, List<Parameter> parameters) {
            this.name = Objects.requireNonNull(name, "name");
            this.offset = offset;
            this.returns = returns;
            this.parameters = copied(parameters);
        }

        /**
         * Answers {@code name}.
         *
         * @return its field name
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code offset}.
         *
         * @return its offset in the definition's struct
         */
        public long offset() {
            return offset;
        }

        /**
         * Answers {@code returns}.
         *
         * @return {@link #STATUS} or {@link #NOTHING}
         */
        public int returns() {
            return returns;
        }

        /**
         * Answers {@code parameters}.
         *
         * @return its parameters, in order
         */
        public List<Parameter> parameters() {
            return parameters;
        }
    }

    /** One parameter of an operation. */
    public static final class Parameter {
        private final String name;
        private final int kind;
        private final int role;
        private final int element;
        private final Optional<String> type;
        private final Optional<String> count;
        private final Optional<String> release;

        /**
         * Makes the value.
         *
         * @param name its name in the prototype
         * @param kind a {@link DescribedKind}
         * @param role {@link #INPUT}, {@link #ANSWER} or {@link #REFUSAL}
         * @param element for a pointer or an array, the kind of what it reaches; else 0
         * @param type the struct, enum or opaque type it names, when its kind names one
         * @param count the parameter counting it
         * @param release the release freeing it, for an answer or a refusal
         */
        public Parameter(String name, int kind, int role, int element, Optional<String> type, Optional<String> count,
                         Optional<String> release) {
            this.name = Objects.requireNonNull(name, "name");
            this.kind = kind;
            this.role = role;
            this.element = element;
            this.type = Objects.requireNonNull(type, "type");
            this.count = Objects.requireNonNull(count, "count");
            this.release = Objects.requireNonNull(release, "release");
        }

        /**
         * Answers {@code name}.
         *
         * @return its name
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code kind}.
         *
         * @return a {@link DescribedKind}
         */
        public int kind() {
            return kind;
        }

        /**
         * Answers {@code role}.
         *
         * @return {@link #INPUT}, {@link #ANSWER} or {@link #REFUSAL}
         */
        public int role() {
            return role;
        }

        /**
         * Answers {@code element}.
         *
         * @return the kind a pointer or an array reaches; 0 otherwise
         */
        public int element() {
            return element;
        }

        /**
         * Answers {@code type}.
         *
         * @return the type it names
         */
        public Optional<String> type() {
            return type;
        }

        /**
         * Answers {@code count}.
         *
         * @return the parameter counting it
         */
        public Optional<String> count() {
            return count;
        }

        /**
         * Answers {@code release}.
         *
         * @return the release freeing it
         */
        public Optional<String> release() {
            return release;
        }
    }
}
