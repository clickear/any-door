package io.github.lgp547.anydoorplugin.request;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.command.WriteCommandAction;

/** Guards the promise that JSON bodies use IntelliJ's JSON language, not only token colouring. */
public class AnyDoorJsonInjectionTest extends BasePlatformTestCase {
    public void testRequestBodyInjectsJsonLanguage() {
        var file=myFixture.configureByText("request.anydoor", "RUN demo.UserService#getUser()\n{\"id\": 1, \"active\": true}");
        AnyDoorJsonBody body=PsiTreeUtil.findChildOfType(file, AnyDoorJsonBody.class);
        assertNotNull("request object should be a JSON injection host", body);
        var injected=InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(body);
        assertNotNull("request body should inject JSON", injected);
        assertFalse(injected.isEmpty());
        assertEquals("JSON", injected.get(0).first.getLanguage().getID());
    }
    public void testInjectedTextUpdatePreservesRequestBody() {
        var file=myFixture.configureByText("request.anydoor", "RUN demo.UserService#getUser()\n{\"id\": 1}");
        AnyDoorJsonBody body=PsiTreeUtil.findChildOfType(file, AnyDoorJsonBody.class);
        assertNotNull(body);
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () -> body.updateText("{\"id\": 2}"));
        assertTrue(file.getText().contains("\"id\": 2"));
    }
    public void testLineCommentDoesNotPreventJsonInjection() {
        var file=myFixture.configureByText("request.anydoor", "RUN demo.UserService#getUser()\n{\n  \"id\": 1 // request id\n}");
        AnyDoorJsonBody body=PsiTreeUtil.findChildOfType(file, AnyDoorJsonBody.class);
        assertNotNull(body);
        var injected=InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(body);
        assertNotNull(injected);
        assertFalse("comments must not disable native JSON editor support", injected.isEmpty());
        assertEquals("JSON", injected.get(0).first.getLanguage().getID());
    }
}
