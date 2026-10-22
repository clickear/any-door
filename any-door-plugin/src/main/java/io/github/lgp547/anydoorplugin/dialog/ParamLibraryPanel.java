package io.github.lgp547.anydoorplugin.dialog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.treeStructure.Tree;
import io.github.lgp547.anydoorplugin.action.AnyDoorAgainOpenAction;
import io.github.lgp547.anydoorplugin.action.AnyDoorPerformed;
import io.github.lgp547.anydoorplugin.data.domain.ParamCollection;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import io.github.lgp547.anydoorplugin.dialog.library.ParamCollectionService;
import io.github.lgp547.anydoorplugin.dialog.utils.IdeClassUtil;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.AnyDoorActionUtil;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/** Postman-light library: Collections navigate; list selects; details inspect. */
public class ParamLibraryPanel extends JPanel implements Disposable {
  private final Project project; private final ParamCollectionService collections;
  private final Tree navTree = new Tree(); private final JBList<ParamIndexData> presetList = new JBList<>(new DefaultListModel<>());
  private final JBTextArea detail = new JBTextArea("选择一个参数方案以预览 JSON"); private final SearchTextField search = new SearchTextField();
  private List<ParamIndexData> all = List.of(); private Node selected = Node.all();
  public ParamLibraryPanel(Project project) { super(new BorderLayout()); this.project=project; collections=ParamCollectionService.instance(project); configure(); buildLayout(); reload(); }
  private void configure() {
    navTree.setRootVisible(false); navTree.setDragEnabled(true); navTree.setDropMode(DropMode.ON); navTree.setTransferHandler(new CollectionTransfer());
    navTree.addTreeSelectionListener(e -> { Node node=node(e.getPath()); if(node!=null){selected=node; refreshList();} });
    presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); presetList.setCellRenderer((list,value,index,isSelected,focus)->row(list,value,isSelected));
    presetList.addListSelectionListener(e->{if(!e.getValueIsAdjusting()) preview(presetList.getSelectedValue());});
    presetList.addMouseListener(new java.awt.event.MouseAdapter(){ public void mouseClicked(java.awt.event.MouseEvent e){if(e.getClickCount()==2)open();}});
    detail.setEditable(false); detail.setFont(new Font(Font.MONOSPACED,Font.PLAIN,detail.getFont().getSize()));
    search.addDocumentListener(new com.intellij.ui.DocumentAdapter(){protected void textChanged(@NotNull javax.swing.event.DocumentEvent e){refreshList();}});
  }
  private void buildLayout() {
    JPanel left=new JPanel(new BorderLayout()); left.add(collectionActions(),BorderLayout.NORTH); left.add(new JBScrollPane(navTree),BorderLayout.CENTER);
    JPanel middle=new JPanel(new BorderLayout()); middle.add(search,BorderLayout.NORTH); middle.add(new JBScrollPane(presetList),BorderLayout.CENTER); middle.add(presetActions(),BorderLayout.SOUTH);
    JPanel right=new JPanel(new BorderLayout()); right.add(new JLabel("  方案详情"),BorderLayout.NORTH); right.add(new JBScrollPane(detail),BorderLayout.CENTER); right.add(detailActions(),BorderLayout.SOUTH);
    JSplitPane first=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,middle); first.setDividerLocation(245); JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,first,right); split.setDividerLocation(650); add(split);
  }
  private JComponent collectionActions(){ JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,3,3)); JButton add=new JButton("+ Collection"), rename=new JButton("Rename"), del=new JButton("Delete"); add.addActionListener(e->create()); rename.addActionListener(e->rename()); del.addActionListener(e->delete()); p.add(add);p.add(rename);p.add(del);return p; }
  private JComponent presetActions(){ JPanel p=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,3)); JButton fav=new JButton("☆ 收藏"),open=new JButton("打开调用");fav.addActionListener(e->favorite());open.addActionListener(e->open());p.add(fav);p.add(open);return p; }
  private JComponent detailActions(){ JPanel p=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,3)); JButton copy=new JButton("复制 JSON");copy.addActionListener(e->{detail.selectAll();detail.copy();detail.select(0,0);});p.add(copy);return p; }
  private Component row(JList<? extends ParamIndexData> list,ParamIndexData v,boolean selected){ JPanel p=new JPanel(new BorderLayout());p.setOpaque(true);p.setBorder(BorderFactory.createEmptyBorder(7,8,7,8));p.setBackground(selected?list.getSelectionBackground():list.getBackground()); JLabel title=new JLabel((Boolean.TRUE.equals(v.getFavorite())?"★ ":"")+safe(v.getName()));title.setFont(title.getFont().deriveFont(Font.BOLD));JLabel meta=new JLabel(shortName(v.getQualifiedMethodName())+" · "+(v.getLastUsedAt()==null?"从未使用":"最近使用"));meta.setForeground(selected?list.getSelectionForeground():com.intellij.ui.JBColor.GRAY);p.add(title,BorderLayout.NORTH);p.add(meta,BorderLayout.SOUTH);return p; }
  private void reload(){DataContext.instance(project).listParamIndexDataAsync(items->{all=items; rebuildNav(); refreshList();});}
  private void rebuildNav(){DefaultMutableTreeNode root=n(Node.root());root.add(n(Node.all()));root.add(n(Node.recent()));root.add(n(Node.favorites()));DefaultMutableTreeNode groups=n(Node.collections());root.add(groups);List<String> methods=all.stream().map(ParamIndexData::getQualifiedMethodName).filter(Objects::nonNull).distinct().toList();for(ParamCollection c:collections.list()){DefaultMutableTreeNode g=n(Node.collection(c));c.getMethodNames().stream().filter(methods::contains).forEach(m->g.add(n(Node.method(m))));groups.add(g);}DefaultMutableTreeNode un=n(Node.ungrouped());methods.stream().filter(m->ParamCollectionService.UNGROUPED.equals(collections.collectionOf(m))).forEach(m->un.add(n(Node.method(m))));groups.add(un);navTree.setModel(new DefaultTreeModel(root));for(int i=0;i<navTree.getRowCount();i++)navTree.expandRow(i);}
  private void refreshList(){String q=search.getText().trim().toLowerCase();List<ParamIndexData> items=all.stream().filter(x->matches(x,selected)).filter(x->q.isEmpty()||(safe(x.getName())+" "+safe(x.getQualifiedMethodName())).toLowerCase().contains(q)).sorted(Comparator.comparing((ParamIndexData x)->!Boolean.TRUE.equals(x.getFavorite())).thenComparing(ParamIndexData::getLastUsedAt,Comparator.nullsLast(Comparator.reverseOrder()))).collect(Collectors.toList());DefaultListModel<ParamIndexData> model=(DefaultListModel<ParamIndexData>)presetList.getModel();model.clear();items.forEach(model::addElement);detail.setText("选择一个参数方案以预览 JSON");}
  private boolean matches(ParamIndexData x,Node n){return switch(n.kind){case ALL,COLLECTIONS->true;case RECENT->x.getLastUsedAt()!=null;case FAVORITES->Boolean.TRUE.equals(x.getFavorite());case METHOD->Objects.equals(n.method,x.getQualifiedMethodName());case COLLECTION->n.collection.getMethodNames().contains(x.getQualifiedMethodName());case UNGROUPED->ParamCollectionService.UNGROUPED.equals(collections.collectionOf(x.getQualifiedMethodName()));default->false;};}
  private void preview(ParamIndexData index){if(index==null)return;detail.setText("加载中…");DataContext.instance(project).findParamDataAsync(classOf(index.getQualifiedMethodName()),index.getId(),item->detail.setText(item==null?"参数方案不存在":JsonUtil.formatterJson(item.getParam())));}
  private void favorite(){ParamIndexData x=presetList.getSelectedValue();if(x!=null)DataContext.instance(project).setFavoriteAsync(classOf(x.getQualifiedMethodName()),x.getQualifiedMethodName(),x.getId(),!Boolean.TRUE.equals(x.getFavorite()),r->reload());}
  private void open(){ParamIndexData x=presetList.getSelectedValue();if(x==null)return;WriteIntentReadAction.run((Runnable)()->{var c=IdeClassUtil.findClass(project,classOf(x.getQualifiedMethodName()));var m=IdeClassUtil.findMethod(project,x.getQualifiedMethodName());if(c!=null&&m!=null)new AnyDoorPerformed().doUseNewUI(project.getService(AnyDoorSettingsState.class),project,c,m,AnyDoorActionUtil.genCacheKey(c,m),()->AnyDoorAgainOpenAction.PRE_METHOD_MAP.put(project.getName(),m),x.getId());});}
  private void create(){String name=Messages.showInputDialog(project,"Collection 名称","新建 Collection",null);if(name!=null&&!name.isBlank()){collections.create(name);rebuildNav();}}
  private void rename(){Node x=node(navTree.getSelectionPath());if(x!=null&&x.kind==Kind.COLLECTION){String name=Messages.showInputDialog(project,"Collection 名称","重命名",null,x.collection.getName(),null);if(name!=null&&!name.isBlank()){collections.rename(x.collection.getId(),name);rebuildNav();}}}
  private void delete(){Node x=node(navTree.getSelectionPath());if(x!=null&&x.kind==Kind.COLLECTION&&Messages.showYesNoDialog(project,"删除 Collection，不会删除参数方案。","删除 Collection",null)==Messages.YES){collections.delete(x.collection.getId());selected=Node.all();rebuildNav();refreshList();}}
  private Node node(TreePath p){if(p==null||!(p.getLastPathComponent() instanceof DefaultMutableTreeNode n)||!(n.getUserObject() instanceof Node x))return null;return x;}private DefaultMutableTreeNode n(Node n){return new DefaultMutableTreeNode(n);}private static String classOf(String m){int i=m==null?-1:m.lastIndexOf('#');return i<0?"":m.substring(0,i);}private static String shortName(String m){int i=m==null?-1:m.lastIndexOf('#');return i<0?safe(m):m.substring(i+1);}private static String safe(String s){return s==null||s.isBlank()?"未命名":s;}@Override public void dispose(){}
  private enum Kind{ROOT,ALL,RECENT,FAVORITES,COLLECTIONS,COLLECTION,UNGROUPED,METHOD}private record Node(Kind kind,String method,ParamCollection collection){static Node root(){return new Node(Kind.ROOT,null,null);}static Node all(){return new Node(Kind.ALL,null,null);}static Node recent(){return new Node(Kind.RECENT,null,null);}static Node favorites(){return new Node(Kind.FAVORITES,null,null);}static Node collections(){return new Node(Kind.COLLECTIONS,null,null);}static Node collection(ParamCollection c){return new Node(Kind.COLLECTION,null,c);}static Node ungrouped(){return new Node(Kind.UNGROUPED,null,null);}static Node method(String m){return new Node(Kind.METHOD,m,null);}@Override public String toString(){return switch(kind){case ALL->"全部方案";case RECENT->"最近使用";case FAVORITES->"★ 收藏";case COLLECTIONS->"Collections";case COLLECTION->collection.getName();case UNGROUPED->"未归类";case METHOD->shortName(method);default->"";};}}
  private final class CollectionTransfer extends TransferHandler{public int getSourceActions(JComponent c){return MOVE;}protected Transferable createTransferable(JComponent c){Node x=node(navTree.getSelectionPath());return x!=null&&x.kind==Kind.METHOD?new StringSelection(x.method):null;}public boolean canImport(TransferSupport s){if(!s.isDataFlavorSupported(DataFlavor.stringFlavor))return false;Node x=node(((JTree.DropLocation)s.getDropLocation()).getPath());return x!=null&&(x.kind==Kind.COLLECTION||x.kind==Kind.UNGROUPED);}public boolean importData(TransferSupport s){try{String method=(String)s.getTransferable().getTransferData(DataFlavor.stringFlavor);Node target=node(((JTree.DropLocation)s.getDropLocation()).getPath());collections.moveMethod(method,target.kind==Kind.UNGROUPED?ParamCollectionService.UNGROUPED:target.collection.getId());rebuildNav();return true;}catch(Exception e){return false;}}}
}
