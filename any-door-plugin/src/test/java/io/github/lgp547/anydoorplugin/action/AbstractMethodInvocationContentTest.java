package io.github.lgp547.anydoorplugin.action;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.lgp547.anydoorplugin.util.AnyDoorActionUtil;
import io.github.lgp547.anydoorplugin.util.JsonUtil;

public class AbstractMethodInvocationContentTest extends BasePlatformTestCase {
    public void testAbstractDeclarationUsesPositionsWithoutRenamingNestedDtoFields() throws Exception {
        PsiClass type = myFixture.addClass("abstract class Handler { public abstract String run(String userId, Object request); }");
        PsiMethod method = type.findMethodsByName("run", false)[0];
        String content = AnyDoorActionUtil.prepareInvocationContent(type, method,
                "{\"userId\":\"u1\",\"request\":{\"userId\":\"nested\"}}");
        var body = JsonUtil.objectMapper.readTree(content);
        assertEquals("u1", body.get("args0").asText());
        assertEquals("nested", body.get("args1").get("userId").asText());
        assertFalse(body.has("userId"));
    }

    public void testBatchCallsToAbstractDeclarationKeepParameterOrder() throws Exception {
        PsiClass type = myFixture.addClass("abstract class Handler { public abstract String run(String userId, int retries); }");
        String content = AnyDoorActionUtil.prepareInvocationContent(type, type.findMethodsByName("run", false)[0],
                "[{\"userId\":\"u1\",\"retries\":2},{\"userId\":\"u2\",\"retries\":3}]");
        var body = JsonUtil.objectMapper.readTree(content);
        assertEquals("u1", body.get(0).get("args0").asText());
        assertEquals(2, body.get(0).get("args1").asInt());
        assertEquals("u2", body.get(1).get("args0").asText());
        assertEquals(3, body.get(1).get("args1").asInt());
    }

    public void testImplementedMethodInAbstractClassKeepsNamedParameters() {
        PsiClass type = myFixture.addClass("abstract class Handler { public String run(String userId) { return userId; } }");
        String original = "{\"userId\":\"u1\"}";
        assertEquals(original, AnyDoorActionUtil.prepareInvocationContent(type, type.findMethodsByName("run", false)[0], original));
    }

    public void testInterfaceDeclarationsContinueToUsePositions() throws Exception {
        PsiClass type = myFixture.addClass("interface Handler { String run(String userId); }");
        String content = AnyDoorActionUtil.prepareInvocationContent(type, type.findMethodsByName("run", false)[0], "{\"userId\":\"u1\"}");
        assertEquals("u1", JsonUtil.objectMapper.readTree(content).get("args0").asText());
    }
}
