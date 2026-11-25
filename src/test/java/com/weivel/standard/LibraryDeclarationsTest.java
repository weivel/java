package com.weivel.standard;

import static com.weivel.standard.StandardFixture.refusal;
import static com.weivel.standard.StandardFixture.standard;
import static com.weivel.standard.StandardFixture.value;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.weivel.core.CoreProbes;
import com.weivel.host.LibraryAnswer;
import com.weivel.host.LibraryDeclaration;
import com.weivel.host.LoadedLibrary;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LibraryDeclarationsTest {
    private static final String ECHO_DECLARATION = "{\"importName\": \"echo\", \"version\": \"0.1.0\","
            + " \"release\": \"echo_release\", \"services\": [{\"id\": \"echo\", \"functions\": ["
            + "  {\"name\": \"echo\", \"symbol\": \"echo_echo\", \"kind\": \"bytes\"},"
            + "  {\"name\": \"where\", \"symbol\": \"echo_where\", \"kind\": \"bytes\","
            + "   \"context\": [\"home\", \"arguments\"]}]}]}";

    private static final LibraryDeclaration ECHO = new LibraryDeclaration("echo", "0.1.0", Optional.of("echo_release"),
            Collections.singletonList(new LibraryDeclaration.Service("echo", Arrays.asList(
                    LibraryDeclaration.Function.bytes("echo", "echo_echo"),
                    new LibraryDeclaration.Function("where", "echo_where", LibraryDeclaration.BYTES, 2 | 4)))));

    @Test
    void aDeclarationIsReadIntoTheHostsDeclarationValue() {
        assertEquals(ECHO, value(standard().readLibrary(ECHO_DECLARATION)));
    }

    @Test
    void aDeclarationReadIsLoadedThroughTheHost() throws Exception {
        LibraryDeclaration declaration = value(standard().readLibrary(ECHO_DECLARATION));

        try (StandardFixture.TestHost fixture = new StandardFixture.TestHost()) {
            LoadedLibrary loaded = CoreProbes.ok(fixture.host.loadLibrary(CoreProbes.echoBinary(), declaration));
            LibraryAnswer answer = loaded.services().get(0).call("echo", "hello".getBytes(StandardCharsets.UTF_8));

            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ((LibraryAnswer.Returned) answer).bytes());
        }
    }

    @Test
    void aDeclarationOfDefinitionsAloneIsReadWithNoReleaseAndLoadedThroughTheHost() throws Exception {
        String definitions = "{\"importName\": \"echo\", \"version\": \"0.2.0\", \"services\": [{\"id\": \"echo\","
                + " \"functions\": [{\"name\": \"definition\", \"symbol\": \"echo_service_definition\","
                + " \"kind\": \"definition\"}]}]}";

        LibraryDeclaration declaration = value(standard().readLibrary(definitions));

        assertEquals(new LibraryDeclaration("echo", "0.2.0", Optional.<String>empty(), Collections.singletonList(
                new LibraryDeclaration.Service("echo", Collections.singletonList(
                        LibraryDeclaration.Function.definition("definition", "echo_service_definition"))))),
                declaration);
        try (StandardFixture.TestHost fixture = new StandardFixture.TestHost()) {
            LoadedLibrary loaded = CoreProbes.ok(fixture.host.loadLibrary(CoreProbes.echoBinary(), declaration));

            assertEquals(Collections.singleton("echo"), loaded.definitions().keySet());
        }
    }

    @Test
    void aDeclarationOfContractsAloneIsReadWithItsContractsAndLoadedRegisteringNothing() throws Exception {
        String contractsAlone = "{\"importName\": \"echo\", \"version\": \"0.2.0\", \"contracts\": ["
                + " {\"capability\": \"echo.provider\", \"description\": \"echo_provider_description\"}]}";

        LibraryDeclaration declaration = value(standard().readLibrary(contractsAlone));

        assertEquals(new LibraryDeclaration("echo", "0.2.0", Optional.<String>empty(),
                Collections.<LibraryDeclaration.Service>emptyList(), Collections.singletonList(
                        new LibraryDeclaration.Contract("echo.provider", "echo_provider_description"))),
                declaration);
        try (StandardFixture.TestHost fixture = new StandardFixture.TestHost()) {
            LoadedLibrary loaded = CoreProbes.ok(fixture.host.loadLibrary(CoreProbes.echoBinary(), declaration));

            assertEquals(Collections.emptyList(), loaded.services());
            assertEquals(Collections.emptyMap(), loaded.definitions());
        }
    }

    @Test
    void aDeclarationOfNeitherAServiceNorAContractIsRefusedByTheRead() {
        String neither = "{\"importName\": \"echo\", \"version\": \"0.2.0\"}";

        refusal(standard().readLibrary(neither));
    }

    @Test
    void aDeclarationTheRulesRefuseIsRefusedAtItsField() {
        String noSymbol = "{\"importName\": \"echo\", \"version\": \"0.1.0\", \"release\": \"echo_release\","
                + " \"services\": [{\"id\": \"echo\", \"functions\": [{\"name\": \"echo\", \"kind\": \"bytes\"}]}]}";

        assertEquals("services[0].functions[0].symbol", refusal(standard().readLibrary(noSymbol)).path());
    }
}
