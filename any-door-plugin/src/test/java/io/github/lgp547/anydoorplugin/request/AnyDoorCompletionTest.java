package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;

/** Checks the editor-facing completion contracts, not only the contributor implementation. */
public class AnyDoorCompletionTest extends BasePlatformTestCase {
    public void testContributorIsRegisteredForAnyDoorLanguage() {
        assertTrue(com.intellij.codeInsight.completion.CompletionContributor.forLanguage(AnyDoorLanguage.INSTANCE).stream().anyMatch(AnyDoorCompletionContributor.class::isInstance));
    }
    public void testDirectiveCompletionAppearsAfterAtSign() {
        myFixture.configureByText("request.anydoor", "@<caret>");
        assertSame(AnyDoorLanguage.INSTANCE, myFixture.getFile().getLanguage());
        LookupElement[] items=myFixture.completeBasic();
        assertNotNull(items);
        assertTrue(Arrays.stream(items).map(LookupElement::getLookupString).collect(java.util.stream.Collectors.joining(", ")), Arrays.stream(items).anyMatch(item -> item.getLookupString().contains("pid")));
    }
    public void testVariableCompletionOffersFileVariables() {
        myFixture.configureByText("request.anydoor", "@userId = 1\nRUN demo.Service#get()\n{\"id\": \"{{<caret>\"}");
        LookupElement[] items=myFixture.completeBasic();
        assertNotNull(items);
        assertTrue(Arrays.stream(items).map(LookupElement::getLookupString).collect(java.util.stream.Collectors.joining(", ")), Arrays.stream(items).anyMatch(item -> item.getLookupString().contains("userId")));
    }
    public void testRunClassCompletionUsesProjectClasses() {
        myFixture.addFileToProject("demo/UserService.java", "package demo; public class UserService {}");
        myFixture.configureByText("request.anydoor", "RUN <caret>");
        LookupElement[] items=myFixture.completeBasic();
        assertNotNull(items);
        assertTrue(Arrays.stream(items).map(LookupElement::getLookupString).collect(java.util.stream.Collectors.joining(", ")), Arrays.stream(items).anyMatch(item -> item.getLookupString().equals("demo.UserService")));
    }
}
