package io.github.lgp547.anydoorplugin.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import io.github.lgp547.anydoor.common.dto.AnyDoorDiagnosticReport;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InvocationDiagnosticsPanel extends JPanel implements Disposable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Project project;
    private final InvocationDiagnosticService service;
    private final DefaultListModel<DiagnosticRecord> model = new DefaultListModel<>();
    private final JBList<DiagnosticRecord> history = new JBList<>(model);
    private final JTree checks = new JTree(new DefaultMutableTreeNode("No diagnostics"));
    private final JBTextArea details = new JBTextArea();
    private final JLabel summary = new JLabel(" Run an invocation or select Diagnose Last Invocation");
    private AutoCloseable subscription;

    public InvocationDiagnosticsPanel(Project project) {
        super(new BorderLayout()); this.project = project; this.service = InvocationDiagnosticService.instance(project); build();
        subscription = service.listen(values -> ApplicationManager.getApplication().invokeLater(() -> refresh(values)));
    }

    private void build() {
        history.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        history.setCellRenderer((list, value, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(4,2)); row.setOpaque(true); row.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
            row.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            row.add(new JLabel(icon(value.state()) + "  " + value.invocation()), BorderLayout.NORTH);
            JLabel meta = new JLabel(value.target() + "  ·  " + TIME.format(Instant.ofEpochMilli(value.startedAt()))); meta.setForeground(UIManager.getColor("Label.disabledForeground")); row.add(meta, BorderLayout.SOUTH); return row;
        });
        history.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) show(history.getSelectedValue()); });
        checks.addTreeSelectionListener(event -> showNode());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        JButton diagnose = button("Diagnose Last Invocation", e -> service.diagnoseLast());
        JButton retry = button("Retry", e -> { DiagnosticRecord record=history.getSelectedValue();if(record!=null)service.retry(record); });
        JButton loader = button("Choose ClassLoader", e -> chooseClassLoader());
        JButton bean = button("Choose Bean", e -> chooseBean());
        JButton copy = button("Copy Report", e -> copyReport());
        JButton export = button("Export", e -> exportReport());
        JButton clear = button("Clear", e -> service.clear());
        buttons.add(diagnose);buttons.add(retry);buttons.add(loader);buttons.add(bean);buttons.add(copy);buttons.add(export);buttons.add(clear);

        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true); details.setFont(new Font(Font.MONOSPACED,Font.PLAIN,details.getFont().getSize()));
        JBSplitter right = new JBSplitter(true,0.58f); right.setFirstComponent(new JBScrollPane(checks)); right.setSecondComponent(new JBScrollPane(details));
        JPanel detail = new JPanel(new BorderLayout()); detail.add(summary,BorderLayout.NORTH);detail.add(right,BorderLayout.CENTER);
        JBSplitter main = new JBSplitter(false,0.3f);main.setFirstComponent(new JBScrollPane(history));main.setSecondComponent(detail);
        add(buttons,BorderLayout.NORTH);add(main,BorderLayout.CENTER);
    }

    private JButton button(String text, java.awt.event.ActionListener action) { JButton result=new JButton(text);result.addActionListener(action);return result; }
    private void refresh(List<DiagnosticRecord> values) {
        String selected=history.getSelectedValue()==null?null:history.getSelectedValue().id(); model.clear();values.forEach(model::addElement);
        if(selected!=null)for(int i=0;i<model.size();i++)if(selected.equals(model.get(i).id()))history.setSelectedIndex(i);
        if(history.getSelectedIndex()<0&&!model.isEmpty())history.setSelectedIndex(0);if(model.isEmpty())show(null);
    }

    private void show(DiagnosticRecord record) {
        if(record==null){summary.setText(" No diagnostic records");checks.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No diagnostics")));details.setText("");return;}
        summary.setText(" "+record.state()+"  ·  "+record.target()+"  ·  protocol "+(record.report()==null?"pending":record.report().getProtocolVersion()));
        DefaultMutableTreeNode root=new DefaultMutableTreeNode(record.invocation());
        if(record.report()==null)root.add(new DefaultMutableTreeNode("● Running diagnostics…"));
        else for(AnyDoorDiagnosticReport.Stage stage:record.report().getStages()){
            DefaultMutableTreeNode stageNode=new DefaultMutableTreeNode(new Node(stage));root.add(stageNode);
            for(AnyDoorDiagnosticReport.Check check:stage.getChecks())stageNode.add(new DefaultMutableTreeNode(new Node(check)));
        }
        checks.setModel(new DefaultTreeModel(root));for(int i=0;i<checks.getRowCount();i++)checks.expandRow(i);
        details.setText(record.transportError()==null?"Select a stage or check to inspect evidence and suggested actions.":record.transportError());details.setCaretPosition(0);
    }

    private void showNode() {
        if(checks.getSelectionPath()==null)return;Object value=((DefaultMutableTreeNode)checks.getSelectionPath().getLastPathComponent()).getUserObject();String text;
        if(value instanceof Node node&&node.check!=null){StringBuilder out=new StringBuilder(node.check.getTitle()).append("\nStatus: ").append(node.check.getStatus()).append("\n\n").append(node.check.getSummary());
            if(!node.check.getEvidence().isEmpty()){out.append("\n\nEvidence:\n");node.check.getEvidence().forEach(item->out.append("• ").append(item).append('\n'));}
            if(!node.check.getSuggestions().isEmpty()){out.append("\nSuggested actions:\n");node.check.getSuggestions().forEach(item->out.append("• ").append(item).append('\n'));}text=out.toString();}
        else if(value instanceof Node node){text=node.stage.getName()+"\nStatus: "+node.stage.getStatus()+"\nDuration: "+node.stage.getDurationMillis()+" ms";}else text=String.valueOf(value);
        details.setText(text);details.setCaretPosition(0);
    }

    private void chooseClassLoader() {
        DiagnosticRecord record=history.getSelectedValue();if(record==null||record.report()==null)return;List<String> choices=new ArrayList<>();
        Pattern pattern=Pattern.compile("id=([^,]+)");for(AnyDoorDiagnosticReport.Stage stage:record.report().getStages())for(AnyDoorDiagnosticReport.Check check:stage.getChecks())if("class.loaded".equals(check.getId()))for(String evidence:check.getEvidence()){Matcher matcher=pattern.matcher(evidence);if(matcher.find())choices.add(matcher.group(1));}
        if(choices.isEmpty()){Messages.showInfoMessage(project,"No ClassLoader candidates were reported.","AnyDoor Diagnostics");return;}
        int selected=Messages.showChooseDialog(project,"Select the ClassLoader and rerun diagnostics", "AnyDoor Diagnostics", null, choices.toArray(new String[0]), choices.get(0));if(selected>=0)service.retryWithClassLoader(record,choices.get(selected));
    }

    private void chooseBean() {
        DiagnosticRecord record=history.getSelectedValue();if(record==null||record.report()==null)return;List<String> choices=new ArrayList<>();
        for(AnyDoorDiagnosticReport.Stage stage:record.report().getStages())for(AnyDoorDiagnosticReport.Check check:stage.getChecks())if("spring.beans".equals(check.getId()))choices.addAll(check.getEvidence());
        if(choices.isEmpty()){Messages.showInfoMessage(project,"No Spring Bean candidates were reported.","AnyDoor Diagnostics");return;}
        int selected=Messages.showChooseDialog(project,"Select the Bean and rerun diagnostics", "AnyDoor Diagnostics", null, choices.toArray(new String[0]), choices.get(0));if(selected<0)return;
        String choice=choices.get(selected),name=field(choice,"name"),context=field(choice,"contextId");if(name!=null)service.retryWithBean(record,name,context);
    }

    private void copyReport(){DiagnosticRecord record=history.getSelectedValue();if(record!=null&&record.report()!=null)Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(service.toJson(record)),null);}
    private void exportReport(){DiagnosticRecord record=history.getSelectedValue();if(record==null||record.report()==null)return;try{var dialog=FileChooserFactory.getInstance().createSaveFileDialog(new FileSaverDescriptor("Export AnyDoor Diagnostic Report","The report is sanitized before export","json"),project);var file=dialog.save(java.nio.file.Path.of(project.getBasePath()),"anydoor-diagnostic-"+record.id()+".json");if(file!=null)Files.writeString(file.getFile().toPath(),service.toJson(record));}catch(Exception error){Messages.showErrorDialog(project,error.getMessage(),"Export Diagnostics");}}
    private static String field(String value,String key){Matcher matcher=Pattern.compile("(?:^|, )"+Pattern.quote(key)+"=([^,]+)").matcher(value);return matcher.find()?matcher.group(1):null;}
    private static String icon(DiagnosticRecord.State state){return switch(state){case RUNNING->"●";case PASS->"✓";case WARN->"⚠";case FAIL->"✕";};}
    @Override public void dispose(){try{if(subscription!=null)subscription.close();}catch(Exception ignored){}}
    private static final class Node { final AnyDoorDiagnosticReport.Stage stage;final AnyDoorDiagnosticReport.Check check;Node(AnyDoorDiagnosticReport.Stage stage){this.stage=stage;this.check=null;}Node(AnyDoorDiagnosticReport.Check check){this.stage=null;this.check=check;}@Override public String toString(){return check==null?symbol(stage.getStatus())+" "+stage.getName()+" ("+stage.getDurationMillis()+" ms)":symbol(check.getStatus())+" "+check.getTitle()+" — "+check.getSummary();}private static String symbol(String status){if("FAIL".equals(status))return"✕";if("WARN".equals(status))return"⚠";if("SKIP".equals(status))return"○";return"✓";}}
}
