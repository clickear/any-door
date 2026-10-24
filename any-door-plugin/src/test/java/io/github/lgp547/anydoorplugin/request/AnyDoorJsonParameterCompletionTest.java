package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;

/** The request body should complete from the method the current RUN block targets. */
public class AnyDoorJsonParameterCompletionTest extends BasePlatformTestCase {
    public void testJsonBodyOffersCurrentMethodParameters() {
        myFixture.addFileToProject("demo/UserService.java", "package demo; public class UserService { public void create(String userId, int retries) {} }");
        myFixture.configureByText("request.anydoor", "RUN demo.UserService#create()\n{\n  <caret>\n}");
        LookupElement[] items = myFixture.completeBasic();
        assertNotNull(items);
        var values = Arrays.stream(items).map(LookupElement::getLookupString).toList();
        assertTrue(values.toString(), values.contains("userId"));
        assertTrue(values.toString(), values.contains("retries"));
    }
    public void testSelectingParameterWritesJsonKeySyntax() {
        myFixture.addFileToProject("demo/UserService.java", "package demo; public class UserService { public void create(String userId) {} }");
        myFixture.configureByText("request.anydoor", "RUN demo.UserService#create()\n{\n  <caret>\n}");
        myFixture.completeBasic();
        var lookup = myFixture.getLookup();
        assertNotNull(lookup);
        lookup.setCurrentItem(lookup.getItems().stream().filter(item -> item.getLookupString().equals("userId")).findFirst().orElseThrow());
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("\"userId\": "));
    }
}
