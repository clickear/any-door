package io.github.lgp547.anydoorplugin.request;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Ensures large request files stay navigable through the standard Structure UI. */
public class AnyDoorStructureViewTest extends BasePlatformTestCase {
    public void testNamedRequestsAppearInStructure() {
        var file = myFixture.configureByText("requests.anydoor", "### Create user\nRUN demo.UserService#create()\n{}\n\n### Delete user\nRUN demo.UserService#delete()\n{}");
        var builder = (com.intellij.ide.structureView.TreeBasedStructureViewBuilder) new AnyDoorStructureViewFactory().getStructureViewBuilder(file);
        var model = builder.createStructureViewModel(myFixture.getEditor());
        var children = model.getRoot().getChildren();
        assertEquals(2, children.length);
        assertEquals("Create user", children[0].getPresentation().getPresentableText());
        assertEquals("demo.UserService#delete()", children[1].getPresentation().getLocationString());
    }
}
