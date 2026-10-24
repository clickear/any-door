package io.github.lgp547.anydoorplugin.action;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.lgp547.anydoorplugin.AnyDoorInfo;
import io.github.lgp547.anydoorplugin.util.ImportNewUtil;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import java.util.List;
import java.util.Map;

public class MethodInvocationSnapshotTest extends BasePlatformTestCase {
    public void testCaptureCopiesInterfaceMetadataAndPreparesPositionalArguments() throws Exception {
        PsiClass owner = addType("package demo; interface Sample { void run(java.lang.String name, java.util.List<java.lang.Integer> ids); }");
        MethodInvocationSnapshot snapshot = MethodInvocationSnapshot.capture(owner.findMethodsByName("run", false)[0]);
        assertEquals("demo.Sample", snapshot.className());
        assertEquals("run", snapshot.methodName());
        assertEquals(List.of("java.lang.String", "java.util.List"), snapshot.parameterTypeNames());
        assertEquals(Map.of("name", "args0", "ids", "args1"), snapshot.parameterNameTransformer());
        assertTrue(snapshot.interfaceType());
        var content = JsonUtil.objectMapper.readTree(snapshot.prepareContent("{\"name\":\"n\",\"ids\":[1]}"));
        assertEquals("n", content.get("args0").asText());
        assertEquals(1, content.get("args1").get(0).asInt());
    }

    public void testAbstractDeclarationKeepsBeanSelectionParameterCompatibility() throws Exception {
        PsiClass owner = addType("package demo; abstract class Sample { abstract void run(java.lang.String userId); }");
        MethodInvocationSnapshot snapshot = MethodInvocationSnapshot.capture(owner.findMethodsByName("run", false)[0]);
        assertFalse(snapshot.interfaceType());
        assertTrue(snapshot.abstractMethod());
        assertEquals("u1", JsonUtil.objectMapper.readTree(snapshot.prepareContent("{\"userId\":\"u1\"}")).get("args0").asText());
    }

    public void testCapturedMetadataSurvivesLaterPsiEdits() {
        PsiClass owner = addType("package demo; class Sample { void run(java.lang.String value) {} }");
        PsiMethod method = owner.findMethodsByName("run", false)[0];
        MethodInvocationSnapshot snapshot = MethodInvocationSnapshot.capture(method);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            method.setName("renamed");
            method.getParameterList().getParameters()[0].setName("different");
        });
        assertEquals("run", snapshot.methodName());
        assertEquals("demo.Sample#run(java.lang.String)", snapshot.qualifiedMethodName());
        assertEquals(Map.of("value", "args0"), snapshot.parameterNameTransformer());
        assertEquals("{\"value\":\"original\"}", snapshot.prepareContent("{\"value\":\"original\"}"));
    }

    public void testInvocationUsesThePrebuiltAgentBundleOutsideThePluginClasspath() throws Exception {
        var dto = JsonUtil.objectMapper.readTree(AnyDoorPerformed.getJsonDtoStr(getProject(), "demo.Sample", "run", List.of(), "{}", true, null));
        assertEquals(ImportNewUtil.getPluginAgentPath(AnyDoorInfo.ANY_DOOR_ALL_DEPENDENCE_JAR), dto.get("jarPaths").get(1).asText());
    }

    private PsiClass addType(String source) {
        return ((PsiJavaFile) myFixture.addFileToProject("demo/Sample.java", source)).getClasses()[0];
    }
}
