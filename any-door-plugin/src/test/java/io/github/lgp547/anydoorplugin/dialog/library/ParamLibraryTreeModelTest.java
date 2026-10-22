package io.github.lgp547.anydoorplugin.dialog.library;

import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParamLibraryTreeModelTest {

    @Test
    void groupsPresetsByClassAndFullMethodSignature() {
        ParamLibraryTreeModel.Node root = ParamLibraryTreeModel.build(List.of(
                item(2L, "数字参数", "com.example.OrderService#query(java.lang.Long)"),
                item(1L, "字符串参数", "com.example.OrderService#query(java.lang.String)"),
                item(3L, "默认参数", "com.example.UserService#find()")
        ), "");

        assertEquals(List.of("OrderService", "UserService"), labels(root.getChildren()));
        assertEquals(
                List.of("query(java.lang.Long)", "query(java.lang.String)"),
                labels(root.getChildren().get(0).getChildren())
        );
    }

    @Test
    void filtersByClassMethodAndPresetWhileKeepingParentPath() {
        List<ParamIndexData> items = List.of(
                item(1L, "正常订单", "com.example.OrderService#submit(java.lang.String)"),
                item(2L, "异常订单", "com.example.OrderService#cancel(java.lang.String)"),
                item(3L, "普通用户", "com.example.UserService#find()")
        );

        ParamLibraryTreeModel.Node byPreset = ParamLibraryTreeModel.build(items, "异常");
        assertEquals(List.of("OrderService"), labels(byPreset.getChildren()));
        assertEquals(List.of("cancel(java.lang.String)"), labels(byPreset.getChildren().get(0).getChildren()));
        assertEquals(List.of("异常订单"), labels(byPreset.getChildren().get(0).getChildren().get(0).getChildren()));

        ParamLibraryTreeModel.Node byClass = ParamLibraryTreeModel.build(items, "userservice");
        assertEquals(List.of("UserService"), labels(byClass.getChildren()));
    }

    @Test
    void sortsPresetNamesInsideMethod() {
        ParamLibraryTreeModel.Node root = ParamLibraryTreeModel.build(List.of(
                item(2L, "Beta", "com.example.OrderService#submit()"),
                item(1L, "alpha", "com.example.OrderService#submit()")
        ), "");

        assertEquals(
                List.of("alpha", "Beta"),
                labels(root.getChildren().get(0).getChildren().get(0).getChildren())
        );
    }

    private static ParamIndexData item(Long id, String name, String qualifiedMethodName) {
        ParamIndexData item = new ParamIndexData();
        item.setId(id);
        item.setName(name);
        item.setQualifiedMethodName(qualifiedMethodName);
        return item;
    }

    private static List<String> labels(List<ParamLibraryTreeModel.Node> nodes) {
        return nodes.stream().map(ParamLibraryTreeModel.Node::getLabel).toList();
    }
}
