package io.github.intisy.ai.seam.jvm.library;

import io.github.intisy.ai.engine.PluginException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The source file of one service's generated interface. */
final class ServiceSource {

    private ServiceSource() {
    }

    static String of(LibraryDeclaration declaration, LibraryDeclaration.ServiceDeclaration service, int index,
                     String javaPackage, SchemaTypes types) {
        String path = "services[" + index + "]";
        refuseDuplicateFunctions(service, path, types);
        String name = JavaNames.pascalCase(service.id());
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            throw types.refusal(path + ".id", "is " + JavaNames.stringLiteral(service.id()) + ", which yields "
                    + JavaNames.stringLiteral(name) + ", not a Java identifier", "start the service id with a letter");
        }
        List<String> methods = new ArrayList<String>();
        List<LibraryDeclaration.FunctionDeclaration> functions = service.functions();
        for (int functionIndex = 0; functionIndex < functions.size(); functionIndex++) {
            methods.add(methodSource(functions.get(functionIndex), path + ".functions[" + functionIndex + "]", types));
        }
        return fileSource(declaration, service, javaPackage, name + "Service", methods, types);
    }

    private static void refuseDuplicateFunctions(LibraryDeclaration.ServiceDeclaration service, String path, SchemaTypes types) {
        Set<String> seen = new HashSet<String>();
        List<LibraryDeclaration.FunctionDeclaration> functions = service.functions();
        for (int index = 0; index < functions.size(); index++) {
            if (!seen.add(functions.get(index).name())) {
                throw types.refusal(path + ".functions[" + index + "].name", "repeats the function " + functions.get(index).name(),
                        "give every function of a service its own name");
            }
        }
    }

    /**
     * @implNote A reserved word is refused rather than renamed, because {@link LibraryServices}
     * finds the function a method calls by the method's own name.
     */
    private static String methodSource(LibraryDeclaration.FunctionDeclaration function, String path, SchemaTypes types) {
        String name = function.name();
        if (JavaNames.isReserved(name)) {
            throw types.refusal(path + ".name", "is " + JavaNames.stringLiteral(name) + ", a word Java reserves, so no method can carry it",
                    "rename the function in library.json; a Java service calls each function through a method of the same name");
        }
        String typeName = JavaNames.pascalCase(name);
        JavaType argument = types.typeOf(function.argument(), path + ".argument", typeName + "Argument");
        JavaType result = types.typeOf(function.result(), path + ".result", typeName + "Result");
        if (name.equals("wait") && argument.present().equals("long")) {
            throw types.refusal(path + ".name", "is \"wait\" with an integer argument, which would override Object.wait(long), a final method",
                    "rename the function in library.json, or declare its argument as an object");
        }
        return "    " + result.present() + " " + name + "(" + argument.present() + " argument);";
    }

    private static String fileSource(LibraryDeclaration declaration, LibraryDeclaration.ServiceDeclaration service, String javaPackage,
                                     String interfaceName, List<String> methods, SchemaTypes types) {
        List<String> lines = new ArrayList<String>();
        lines.add("// Generated from the library.json of " + JavaNames.asciiText(declaration.id() + " " + declaration.version())
                + "; regenerate it from the declaration rather than editing it.");
        lines.add("package " + javaPackage + ";");
        lines.add("");
        for (String imported : LibraryTypeGenerator.sortedImports(types.imports())) {
            lines.add("import " + imported + ";");
        }
        lines.add("");
        lines.add("public interface " + interfaceName + " {");
        for (String method : methods) {
            lines.add("");
            lines.add(method);
        }
        lines.add("");
        lines.add("    ServiceType<" + interfaceName + "> " + JavaNames.upperSnakeCase(service.id()) + " = () -> "
                + JavaNames.stringLiteral(service.id()) + ";");
        for (String nested : types.declarations()) {
            lines.add("");
            lines.add(nested);
        }
        lines.add("}");
        lines.add("");
        return String.join("\n", lines);
    }
}
