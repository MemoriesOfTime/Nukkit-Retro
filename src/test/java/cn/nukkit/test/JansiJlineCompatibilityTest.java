package cn.nukkit.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JansiJlineCompatibilityTest {

    /**
     * jline 2.14.6's AnsiWindowsTerminal (instantiated by TerminalFactory on
     * Windows during CommandReader startup) calls
     * org.fusesource.jansi.AnsiConsole.wrapOutputStream(OutputStream) from its
     * constructor. jansi 2.x removed that method, so a jansi 2.x jar on the
     * classpath crashes server startup on Windows with NoSuchMethodError.
     *
     * The constructor is Windows-flavor only, but it performs no Windows-native
     * calls before the jansi linkage, so instantiating it here guards the exact
     * linkage on every OS.
     */
    @Test
    public void jlineWindowsTerminalLinksAgainstBundledJansi() throws Exception {
        Object terminal = Class.forName("jline.AnsiWindowsTerminal").newInstance();
        assertNotNull(terminal);
    }
}
