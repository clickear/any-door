package io.github.lgp547.anydoorplugin.dialog;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.lgp547.anydoorplugin.data.domain.Data;
import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.utils.IdeClassUtil;
import io.github.lgp547.anydoorplugin.util.JsonUtil;

public class MethodDataContextTemplateTest extends BasePlatformTestCase {
    public void testDefaultTemplateIncludesInheritedInstanceFieldsAndSkipsStaticFields() throws Exception {
        MethodDataContext context = context(null);
        var payload = JsonUtil.objectMapper.readTree(context.getSelectedItem().getParam()).get("payload");
        assertNotNull(payload);
        assertTrue(payload.toString(), payload.has("own"));
        assertTrue(payload.toString(), payload.has("inherited"));
        assertFalse(payload.toString(), payload.has("CONSTANT"));
    }

    public void testGeneratingTemplatesDoesNotOverwriteTheSelectedSavedPreset() throws Exception {
        ParamDataItem preset = new ParamDataItem("Saved", "Handler#run(Payload)", "{\"payload\":{\"own\":42}}");
        preset.setId(1L);
        MethodDataContext context = context(preset);
        context.onEvent(() -> EventType.ADD_ALL_PARAM_ITEM);
        assertFalse(context.hasPersistedSelection());
        assertEquals("{\"payload\":{\"own\":42}}", preset.getParam());
        assertTrue(JsonUtil.objectMapper.readTree(context.getSelectedItem().getParam()).get("payload").has("inherited"));
        context.onEvent(() -> EventType.ADD_SIMPLE_PARAM_ITEM);
        assertTrue(JsonUtil.objectMapper.readTree(context.getSelectedItem().getParam()).get("payload").isNull());
        assertEquals("{\"payload\":{\"own\":42}}", preset.getParam());
        assertEquals(1, context.listPersistedData().size());
    }

    private MethodDataContext context(ParamDataItem preset) {
        PsiJavaFile file = (PsiJavaFile) myFixture.addFileToProject("Handler.java",
                "class Parent { int inherited; } class Payload extends Parent { int own; static\n int CONSTANT; } class Handler { void run(Payload payload) {} }");
        PsiClass owner = java.util.Arrays.stream(file.getClasses()).filter(type -> "Handler".equals(type.getName())).findFirst().orElseThrow();
        String qualified = IdeClassUtil.getMethodQualifiedName(owner.findMethodsByName("run", false)[0]);
        Data<ParamDataItem> data = new Data<>("Handler");
        if (preset != null) { preset.setQualifiedName(qualified); data.getDataList().add(preset); }
        return new ClassDataContext(owner, data, getProject()).newMethodDataContext(qualified, preset == null ? null : preset.getId(), null);
    }
}
