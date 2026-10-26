package io.github.intisy.ai.seam.jvm.library;

import io.github.intisy.ai.engine.PluginException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The Java a plugin author compiles against for a library's services: one interface per service,
 * holding a method per function, the types its schemas need, and the service's typed key.
 *
 * @implNote Types only. The calls are assembled at run time by {@link LibraryServices}, which maps
 * each method to the function of the same name, so nothing generated here executes beyond the key.
 * It is the twin of the TypeScript generator in {@code weivel/typescript} and refuses the same
 * declarations at the same paths. It refuses three things more, each one Java cannot express and
 * TypeScript can: a function named by a Java reserved word, which the proxy could not find a method
 * for; a {@code \}{@code u} sequence in the version the header names, which javac would read as a
 * character even inside a comment; and two nested types whose paths give them one name.
 */
public final class LibraryTypeGenerator {

    static final String CONTRACT_PACKAGE = SchemaTypes.CONTRACT_PACKAGE;

    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\x00-\\x1f\\x7f-\\x9f\\u2028\\u2029]");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u");
    private static final String SERVICE_TYPE = CONTRACT_PACKAGE + ".ServiceType";

    private LibraryTypeGenerator() {
    }

    /**
     * Generates the interface of each service a declaration offers.
     *
     * @param declaration the library's verified declaration
     * @param javaPackage the package the interfaces are declared in
     * @return each interface's source, keyed by its file name, in declaration order
     * @throws PluginException naming the library and the schema path, for a declaration the
     * generator cannot express
     * @throws IllegalArgumentException when {@code javaPackage} is not a Java package name
     */
    public static Map<String, String> generate(LibraryDeclaration declaration, String javaPackage) {
        refuseNonPackage(javaPackage);
        refuseHeaderBreakout(declaration);
        Map<String, String> sources = new LinkedHashMap<String, String>();
        Set<String> contractTypes = new TreeSet<String>();
        List<LibraryDeclaration.ServiceDeclaration> services = declaration.services();
        for (int index = 0; index < services.size(); index++) {
            SchemaTypes types = new SchemaTypes(declaration.id(), interfaceName(services.get(index)));
            String source = ServiceSource.of(declaration, services.get(index), index, javaPackage, types);
            sources.put(interfaceName(services.get(index)) + ".java", source);
            contractTypes.addAll(types.contractTypes());
        }
        refuseCollidingNames(declaration, contractTypes);
        return sources;
    }

    /**
     * Reads a {@code library.json} and writes the interface of each service it offers.
     *
     * @param declarationFile the {@code library.json} to read
     * @param outputDirectory the source root to write into, under the package's directories
     * @param javaPackage the package the interfaces are declared in
     * @return the files written, in declaration order
     * @throws IOException when the declaration cannot be read or a source cannot be written
     * @throws PluginException when the declaration is invalid or cannot be expressed in Java
     * @throws IllegalArgumentException when {@code javaPackage} is not a Java package name
     */
    public static List<Path> writeSources(Path declarationFile, Path outputDirectory, String javaPackage) throws IOException {
        refuseNonPackage(javaPackage);
        String text = new String(Files.readAllBytes(declarationFile), StandardCharsets.UTF_8);
        Map<String, String> sources = generate(LibraryDeclaration.parse(text), javaPackage);
        Path packageDirectory = outputDirectory.resolve(javaPackage.replace('.', '/'));
        Files.createDirectories(packageDirectory);
        List<Path> written = new ArrayList<Path>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = packageDirectory.resolve(source.getKey());
            Files.write(file, source.getValue().getBytes(StandardCharsets.UTF_8));
            written.add(file);
        }
        return written;
    }

    /**
     * Writes the interfaces of a {@code library.json}'s services, naming each file written.
     *
     * @param arguments {@code <library.json> <output dir> <java package>}
     */
    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("usage: LibraryTypeGenerator <library.json> <output dir> <java package>");
            System.exit(1);
        }
        try {
            for (Path written : writeSources(Paths.get(arguments[0]), Paths.get(arguments[1]), arguments[2])) {
                System.out.println(written);
            }
        } catch (IOException | RuntimeException refused) {
            System.err.println(refused.getMessage());
            System.exit(1);
        }
    }

    static String pascalCase(String name) {
        return JavaNames.pascalCase(name);
    }

    private static String interfaceName(LibraryDeclaration.ServiceDeclaration service) {
        return JavaNames.pascalCase(service.id()) + "Service";
    }

    private static void refuseNonPackage(String javaPackage) {
        for (String segment : javaPackage.split("\\.", -1)) {
            if (!JavaNames.isIdentifier(segment)) {
                throw new IllegalArgumentException("java package \"" + javaPackage + "\" is not a package name: \"" + segment
                        + "\" is not a Java identifier; name a package such as com.example.plugin");
            }
        }
    }

    /** The header interpolates the id and version, and a line break in either would end its comment. */
    private static void refuseHeaderBreakout(LibraryDeclaration declaration) {
        refuseHeaderBreakout(declaration.id(), "id", declaration.id());
        refuseHeaderBreakout(declaration.id(), "version", declaration.version());
    }

    private static void refuseHeaderBreakout(String libraryId, String field, String value) {
        if (CONTROL_CHARACTER.matcher(value).find()) {
            throw headerRefusal(libraryId, field, value, "a control character");
        }
        if (UNICODE_ESCAPE.matcher(value).find()) {
            throw headerRefusal(libraryId, field, value, "a backslash before u, which javac reads as a character escape even in a comment");
        }
    }

    private static PluginException headerRefusal(String libraryId, String field, String value, String carried) {
        return new PluginException(libraryId, "cannot generate types: " + field + " is " + JavaNames.stringLiteral(value)
                + ", which carries " + carried, "remove control characters from the declaration's " + field);
    }

    /**
     * @implNote Mirrors the TypeScript rule, under which a service's Pascal name and that name plus
     * {@code Service} are both declared beside the imported types. Java declares only the second, in
     * a file of its own, but refusing the same ids keeps one declaration valid for both generators.
     */
    private static void refuseCollidingNames(LibraryDeclaration declaration, Set<String> contractTypes) {
        Set<String> taken = new HashSet<String>(contractTypes);
        taken.add("ServiceType");
        List<LibraryDeclaration.ServiceDeclaration> services = declaration.services();
        for (int index = 0; index < services.size(); index++) {
            String name = JavaNames.pascalCase(services.get(index).id());
            for (String declared : new String[] {name, name + "Service"}) {
                if (!taken.add(declared)) {
                    throw new PluginException(declaration.id(), "cannot generate types: services[" + index + "].id generates the name "
                            + declared + ", which is already taken in the generated module", "give the service an id that does not collide");
                }
            }
        }
    }

    static List<String> sortedImports(Set<String> imports) {
        List<String> sorted = new ArrayList<String>(imports);
        sorted.add(SERVICE_TYPE);
        Collections.sort(sorted);
        return sorted;
    }
}
