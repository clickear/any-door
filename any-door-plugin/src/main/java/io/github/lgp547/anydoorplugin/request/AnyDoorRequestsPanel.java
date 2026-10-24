package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBLabel;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Services-like overview: configured attach target plus the latest request attempts. */
public final class AnyDoorRequestsPanel extends JPanel {
    private final Project project; private final JBLabel target = new JBLabel(); private final JBList<RequestHistoryItem> history = new JBList<>(new DefaultListModel<>());
    public AnyDoorRequestsPanel(Project project){super(new BorderLayout());this.project=project;build();refresh();}
    private void build(){JPanel top=new JPanel(new BorderLayout());JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,4,4));JButton change=new JButton("选择 PID");change.addActionListener(e->selectPid());JButton rerun=new JButton("复跑所选");rerun.addActionListener(e->{RequestHistoryItem item=history.getSelectedValue();if(item!=null&&item.getRequestText()!=null)AnyDoorRequestRunner.run(project,item.getRequestText());});JButton refresh=new JButton("刷新");refresh.addActionListener(e->refresh());actions.add(change);actions.add(rerun);actions.add(refresh);top.add(target,BorderLayout.NORTH);top.add(actions,BorderLayout.SOUTH);history.setCellRenderer((list,value,index,selected,focus)->{JPanel row=new JPanel(new BorderLayout());row.setOpaque(true);row.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));row.setBackground(selected?list.getSelectionBackground():list.getBackground());row.add(new JLabel(value.getName()+" · "+value.getStatus()),BorderLayout.NORTH);row.add(new JLabel(value.getTarget()+" · "+DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value.getTime()))),BorderLayout.SOUTH);return row;});history.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent event){if(event.getClickCount()==2&&history.getSelectedValue()!=null){String result=RequestResultService.instance(project).get(history.getSelectedValue().getName());if(result!=null)Messages.showInfoMessage(project,result,"AnyDoor · "+history.getSelectedValue().getName());}}});add(top,BorderLayout.NORTH);add(new JBScrollPane(history),BorderLayout.CENTER);}
    private void selectPid(){String value=Messages.showInputDialog(project,"目标 JVM PID", "AnyDoor Target", null, String.valueOf(project.getService(AnyDoorSettingsState.class).pid), null);try{if(value!=null)project.getService(AnyDoorSettingsState.class).pid=Long.parseLong(value.trim());refresh();}catch(NumberFormatException ignored){}}
    private void refresh(){long pid=project.getService(AnyDoorSettingsState.class).pid;target.setText("  Target JVM: "+(pid>0?"PID "+pid:"未选择"));DefaultListModel<RequestHistoryItem> model=(DefaultListModel<RequestHistoryItem>)history.getModel();model.clear();RequestHistoryService.instance(project).list().forEach(model::addElement);}
}
