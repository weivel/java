package io.github.intisy.ai.seam.jvm.library;

import static io.github.intisy.ai.seam.jvm.library.EchoFixture.ECHO_PIN;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshCache;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshHost;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import io.github.intisy.ai.api.AppDescriptor;
import io.github.intisy.ai.api.ServiceType;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class LibraryTypeGeneratorTest {

    private static final String GENERATED_PACKAGE = "io.github.intisy.ai.seam.jvm.library.generated";
    private static final String APP_DESCRIPTOR = "https://weivel.github.io/model/plugin.schema.json#/definitions/appDescriptor";

    private static final String GENERATED_ECHO_SERVICE = String.join("\n",
            "// Generated from the library.json of echo 0.1.1; regenerate it from the declaration rather than editing it.",
            "package io.github.intisy.ai.seam.jvm.library.generated;",
            "",
            "import com.google.gson.JsonElement;",
            "import com.google.gson.JsonObject;",
            "import io.github.intisy.ai.api.ServiceType;",
            "import java.util.Objects;",
            "",
            "public interface EchoService {",
            "",
            "    JsonElement echo(JsonElement argument);",
            "",
            "    JsonObject fail(JsonObject argument);",
            "",
            "    String repeat(RepeatArgument argument);",
            "",
            "    String garbage(JsonObject argument);",
            "",
            "    ServiceType<EchoService> ECHO = () -> \"echo\";",
            "",
            "    final class RepeatArgument {",
            "        private final long count;",
            "",
            "        public RepeatArgument(long count) {",
            "            this.count = count;",
            "        }",
            "",
            "        public long getCount() {",
            "            return count;",
            "        }",
            "",
            "        @Override",
            "        public boolean equals(Object other) {",
            "            if (this == other) {",
            "                return true;",
            "            }",
            "            if (!(other instanceof RepeatArgument)) {",
            "                return false;",
            "            }",
            "            RepeatArgument that = (RepeatArgument) other;",
            "            return Objects.equals(count, that.count);",
            "        }",
            "",
            "        @Override",
            "        public int hashCode() {",
            "            return Objects.hash(count);",
            "        }",
            "",
            "        @Override",
            "        public String toString() {",
            "            return \"RepeatArgument{count=\" + count + \"}\";",
            "        }",
            "    }",
            "}",
            "");

    private static Path modelDirectory() {
        return Paths.get(System.getProperty("model.directory", "../model"));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String echoDeclarationText() throws IOException {
        return read(modelDirectory().resolve("test").resolve("fixtures").resolve("echo.library.json"));
    }

    private static LibraryDeclaration echoDeclaration() throws IOException {
        return LibraryDeclaration.parse(echoDeclarationText());
    }

    private static JsonObject fixtureDeclaration(String serviceId, String argument, String result) {
        return JsonParser.parseString("{\"id\":\"fixture\",\"version\":\"1.0.0\",\"binary\":\"fixture\",\"release\":\"fixture_release\","
                + "\"services\":[{\"id\":\"" + serviceId + "\",\"functions\":[{\"name\":\"get\",\"symbol\":\"fixture_get\",\"kind\":\"json\","
                + "\"argument\":" + argument + ",\"result\":" + result + "}]}]}").getAsJsonObject();
    }

    private static LibraryDeclaration declarationWithResult(String result, String argument) {
        return LibraryDeclaration.parse(fixtureDeclaration("app-registry", argument, result).toString());
    }

    private static LibraryDeclaration declarationWithResult(String result) {
        return declarationWithResult(result, "{\"type\":\"object\"}");
    }

    private static Map<String, String> generated(LibraryDeclaration declaration) {
        return LibraryTypeGenerator.generate(declaration, GENERATED_PACKAGE);
    }

    private static String refusalMessage(LibraryDeclaration declaration) {
        return assertThrows(PluginException.class, () -> generated(declaration)).getMessage();
    }

    private static void assertRefusedAt(String expected, LibraryDeclaration declaration) {
        String message = refusalMessage(declaration);
        assertTrue(message.contains(expected), message);
    }

    /** Compiles the generated sources the way a plugin author would, at release 8 against this module's classpath. */
    private static ClassLoader compiled(Map<String, String> sources) throws IOException {
        Path root = Files.createTempDirectory("weivel-generated-java-");
        Path classes = Files.createDirectories(root.resolve("classes"));
        List<File> files = new ArrayList<File>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = root.resolve("src").resolve(GENERATED_PACKAGE.replace('.', '/')).resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, source.getValue().getBytes(StandardCharsets.UTF_8));
            files.add(file.toFile());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = Arrays.asList("--release", "8", "-Xlint:all", "-encoding", "UTF-8",
                    "-classpath", System.getProperty("java.class.path"), "-d", classes.toString());
            boolean compiledCleanly = compiler.getTask(null, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromFiles(files)).call();
            List<String> reported = new ArrayList<String>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                reported.add(diagnostic.getKind() + " " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null));
            }
            assertTrue(compiledCleanly && reported.isEmpty(), reported.toString());
        }
        return new URLClassLoader(new URL[] {classes.toUri().toURL()}, LibraryTypeGeneratorTest.class.getClassLoader());
    }

    private static Class<?> loaded(ClassLoader loader, String simpleName) throws ClassNotFoundException {
        Class<?> type = Class.forName(GENERATED_PACKAGE + "." + simpleName, true, loader);
        assertSame(loader, type.getClassLoader(), "resolved from the test classpath rather than the generated classes");
        return type;
    }

    private static Map<String, String> fieldTypes(Class<?> type) {
        Map<String, String> types = new LinkedHashMap<String, String>();
        for (Field field : type.getDeclaredFields()) {
            types.put(field.getName(), field.getGenericType().getTypeName());
        }
        return types;
    }

    @Test
    void emitsTheEchoServicesInterfaceAndTypedKeyWhichCompileAndLoad() throws Exception {
        Map<String, String> sources = generated(echoDeclaration());
        assertEquals(Collections.singleton("EchoService.java"), sources.keySet());
        assertEquals(GENERATED_ECHO_SERVICE, sources.get("EchoService.java"));

        Class<?> echoService = loaded(compiled(sources), "EchoService");
        Class<?> repeatArgument = loaded(echoService.getClassLoader(), "EchoService$RepeatArgument");

        assertEquals(JsonElement.class, echoService.getMethod("echo", JsonElement.class).getReturnType());
        assertEquals(JsonObject.class, echoService.getMethod("fail", JsonObject.class).getReturnType());
        assertEquals(String.class, echoService.getMethod("repeat", repeatArgument).getReturnType());
        assertEquals(String.class, echoService.getMethod("garbage", JsonObject.class).getReturnType());
        ServiceType<?> key = (ServiceType<?>) echoService.getField("ECHO").get(null);
        assertEquals("echo", key.id());
        Object argument = repeatArgument.getConstructor(long.class).newInstance(3L);
        assertEquals(argument, repeatArgument.getConstructor(long.class).newInstance(3L));
        assertEquals("RepeatArgument{count=3}", argument.toString());
    }

    @Test
    void startsWithAHeaderNamingTheDeclarationItWasGeneratedFrom() throws IOException {
        String header = generated(echoDeclaration()).get("EchoService.java").split("\n")[0];
        assertTrue(header.matches("^// .*[Gg]enerated.*echo 0\\.1\\.1.*"), header);
    }

    @Test
    void mapsARefToAModelDefinitionOntoTheContractsOwnTypeImportedAndNeverDeclared() throws Exception {
        Map<String, String> sources = generated(declarationWithResult("{\"$ref\":\"" + APP_DESCRIPTOR + "\"}"));
        String source = sources.get("AppRegistryService.java");

        assertTrue(source.contains("import io.github.intisy.ai.api.AppDescriptor;"), source);
        assertFalse(source.matches("(?s).*(class|interface|enum) AppDescriptor\\b.*"), source);
        assertTrue(source.contains("    AppDescriptor get(JsonObject argument);"), source);
        Class<?> service = loaded(compiled(sources), "AppRegistryService");
        assertEquals(AppDescriptor.class, service.getMethod("get", JsonObject.class).getReturnType());
    }

    @Test
    void refusesAModelRefToADefinitionTheModelDoesNotPublishNamingIt() {
        String[][] unpublished = {
            {"plugin.schema.json", "pluginContext"},
            {"plugin.schema.json", "noSuchThing"},
            {"library.schema.json", "appDescriptor"},
            {"other.schema.json", "appDescriptor"},
        };
        for (String[] reference : unpublished) {
            String ref = "https://weivel.github.io/model/" + reference[0] + "#/definitions/" + reference[1];
            assertRefusedAt("result.$ref names " + reference[1] + " in " + reference[0] + ", which weivel/model does not publish",
                    declarationWithResult("{\"$ref\":\"" + ref + "\"}"));
        }
    }

    @Test
    void refusesARefOutsideTheModel() {
        assertRefusedAt("services[0].functions[0].result.$ref", declarationWithResult("{\"$ref\":\"#/definitions/local\"}"));
        assertRefusedAt("services[0].functions[0].result.$ref",
                declarationWithResult("{\"$ref\":\"https://example.com/x.json#/definitions/appDescriptor\"}"));
    }

    @Test
    void refusesASchemaFormItDoesNotSupportNamingTheSchemaPath() {
        assertRefusedAt("services[0].functions[0].result.oneOf", declarationWithResult("{\"oneOf\":[{\"type\":\"string\"}]}"));
        assertRefusedAt("services[0].functions[0].result.type", declarationWithResult("{\"type\":[\"string\",\"null\"]}"));
        assertRefusedAt("services[0].functions[0].result.properties.nested.items.anyOf",
                declarationWithResult("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"array\",\"items\":{\"anyOf\":[]}}}}"));
        assertRefusedAt("services[0].functions[0].argument.additionalProperties",
                declarationWithResult("{\"type\":\"string\"}", "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"));
        assertRefusedAt("services[0].functions[0].result.items", declarationWithResult("{\"items\":{}}"));
        assertRefusedAt("services[0].functions[0].result.required", declarationWithResult("{\"type\":\"string\",\"required\":[]}"));
    }

    @Test
    void mapsEverySupportedForm() throws Exception {
        LibraryDeclaration declaration = declarationWithResult("{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"description\":\"annotations are ignored\"},"
                + "\"size\":{\"type\":\"number\"},"
                + "\"count\":{\"type\":\"integer\",\"minimum\":0},"
                + "\"enabled\":{\"type\":\"boolean\"},"
                + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"mode\":{\"enum\":[\"fast\",\"slow\",3,null]},"
                + "\"not-an-identifier\":{},"
                + "\"accepted\":true,"
                + "\"refused\":false,"
                + "\"anything\":{\"type\":\"array\",\"items\":true}},"
                + "\"required\":[\"name\",\"mode\",\"undeclared\",\"also-undeclared\"],"
                + "\"additionalProperties\":false}");
        Map<String, String> sources = generated(declaration);
        Class<?> result = loaded(compiled(sources), "AppRegistryService$GetResult");

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("name", "java.lang.String");
        expected.put("size", "java.lang.Double");
        expected.put("count", "java.lang.Long");
        expected.put("enabled", "java.lang.Boolean");
        expected.put("tags", "java.util.List<java.lang.String>");
        expected.put("mode", "com.google.gson.JsonElement");
        expected.put("notAnIdentifier", "com.google.gson.JsonElement");
        expected.put("accepted", "com.google.gson.JsonElement");
        expected.put("refused", "java.lang.Void");
        expected.put("anything", "java.util.List<com.google.gson.JsonElement>");
        expected.put("undeclared", "com.google.gson.JsonElement");
        expected.put("alsoUndeclared", "com.google.gson.JsonElement");
        assertEquals(expected, fieldTypes(result));
        assertEquals("not-an-identifier", result.getDeclaredField("notAnIdentifier").getAnnotation(SerializedName.class).value());
        assertEquals("also-undeclared", result.getDeclaredField("alsoUndeclared").getAnnotation(SerializedName.class).value());
    }

    @Test
    void mapsRequiredPrimitivesUnboxedAndNamesNestedTypesFromTheirPath() throws Exception {
        LibraryDeclaration declaration = declarationWithResult("{\"type\":\"object\",\"properties\":{"
                + "\"count\":{\"type\":\"integer\"},"
                + "\"ratio\":{\"type\":\"number\"},"
                + "\"on\":{\"type\":\"boolean\"},"
                + "\"speed\":{\"enum\":[\"fast\",\"slow\",\"fast\",\"very-slow\"]},"
                + "\"flags\":{\"enum\":[true,false]},"
                + "\"levels\":{\"enum\":[1,2,3]},"
                + "\"nothing\":{\"enum\":[null]},"
                + "\"owners\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"class\":{\"type\":\"string\"}},\"additionalProperties\":false}}},"
                + "\"required\":[\"count\",\"ratio\",\"on\",\"flags\",\"levels\"],\"additionalProperties\":false}");
        ClassLoader loader = compiled(generated(declaration));
        Class<?> result = loaded(loader, "AppRegistryService$GetResult");
        Class<?> speed = loaded(loader, "AppRegistryService$GetResultSpeed");
        Class<?> owner = loaded(loader, "AppRegistryService$GetResultOwnersItem");

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("count", "long");
        expected.put("ratio", "double");
        expected.put("on", "boolean");
        expected.put("speed", speed.getTypeName());
        expected.put("flags", "boolean");
        expected.put("levels", "long");
        expected.put("nothing", "java.lang.Void");
        expected.put("owners", "java.util.List<" + owner.getTypeName() + ">");
        assertEquals(expected, fieldTypes(result));
        assertEquals(Arrays.asList("FAST", "SLOW", "VERY_SLOW"), constantNames(speed));
        assertEquals("very-slow", speed.getField("VERY_SLOW").getAnnotation(SerializedName.class).value());
        assertEquals("class", owner.getDeclaredField("classValue").getAnnotation(SerializedName.class).value());
        assertEquals(String.class, owner.getMethod("getClassValue").getReturnType());
    }

    private static List<String> constantNames(Class<?> enumType) {
        List<String> names = new ArrayList<String>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    @Test
    void mapsAdditionalPropertiesTrueLikeAnAbsentOneAndBooleanSchemasToJsonElementAndVoid() throws Exception {
        Map<String, String> open = generated(declarationWithResult("false",
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}},\"additionalProperties\":true}"));
        Map<String, String> absent = generated(declarationWithResult("false",
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}"));
        assertEquals(absent, open);
        Class<?> service = loaded(compiled(open), "AppRegistryService");
        Class<?> argument = loaded(service.getClassLoader(), "AppRegistryService$GetArgument");
        assertEquals(Void.class, service.getMethod("get", argument).getReturnType());
        assertTrue(generated(declarationWithResult("{\"type\":\"string\"}", "true")).get("AppRegistryService.java")
                .contains("    String get(JsonElement argument);"));
    }

    @Test
    void refusesAServiceIdThatYieldsNoJavaIdentifierNamingIt() {
        for (String id : new String[] {"2fa", "1-password"}) {
            assertRefusedAt("services[0].id", LibraryDeclaration.parse(fixtureDeclaration(id, "{}", "{\"type\":\"string\"}").toString()));
        }
    }

    @Test
    void refusesAControlCharacterInTheVersionTheHeaderNames() {
        for (String version : new String[] {"1.0.0\nclass Injected {}", "1.0.0\u2028x", "1.0.0\r"}) {
            JsonObject declaration = fixtureDeclaration("app-registry", "{}", "{\"type\":\"string\"}");
            declaration.addProperty("version", version);
            assertRefusedAt("cannot generate types: version", LibraryDeclaration.parse(declaration.toString()));
        }
    }

    @Test
    void writesABackslashInTheVersionSoJavacCannotReadAnEscapeOutOfIt() throws Exception {
        JsonObject declaration = fixtureDeclaration("app-registry", "{}", "{\"type\":\"string\"}");
        declaration.addProperty("version", "1.0.0\\u000aclass Injected {}");
        Map<String, String> sources = generated(LibraryDeclaration.parse(declaration.toString()));

        assertTrue(sources.get("AppRegistryService.java").startsWith(
                "// Generated from the library.json of fixture 1.0.0\\u005cu000aclass Injected {}; "), sources.get("AppRegistryService.java"));
        ClassLoader loader = compiled(sources);
        loaded(loader, "AppRegistryService");
        assertThrows(ClassNotFoundException.class, () -> Class.forName(GENERATED_PACKAGE + ".Injected", false, loader));
    }

    @Test
    void refusesTwoServicesWhoseFilesDifferOnlyInCase() {
        JsonObject declaration = fixtureDeclaration("ab", "{}", "{\"type\":\"string\"}");
        JsonArray services = declaration.getAsJsonArray("services");
        JsonObject second = services.get(0).deepCopy().getAsJsonObject();
        second.addProperty("id", "a-b");
        services.add(second);
        String message = refusalMessage(LibraryDeclaration.parse(declaration.toString()));
        assertTrue(message.contains("services[1].id \"a-b\" generates ABService.java"), message);
        assertTrue(message.contains("service \"ab\""), message);
    }

    @Test
    void refusesAWaitFunctionTakingAnIntegerWhichWouldOverrideAFinalMethod() {
        JsonObject declaration = fixtureDeclaration("app-registry", "{\"type\":\"integer\"}", "{\"type\":\"string\"}");
        declaration.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                .addProperty("name", "wait");
        assertRefusedAt("services[0].functions[0].name", LibraryDeclaration.parse(declaration.toString()));
        JsonObject objectArgument = fixtureDeclaration("app-registry", "{}", "{\"type\":\"string\"}");
        objectArgument.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                .addProperty("name", "wait");
        assertTrue(generated(LibraryDeclaration.parse(objectArgument.toString())).get("AppRegistryService.java")
                .contains("    String wait(JsonElement argument);"));
    }

    @Test
    void refusesTwoServicesWhoseNamesCollide() {
        JsonObject declaration = fixtureDeclaration("app-registry", "{}", "{\"type\":\"string\"}");
        JsonArray services = declaration.getAsJsonArray("services");
        services.add(services.get(0).deepCopy());
        assertRefusedAt("AppRegistry", LibraryDeclaration.parse(declaration.toString()));
    }

    @Test
    void refusesAServiceWhosePascalNameCollidesWithAnImportedContractType() {
        JsonObject declaration = fixtureDeclaration("app-descriptor", "{}", "{\"$ref\":\"" + APP_DESCRIPTOR + "\"}");
        assertRefusedAt("services[0].id", LibraryDeclaration.parse(declaration.toString()));
    }

    @Test
    void refusesARepeatedFunctionName() {
        JsonObject declaration = fixtureDeclaration("app-registry", "{}", "{\"type\":\"string\"}");
        JsonArray functions = declaration.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions");
        functions.add(functions.get(0).deepCopy());
        assertRefusedAt("services[0].functions[1].name", LibraryDeclaration.parse(declaration.toString()));
    }

    @Test
    void refusesAFunctionNameJavaReservesWhichTheProxyCouldNotMapToAMethod() {
        for (String name : new String[] {"default", "class", "int"}) {
            JsonObject declaration = fixtureDeclaration("app-registry", "{}", "{\"type\":\"string\"}");
            declaration.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                    .addProperty("name", name);
            assertRefusedAt("services[0].functions[0].name", LibraryDeclaration.parse(declaration.toString()));
        }
    }

    @Test
    void numbersTwoNestedTypesTheirPathsNameAlikeInTheOrderTheyAreReached() throws Exception {
        String object = "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}},\"additionalProperties\":false}";
        ClassLoader loader = compiled(generated(
                declarationWithResult("{\"type\":\"object\",\"properties\":{\"a-b\":" + object + ",\"aB\":" + object + "}}")));
        Class<?> result = loaded(loader, "AppRegistryService$GetResult");

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("aB", loaded(loader, "AppRegistryService$GetResultAB").getTypeName());
        expected.put("aB2", loaded(loader, "AppRegistryService$GetResultAB2").getTypeName());
        assertEquals(expected, fieldTypes(result));
    }

    @Test
    void numbersANestedTypeWhosePropertyAddsNothingToItsParentsName() throws Exception {
        ClassLoader loader = compiled(generated(
                declarationWithResult("{\"type\":\"object\",\"properties\":{\"_\":{\"type\":\"object\",\"properties\":{\"x\":{}}}}}")));
        Class<?> result = loaded(loader, "AppRegistryService$GetResult");
        Class<?> nested = loaded(loader, "AppRegistryService$GetResult2");

        assertEquals(Collections.singletonMap("_Value", nested.getTypeName()), fieldTypes(result));
        assertEquals("_", result.getDeclaredField("_Value").getAnnotation(SerializedName.class).value());
        assertEquals(Collections.singletonMap("x", "com.google.gson.JsonElement"), fieldTypes(nested));
    }

    @Test
    void refusesAJavaPackageThatIsNotAPackageName() throws IOException {
        Path output = Files.createTempDirectory("weivel-generated-java-");
        Path declarationFile = output.resolve("library.json");
        Files.write(declarationFile, echoDeclarationText().getBytes(StandardCharsets.UTF_8));
        for (String javaPackage : new String[] {"", "1bad", "a..b", "com.class.x", "com.example-x"}) {
            IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                    () -> LibraryTypeGenerator.writeSources(declarationFile, output, javaPackage));
            assertTrue(refusal.getMessage().contains("\"" + javaPackage + "\""), refusal.getMessage());
        }
    }

    @Test
    void writesEachServiceIntoItsPackageDirectory() throws IOException {
        Path output = Files.createTempDirectory("weivel-generated-java-");
        Path declarationFile = output.resolve("library.json");
        Files.write(declarationFile, echoDeclarationText().getBytes(StandardCharsets.UTF_8));

        List<Path> written = LibraryTypeGenerator.writeSources(declarationFile, output.resolve("out"), GENERATED_PACKAGE);

        Path expected = output.resolve("out").resolve(GENERATED_PACKAGE.replace('.', '/')).resolve("EchoService.java");
        assertEquals(Collections.singletonList(expected), written);
        assertEquals(GENERATED_ECHO_SERVICE, read(expected));
    }

    @Test
    void roundTripsAGeneratedObjectThroughALibraryCallWithGson() throws Exception {
        JsonObject declaring = JsonParser.parseString(echoDeclarationText()).getAsJsonObject();
        JsonObject echoFunction = declaring.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject();
        JsonElement shape = JsonParser.parseString("{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\"},\"count\":{\"type\":\"integer\"},\"ratio\":{\"type\":\"number\"},"
                + "\"speed\":{\"enum\":[\"very-fast\",\"slow\"]},\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},"
                + "\"owner\":{\"type\":\"object\",\"properties\":{\"class\":{\"type\":\"string\"}},\"additionalProperties\":false}},"
                + "\"required\":[\"name\",\"count\"],\"additionalProperties\":false}");
        echoFunction.add("argument", shape);
        echoFunction.add("result", shape.deepCopy());
        LibraryDeclaration declaration = LibraryDeclaration.parse(declaring.toString());
        ClassLoader loader = compiled(LibraryTypeGenerator.generate(declaration, GENERATED_PACKAGE));
        Class<?> service = loaded(loader, "EchoService");
        Class<?> argumentType = loaded(loader, "EchoService$EchoArgument");
        Class<?> speedType = loaded(loader, "EchoService$EchoArgumentSpeed");
        Class<?> ownerType = loaded(loader, "EchoService$EchoArgumentOwner");
        Constructor<?> argumentConstructor = argumentType.getConstructors()[0];
        Object owner = ownerType.getConstructors()[0].newInstance("first");
        Object argument = argumentConstructor.newInstance("named", 7L, 0.5, speedType.getField("VERY_FAST").get(null), Arrays.asList(1L, 2L), owner);

        PluginHost host = freshHost();
        LibraryAcquirer.AcquiredLibrary acquired = new LibraryAcquirer().acquire(ECHO_PIN, freshCache());
        LibraryBridge bridge = LibraryBridge.open(acquired.binaryPath(), declaration);
        try {
            new LibraryServices(host).provide(bridge, declaration, Collections.<String, Class<?>>singletonMap("echo", service));
            Method echo = service.getMethod("echo", argumentType);
            Object result = echo.invoke(host.service("echo"), argument);

            String wire = "{\"name\":\"named\",\"count\":7,\"ratio\":0.5,\"speed\":\"very-fast\",\"tags\":[1,2],\"owner\":{\"class\":\"first\"}}";
            assertEquals(wire, new Gson().toJson(argument));
            assertSame(loaded(loader, "EchoService$EchoResult"), result.getClass());
            assertEquals(wire, new Gson().toJson(result));
            assertEquals("named", result.getClass().getMethod("getName").invoke(result));
            assertEquals(7L, result.getClass().getMethod("getCount").invoke(result));
            assertEquals(loaded(loader, "EchoService$EchoResultSpeed").getField("VERY_FAST").get(null),
                    result.getClass().getMethod("getSpeed").invoke(result));
        } finally {
            bridge.close();
        }
    }

    @Test
    void publishesExactlyTheDefinitionsTheModelSiblingDoes() throws IOException {
        Map<String, List<String>> published = new LinkedHashMap<String, List<String>>();
        for (String file : new String[] {"library.schema.json", "plugin.schema.json"}) {
            JsonObject schema = JsonParser.parseString(read(modelDirectory().resolve(file))).getAsJsonObject();
            JsonObject definitions = schema.has("definitions") ? schema.getAsJsonObject("definitions") : new JsonObject();
            published.put(file, new ArrayList<String>(new TreeSet<String>(definitions.keySet())));
        }
        assertEquals(published, ModelDefinitions.published());
    }

    @Test
    void mapsEveryPublishedDefinitionOntoATypeTheContractDeclares() {
        List<String> missing = new ArrayList<String>();
        for (Map.Entry<String, List<String>> file : ModelDefinitions.published().entrySet()) {
            for (String definition : file.getValue()) {
                try {
                    Class.forName(SchemaTypes.CONTRACT_PACKAGE + "." + JavaNames.pascalCase(definition));
                } catch (ClassNotFoundException absent) {
                    missing.add(file.getKey() + "#" + definition);
                }
            }
        }
        assertEquals(Collections.emptyList(), missing);
    }
}
