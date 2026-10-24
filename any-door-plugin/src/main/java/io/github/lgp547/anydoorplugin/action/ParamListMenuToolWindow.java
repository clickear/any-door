package io.github.lgp547.anydoorplugin.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.github.lgp547.anydoorplugin.dialog.ParamLibraryPanel;
import io.github.lgp547.anydoorplugin.request.AnyDoorRequestsPanel;
import io.github.lgp547.anydoorplugin.result.InvocationResultsPanel;
import org.jetbrains.annotations.NotNull;

/**
 * @description:
 * @author: zhouh
 * @date: 2023-06-16 11:03
 **/
public class ParamListMenuToolWindow implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ParamLibraryPanel ui = new ParamLibraryPanel(project);

        // 获取内容工厂的实例
        ContentFactory contentFactory = ApplicationManager.getApplication().getService(ContentFactory.class);
        // 获取 ToolWindow 显示的内容
        Content content = contentFactory.createContent(ui, "Parameter Library", false);
        content.setDisposer(ui);
        // 设置 ToolWindow 显示的内容
        toolWindow.getContentManager().addContent(content);
        AnyDoorRequestsPanel requests = new AnyDoorRequestsPanel(project);
        Content requestContent = contentFactory.createContent(requests, "Requests", false);
        toolWindow.getContentManager().addContent(requestContent);
        InvocationResultsPanel results = new InvocationResultsPanel(project);
        Content resultContent = contentFactory.createContent(results, "Results", false);
        resultContent.setDisposer(results);
        toolWindow.getContentManager().addContent(resultContent);
        //初始化数据

        // 全局使用

    }
}
