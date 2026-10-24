package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class RequestHistoryService {
    private final AnyDoorSettingsState settings;
    public RequestHistoryService(Project project){settings=project.getService(AnyDoorSettingsState.class);}
    public static RequestHistoryService instance(Project project){return project.getService(RequestHistoryService.class);}
    public void add(String name,String target,String status){settings.requestHistory.add(0,new RequestHistoryItem(name,target,status,System.currentTimeMillis()));if(settings.requestHistory.size()>50)settings.requestHistory.subList(50,settings.requestHistory.size()).clear();}
    public void add(String name,String target,String status,String requestText){RequestHistoryItem item=new RequestHistoryItem(name,target,status,System.currentTimeMillis());item.setRequestText(requestText);settings.requestHistory.add(0,item);if(settings.requestHistory.size()>50)settings.requestHistory.subList(50,settings.requestHistory.size()).clear();}
    public List<RequestHistoryItem> list(){return List.copyOf(settings.requestHistory);}
}
