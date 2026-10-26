package io.github.intisy.ai.seam.jvm.library;

import java.util.ArrayList;
import java.util.List;

/** The source of the classes and enums the type generator nests inside a service interface. */
final class JavaDeclarations {

    private static final String MEMBER = "        ";
    private static final String BODY = "            ";

    private JavaDeclarations() {
    }

    /** One field of a generated class, and the property it carries on the wire. */
    static final class Member {
        private final String wireName;
        private final String javaName;
        private final String type;

        Member(String wireName, String javaName, String type) {
            this.wireName = wireName;
            this.javaName = javaName;
            this.type = type;
        }

        boolean isRenamed() {
            return !wireName.equals(javaName);
        }
    }

    /** One constant of a generated enum, and the string it stands for on the wire. */
    static final class Constant {
        private final String wireValue;
        private final String javaName;

        Constant(String wireValue, String javaName) {
            this.wireValue = wireValue;
            this.javaName = javaName;
        }
    }

    static String classSource(String name, List<Member> members) {
        List<String> lines = new ArrayList<String>();
        lines.add("    final class " + name + " {");
        for (Member member : members) {
            if (member.isRenamed()) {
                lines.add(MEMBER + "@SerializedName(" + JavaNames.stringLiteral(member.wireName) + ")");
            }
            lines.add(MEMBER + "private final " + member.type + " " + member.javaName + ";");
        }
        if (!members.isEmpty()) {
            lines.add("");
        }
        lines.addAll(constructor(name, members));
        for (Member member : members) {
            lines.add("");
            lines.addAll(getter(member));
        }
        lines.add("");
        lines.addAll(equalsMethod(name, members));
        lines.add("");
        lines.addAll(hashCodeMethod(members));
        lines.add("");
        lines.addAll(toStringMethod(name, members));
        lines.add("    }");
        return String.join("\n", lines);
    }

    static String enumSource(String name, List<Constant> constants) {
        List<String> lines = new ArrayList<String>();
        lines.add("    enum " + name + " {");
        for (int index = 0; index < constants.size(); index++) {
            Constant constant = constants.get(index);
            lines.add(MEMBER + "@SerializedName(" + JavaNames.stringLiteral(constant.wireValue) + ")");
            lines.add(MEMBER + constant.javaName + (index == constants.size() - 1 ? "" : ","));
        }
        lines.add("    }");
        return String.join("\n", lines);
    }

    private static List<String> constructor(String name, List<Member> members) {
        List<String> parameters = new ArrayList<String>();
        for (Member member : members) {
            parameters.add(member.type + " " + member.javaName);
        }
        List<String> lines = new ArrayList<String>();
        lines.add(MEMBER + "public " + name + "(" + String.join(", ", parameters) + ") {");
        for (Member member : members) {
            lines.add(BODY + "this." + member.javaName + " = " + member.javaName + ";");
        }
        lines.add(MEMBER + "}");
        return lines;
    }

    private static List<String> getter(Member member) {
        List<String> lines = new ArrayList<String>();
        lines.add(MEMBER + "public " + member.type + " get" + JavaNames.capitalized(member.javaName) + "() {");
        lines.add(BODY + "return " + member.javaName + ";");
        lines.add(MEMBER + "}");
        return lines;
    }

    private static List<String> equalsMethod(String name, List<Member> members) {
        List<String> lines = new ArrayList<String>();
        lines.add(MEMBER + "@Override");
        lines.add(MEMBER + "public boolean equals(Object other) {");
        if (members.isEmpty()) {
            lines.add(BODY + "return other instanceof " + name + ";");
        } else {
            lines.add(BODY + "if (this == other) {");
            lines.add(BODY + "    return true;");
            lines.add(BODY + "}");
            lines.add(BODY + "if (!(other instanceof " + name + ")) {");
            lines.add(BODY + "    return false;");
            lines.add(BODY + "}");
            lines.add(BODY + name + " that = (" + name + ") other;");
            lines.add(BODY + "return " + fieldComparisons(members) + ";");
        }
        lines.add(MEMBER + "}");
        return lines;
    }

    private static String fieldComparisons(List<Member> members) {
        List<String> comparisons = new ArrayList<String>();
        for (Member member : members) {
            comparisons.add("Objects.equals(" + member.javaName + ", that." + member.javaName + ")");
        }
        return String.join("\n" + BODY + "        && ", comparisons);
    }

    private static List<String> hashCodeMethod(List<Member> members) {
        List<String> names = new ArrayList<String>();
        for (Member member : members) {
            names.add(member.javaName);
        }
        List<String> lines = new ArrayList<String>();
        lines.add(MEMBER + "@Override");
        lines.add(MEMBER + "public int hashCode() {");
        lines.add(BODY + (members.isEmpty() ? "return 0;" : "return Objects.hash(" + String.join(", ", names) + ");"));
        lines.add(MEMBER + "}");
        return lines;
    }

    private static List<String> toStringMethod(String name, List<Member> members) {
        List<String> parts = new ArrayList<String>();
        for (int index = 0; index < members.size(); index++) {
            String javaName = members.get(index).javaName;
            parts.add("\"" + (index == 0 ? name + "{" : ", ") + javaName + "=\" + " + javaName);
        }
        String body = members.isEmpty() ? "\"" + name + "{}\"" : String.join(" + ", parts) + " + \"}\"";
        List<String> lines = new ArrayList<String>();
        lines.add(MEMBER + "@Override");
        lines.add(MEMBER + "public String toString() {");
        lines.add(BODY + "return " + body + ";");
        lines.add(MEMBER + "}");
        return lines;
    }

    static boolean usesObjects(List<Member> members) {
        return !members.isEmpty();
    }

    static boolean usesSerializedName(List<Member> members) {
        for (Member member : members) {
            if (member.isRenamed()) {
                return true;
            }
        }
        return false;
    }
}
