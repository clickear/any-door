package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import io.github.lgp547.anydoorplugin.util.AnyDoorIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.LinkedHashMap;
import java.util.Map;

/** Makes request colours first-class colour-scheme settings instead of hard-coded theme assumptions. */
public final class AnyDoorColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] ATTRIBUTES = {
            descriptor("Request title", AnyDoorSyntaxHighlighter.TITLE),
            descriptor("Directive marker (@)", AnyDoorSyntaxHighlighter.DIRECTIVE),
            descriptor("Variable definition", AnyDoorSyntaxHighlighter.DIRECTIVE_NAME),
            descriptor("RUN keyword", AnyDoorSyntaxHighlighter.RUN),
            descriptor("Target class", AnyDoorSyntaxHighlighter.CLASS),
            descriptor("Target method", AnyDoorSyntaxHighlighter.METHOD),
            descriptor("Parameter type", AnyDoorSyntaxHighlighter.TYPE),
            descriptor("Template variable", AnyDoorSyntaxHighlighter.VARIABLE),
            descriptor("Comment", AnyDoorSyntaxHighlighter.COMMENT),
            descriptor("JSON property", AnyDoorSyntaxHighlighter.JSON_KEY),
            descriptor("JSON string", AnyDoorSyntaxHighlighter.JSON_STRING),
            descriptor("JSON number", AnyDoorSyntaxHighlighter.JSON_NUMBER)
    };

    @Override public @Nullable Icon getIcon() { return AnyDoorIcons.default_icon; }
    @Override public @NotNull SyntaxHighlighter getHighlighter() { return new AnyDoorSyntaxHighlighter(); }
    @Override public @NotNull String getDemoText() {
        return "### <title>Create user</title>\n"
                + "<directive>@</directive><definition>userId</definition> = \"42\"\n\n"
                + "<run>RUN</run> <class>demo.UserService</class>#<method>create</method><type>(UserCommand)</type>\n"
                + "{\n  <property>\"id\"</property>: \"<variable>{{userId}}</variable>\",\n"
                + "  <property>\"createdAt\"</property>: \"<variable>{{now.iso}}</variable>\", <comment>// generated time</comment>\n"
                + "  <property>\"retry\"</property>: <number>3</number>\n}";
    }
    @Override public @NotNull AttributesDescriptor[] getAttributeDescriptors() { return ATTRIBUTES; }
    @Override public @NotNull ColorDescriptor[] getColorDescriptors() { return ColorDescriptor.EMPTY_ARRAY; }
    @Override public @NotNull String getDisplayName() { return "AnyDoor Request"; }
    @Override public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        Map<String, TextAttributesKey> tags = new LinkedHashMap<>();
        tags.put("title", AnyDoorSyntaxHighlighter.TITLE); tags.put("directive", AnyDoorSyntaxHighlighter.DIRECTIVE);
        tags.put("definition", AnyDoorSyntaxHighlighter.DIRECTIVE_NAME); tags.put("run", AnyDoorSyntaxHighlighter.RUN);
        tags.put("class", AnyDoorSyntaxHighlighter.CLASS); tags.put("method", AnyDoorSyntaxHighlighter.METHOD);
        tags.put("type", AnyDoorSyntaxHighlighter.TYPE); tags.put("variable", AnyDoorSyntaxHighlighter.VARIABLE);
        tags.put("comment", AnyDoorSyntaxHighlighter.COMMENT); tags.put("property", AnyDoorSyntaxHighlighter.JSON_KEY);
        tags.put("number", AnyDoorSyntaxHighlighter.JSON_NUMBER); return tags;
    }
    private static AttributesDescriptor descriptor(String name, TextAttributesKey key) { return new AttributesDescriptor(name, key); }
}
