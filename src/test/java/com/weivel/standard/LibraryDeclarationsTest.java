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
import org.junit.jupiter.api.Test;

class LibraryDeclarationsTest {
    private static final String ECHO_DECLARATION = "{\"importName\": \"echo\", \"version\": \"0.1.0\","
            + " \"release\": \"echo_release\", \"services\": [{\"id\": \"echo\", \"functions\": ["
            + "  {\"name\": \"echo\", \"symbol\": \"echo_echo\", \"kind\": \"bytes\"},"
            + "  {\"name\": \"where\", \"symbol\": \"echo_where\", \"kind\": \"bytes\","
            + "   \"context\": [\"home\", \"arguments\"]}]}]}";

    private static final LibraryDeclaration ECHO = new LibraryDeclaration("echo", "0.1.0", "echo_release",
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
    void aDeclarationTheRulesRefuseIsRefusedAtItsField() {
        String noSymbol = "{\"importName\": \"echo\", \"version\": \"0.1.0\", \"release\": \"echo_release\","
                + " \"services\": [{\"id\": \"echo\", \"functions\": [{\"name\": \"echo\", \"kind\": \"bytes\"}]}]}";

        assertEquals("services[0].functions[0].symbol", refusal(standard().readLibrary(noSymbol)).path());
    }
}
