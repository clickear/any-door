package io.github.lgp547.anydoorplugin.request;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Guards the editor-facing presentation contracts independently of the runner. */
public class AnyDoorEditorPresentationTest extends BasePlatformTestCase {
    public void testMultilinePayloadIsFoldable() {
        var file = myFixture.configureByText("request.anydoor", "RUN demo.UserService#create()\n{\n  \"name\": \"Ada\",\n  \"active\": true\n}");
        var descriptors = new AnyDoorFoldingBuilder().buildFoldRegions(file.getNode(), myFixture.getEditor().getDocument());
        assertEquals(1, descriptors.length);
        assertEquals("{ … }", new AnyDoorFoldingBuilder().getPlaceholderText(descriptors[0].getElement()));
    }
    public void testColourSchemeExposesTemplateAndJsonSettings() {
        var descriptors = new AnyDoorColorSettingsPage().getAttributeDescriptors();
        assertTrue(java.util.Arrays.stream(descriptors).anyMatch(item -> item.getKey() == AnyDoorSyntaxHighlighter.VARIABLE));
        assertTrue(java.util.Arrays.stream(descriptors).anyMatch(item -> item.getKey() == AnyDoorSyntaxHighlighter.JSON_KEY));
    }
}
