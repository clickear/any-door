package io.github.lgp547.anydoorplugin.dialog;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainUISizingTest {

    @Test
    void expandsWindowToMatchPreferredContentSize() {
        Dimension current = new Dimension(180, 120);
        Dimension preferred = new Dimension(670, 500);

        Dimension resized = MainUI.expandToFit(current, preferred);

        assertEquals(new Dimension(670, 500), resized);
    }

    @Test
    void keepsExistingWindowSizeWhenAlreadyLargerThanContent() {
        Dimension current = new Dimension(900, 650);
        Dimension preferred = new Dimension(670, 500);

        Dimension resized = MainUI.expandToFit(current, preferred);

        assertEquals(new Dimension(900, 650), resized);
    }
}
