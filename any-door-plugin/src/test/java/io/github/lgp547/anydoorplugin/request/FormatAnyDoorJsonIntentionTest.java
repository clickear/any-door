package io.github.lgp547.anydoorplugin.request;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** JSON formatting should be available from the normal editor intention workflow. */
public class FormatAnyDoorJsonIntentionTest extends BasePlatformTestCase {
    public void testIntentionFormatsJsonAtCaret() {
        var file = myFixture.configureByText("request.anydoor", "RUN demo.UserService#create()\n{\"id\":1,\"active\":true<caret>}");
        var intention = new FormatAnyDoorJsonIntention();
        assertTrue(intention.isAvailable(getProject(), myFixture.getEditor(), file));
        intention.invoke(getProject(), myFixture.getEditor(), file);
        assertTrue(myFixture.getEditor().getDocument().getText(), myFixture.getEditor().getDocument().getText().contains("\n  \"id\": 1,"));
    }
}
