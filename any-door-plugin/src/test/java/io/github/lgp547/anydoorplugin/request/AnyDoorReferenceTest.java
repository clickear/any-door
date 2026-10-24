package io.github.lgp547.anydoorplugin.request;

import com.intellij.psi.PsiElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Regression test for Cmd/Ctrl navigation on a RUN class target. */
public class AnyDoorReferenceTest extends BasePlatformTestCase {
    public void testRunClassResolvesToProjectClass() {
        myFixture.addFileToProject("demo/UserService.java", "package demo; public class UserService { public String getUser() { return \"ok\"; } }");
        var file = myFixture.configureByText("request.anydoor", "RUN demo.UserService#getUser()\n{}");
        PsiElement element = file.findElementAt(file.getText().indexOf("demo.UserService") + 2);
        assertNotNull(element);
        int offset=file.getText().indexOf("demo.UserService")+2;
        var targets=new AnyDoorGotoDeclarationHandler().getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Cmd/Ctrl click should expose a declaration target", targets);
        assertEquals(1, targets.length);
        assertEquals("UserService", targets[0].getContainingFile().getName().replace(".java", ""));
    }
    public void testRunMethodResolvesToProjectMethod() {
        myFixture.addFileToProject("demo/UserService.java", "package demo; public class UserService { public String getUser() { return \"ok\"; } }");
        var file = myFixture.configureByText("request.anydoor", "RUN demo.UserService#getUser()\n{}");
        int offset=file.getText().indexOf("getUser")+2;
        var targets=new AnyDoorGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, myFixture.getEditor());
        assertNotNull(targets); assertEquals(1, targets.length); assertEquals("getUser", ((com.intellij.psi.PsiMethod)targets[0]).getName());
    }
    public void testDirectNavigationResolvesFromLexerLeaf() {
        myFixture.addFileToProject("demo/UserService.java", "package demo; public class UserService { public String getUser() { return \"ok\"; } }");
        var file = myFixture.configureByText("request.anydoor", "RUN demo.UserService#getUser()\n{}");
        int offset = file.getText().indexOf("getUser") + 2;
        var target = new AnyDoorRunNavigationProvider().getNavigationElement(file.findElementAt(offset));
        assertNotNull("Cmd/Ctrl-click direct navigation should resolve from the clicked leaf", target);
        assertEquals("getUser", ((com.intellij.psi.PsiMethod) target).getName());
    }
    public void testVariableDirectNavigationResolvesToDefinition() {
        var file = myFixture.configureByText("request.anydoor", "@userId = 42\nRUN demo.UserService#getUser()\n{\"id\": \"{{userId}}\"}");
        int offset = file.getText().indexOf("{{userId}}") + 3;
        var target = new AnyDoorRunNavigationProvider().getNavigationElement(file.findElementAt(offset));
        assertNotNull("Cmd/Ctrl-click on a template variable should jump to its definition", target);
        assertEquals("userId", target.getText());
    }
    public void testVariableNavigationAlsoWorksFromInjectedJson() {
        var file = myFixture.configureByText("request.anydoor", "@userId = 42\nRUN demo.UserService#getUser()\n{\"id\": \"{{userId}}\"}");
        var body = PsiTreeUtil.findChildOfType(file, AnyDoorJsonBody.class);
        assertNotNull(body);
        var injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(body);
        assertNotNull(injected);
        var json = injected.get(0).first;
        int offset = json.getText().indexOf("userId") + 2;
        var target = new AnyDoorRunNavigationProvider().getNavigationElement(json.findElementAt(offset));
        assertNotNull("Navigation must survive JSON language injection", target);
        assertEquals("userId", target.getText());
    }
}
