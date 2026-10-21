package io.github.lgp547.anydoorplugin.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.github.lgp547.anydoorplugin.data.domain.Data;
import io.github.lgp547.anydoorplugin.dialog.utils.IdeClassUtil;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class MethodDataContextReadActionTest extends LightJavaCodeInsightFixtureTestCase {

    private MethodDataContext context;

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_17;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("""
                package example;

                public class Request {
                    public String name;
                    public Nested nested;
                    public java.time.LocalDateTime createdAt;

                    public static class Nested {
                        public long id;
                    }
                }
                """);
        PsiClass handler = myFixture.addClass("""
                package example;

                import java.util.List;
                import java.util.Map;

                public class RequestHandler {
                    public void handle(int count, Request request, List<String> tags, Map<String, Integer> scores) {
                    }
                }
                """);
        ClassDataContext classContext = new ClassDataContext(handler, new Data<>("example.RequestHandler"), getProject());
        String methodName = IdeClassUtil.getMethodQualifiedName(handler.findMethodsByName("handle", false)[0]);
        context = new MethodDataContext(classContext, methodName, "{}", getProject());
    }

    public void testCreatesSimpleParamTextInsideReadAction() throws Exception {
        JsonObject json = JsonParser.parseString(callWithoutReadAccess(context::createSimpleParamText)).getAsJsonObject();

        assertTrue(json.has("count"));
        assertTrue(json.has("request"));
        assertTrue(json.has("tags"));
        assertTrue(json.has("scores"));
    }

    public void testCreatesAllParamTextInsideReadAction() throws Exception {
        JsonObject json = JsonParser.parseString(callWithoutReadAccess(context::createAllParamText)).getAsJsonObject();

        assertEquals(0, json.get("count").getAsInt());
        assertEquals("", json.getAsJsonObject("request").get("name").getAsString());
        assertEquals(0, json.getAsJsonObject("request").getAsJsonObject("nested").get("id").getAsLong());
        assertEquals("", json.getAsJsonArray("tags").get(0).getAsString());
        assertEquals(0, json.getAsJsonObject("scores").get("").getAsInt());
    }

    public void testMissingMethodReturnsEmptyJson() throws Exception {
        MethodDataContext missing = new MethodDataContext(
                new ClassDataContext(null, new Data<>("missing.Type"), getProject()),
                "missing.Type#call()",
                "{}",
                getProject()
        );

        assertEquals("{}", JsonParser.parseString(callWithoutReadAccess(missing::createAllParamText)).toString());
    }

    public void testValidatesDateParamsInsideReadAction() throws Exception {
        ValidationInfo validation = callWithoutReadAccess(() -> context.validateParam(
                    "{\"count\":1,\"request\":{\"name\":\"ok\",\"nested\":{\"id\":2},\"createdAt\":\"invalid\"},\"tags\":[\"a\"],\"scores\":{\"a\":1}}",
                    "yyyy-MM-dd HH:mm:ss",
                    null
            ));

        assertNotNull(validation);
        assertTrue(validation.message.contains("request.createdAt"));
    }

    private <T> T callWithoutReadAccess(Supplier<T> supplier) throws Exception {
        Future<T> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
            return supplier.get();
        });
        return future.get(30, TimeUnit.SECONDS);
    }
}
