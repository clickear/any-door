package io.github.lgp547.anydoorplugin.request;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.command.WriteCommandAction;

/** Typing a template should have the same low-friction closing behaviour as an HTTP request variable. */
public class AnyDoorTypedHandlerTest extends BasePlatformTestCase {
    public void testSecondTemplateBraceCompletesClosingBraces() {
        var file = myFixture.configureByText("request.anydoor", "{<caret>");
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            myFixture.getEditor().getDocument().insertString(1, "{");
            myFixture.getEditor().getCaretModel().moveToOffset(2);
        });
        new AnyDoorTypedHandler().charTyped('{', getProject(), myFixture.getEditor(), file);
        assertEquals("{{}}", myFixture.getEditor().getDocument().getText());
        assertEquals(2, myFixture.getEditor().getCaretModel().getOffset());
    }
}
