package com.mayhew3.drafttower.client.players;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;
import gwtquery.plugins.draggable.client.DraggableOptions;
import gwtquery.plugins.draggable.client.DraggableOptions.CursorAt;
import gwtquery.plugins.draggable.client.DraggableOptions.RevertOption;
import gwtquery.plugins.draggable.client.events.DragStartEvent;
import gwtquery.plugins.draggable.client.events.DragStartEvent.DragStartEventHandler;
import gwtquery.plugins.draggable.client.events.DragStopEvent;
import gwtquery.plugins.draggable.client.events.DragStopEvent.DragStopEventHandler;
import gwtquery.plugins.droppable.client.DroppableOptions;
import gwtquery.plugins.droppable.client.DroppableOptions.DroppableFunction;
import gwtquery.plugins.droppable.client.DroppableOptions.DroppableTolerance;
import gwtquery.plugins.droppable.client.events.DragAndDropContext;
import gwtquery.plugins.droppable.client.gwt.DragAndDropCellTable;
import gwtquery.plugins.droppable.client.gwt.DragAndDropColumn;

/**
 * Base class for tables supporting player drag and drop.
 */
public abstract class PlayerTable<T> extends DragAndDropCellTable<T>
    implements PlayerTableView<T> {

  interface Resources extends ClientBundle {
    interface Css extends CssResource {
      String table();
      String dragHelper();
      String dropHoverTop();
      String dropHoverBottom();
    }

    @Source("PlayerTable.css")
    Css css();
  }

  protected static final Resources.Css BASE_CSS = ((Resources) GWT.create(Resources.class)).css();

  private HandlerRegistration mouseMoveHandler;
  private boolean isDragging;
  private Runnable runAfterDrag;

  public PlayerTable(PlayerDataProvider<T> presenter) {
    addDragStartHandler(new DragStartEventHandler() {
      @Override
      public void onDragStart(DragStartEvent dragStartEvent) {
        isDragging = true;
      }
    });
    addDragStopHandler(new DragStopEventHandler() {
      @Override
      public void onDragStop(DragStopEvent dragStopEvent) {
        isDragging = false;
        if (runAfterDrag != null) {
          runAfterDrag.run();
          runAfterDrag = null;
        }
      }
    });

    presenter.setView(this);
  }

  public void initDragging(DragAndDropColumn<T, ?> column, DroppableFunction onDrop) {
    DraggableOptions draggableOptions = column.getDraggableOptions();
    Element helper = DOM.createDiv();
    helper.addClassName(BASE_CSS.dragHelper());
    draggableOptions.setHelper(helper);
    draggableOptions.setCursor(Cursor.ROW_RESIZE);
    draggableOptions.setCursorAt(new CursorAt(0, 0, null, null));
    draggableOptions.setRevert(RevertOption.ON_INVALID_DROP);

    DroppableOptions droppableOptions = column.getDroppableOptions();
    droppableOptions.setTolerance(DroppableTolerance.POINTER);
    droppableOptions.setOnOver(new DroppableFunction() {
      @Override
      public void f(final DragAndDropContext dragAndDropContext) {
        clearMouseMoveHandler();
        mouseMoveHandler = addDomHandler(new MouseMoveHandler() {
          @Override
          public void onMouseMove(MouseMoveEvent event) {
            removeHoverClasses(dragAndDropContext);
            String hoverClass = isTopDrop(dragAndDropContext, event.getClientY())
                ? BASE_CSS.dropHoverTop()
                : BASE_CSS.dropHoverBottom();
            getTRParent(dragAndDropContext).addClassName(hoverClass);
          }
        }, MouseMoveEvent.getType());
      }
    });
    DroppableFunction removeHover = new DroppableFunction() {
      @Override
      public void f(DragAndDropContext dragAndDropContext) {
        clearMouseMoveHandler();
        removeHoverClasses(dragAndDropContext);
      }
    };
    droppableOptions.setOnOut(removeHover);
    droppableOptions.setOnDeactivate(removeHover);
    droppableOptions.setOnDrop(onDrop);
  }

  private void clearMouseMoveHandler() {
    if (mouseMoveHandler != null) {
      mouseMoveHandler.removeHandler();
      mouseMoveHandler = null;
    }
  }

  protected boolean isTopDrop(DragAndDropContext dragAndDropContext, boolean adjustByTop) {
    return isTopDrop(dragAndDropContext, dragAndDropContext.getHelperPosition().top
        + (adjustByTop ? getAbsoluteTop() : 0));
  }

  private static boolean isTopDrop(DragAndDropContext dragAndDropContext, int cursorTop) {
    com.google.gwt.dom.client.Element droppableWidget = dragAndDropContext.getDroppable();
    return cursorTop < droppableWidget.getAbsoluteTop() + droppableWidget.getOffsetHeight() / 2;
  }

  private void removeHoverClasses(DragAndDropContext dragAndDropContext) {
    getTRParent(dragAndDropContext).removeClassName(BASE_CSS.dropHoverBottom());
    getTRParent(dragAndDropContext).removeClassName(BASE_CSS.dropHoverTop());
  }

  private static Element getTRParent(DragAndDropContext dragAndDropContext) {
    Element droppable = dragAndDropContext.getDroppable();
    while (!droppable.getTagName().equalsIgnoreCase("tr")
        && droppable.hasParentElement()) {
      droppable = droppable.getParentElement();
    }
    return droppable;
  }

  @Override
  public void refresh() {
    if (isDragging) {
      runAfterDrag = new Runnable() {
        @Override
        public void run() {
          setVisibleRangeAndClearData(getVisibleRange(), true);
        }
      };
    } else {
      setVisibleRangeAndClearData(getVisibleRange(), true);
    }
  }
}