package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestRequestTokenDialogUsageTest {

    @Test
    void newestDialogLoadWins() {
        LatestRequestToken token = new LatestRequestToken();
        long first = token.nextToken();
        long second = token.nextToken();

        assertFalse(token.isCurrent(first));
        assertTrue(token.isCurrent(second));
    }
}
