package io.github.intisy.ai.seam.jvm.library;

/** A schema's Java type, spelled for a value that is always present and for one that may be absent. */
final class JavaType {

    private final String present;
    private final String absentable;

    private JavaType(String present, String absentable) {
        this.present = present;
        this.absentable = absentable;
    }

    static JavaType reference(String name) {
        return new JavaType(name, name);
    }

    static JavaType primitive(String primitive, String boxed) {
        return new JavaType(primitive, boxed);
    }

    /** @return the spelling for a required property, an argument or a result */
    String present() {
        return present;
    }

    /** @return the spelling for an optional property or a list element, which a primitive cannot be */
    String absentable() {
        return absentable;
    }

    JavaType boxed() {
        return reference(absentable);
    }
}
