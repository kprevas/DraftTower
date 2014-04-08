package com.mayhew3.drafttower.client;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gwt.cell.client.*;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.text.shared.AbstractSafeHtmlRenderer;
import com.google.gwt.user.cellview.client.*;
import com.google.gwt.user.cellview.client.ColumnSortEvent.AsyncHandler;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.Window;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mayhew3.drafttower.client.events.ChangePlayerRankEvent;
import com.mayhew3.drafttower.client.events.LoginEvent;
import com.mayhew3.drafttower.client.events.PlayerSelectedEvent;
import com.mayhew3.drafttower.client.events.ShowPlayerPopupEvent;
import com.mayhew3.drafttower.server.GinBindingAnnotations.QueueAreaTop;
import com.mayhew3.drafttower.shared.*;
import gwtquery.plugins.draggable.client.events.DragStartEvent;
import gwtquery.plugins.draggable.client.events.DragStartEvent.DragStartEventHandler;
import gwtquery.plugins.droppable.client.DroppableOptions.DroppableFunction;
import gwtquery.plugins.droppable.client.events.DragAndDropContext;
import gwtquery.plugins.droppable.client.gwt.DragAndDropColumn;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.google.gwt.user.client.ui.HasHorizontalAlignment.ALIGN_RIGHT;
import static com.mayhew3.drafttower.shared.PlayerColumn.*;

/**
 * Table widget for displaying player stats.
 */
public class UnclaimedPlayerTable extends PlayerTable<Player> implements
    LoginEvent.Handler {

  interface Resources extends ClientBundle {
    interface Css extends CssResource {
      String injury();
      String newsCell();
      String rightAlign();
      String batterStat();
      String pitcherStat();
      String splitHeader();
    }

    @Source("UnclaimedPlayerTable.css")
    Css css();
  }

  protected static final Resources.Css CSS = ((Resources) GWT.create(Resources.class)).css();
  static {
    CSS.ensureInjected();
  }

  public interface Templates extends SafeHtmlTemplates {
    @Template("<span title=\"{0}\">{1}</span>")
    SafeHtml header(String longName, SafeHtml shortName);

    @Template("<span class=\"{0}\">" +
        "<span class=\"{1}\">{2}</span>" +
        "/" +
        "<span class=\"{3}\">{4}</span>" +
        "</span>")
    SafeHtml splitHeader(String className,
        String batterStatClassName,
        String batterShortName,
        String pitcherClassName,
        String pitcherShortName);

    @Template("<span class=\"{0}\" title=\"{1}\">+</span>")
    SafeHtml injury(String className, String injury);

    @Template("<span class=\"{0}\">{1}</span>")
    SafeHtml cell(String style, String value);
  }
  private static final Templates TEMPLATES = GWT.create(Templates.class);

  private static class ColumnSort {
    private PlayerColumn column;
    private boolean isAscending;

    ColumnSort(PlayerColumn column, boolean ascending) {
      this.column = column;
      isAscending = ascending;
    }
  }

  private class PlayerColumnHeader extends Header<SafeHtml> {

    private final PlayerColumn column;
    private final PlayerColumn pitcherColumn;

    public PlayerColumnHeader(PlayerColumn column, PlayerColumn pitcherColumn) {
      super(new SafeHtmlCell());
      this.column = column;
      this.pitcherColumn = pitcherColumn;
    }

    @Override
    public SafeHtml getValue() {
      return TEMPLATES.header(getLongName(), getShortName());
    }

    private SafeHtml getShortName() {
      if (pitcherColumn != null) {
        if (isPitcherFilter(positionFilter)) {
          return new SafeHtmlBuilder()
              .appendEscaped(pitcherColumn.getShortName())
              .toSafeHtml();
        }
        if (isPitchersAndBattersFilter(positionFilter)) {
          return TEMPLATES.splitHeader(CSS.splitHeader(),
              CSS.batterStat(),
              column.getShortName(),
              CSS.pitcherStat(),
              pitcherColumn.getShortName());
        }
      }
      return new SafeHtmlBuilder()
          .appendEscaped(column.getShortName())
          .toSafeHtml();
    }

    private String getLongName() {
      if (pitcherColumn != null) {
        if (isPitcherFilter(positionFilter)) {
          return pitcherColumn.getLongName();
        }
        if (isPitchersAndBattersFilter(UnclaimedPlayerTable.this.positionFilter)) {
          return column.getLongName() + "/" + pitcherColumn.getLongName();
        }
      }
      return column.getLongName();
    }
  }

  private static class PlayerValue {
    private Player player;
    private String value;

    private PlayerValue(Player player, String value) {
      this.player = player;
      this.value = value;
    }
  }

  private abstract class PlayerTableColumn<C> extends DragAndDropColumn<Player, C> {
    protected final PlayerColumn column;

    public PlayerTableColumn(Cell<C> cell, PlayerColumn column) {
      super(cell);
      this.column = column;
      setSortable(true);

      if (column != NAME && column != MLB && column != ELIG) {
        setHorizontalAlignment(ALIGN_RIGHT);
      }

      DroppableFunction onDrop = new DroppableFunction() {
        @Override
        public void f(DragAndDropContext dragAndDropContext) {
          Player draggedPlayer = dragAndDropContext.getDraggableData();
          Player droppedPlayer = dragAndDropContext.getDroppableData();
          if (draggedPlayer.getPlayerId() != droppedPlayer.getPlayerId()) {
            int prevRank = Integer.parseInt(MYRANK.get(draggedPlayer));
            int targetRank = Integer.parseInt(MYRANK.get(droppedPlayer));
            if (prevRank > targetRank && !isTopDrop(dragAndDropContext, false)) {
              targetRank++;
            }
            if (prevRank < targetRank && isTopDrop(dragAndDropContext, false)) {
              targetRank--;
            }
            eventBus.fireEvent(new ChangePlayerRankEvent(
                draggedPlayer.getPlayerId(),
                targetRank,
                prevRank));
          }
        }
      };
      initDragging(this, onDrop);
    }

    public PlayerColumn getColumn() {
      return column;
    }

    public abstract PlayerColumn getSortedColumn();

    public abstract ColumnSort getSortedColumn(boolean isAscending);

    protected abstract void updateDefaultSort();
  }

  public class NonStatPlayerTableColumn extends PlayerTableColumn<String> {

    public NonStatPlayerTableColumn(PlayerColumn column) {
      super(createCell(column), column);

      setDefaultSortAscending(column.isDefaultSortAscending());

      if (column == MYRANK) {
        setFieldUpdater(new FieldUpdater<Player, String>() {
          @Override
          public void update(int index, Player player, String newRank) {
            String currentRank = MYRANK.get(player);
            if (!newRank.equals(currentRank)) {
              try {
                eventBus.fireEvent(new ChangePlayerRankEvent(player.getPlayerId(),
                    Integer.parseInt(newRank), Integer.parseInt(currentRank)));
              } catch (NumberFormatException e) {
                // whatevs
              }
            }
          }
        });
      }
    }

    @Override
    public String getValue(Player player) {
      if (column == WIZARD) {
        return PlayerColumn.getWizard(player, positionFilter);
      } else {
        return column.get(player);
      }
    }

    @Override
    public PlayerColumn getSortedColumn() {
      return column;
    }

    @Override
    public ColumnSort getSortedColumn(boolean isAscending) {
      return new ColumnSort(column, isAscending);
    }

    @Override
    protected void updateDefaultSort() {
      // No-op.
    }
  }

  public class StatPlayerTableColumn extends PlayerTableColumn<PlayerValue> {

    private final PlayerColumn pitcherColumn;

    public StatPlayerTableColumn(PlayerColumn column, PlayerColumn pitcherColumn) {
      super(createStatCell(), column);
      this.pitcherColumn = pitcherColumn;
      updateDefaultSort();
    }

    @Override
    protected void updateDefaultSort() {
      setDefaultSortAscending(pitcherColumn.isDefaultSortAscending() && isPitcherFilter(positionFilter));
    }

    @Override
    public PlayerValue getValue(Player player) {
      if (tableSpec.getSortCol() == column) {
        return new PlayerValue(player, column.get(player));
      }
      if (tableSpec.getSortCol() == pitcherColumn) {
        return new PlayerValue(player, pitcherColumn.get(player));
      }
      if (pitcherColumn != null && pitcherColumn.get(player) != null) {
        return new PlayerValue(player, pitcherColumn.get(player));
      }
      return new PlayerValue(player, column.get(player));
    }

    @Override
    public PlayerColumn getSortedColumn() {
      return isPitcherFilter(UnclaimedPlayerTable.this.positionFilter) ? pitcherColumn : column;
    }

    @Override
    public ColumnSort getSortedColumn(boolean isAscending) {
      if (isPitcherFilter(positionFilter)) {
        return new ColumnSort(pitcherColumn, isAscending);
      } else if (isPitchersAndBattersFilter(positionFilter)) {
        PlayerColumn sortColumn = isAscending ? pitcherColumn : column;
        return new ColumnSort(sortColumn, sortColumn.isDefaultSortAscending());
      } else {
        return new ColumnSort(column, isAscending);
      }
    }
  }

  static {
    BASE_CSS.ensureInjected();
  }

  private static final PlayerColumn COLUMNS[] = {
      NAME, MLB, ELIG, G, AB, HR, RBI, OBP, SLG, RHR, SBCS, RANK, WIZARD, DRAFT, MYRANK
  };
  private static final PlayerColumn PITCHER_COLUMNS[] = {
      null, null, null, null, null, INN, K, ERA, WHIP, WL, S, null, null, null, null, null
  };

  public static final PlayerDataSet DEFAULT_DATA_SET = PlayerDataSet.CBSSPORTS;
  public static final PlayerColumn DEFAULT_SORT_COL = PlayerColumn.MYRANK;
  public static final boolean DEFAULT_SORT_ASCENDING = true;

  private final Provider<Integer> queueAreaTopProvider;

  private EnumSet<Position> positionFilter = EnumSet.allOf(Position.class);
  private final TableSpec tableSpec;
  private boolean hideInjuries;
  private String nameFilter;
  private final Map<PlayerColumn, PlayerTableColumn<?>> playerColumns = new EnumMap<>(PlayerColumn.class);

  @Inject
  public UnclaimedPlayerTable(AsyncDataProvider<Player> dataProvider,
      BeanFactory beanFactory,
      final EventBus eventBus,
      @QueueAreaTop Provider<Integer> queueAreaTopProvider) {
    super(eventBus);
    this.queueAreaTopProvider = queueAreaTopProvider;

    tableSpec = beanFactory.createTableSpec().as();
    tableSpec.setPlayerDataSet(DEFAULT_DATA_SET);
    tableSpec.setSortCol(DEFAULT_SORT_COL);
    tableSpec.setAscending(DEFAULT_SORT_ASCENDING);

    addStyleName(BASE_CSS.table());
    setPageSize(40);
    ((AbstractHeaderOrFooterBuilder<?>) getHeaderBuilder()).setSortIconStartOfLine(false);

    addColumn(new Column<Player, SafeHtml>(new SafeHtmlCell()) {
      @Override
      public SafeHtml getValue(Player player) {
        if (player.getInjury() != null) {
          return TEMPLATES.injury(CSS.injury(), player.getInjury());
        }
        return null;
      }
    });

    for (int i = 0; i < COLUMNS.length; i++) {
      PlayerColumn column = COLUMNS[i];
      PlayerColumn pitcherColumn = PITCHER_COLUMNS[i];
      PlayerTableColumn<?> playerTableColumn = pitcherColumn == null
          ? new NonStatPlayerTableColumn(column)
          : new StatPlayerTableColumn(column, pitcherColumn);
      addColumn(playerTableColumn, new PlayerColumnHeader(column, pitcherColumn));
      if (playerTableColumn.getHorizontalAlignment() == ALIGN_RIGHT) {
        getHeader(getColumnIndex(playerTableColumn)).setHeaderStyleNames(CSS.rightAlign());
      }
      playerColumns.put(column, playerTableColumn);

      if (column == NAME) {
        Column<Player, String> newsColumn = new Column<Player, String>(new ClickableTextCell()) {
          @Override
          public String getValue(Player object) {
            return "?";
          }
        };
        newsColumn.setFieldUpdater(new FieldUpdater<Player, String>() {
          @Override
          public void update(int index, Player player, String value) {
            eventBus.fireEvent(new ShowPlayerPopupEvent(player));
          }
        });
        newsColumn.setCellStyleNames(CSS.newsCell());
        addColumn(newsColumn);
      }
    }

    dataProvider.addDataDisplay(this);
    addColumnSortHandler(new AsyncHandler(this) {
      @Override
      public void onColumnSort(ColumnSortEvent event) {
        ColumnSort sortedColumn = getSortedColumn(isSortedAscending());
        tableSpec.setSortCol(sortedColumn.column);
        tableSpec.setAscending(sortedColumn.isAscending);
        super.onColumnSort(event);
        updateDropEnabled();
      }
    });

    addDragStartHandler(new DragStartEventHandler() {
      @Override
      public void onDragStart(DragStartEvent dragStartEvent) {
        Player player = dragStartEvent.getDraggableData();
        dragStartEvent.getHelper().setInnerSafeHtml(
            new SafeHtmlBuilder().appendEscaped(NAME.get(player))
                .toSafeHtml());
      }
    });
    updateDropEnabled();

    final SingleSelectionModel<Player> selectionModel = new SingleSelectionModel<>();
    setSelectionModel(selectionModel);
    getSelectionModel().addSelectionChangeHandler(new Handler() {
      @Override
      public void onSelectionChange(SelectionChangeEvent event) {
        Player player = selectionModel.getSelectedObject();
        eventBus.fireEvent(new PlayerSelectedEvent(player.getPlayerId(), NAME.get(player)));
      }
    });
    setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);

    eventBus.addHandler(LoginEvent.TYPE, this);

    Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        computePageSize();
      }
    });
  }

  private AbstractCell<String> createCell(PlayerColumn column) {
    if (column == MYRANK) {
      return new EditTextCell();
    } else {
      return new TextCell();
    }
  }

  private AbstractCell<PlayerValue> createStatCell() {
    return new AbstractSafeHtmlCell<PlayerValue>(new AbstractSafeHtmlRenderer<PlayerValue>() {
      @Override
      public SafeHtml render(PlayerValue value) {
        SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (value.value != null) {
          if (isPitchersAndBattersFilter(positionFilter)) {
            String style;
            if (ELIG.get(value.player).contains(Position.P.getShortName())) {
              style = CSS.pitcherStat();
            } else {
              style = CSS.batterStat();
            }
            builder.append(TEMPLATES.cell(style, value.value));
          } else {
            builder.appendEscaped(value.value);
          }
        }
        return builder.toSafeHtml();
      }
    }) {
      @Override
      protected void render(Context context, SafeHtml value, SafeHtmlBuilder sb) {
        if (value != null) {
          sb.append(value);
        }
      }
    };
  }

  void computePageSize() {
    TableRowElement rowElement = getRowElement(0);
    if (rowElement != null) {
      int availableHeight = queueAreaTopProvider.get() - rowElement.getAbsoluteTop();
      int pageSize = availableHeight / rowElement.getOffsetHeight();
      if (pageSize != getPageSize()) {
        setPageSize(pageSize);
      }
    }
  }

  public PlayerColumn getSortedColumn() {
    ColumnSortList columnSortList = getColumnSortList();
    if (columnSortList.size() > 0) {
      PlayerTableColumn<?> column = (PlayerTableColumn<?>) columnSortList.get(0).getColumn();
      return column.getSortedColumn();
    }
    return null;
  }

  public ColumnSort getSortedColumn(boolean isAscending) {
    ColumnSortList columnSortList = getColumnSortList();
    if (columnSortList.size() > 0) {
      PlayerTableColumn<?> column = (PlayerTableColumn<?>) columnSortList.get(0).getColumn();
      return column.getSortedColumn(isAscending);
    }
    return null;
  }

  private boolean isSortedAscending() {
    ColumnSortList columnSortList = getColumnSortList();
    return columnSortList.size() > 0 && columnSortList.get(0).isAscending();
  }

  public EnumSet<Position> getPositionFilter() {
    return positionFilter;
  }

  public boolean getHideInjuries() {
    return hideInjuries;
  }

  public void setPositionFilter(EnumSet<Position> positionFilter) {
    boolean reSort = isPitcherFilter(this.positionFilter) != isPitcherFilter(positionFilter);
    this.positionFilter = positionFilter;
    for (PlayerTableColumn<?> playerTableColumn : playerColumns.values()) {
      playerTableColumn.updateDefaultSort();
    }
    if (reSort) {
      ColumnSortEvent.fire(this, getColumnSortList());
    }
    setVisibleRangeAndClearData(new Range(0, getPageSize()), true);
  }

  public void setPlayerDataSet(PlayerDataSet playerDataSet) {
    tableSpec.setPlayerDataSet(playerDataSet);
    updateDropEnabled();
    setVisibleRangeAndClearData(getVisibleRange(), true);
  }

  public void setNameFilter(String nameFilter) {
    this.nameFilter = nameFilter;
    setVisibleRangeAndClearData(new Range(0, getPageSize()), true);
  }

  @SuppressWarnings("unchecked")
  private void updateDropEnabled() {
    boolean dropEnabled = getSortedColumn() == MYRANK;
    for (int i = 0; i < getColumnCount(); i++) {
      Column<Player, ?> column = getColumn(i);
      if (column instanceof DragAndDropColumn) {
        ((DragAndDropColumn<Player, ?>) column).getDroppableOptions().setDisabled(!dropEnabled);
      }
    }
  }

  public void setHideInjuries(boolean hideInjuries) {
    this.hideInjuries = hideInjuries;
    setVisibleRangeAndClearData(getVisibleRange(), true);
  }

  public TableSpec getTableSpec() {
    return tableSpec;
  }

  public String getNameFilter() {
    return nameFilter;
  }

  @Override
  public void onLogin(LoginEvent event) {
    PlayerDataSet initialWizardTable = event.getLoginResponse().getInitialWizardTable();
    if (initialWizardTable != null) {
      tableSpec.setPlayerDataSet(initialWizardTable);
      tableSpec.setSortCol(PlayerColumn.WIZARD);
      tableSpec.setAscending(false);
    }

    ColumnSortList columnSortList = getColumnSortList();
    columnSortList.clear();
    columnSortList.push(new ColumnSortInfo(playerColumns.get(tableSpec.getSortCol()),
        tableSpec.isAscending()));
    setVisibleRangeAndClearData(getVisibleRange(), true);
    updateDropEnabled();
  }

  @Override
  protected boolean needsRefresh(List<DraftPick> oldPicks, List<DraftPick> newPicks) {
    for (int i = oldPicks.size(); i < newPicks.size(); i++) {
      final long pickedPlayerId = newPicks.get(i).getPlayerId();
      if (Iterables.any(getVisibleItems(), new Predicate<Player>() {
        @Override
        public boolean apply(Player player) {
          return player.getPlayerId() == pickedPlayerId;
        }
      })) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPitcherFilter(EnumSet<Position> positionFilter) {
    return positionFilter.equals(EnumSet.of(Position.P));
  }

  private boolean isPitchersAndBattersFilter(EnumSet<Position> positionFilter) {
    return positionFilter.isEmpty()
        || (positionFilter.contains(Position.P) && positionFilter.size() > 1);
  }
}