package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

/** Unresolved variables should be recoverable from the editor without manual boilerplate. */
public class AnyDoorVariableQuickFixTest extends BasePlatformTestCase {
    public void testCreatesDefinitionForUnresolvedVariable() {
        myFixture.configureByText("request.anydoor", "### Create user\nRUN demo.UserService#create()\n{\"id\": \"{{userId}}\"}");
        List<HighlightInfo> highlights = myFixture.doHighlighting();
        assertTrue(highlights.stream().anyMatch(info -> info.getDescription() != null && info.getDescription().contains("未定义变量")));
        var fixes = myFixture.getAllQuickFixes();
        var create = fixes.stream().filter(action -> action.getText().equals("Create @userId variable")).findFirst();
        assertTrue("the unresolved-variable warning should offer a one-click definition", create.isPresent());
        myFixture.launchAction(create.get());
        assertTrue(myFixture.getFile().getText().startsWith("### Create user\n@userId = \"\"\n"));
    }
}
