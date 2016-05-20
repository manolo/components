/*
 * Copyright 2000-2014 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.Touch;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.touch.client.Point;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.HasEnabled;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.ResizeComposite;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.DeferredWorker;
import com.vaadin.client.Focusable;
import com.vaadin.client.WidgetUtil;
import com.vaadin.client.data.DataChangeHandler;
import com.vaadin.client.data.DataSource;
import com.vaadin.client.renderers.ComplexRenderer;
import com.vaadin.client.renderers.Renderer;
import com.vaadin.client.ui.FocusUtil;
import com.vaadin.client.ui.SubPartAware;
import com.vaadin.client.ui.dd.DragAndDropHandler;
import com.vaadin.client.ui.dd.DragAndDropHandler.DragAndDropCallback;
import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.ColumnConfiguration;
import com.vaadin.client.widget.escalator.EscalatorUpdater;
import com.vaadin.client.widget.escalator.FlyweightCell;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.escalator.RowVisibilityChangeEvent;
import com.vaadin.client.widget.escalator.RowVisibilityChangeHandler;
import com.vaadin.client.widget.escalator.ScrollbarBundle.Direction;
import com.vaadin.client.widget.grid.AutoScroller;
import com.vaadin.client.widget.grid.AutoScroller.AutoScrollerCallback;
import com.vaadin.client.widget.grid.AutoScroller.ScrollAxis;
import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.client.widget.grid.CellStyleGenerator;
import com.vaadin.client.widget.grid.DetailsGenerator;
import com.vaadin.client.widget.grid.EventCellReference;
import com.vaadin.client.widget.grid.RendererCellReference;
import com.vaadin.client.widget.grid.RowReference;
import com.vaadin.client.widget.grid.RowStyleGenerator;
import com.vaadin.client.widget.grid.events.BodyClickHandler;
import com.vaadin.client.widget.grid.events.BodyDoubleClickHandler;
import com.vaadin.client.widget.grid.events.BodyKeyDownHandler;
import com.vaadin.client.widget.grid.events.BodyKeyPressHandler;
import com.vaadin.client.widget.grid.events.BodyKeyUpHandler;
import com.vaadin.client.widget.grid.events.ColumnReorderEvent;
import com.vaadin.client.widget.grid.events.ColumnReorderHandler;
import com.vaadin.client.widget.grid.events.ColumnResizeEvent;
import com.vaadin.client.widget.grid.events.ColumnResizeHandler;
import com.vaadin.client.widget.grid.events.ColumnVisibilityChangeEvent;
import com.vaadin.client.widget.grid.events.ColumnVisibilityChangeHandler;
import com.vaadin.client.widget.grid.events.DataAvailableEvent;
import com.vaadin.client.widget.grid.events.DataAvailableHandler;
import com.vaadin.client.widget.grid.events.EditorDomEvent;
import com.vaadin.client.widget.grid.events.EditorHandler;
import com.vaadin.client.widget.grid.events.FooterClickHandler;
import com.vaadin.client.widget.grid.events.FooterDoubleClickHandler;
import com.vaadin.client.widget.grid.events.FooterKeyDownHandler;
import com.vaadin.client.widget.grid.events.FooterKeyPressHandler;
import com.vaadin.client.widget.grid.events.FooterKeyUpHandler;
import com.vaadin.client.widget.grid.events.GridClickEvent;
import com.vaadin.client.widget.grid.events.GridDoubleClickEvent;
import com.vaadin.client.widget.grid.events.GridKeyDownEvent;
import com.vaadin.client.widget.grid.events.GridKeyPressEvent;
import com.vaadin.client.widget.grid.events.GridKeyUpEvent;
import com.vaadin.client.widget.grid.events.HeaderClickHandler;
import com.vaadin.client.widget.grid.events.HeaderDoubleClickHandler;
import com.vaadin.client.widget.grid.events.HeaderKeyDownHandler;
import com.vaadin.client.widget.grid.events.HeaderKeyPressHandler;
import com.vaadin.client.widget.grid.events.HeaderKeyUpHandler;
import com.vaadin.client.widget.grid.events.ScrollEvent;
import com.vaadin.client.widget.grid.events.ScrollHandler;
import com.vaadin.client.widget.grid.events.SelectAllEvent;
import com.vaadin.client.widget.grid.events.SelectAllHandler;
import com.vaadin.client.widget.grid.selection.HasSelectionHandlers;
import com.vaadin.client.widget.grid.selection.SelectionEvent;
import com.vaadin.client.widget.grid.selection.SelectionHandler;
import com.vaadin.client.widget.grid.selection.SelectionModel;
import com.vaadin.client.widget.grid.selection.SelectionModel.Single;
import com.vaadin.client.widget.grid.selection.SelectionModelMulti;
import com.vaadin.client.widget.grid.selection.SelectionModelNone;
import com.vaadin.client.widget.grid.selection.SelectionModelSingle;
import com.vaadin.client.widget.grid.sort.Sort;
import com.vaadin.client.widget.grid.sort.SortEvent;
import com.vaadin.client.widget.grid.sort.SortHandler;
import com.vaadin.client.widget.grid.sort.SortOrder;
import com.vaadin.client.widgets.escalator.Escalator;
import com.vaadin.client.widgets.escalator.SubPartArguments;
import com.vaadin.client.widgets.grid.Editor.State;
import com.vaadin.client.widgets.grid.Footer.FooterRow;
import com.vaadin.client.widgets.grid.Header.HeaderRow;
import com.vaadin.client.widgets.grid.StaticSection.StaticRow;
import com.vaadin.shared.data.sort.SortDirection;
import com.vaadin.shared.ui.grid.GridConstants;
import com.vaadin.shared.ui.grid.GridConstants.Section;
import com.vaadin.shared.ui.grid.HeightMode;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.ui.grid.ScrollDestination;
import com.vaadin.shared.util.SharedUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * A data grid view that supports columns and lazy loading of data rows from a
 * data source.
 * 
 * <h1>Columns</h1>
 * <p>
 * Each column in Grid is represented by a {@link Column}. Each
 * {@code GridColumn} has a custom implementation for
 * {@link Column#getValue(Object)} that gets the row object as an argument, and
 * returns the value for that particular column, extracted from the row object.
 * <p>
 * Each column also has a Renderer. Its function is to take the value that is
 * given by the {@code GridColumn} and display it to the user. A simple column
 * might have a {@link com.vaadin.client.renderers.TextRenderer TextRenderer}
 * that simply takes in a {@code String} and displays it as the cell's content.
 * A more complex renderer might be
 * {@link com.vaadin.client.renderers.ProgressBarRenderer ProgressBarRenderer}
 * that takes in a floating point number, and displays a progress bar instead,
 * based on the given number.
 * <p>
 * <em>See:</em> {@link #addColumn(Column)}, {@link #addColumn(Column, int)} and
 * {@link #addColumns(Column<C, T>...)}. <em>Also</em>
 * {@link Column#setRenderer(Renderer)}.
 * 
 * <h1>Data Sources</h1>
 * <p>
 * Grid gets its data from a {@link DataSource}, providing row objects to Grid
 * from a user-defined endpoint. It can be either a local in-memory data source
 * (e.g. {@link com.vaadin.client.widget.grid.datasources.ListDataSource
 * ListDataSource}) or even a remote one, retrieving data from e.g. a REST API
 * (see {@link com.vaadin.client.data.AbstractRemoteDataSource
 * AbstractRemoteDataSource}).
 * 
 * 
 * @param <T>
 *            The row type of the grid. The row type is the POJO type from where
 *            the data is retrieved into the column cells.
 * @since 7.4
 * @author Vaadin Ltd
 */
public class Grid<T> extends ResizeComposite implements
        HasSelectionHandlers<T>, SubPartAware, DeferredWorker, Focusable,
        com.google.gwt.user.client.ui.Focusable, HasWidgets, HasEnabled {

    private static final String STYLE_NAME = "v-grid";

    static final String SELECT_ALL_CHECKBOX_CLASSNAME = "-select-all-checkbox";

    private static final String CUSTOM_STYLE_PROPERTY_NAME = "customStyle";

    /**
     * An initial height that is given to new details rows before rendering the
     * appropriate widget that we then can be measure
     * 
     * @see GridSpacerUpdater
     */
    static final double DETAILS_ROW_INITIAL_HEIGHT = 50;

    EventCellReference<T> eventCell = new EventCellReference<T>(this);
    private GridKeyDownEvent keyDown = new GridKeyDownEvent(this, eventCell);
    private GridKeyUpEvent keyUp = new GridKeyUpEvent(this, eventCell);
    private GridKeyPressEvent keyPress = new GridKeyPressEvent(this, eventCell);
    private GridClickEvent clickEvent = new GridClickEvent(this, eventCell);
    private GridDoubleClickEvent doubleClickEvent = new GridDoubleClickEvent(
            this, eventCell);

    /**
     * Escalator used internally by grid to render the rows
     */
    public Escalator escalator = GWT.create(Escalator.class);

    final Header header = GWT.create(Header.class);

    final Footer footer = GWT.create(Footer.class);

    final Sidebar sidebar = new Sidebar(this);

    /**
     * List of columns in the grid. Order defines the visible order.
     */
    List<Column<?, T>> columns = new ArrayList<Column<?, T>>();

    /**
     * The datasource currently in use. <em>Note:</em> it is <code>null</code>
     * on initialization, but not after that.
     */
    DataSource<T> dataSource;

    /**
     * Currently available row range in DataSource.
     */
    private Range currentDataAvailable = Range.withLength(0, 0);

    /**
     * The number of frozen columns, 0 freezes the selection column if
     * displayed, -1 also prevents selection col from freezing.
     */
    private int frozenColumnCount = 0;

    /**
     * Current sort order. The (private) sort() method reads this list to
     * determine the order in which to present rows.
     */
    List<SortOrder> sortOrder = new ArrayList<SortOrder>();

    private Renderer<Boolean> selectColumnRenderer = null;

    SelectionColumn<T> selectionColumn;

    String rowStripeStyleName;
    String rowHasDataStyleName;
    String rowSelectedStyleName;
    String cellFocusStyleName;
    String rowFocusStyleName;

    /**
     * Current selection model.
     */
    private SelectionModel<T> selectionModel;

    public final CellFocusHandler<T> cellFocusHandler;

    private final UserSorter<T> sorter = new UserSorter<T>(this);

    private final Editor<T> editor = GWT.create(Editor.class);

    boolean dataIsBeingFetched = false;

    /**
     * The cell a click event originated from
     * <p>
     * This is a workaround to make Chrome work like Firefox. In Chrome,
     * normally if you start a drag on one cell and release on:
     * <ul>
     * <li>that same cell, the click event is that {@code <td>}.
     * <li>a cell on that same row, the click event is the parent {@code <tr>}.
     * <li>a cell on another row, the click event is the table section ancestor
     * ({@code <thead>}, {@code <tbody>} or {@code <tfoot>}).
     * </ul>
     * 
     * @see #onBrowserEvent(Event)
     */
    private Cell cellOnPrevMouseDown;

    /**
     * A scheduled command to re-evaluate the widths of <em>all columns</em>
     * that have calculated widths. Most probably called because
     * minwidth/maxwidth/expandratio has changed.
     */
    final AutoColumnWidthsRecalculator<T> autoColumnWidthsRecalculator = new AutoColumnWidthsRecalculator<T>(this);

    private boolean enabled = true;

    DetailsGenerator detailsGenerator = DetailsGenerator.NULL;
    private GridSpacerUpdater<T> gridSpacerUpdater = new GridSpacerUpdater<T>(this);
    /** A set keeping track of the indices of all currently open details */
    private Set<Integer> visibleDetails = new HashSet<Integer>();

    private boolean columnReorderingAllowed;

    ColumnHider<T> columnHider = new ColumnHider<T>(this);

    private DragAndDropHandler dndHandler = new DragAndDropHandler();

    private AutoScroller autoScroller = new AutoScroller(this);

    private DragAndDropHandler.DragAndDropCallback headerCellDndCallback = new DragAndDropCallback() {

        private final AutoScrollerCallback autoScrollerCallback = new AutoScrollerCallback() {

            @Override
            public void onAutoScroll(int scrollDiff) {
                autoScrollX = scrollDiff;
                onDragUpdate(null);
            }

            @Override
            public void onAutoScrollReachedMin() {
                // make sure the drop marker is visible on the left
                autoScrollX = 0;
                updateDragDropMarker(clientX);
            }

            @Override
            public void onAutoScrollReachedMax() {
                // make sure the drop marker is visible on the right
                autoScrollX = 0;
                updateDragDropMarker(clientX);
            }
        };
        /**
         * Elements for displaying the dragged column(s) and drop marker
         * properly
         */
        private Element table;
        private Element tableHeader;
        /** Marks the column drop location */
        private Element dropMarker;
        /** A copy of the dragged column(s), moves with cursor. */
        private Element dragElement;
        /** Tracks index of the column whose left side the drop would occur */
        private int latestColumnDropIndex;
        /**
         * Map of possible drop positions for the column and the corresponding
         * column index.
         */
        private final TreeMap<Double, Integer> possibleDropPositions = new TreeMap<Double, Integer>();
        /**
         * Makes sure that drag cancel doesn't cause anything unwanted like sort
         */
        private HandlerRegistration columnSortPreventRegistration;

        private int clientX;

        /** How much the grid is being auto scrolled while dragging. */
        private int autoScrollX;

        /** Captures the value of the focused column before reordering */
        private int focusedColumnIndex;

        /** Offset caused by the drag and drop marker width */
        private double dropMarkerWidthOffset;

        private void initHeaderDragElementDOM() {
            if (table == null) {
                tableHeader = DOM.createTHead();
                dropMarker = DOM.createDiv();
                tableHeader.appendChild(dropMarker);
                table = DOM.createTable();
                table.appendChild(tableHeader);
                table.setClassName("header-drag-table");
            }
            // update the style names on each run in case primary name has been
            // modified
            tableHeader.setClassName(escalator.getHeader().getElement()
                    .getClassName());
            dropMarker.setClassName(getStylePrimaryName() + "-drop-marker");
            int topOffset = 0;
            for (int i = 0; i < eventCell.getRowIndex(); i++) {
                topOffset += escalator.getHeader().getRowElement(i)
                        .getFirstChildElement().getOffsetHeight();
            }
            tableHeader.getStyle().setTop(topOffset, Unit.PX);

            getElement().appendChild(table);

            dropMarkerWidthOffset = WidgetUtil
                    .getRequiredWidthBoundingClientRectDouble(dropMarker) / 2;
        }

        @Override
        public void onDragUpdate(Event e) {
            if (e != null) {
                clientX = WidgetUtil.getTouchOrMouseClientX(e);
                autoScrollX = 0;
            }
            resolveDragElementHorizontalPosition(clientX);
            updateDragDropMarker(clientX);
        }

        private void updateDragDropMarker(final int clientX) {
            final double scrollLeft = getScrollLeft();
            final double cursorXCoordinate = clientX
                    - escalator.getHeader().getElement().getAbsoluteLeft();
            final Entry<Double, Integer> cellEdgeOnRight = possibleDropPositions
                    .ceilingEntry(cursorXCoordinate);
            final Entry<Double, Integer> cellEdgeOnLeft = possibleDropPositions
                    .floorEntry(cursorXCoordinate);
            final double diffToRightEdge = cellEdgeOnRight == null ? Double.MAX_VALUE
                    : cellEdgeOnRight.getKey() - cursorXCoordinate;
            final double diffToLeftEdge = cellEdgeOnLeft == null ? Double.MAX_VALUE
                    : cursorXCoordinate - cellEdgeOnLeft.getKey();

            double dropMarkerLeft = 0 - scrollLeft;
            if (diffToRightEdge > diffToLeftEdge) {
                latestColumnDropIndex = cellEdgeOnLeft.getValue();
                dropMarkerLeft += cellEdgeOnLeft.getKey();
            } else {
                latestColumnDropIndex = cellEdgeOnRight.getValue();
                dropMarkerLeft += cellEdgeOnRight.getKey();
            }

            dropMarkerLeft += autoScrollX;

            final double frozenColumnsWidth = autoScroller
                    .getFrozenColumnsWidth();
            final double rightBoundaryForDrag = getSidebarBoundaryComparedTo(dropMarkerLeft);
            final int visibleColumns = getVisibleColumns().size();

            // First check if the drop marker should move left because of the
            // sidebar opening button. this only the case if the grid is
            // scrolled to the right
            if (latestColumnDropIndex == visibleColumns
                    && rightBoundaryForDrag < dropMarkerLeft
                    && dropMarkerLeft <= escalator.getInnerWidth()) {
                dropMarkerLeft = rightBoundaryForDrag - dropMarkerWidthOffset;
            }

            // Check if the drop marker shouldn't be shown at all
            else if (dropMarkerLeft < frozenColumnsWidth
                    || dropMarkerLeft > Math.min(rightBoundaryForDrag,
                            escalator.getInnerWidth()) || dropMarkerLeft < 0) {
                dropMarkerLeft = -10000000;
            }
            dropMarker.getStyle().setLeft(dropMarkerLeft, Unit.PX);
        }

        private void resolveDragElementHorizontalPosition(final int clientX) {
            double left = clientX - table.getAbsoluteLeft();

            // Do not show the drag element beyond a spanned header cell
            // limitation
            final Double leftBound = possibleDropPositions.firstKey();
            final Double rightBound = possibleDropPositions.lastKey();
            final double scrollLeft = getScrollLeft();
            if (left + scrollLeft < leftBound) {
                left = leftBound - scrollLeft + autoScrollX;
            } else if (left + scrollLeft > rightBound) {
                left = rightBound - scrollLeft + autoScrollX;
            }

            // Do not show the drag element beyond the grid
            final double sidebarBoundary = getSidebarBoundaryComparedTo(left);
            final double gridBoundary = escalator.getInnerWidth();
            final double rightBoundary = Math
                    .min(sidebarBoundary, gridBoundary);

            // Do not show on left of the frozen columns (even if scrolled)
            final int frozenColumnsWidth = (int) autoScroller
                    .getFrozenColumnsWidth();

            left = Math.max(frozenColumnsWidth, Math.min(left, rightBoundary));

            left -= dragElement.getClientWidth() / 2;
            dragElement.getStyle().setLeft(left, Unit.PX);
        }

        private boolean isSidebarOnDraggedRow() {
            return eventCell.getRowIndex() == 0 && sidebar.isInDOM()
                    && !sidebar.isOpen();
        }

        /**
         * Returns the sidebar left coordinate, in relation to the grid. Or
         * Double.MAX_VALUE if it doesn't cause a boundary.
         */
        private double getSidebarBoundaryComparedTo(double left) {
            if (isSidebarOnDraggedRow()) {
                double absoluteLeft = left + getElement().getAbsoluteLeft();
                double sidebarLeft = sidebar.getElement().getAbsoluteLeft();
                double diff = absoluteLeft - sidebarLeft;

                if (diff > 0) {
                    return left - diff;
                }
            }
            return Double.MAX_VALUE;
        }

        @Override
        public boolean onDragStart(Event e) {
            calculatePossibleDropPositions();

            if (possibleDropPositions.isEmpty()) {
                return false;
            }

            initHeaderDragElementDOM();
            // needs to clone focus and sorting indicators too (UX)
            dragElement = DOM.clone(eventCell.getElement(), true);
            dragElement.getStyle().clearWidth();
            dropMarker.getStyle().setProperty("height",
                    dragElement.getStyle().getHeight());
            tableHeader.appendChild(dragElement);
            // mark the column being dragged for styling
            eventCell.getElement().addClassName("dragged");
            // mark the floating cell, for styling & testing
            dragElement.addClassName("dragged-column-header");

            // start the auto scroll handler
            autoScroller.setScrollArea(60);
            autoScroller.start(e, ScrollAxis.HORIZONTAL, autoScrollerCallback);
            return true;
        }

        @Override
        public void onDragEnd() {
            table.removeFromParent();
            dragElement.removeFromParent();
            eventCell.getElement().removeClassName("dragged");
        }

        @Override
        public void onDrop() {
            final int draggedColumnIndex = eventCell.getColumnIndex();
            final int colspan = header.getRow(eventCell.getRowIndex())
                    .getCell(eventCell.getColumn()).getColspan();
            if (latestColumnDropIndex != draggedColumnIndex
                    && latestColumnDropIndex != (draggedColumnIndex + colspan)) {
                List<Column<?, T>> columns = getColumns();
                List<Column<?, T>> reordered = new ArrayList<Column<?, T>>();
                if (draggedColumnIndex < latestColumnDropIndex) {
                    reordered.addAll(columns.subList(0, draggedColumnIndex));
                    reordered.addAll(columns.subList(draggedColumnIndex
                            + colspan, latestColumnDropIndex));
                    reordered.addAll(columns.subList(draggedColumnIndex,
                            draggedColumnIndex + colspan));
                    reordered.addAll(columns.subList(latestColumnDropIndex,
                            columns.size()));
                } else {
                    reordered.addAll(columns.subList(0, latestColumnDropIndex));
                    reordered.addAll(columns.subList(draggedColumnIndex,
                            draggedColumnIndex + colspan));
                    reordered.addAll(columns.subList(latestColumnDropIndex,
                            draggedColumnIndex));
                    reordered.addAll(columns.subList(draggedColumnIndex
                            + colspan, columns.size()));
                }
                reordered.remove(selectionColumn); // since setColumnOrder will
                                                   // add it anyway!

                // capture focused cell column before reorder
                Cell focusedCell = cellFocusHandler.getFocusedCell();
                if (focusedCell != null) {
                    // take hidden columns into account
                    focusedColumnIndex = getColumns().indexOf(
                            getVisibleColumn(focusedCell.getColumn()));
                }

                Column<?, T>[] array = reordered.toArray(new Column[reordered
                        .size()]);
                setColumnOrder(array);
                transferCellFocusOnDrop();
            } // else no reordering
        }

        private void transferCellFocusOnDrop() {
            final Cell focusedCell = cellFocusHandler.getFocusedCell();
            if (focusedCell != null) {
                final int focusedColumnIndexDOM = focusedCell.getColumn();
                final int focusedRowIndex = focusedCell.getRow();
                final int draggedColumnIndex = eventCell.getColumnIndex();
                // transfer focus if it was effected by the new column order
                final RowContainer rowContainer = escalator
                        .findRowContainer(focusedCell.getElement());
                if (focusedColumnIndex == draggedColumnIndex) {
                    // move with the dragged column
                    int adjustedDropIndex = latestColumnDropIndex > draggedColumnIndex ? latestColumnDropIndex - 1
                            : latestColumnDropIndex;
                    // remove hidden columns from indexing
                    adjustedDropIndex = getVisibleColumns().indexOf(
                            getColumn(adjustedDropIndex));
                    cellFocusHandler.setCellFocus(focusedRowIndex,
                            adjustedDropIndex, rowContainer);
                } else if (latestColumnDropIndex <= focusedColumnIndex
                        && draggedColumnIndex > focusedColumnIndex) {
                    cellFocusHandler.setCellFocus(focusedRowIndex,
                            focusedColumnIndexDOM + 1, rowContainer);
                } else if (latestColumnDropIndex > focusedColumnIndex
                        && draggedColumnIndex < focusedColumnIndex) {
                    cellFocusHandler.setCellFocus(focusedRowIndex,
                            focusedColumnIndexDOM - 1, rowContainer);
                }
            }
        }

        @Override
        public void onDragCancel() {
            // cancel next click so that we may prevent column sorting if
            // mouse was released on top of the dragged cell
            if (columnSortPreventRegistration == null) {
                columnSortPreventRegistration = Event
                        .addNativePreviewHandler(new NativePreviewHandler() {

                            @Override
                            public void onPreviewNativeEvent(
                                    NativePreviewEvent event) {
                                if (event.getTypeInt() == Event.ONCLICK) {
                                    event.cancel();
                                    event.getNativeEvent().preventDefault();
                                    columnSortPreventRegistration
                                            .removeHandler();
                                    columnSortPreventRegistration = null;
                                }
                            }
                        });
            }
            autoScroller.stop();
        }

        /**
         * Returns the amount of frozen columns. The selection column is always
         * considered frozen, since it can't be moved.
         */
        private int getSelectionAndFrozenColumnCount() {
            // no matter if selection column is frozen or not, it is considered
            // frozen for column dnd reorder
            if (getSelectionModel().getSelectionColumnRenderer() != null) {
                return Math.max(0, getFrozenColumnCount()) + 1;
            } else {
                return Math.max(0, getFrozenColumnCount());
            }
        }

        @SuppressWarnings("boxing")
        private void calculatePossibleDropPositions() {
            possibleDropPositions.clear();

            final int draggedColumnIndex = eventCell.getColumnIndex();
            final StaticRow<?> draggedCellRow = header.getRow(eventCell
                    .getRowIndex());
            final int draggedColumnRightIndex = draggedColumnIndex
                    + draggedCellRow.getCell(eventCell.getColumn())
                            .getColspan();
            final int frozenColumns = getSelectionAndFrozenColumnCount();
            final Range draggedCellRange = Range.between(draggedColumnIndex,
                    draggedColumnRightIndex);
            /*
             * If the dragged cell intersects with a spanned cell in any other
             * header or footer row, then the drag is limited inside that
             * spanned cell. The same rules apply: the cell can't be dropped
             * inside another spanned cell. The left and right bounds keep track
             * of the edges of the most limiting spanned cell.
             */
            int leftBound = -1;
            int rightBound = getColumnCount() + 1;

            final HashSet<Integer> unavailableColumnDropIndices = new HashSet<Integer>();
            final List<StaticRow<?>> rows = new ArrayList<StaticRow<?>>();
            rows.addAll(header.getRows());
            rows.addAll(footer.getRows());
            for (StaticRow<?> row : rows) {
                if (!row.hasSpannedCells()) {
                    continue;
                }
                final boolean isDraggedCellRow = row.equals(draggedCellRow);
                for (int cellColumnIndex = frozenColumns; cellColumnIndex < getColumnCount(); cellColumnIndex++) {
                    StaticCell cell = row.getCell(getColumn(cellColumnIndex));
                    int colspan = cell.getColspan();
                    if (colspan <= 1) {
                        continue;
                    }
                    final int cellColumnRightIndex = cellColumnIndex + colspan;
                    final Range cellRange = Range.between(cellColumnIndex,
                            cellColumnRightIndex);
                    final boolean intersects = draggedCellRange
                            .intersects(cellRange);
                    if (intersects && !isDraggedCellRow) {
                        // if the currently iterated cell is inside or same as
                        // the dragged cell, then it doesn't restrict the drag
                        if (cellRange.isSubsetOf(draggedCellRange)) {
                            cellColumnIndex = cellColumnRightIndex - 1;
                            continue;
                        }
                        /*
                         * if the dragged cell is a spanned cell and it crosses
                         * with the currently iterated cell without sharing
                         * either start or end then not possible to drag the
                         * cell.
                         */
                        if (!draggedCellRange.isSubsetOf(cellRange)) {
                            return;
                        }
                        // the spanned cell overlaps the dragged cell (but is
                        // not the dragged cell)
                        if (cellColumnIndex <= draggedColumnIndex
                                && cellColumnIndex > leftBound) {
                            leftBound = cellColumnIndex;
                        }
                        if (cellColumnRightIndex < rightBound) {
                            rightBound = cellColumnRightIndex;
                        }
                        cellColumnIndex = cellColumnRightIndex - 1;
                    }

                    else { // can't drop inside a spanned cell, or this is the
                           // dragged cell
                        while (colspan > 1) {
                            cellColumnIndex++;
                            colspan--;
                            unavailableColumnDropIndices.add(cellColumnIndex);
                        }
                    }
                }
            }

            if (leftBound == (rightBound - 1)) {
                return;
            }

            double position = autoScroller.getFrozenColumnsWidth();
            // iterate column indices and add possible drop positions
            for (int i = frozenColumns; i < getColumnCount(); i++) {
                Column<?, T> column = getColumn(i);
                if (!unavailableColumnDropIndices.contains(i)
                        && !column.isHidden()) {
                    if (leftBound != -1) {
                        if (i >= leftBound && i <= rightBound) {
                            possibleDropPositions.put(position, i);
                        }
                    } else {
                        possibleDropPositions.put(position, i);
                    }
                }
                position += column.getWidthActual();
            }

            if (leftBound == -1) {
                // add the right side of the last column as columns.size()
                possibleDropPositions.put(position, getColumnCount());
            }
        }

    };

    /**
     * Enumeration for easy setting of selection mode.
     */
    public enum SelectionMode {

        /**
         * Shortcut for {@link SelectionModelSingle}.
         */
        SINGLE {

            @Override
            protected <T> SelectionModel<T> createModel() {
                return GWT.create(SelectionModelSingle.class);
            }
        },

        /**
         * Shortcut for {@link SelectionModelMulti}.
         */
        MULTI {

            @Override
            protected <T> SelectionModel<T> createModel() {
                return GWT.create(SelectionModelMulti.class);
            }
        },

        /**
         * Shortcut for {@link SelectionModelNone}.
         */
        NONE {

            @Override
            protected <T> SelectionModel<T> createModel() {
                return GWT.create(SelectionModelNone.class);
            }
        };

        protected abstract <T> SelectionModel<T> createModel();
    }

    /**
     * Creates a new instance.
     */
    public Grid() {
        initWidget(escalator);
        getElement().setTabIndex(0);
        cellFocusHandler = new CellFocusHandler<T>(this);

        setStylePrimaryName(STYLE_NAME);

        escalator.getHeader().setEscalatorUpdater(createHeaderUpdater());
        escalator.getBody().setEscalatorUpdater(createBodyUpdater());
        escalator.getFooter().setEscalatorUpdater(createFooterUpdater());

        header.setGrid(this);
        Header.HeaderRow defaultRow = header.appendRow();
        header.setDefaultRow(defaultRow);

        footer.setGrid(this);

        editor.setGrid(this);

        setSelectionMode(SelectionMode.SINGLE);

        escalator.getBody().setSpacerUpdater(gridSpacerUpdater);

        escalator.addScrollHandler(new ScrollHandler() {
            @Override
            public void onScroll(ScrollEvent event) {
                fireEvent(new ScrollEvent());
            }
        });

        escalator
                .addRowVisibilityChangeHandler(new RowVisibilityChangeHandler() {
                    @Override
                    public void onRowVisibilityChange(
                            RowVisibilityChangeEvent event) {
                        if (dataSource != null && dataSource.size() != 0) {
                            dataIsBeingFetched = true;
                            dataSource.ensureAvailability(
                                    event.getFirstVisibleRow(),
                                    event.getVisibleRowCount());
                        }
                    }
                });

        // Default action on SelectionEvents. Refresh the body so changed
        // become visible.
        addSelectionHandler(new SelectionHandler<T>() {

            @Override
            public void onSelect(SelectionEvent<T> event) {
                refreshBody();
            }
        });

        // Sink header events and key events
        sinkEvents(getHeader().getConsumedEvents());
        sinkEvents(Arrays.asList(BrowserEvents.KEYDOWN, BrowserEvents.KEYUP,
                BrowserEvents.KEYPRESS, BrowserEvents.DBLCLICK,
                BrowserEvents.MOUSEDOWN, BrowserEvents.CLICK));

        // Make ENTER and SHIFT+ENTER in the header perform sorting
        addHeaderKeyUpHandler(new HeaderKeyUpHandler() {
            @Override
            public void onKeyUp(GridKeyUpEvent event) {
                if (event.getNativeKeyCode() != KeyCodes.KEY_ENTER) {
                    return;
                }
                if (getHeader().getRow(event.getFocusedCell().getRowIndex())
                        .isDefault()) {
                    // Only sort for enter on the default header
                    sorter.sort(event.getFocusedCell().getColumn(),
                            event.isShiftKeyDown());
                }
            }
        });

        addDataAvailableHandler(new DataAvailableHandler() {
            @Override
            public void onDataAvailable(DataAvailableEvent event) {
                dataIsBeingFetched = false;
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == this.enabled) {
            return;
        }

        this.enabled = enabled;
        getElement().setTabIndex(enabled ? 0 : -1);

        // Editor save and cancel buttons need to be disabled.
        boolean editorOpen = editor.getState() != State.INACTIVE;
        if (editorOpen) {
            editor.setGridEnabled(enabled);
        }

        sidebar.setEnabled(enabled);

        getEscalator().setScrollLocked(Direction.VERTICAL,
                !enabled || editorOpen);
        getEscalator().setScrollLocked(Direction.HORIZONTAL, !enabled);
    }

    @Override
    public void setStylePrimaryName(String style) {
        super.setStylePrimaryName(style);
        escalator.setStylePrimaryName(style);
        editor.setStylePrimaryName(style);
        sidebar.setStylePrimaryName(style + "-sidebar");
        sidebar.addStyleName("v-contextmenu");

        String rowStyle = getStylePrimaryName() + "-row";
        rowHasDataStyleName = rowStyle + "-has-data";
        rowSelectedStyleName = rowStyle + "-selected";
        rowStripeStyleName = rowStyle + "-stripe";

        cellFocusStyleName = getStylePrimaryName() + "-cell-focused";
        rowFocusStyleName = getStylePrimaryName() + "-row-focused";

        if (isAttached()) {
            refreshHeader();
            refreshBody();
            refreshFooter();
        }
    }

    /**
     * Creates the escalator updater used to update the header rows in this
     * grid. The updater is invoked when header rows or columns are added or
     * removed, or the content of existing header cells is changed.
     * 
     * @return the new header updater instance
     * 
     * @see GridHeader
     * @see Grid#getHeader()
     */
    protected EscalatorUpdater createHeaderUpdater() {
        return new StaticSectionUpdater<T>(this, header, escalator.getHeader());
    }

    /**
     * Creates the escalator updater used to update the body rows in this grid.
     * The updater is invoked when body rows or columns are added or removed,
     * the content of body cells is changed, or the body is scrolled to expose
     * previously hidden content.
     * 
     * @return the new body updater instance
     */
    protected EscalatorUpdater createBodyUpdater() {
        return new BodyUpdater<T>(this);
    }

    /**
     * Creates the escalator updater used to update the footer rows in this
     * grid. The updater is invoked when header rows or columns are added or
     * removed, or the content of existing header cells is changed.
     * 
     * @return the new footer updater instance
     * 
     * @see GridFooter
     * @see #getFooter()
     */
    protected EscalatorUpdater createFooterUpdater() {
        return new StaticSectionUpdater<T>(this, footer, escalator.getFooter());
    }

    /**
     * Refreshes header or footer rows on demand
     * 
     * @param rows
     *            The row container
     * @param firstRowIsVisible
     *            is the first row visible
     * @param isHeader
     *            <code>true</code> if we refreshing the header, else assumed
     *            the footer
     */
    private void refreshRowContainer(RowContainer rows, StaticSection<?> section) {

        // Add or Remove rows on demand
        int rowDiff = section.getVisibleRowCount() - rows.getRowCount();
        if (rowDiff > 0) {
            rows.insertRows(0, rowDiff);
        } else if (rowDiff < 0) {
            rows.removeRows(0, -rowDiff);
        }

        // Refresh all the rows
        if (rows.getRowCount() > 0) {
            rows.refreshRows(0, rows.getRowCount());
        }
    }

    /**
     * Focus a body cell by row and column index.
     * 
     * @param rowIndex
     *            index of row to focus
     * @param columnIndex
     *            index of cell to focus
     */
    void focusCell(int rowIndex, int columnIndex) {
        final Range rowRange = Range.between(0, dataSource.size());
        final Range columnRange = Range.between(0, getVisibleColumns().size());

        assert rowRange.contains(rowIndex) : "Illegal row index. Should be in range "
                + rowRange;
        assert columnRange.contains(columnIndex) : "Illegal column index. Should be in range "
                + columnRange;

        if (rowRange.contains(rowIndex) && columnRange.contains(columnIndex)) {
            cellFocusHandler.setCellFocus(rowIndex, columnIndex,
                    escalator.getBody());
            WidgetUtil.focus(getElement());
        }
    }

    /**
     * Refreshes all header rows
     */
    void refreshHeader() {
        refreshRowContainer(escalator.getHeader(), header);
    }

    /**
     * Refreshes all body rows
     */
    void refreshBody() {
        escalator.getBody().refreshRows(0, escalator.getBody().getRowCount());
    }

    /**
     * Refreshes all footer rows
     */
    void refreshFooter() {
        refreshRowContainer(escalator.getFooter(), footer);
    }

    /**
     * Adds columns as the last columns in the grid.
     * 
     * @param columns
     *            the columns to add
     */
    public void addColumns(Column<?, T>... columns) {
        int count = getColumnCount();
        for (Column<?, T> column : columns) {
            addColumn(column, count++);
        }
    }

    /**
     * Adds a column as the last column in the grid.
     * 
     * @param column
     *            the column to add
     * @return given column
     */
    public <C extends Column<?, T>> C addColumn(C column) {
        addColumn(column, getColumnCount());
        return column;
    }

    /**
     * Inserts a column into a specific position in the grid.
     * 
     * @param index
     *            the index where the column should be inserted into
     * @param column
     *            the column to add
     * @return given column
     * 
     * @throws IllegalStateException
     *             if Grid's current selection model renders a selection column,
     *             and {@code index} is 0.
     */
    public <C extends Column<?, T>> C addColumn(C column, int index) {
        if (column == selectionColumn) {
            throw new IllegalArgumentException("The selection column many "
                    + "not be added manually");
        } else if (selectionColumn != null && index == 0) {
            throw new IllegalStateException("A column cannot be inserted "
                    + "before the selection column");
        }

        addColumnSkipSelectionColumnCheck(column, index);
        return column;
    }

    private void addColumnSkipSelectionColumnCheck(Column<?, T> column,
            int index) {
        // Register column with grid
        columns.add(index, column);

        header.addColumn(column);
        footer.addColumn(column);

        // Register this grid instance with the column
        ((Column<?, T>) column).setGrid(this);

        // Grid knows about hidden columns, Escalator only knows about what is
        // visible so column indexes do not match
        if (!column.isHidden()) {
            int escalatorIndex = index;
            for (int existingColumn = 0; existingColumn < index; existingColumn++) {
                if (getColumn(existingColumn).isHidden()) {
                    escalatorIndex--;
                }
            }
            escalator.getColumnConfiguration().insertColumns(escalatorIndex, 1);
        }

        // Reapply column width
        column.reapplyWidth();

        // Sink all renderer events
        Set<String> events = new HashSet<String>();
        events.addAll(getConsumedEventsForRenderer(column.getRenderer()));

        if (column.isHidable()) {
            columnHider.updateColumnHidable(column);
        }

        sinkEvents(events);
    }

    void sinkEvents(Collection<String> events) {
        assert events != null;

        int eventsToSink = 0;
        for (String typeName : events) {
            int typeInt = Event.getTypeInt(typeName);
            if (typeInt < 0) {
                // Type not recognized by typeInt
                sinkBitlessEvent(typeName);
            } else {
                eventsToSink |= typeInt;
            }
        }

        if (eventsToSink > 0) {
            sinkEvents(eventsToSink);
        }
    }

    Renderer<?> findRenderer(FlyweightCell cell) {
        Column<?, T> column = getVisibleColumn(cell.getColumn());
        assert column != null : "Could not find column at index:"
                + cell.getColumn();
        return column.getRenderer();
    }

    /**
     * Removes a column from the grid.
     * 
     * @param column
     *            the column to remove
     */
    public void removeColumn(Column<?, T> column) {
        if (column != null && column.equals(selectionColumn)) {
            throw new IllegalArgumentException(
                    "The selection column may not be removed manually.");
        }

        removeColumnSkipSelectionColumnCheck(column);
    }

    private void removeColumnSkipSelectionColumnCheck(Column<?, T> column) {
        int columnIndex = columns.indexOf(column);

        // Remove from column configuration
        escalator.getColumnConfiguration().removeColumns(
                getVisibleColumns().indexOf(column), 1);

        updateFrozenColumns();

        header.removeColumn(column);
        footer.removeColumn(column);

        // de-register column with grid
        ((Column<?, T>) column).setGrid(null);

        columns.remove(columnIndex);

        if (column.isHidable()) {
            columnHider.removeColumnHidingToggle(column);
        }
    }

    /**
     * Returns the amount of columns in the grid.
     * <p>
     * <em>NOTE:</em> this includes the hidden columns in the count.
     * 
     * @return The number of columns in the grid
     */
    public int getColumnCount() {
        return columns.size();
    }

    /**
     * Returns a list columns in the grid, including hidden columns.
     * <p>
     * For currently visible columns, use {@link #getVisibleColumns()}.
     * 
     * @return A unmodifiable list of the columns in the grid
     */
    public List<Column<?, T>> getColumns() {
        return Collections
                .unmodifiableList(new ArrayList<Column<?, T>>(columns));
    }

    /**
     * Returns a list of the currently visible columns in the grid.
     * <p>
     * No {@link Column#isHidden() hidden} columns included.
     * 
     * @since 7.5.0
     * @return A unmodifiable list of the currently visible columns in the grid
     */
    public List<Column<?, T>> getVisibleColumns() {
        ArrayList<Column<?, T>> visible = new ArrayList<Column<?, T>>();
        for (Column<?, T> c : columns) {
            if (!c.isHidden()) {
                visible.add(c);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    /**
     * Returns a column by its index in the grid.
     * <p>
     * <em>NOTE:</em> The indexing includes hidden columns.
     * 
     * @param index
     *            the index of the column
     * @return The column in the given index
     * @throws IllegalArgumentException
     *             if the column index does not exist in the grid
     */
    public Column<?, T> getColumn(int index) throws IllegalArgumentException {
        if (index < 0 || index >= columns.size()) {
            throw new IllegalStateException("Column not found.");
        }
        return columns.get(index);
    }

    Column<?, T> getVisibleColumn(int index)
            throws IllegalArgumentException {
        List<Column<?, T>> visibleColumns = getVisibleColumns();
        if (index < 0 || index >= visibleColumns.size()) {
            throw new IllegalStateException("Column not found.");
        }
        return visibleColumns.get(index);
    }

    /**
     * Returns the header section of this grid. The default header contains a
     * single row displaying the column captions.
     * 
     * @return the header
     */
    protected Header getHeader() {
        return header;
    }

    /**
     * Gets the header row at given index.
     * 
     * @param rowIndex
     *            0 based index for row. Counted from top to bottom
     * @return header row at given index
     * @throws IllegalArgumentException
     *             if no row exists at given index
     */
    public Header.HeaderRow getHeaderRow(int rowIndex) {
        return header.getRow(rowIndex);
    }

    /**
     * Inserts a new row at the given position to the header section. Shifts the
     * row currently at that position and any subsequent rows down (adds one to
     * their indices).
     * 
     * @param index
     *            the position at which to insert the row
     * @return the new row
     * 
     * @throws IllegalArgumentException
     *             if the index is less than 0 or greater than row count
     * @see #appendHeaderRow()
     * @see #prependHeaderRow()
     * @see #removeHeaderRow(HeaderRow)
     * @see #removeHeaderRow(int)
     */
    public Header.HeaderRow addHeaderRowAt(int index) {
        return header.addRowAt(index);
    }

    /**
     * Adds a new row at the bottom of the header section.
     * 
     * @return the new row
     * @see #prependHeaderRow()
     * @see #addHeaderRowAt(int)
     * @see #removeHeaderRow(HeaderRow)
     * @see #removeHeaderRow(int)
     */
    public Header.HeaderRow appendHeaderRow() {
        return header.appendRow();
    }

    /**
     * Returns the current default row of the header section. The default row is
     * a special header row providing a user interface for sorting columns.
     * Setting a header caption for column updates cells in the default header.
     * 
     * @return the default row or null if no default row set
     */
    public Header.HeaderRow getDefaultHeaderRow() {
        return header.getDefaultRow();
    }

    /**
     * Gets the row count for the header section.
     * 
     * @return row count
     */
    public int getHeaderRowCount() {
        return header.getRowCount();
    }

    /**
     * Adds a new row at the top of the header section.
     * 
     * @return the new row
     * @see #appendHeaderRow()
     * @see #addHeaderRowAt(int)
     * @see #removeHeaderRow(HeaderRow)
     * @see #removeHeaderRow(int)
     */
    public Header.HeaderRow prependHeaderRow() {
        return header.prependRow();
    }

    /**
     * Removes the given row from the header section.
     * 
     * @param row
     *            the row to be removed
     * 
     * @throws IllegalArgumentException
     *             if the row does not exist in this section
     * @see #removeHeaderRow(int)
     * @see #addHeaderRowAt(int)
     * @see #appendHeaderRow()
     * @see #prependHeaderRow()
     */
    public void removeHeaderRow(Header.HeaderRow row) {
        header.removeRow(row);
    }

    /**
     * Removes the row at the given position from the header section.
     * 
     * @param index
     *            the position of the row
     * 
     * @throws IllegalArgumentException
     *             if no row exists at given index
     * @see #removeHeaderRow(HeaderRow)
     * @see #addHeaderRowAt(int)
     * @see #appendHeaderRow()
     * @see #prependHeaderRow()
     */
    public void removeHeaderRow(int rowIndex) {
        header.removeRow(rowIndex);
    }

    /**
     * Sets the default row of the header. The default row is a special header
     * row providing a user interface for sorting columns.
     * <p>
     * Note: Setting the default header row will reset all cell contents to
     * Column defaults.
     * 
     * @param row
     *            the new default row, or null for no default row
     * 
     * @throws IllegalArgumentException
     *             header does not contain the row
     */
    public void setDefaultHeaderRow(Header.HeaderRow row) {
        header.setDefaultRow(row);
    }

    /**
     * Sets the visibility of the header section.
     * 
     * @param visible
     *            true to show header section, false to hide
     */
    public void setHeaderVisible(boolean visible) {
        header.setVisible(visible);
    }

    /**
     * Returns the visibility of the header section.
     * 
     * @return true if visible, false otherwise.
     */
    public boolean isHeaderVisible() {
        return header.isVisible();
    }

    /* Grid Footers */

    /**
     * Returns the footer section of this grid. The default footer is empty.
     * 
     * @return the footer
     */
    protected Footer getFooter() {
        return footer;
    }

    /**
     * Gets the footer row at given index.
     * 
     * @param rowIndex
     *            0 based index for row. Counted from top to bottom
     * @return footer row at given index
     * @throws IllegalArgumentException
     *             if no row exists at given index
     */
    public Footer.FooterRow getFooterRow(int rowIndex) {
        return footer.getRow(rowIndex);
    }

    /**
     * Inserts a new row at the given position to the footer section. Shifts the
     * row currently at that position and any subsequent rows down (adds one to
     * their indices).
     * 
     * @param index
     *            the position at which to insert the row
     * @return the new row
     * 
     * @throws IllegalArgumentException
     *             if the index is less than 0 or greater than row count
     * @see #appendFooterRow()
     * @see #prependFooterRow()
     * @see #removeFooterRow(FooterRow)
     * @see #removeFooterRow(int)
     */
    public Footer.FooterRow addFooterRowAt(int index) {
        return footer.addRowAt(index);
    }

    /**
     * Adds a new row at the bottom of the footer section.
     * 
     * @return the new row
     * @see #prependFooterRow()
     * @see #addFooterRowAt(int)
     * @see #removeFooterRow(FooterRow)
     * @see #removeFooterRow(int)
     */
    public Footer.FooterRow appendFooterRow() {
        return footer.appendRow();
    }

    /**
     * Gets the row count for the footer.
     * 
     * @return row count
     */
    public int getFooterRowCount() {
        return footer.getRowCount();
    }

    /**
     * Adds a new row at the top of the footer section.
     * 
     * @return the new row
     * @see #appendFooterRow()
     * @see #addFooterRowAt(int)
     * @see #removeFooterRow(FooterRow)
     * @see #removeFooterRow(int)
     */
    public Footer.FooterRow prependFooterRow() {
        return footer.prependRow();
    }

    /**
     * Removes the given row from the footer section.
     * 
     * @param row
     *            the row to be removed
     * 
     * @throws IllegalArgumentException
     *             if the row does not exist in this section
     * @see #removeFooterRow(int)
     * @see #addFooterRowAt(int)
     * @see #appendFooterRow()
     * @see #prependFooterRow()
     */
    public void removeFooterRow(Footer.FooterRow row) {
        footer.removeRow(row);
    }

    /**
     * Removes the row at the given position from the footer section.
     * 
     * @param index
     *            the position of the row
     * 
     * @throws IllegalArgumentException
     *             if no row exists at given index
     * @see #removeFooterRow(FooterRow)
     * @see #addFooterRowAt(int)
     * @see #appendFooterRow()
     * @see #prependFooterRow()
     */
    public void removeFooterRow(int rowIndex) {
        footer.removeRow(rowIndex);
    }

    /**
     * Sets the visibility of the footer section.
     * 
     * @param visible
     *            true to show footer section, false to hide
     */
    public void setFooterVisible(boolean visible) {
        footer.setVisible(visible);
    }

    /**
     * Returns the visibility of the footer section.
     * 
     * @return true if visible, false otherwise.
     */
    public boolean isFooterVisible() {
        return footer.isVisible();
    }

    public Editor<T> getEditor() {
        return editor;
    }

    protected Escalator getEscalator() {
        return escalator;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <em>Note:</em> This method will change the widget's size in the browser
     * only if {@link #getHeightMode()} returns {@link HeightMode#CSS}.
     * 
     * @see #setHeightMode(HeightMode)
     */
    @Override
    public void setHeight(String height) {
        escalator.setHeight(height);
    }

    @Override
    public void setWidth(String width) {
        escalator.setWidth(width);
    }

    /**
     * Sets the data source used by this grid.
     * 
     * @param dataSource
     *            the data source to use, not null
     * @throws IllegalArgumentException
     *             if <code>dataSource</code> is <code>null</code>
     */
    public void setDataSource(final DataSource<T> dataSource)
            throws IllegalArgumentException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource can't be null.");
        }

        selectionModel.reset();

        if (this.dataSource != null) {
            this.dataSource.setDataChangeHandler(null);
        }

        this.dataSource = dataSource;
        dataSource.setDataChangeHandler(new DataChangeHandler() {
            @Override
            public void dataUpdated(int firstIndex, int numberOfItems) {
                escalator.getBody().refreshRows(firstIndex, numberOfItems);
            }

            @Override
            public void dataRemoved(int firstIndex, int numberOfItems) {
                escalator.getBody().removeRows(firstIndex, numberOfItems);
                Range removed = Range.withLength(firstIndex, numberOfItems);
                cellFocusHandler.rowsRemovedFromBody(removed);
            }

            @Override
            public void dataAdded(int firstIndex, int numberOfItems) {
                escalator.getBody().insertRows(firstIndex, numberOfItems);
                Range added = Range.withLength(firstIndex, numberOfItems);
                cellFocusHandler.rowsAddedToBody(added);
            }

            @Override
            public void dataAvailable(int firstIndex, int numberOfItems) {
                currentDataAvailable = Range.withLength(firstIndex,
                        numberOfItems);
                fireEvent(new DataAvailableEvent(currentDataAvailable));
            }

            @Override
            public void resetDataAndSize(int newSize) {
                RowContainer body = escalator.getBody();
                int oldSize = body.getRowCount();

                // Hide all details.
                Set<Integer> oldDetails = new HashSet<Integer>(visibleDetails);
                for (int i : oldDetails) {
                    setDetailsVisible(i, false);
                }

                if (newSize > oldSize) {
                    body.insertRows(oldSize, newSize - oldSize);
                    cellFocusHandler.rowsAddedToBody(Range.withLength(oldSize,
                            newSize - oldSize));
                } else if (newSize < oldSize) {
                    body.removeRows(newSize, oldSize - newSize);
                    cellFocusHandler.rowsRemovedFromBody(Range.withLength(
                            newSize, oldSize - newSize));
                }

                if (newSize > 0) {
                    dataIsBeingFetched = true;
                    Range visibleRowRange = escalator.getVisibleRowRange();
                    dataSource.ensureAvailability(visibleRowRange.getStart(),
                            visibleRowRange.length());
                } else {
                    // We won't expect any data more data updates, so just make
                    // the bookkeeping happy
                    dataAvailable(0, 0);
                }

                assert body.getRowCount() == newSize;
            }
        });

        int previousRowCount = escalator.getBody().getRowCount();
        if (previousRowCount != 0) {
            escalator.getBody().removeRows(0, previousRowCount);
        }

        setEscalatorSizeFromDataSource();
    }

    private void setEscalatorSizeFromDataSource() {
        assert escalator.getBody().getRowCount() == 0;

        int size = dataSource.size();
        if (size == -1 && isAttached()) {
            // Exact size is not yet known, start with some reasonable guess
            // just to get an initial backend request going
            size = getEscalator().getMaxVisibleRowCount();
        }
        if (size > 0) {
            escalator.getBody().insertRows(0, size);
        }
    }

    /**
     * Gets the {@Link DataSource} for this Grid.
     * 
     * @return the data source used by this grid
     */
    public DataSource<T> getDataSource() {
        return dataSource;
    }

    /**
     * Sets the number of frozen columns in this grid. Setting the count to 0
     * means that no data columns will be frozen, but the built-in selection
     * checkbox column will still be frozen if it's in use. Setting the count to
     * -1 will also disable the selection column.
     * <p>
     * The default value is 0.
     * 
     * @param numberOfColumns
     *            the number of columns that should be frozen
     * 
     * @throws IllegalArgumentException
     *             if the column count is < -1 or > the number of visible
     *             columns
     */
    public void setFrozenColumnCount(int numberOfColumns) {
        if (numberOfColumns < -1 || numberOfColumns > getColumnCount()) {
            throw new IllegalArgumentException(
                    "count must be between -1 and the current number of columns ("
                            + getColumnCount() + ")");
        }

        frozenColumnCount = numberOfColumns;
        updateFrozenColumns();
    }

    private void updateFrozenColumns() {
        escalator.getColumnConfiguration().setFrozenColumnCount(
                getVisibleFrozenColumnCount());
    }

    int getVisibleFrozenColumnCount() {
        int numberOfColumns = getFrozenColumnCount();

        // for the escalator the hidden columns are not in the frozen column
        // count, but for grid they are. thus need to convert the index
        for (int i = 0; i < frozenColumnCount; i++) {
            if (getColumn(i).isHidden()) {
                numberOfColumns--;
            }
        }

        if (numberOfColumns == -1) {
            numberOfColumns = 0;
        } else if (selectionColumn != null) {
            numberOfColumns++;
        }
        return numberOfColumns;
    }

    /**
     * Gets the number of frozen columns in this grid. 0 means that no data
     * columns will be frozen, but the built-in selection checkbox column will
     * still be frozen if it's in use. -1 means that not even the selection
     * column is frozen.
     * <p>
     * <em>NOTE:</em> This includes {@link Column#isHidden() hidden columns} in
     * the count.
     * 
     * @return the number of frozen columns
     */
    public int getFrozenColumnCount() {
        return frozenColumnCount;
    }

    public HandlerRegistration addRowVisibilityChangeHandler(
            RowVisibilityChangeHandler handler) {
        /*
         * Reusing Escalator's RowVisibilityChangeHandler, since a scroll
         * concept is too abstract. e.g. the event needs to be re-sent when the
         * widget is resized.
         */
        return escalator.addRowVisibilityChangeHandler(handler);
    }

    /**
     * Scrolls to a certain row, using {@link ScrollDestination#ANY}.
     * <p>
     * If the details for that row are visible, those will be taken into account
     * as well.
     * 
     * @param rowIndex
     *            zero-based index of the row to scroll to.
     * @throws IllegalArgumentException
     *             if rowIndex is below zero, or above the maximum value
     *             supported by the data source.
     */
    public void scrollToRow(int rowIndex) throws IllegalArgumentException {
        scrollToRow(rowIndex, ScrollDestination.ANY,
                GridConstants.DEFAULT_PADDING);
    }

    /**
     * Scrolls to a certain row, using user-specified scroll destination.
     * <p>
     * If the details for that row are visible, those will be taken into account
     * as well.
     * 
     * @param rowIndex
     *            zero-based index of the row to scroll to.
     * @param destination
     *            desired destination placement of scrolled-to-row. See
     *            {@link ScrollDestination} for more information.
     * @throws IllegalArgumentException
     *             if rowIndex is below zero, or above the maximum value
     *             supported by the data source.
     */
    public void scrollToRow(int rowIndex, ScrollDestination destination)
            throws IllegalArgumentException {
        scrollToRow(rowIndex, destination,
                destination == ScrollDestination.MIDDLE ? 0
                        : GridConstants.DEFAULT_PADDING);
    }

    /**
     * Scrolls to a certain row using only user-specified parameters.
     * <p>
     * If the details for that row are visible, those will be taken into account
     * as well.
     * 
     * @param rowIndex
     *            zero-based index of the row to scroll to.
     * @param destination
     *            desired destination placement of scrolled-to-row. See
     *            {@link ScrollDestination} for more information.
     * @param paddingPx
     *            number of pixels to overscroll. Behavior depends on
     *            destination.
     * @throws IllegalArgumentException
     *             if {@code destination} is {@link ScrollDestination#MIDDLE}
     *             and padding is nonzero, because having a padding on a
     *             centered row is undefined behavior, or if rowIndex is below
     *             zero or above the row count of the data source.
     */
    private void scrollToRow(int rowIndex, ScrollDestination destination,
            int paddingPx) throws IllegalArgumentException {
        int maxsize = escalator.getBody().getRowCount() - 1;

        if (rowIndex < 0) {
            throw new IllegalArgumentException("Row index (" + rowIndex
                    + ") is below zero!");
        }

        if (rowIndex > maxsize) {
            throw new IllegalArgumentException("Row index (" + rowIndex
                    + ") is above maximum (" + maxsize + ")!");
        }

        escalator.scrollToRowAndSpacer(rowIndex, destination, paddingPx);
    }

    /**
     * Scrolls to the beginning of the very first row.
     */
    public void scrollToStart() {
        scrollToRow(0, ScrollDestination.START);
    }

    /**
     * Scrolls to the end of the very last row.
     */
    public void scrollToEnd() {
        scrollToRow(escalator.getBody().getRowCount() - 1,
                ScrollDestination.END);
    }

    /**
     * Sets the vertical scroll offset.
     * 
     * @param px
     *            the number of pixels this grid should be scrolled down
     */
    public void setScrollTop(double px) {
        escalator.setScrollTop(px);
    }

    /**
     * Gets the vertical scroll offset
     * 
     * @return the number of pixels this grid is scrolled down
     */
    public double getScrollTop() {
        return escalator.getScrollTop();
    }

    /**
     * Sets the horizontal scroll offset
     * 
     * @since 7.5.0
     * @param px
     *            the number of pixels this grid should be scrolled right
     */
    public void setScrollLeft(double px) {
        escalator.setScrollLeft(px);
    }

    /**
     * Gets the horizontal scroll offset
     * 
     * @return the number of pixels this grid is scrolled to the right
     */
    public double getScrollLeft() {
        return escalator.getScrollLeft();
    }

    /**
     * Returns the height of the scrollable area in pixels.
     * 
     * @since 7.5.0
     * @return the height of the scrollable area in pixels
     */
    public double getScrollHeight() {
        return escalator.getScrollHeight();
    }

    /**
     * Returns the width of the scrollable area in pixels.
     * 
     * @since 7.5.0
     * @return the width of the scrollable area in pixels.
     */
    public double getScrollWidth() {
        return escalator.getScrollWidth();
    }

    static final Logger getLogger() {
        return Logger.getLogger(Grid.class.getName());
    }

    /**
     * Sets the number of rows that should be visible in Grid's body, while
     * {@link #getHeightMode()} is {@link HeightMode#ROW}.
     * <p>
     * If Grid is currently not in {@link HeightMode#ROW}, the given value is
     * remembered, and applied once the mode is applied.
     * 
     * @param rows
     *            The height in terms of number of rows displayed in Grid's
     *            body. If Grid doesn't contain enough rows, white space is
     *            displayed instead.
     * @throws IllegalArgumentException
     *             if {@code rows} is zero or less
     * @throws IllegalArgumentException
     *             if {@code rows} is {@link Double#isInifinite(double)
     *             infinite}
     * @throws IllegalArgumentException
     *             if {@code rows} is {@link Double#isNaN(double) NaN}
     * 
     * @see #setHeightMode(HeightMode)
     */
    public void setHeightByRows(double rows) throws IllegalArgumentException {
        escalator.setHeightByRows(rows);
    }

    /**
     * Gets the amount of rows in Grid's body that are shown, while
     * {@link #getHeightMode()} is {@link HeightMode#ROW}.
     * <p>
     * By default, it is {@value Escalator#DEFAULT_HEIGHT_BY_ROWS}.
     * 
     * @return the amount of rows that should be shown in Grid's body, while in
     *         {@link HeightMode#ROW}.
     * @see #setHeightByRows(double)
     */
    public double getHeightByRows() {
        return escalator.getHeightByRows();
    }

    /**
     * Defines the mode in which the Grid widget's height is calculated.
     * <p>
     * If {@link HeightMode#CSS} is given, Grid will respect the values given
     * via {@link #setHeight(String)}, and behave as a traditional Widget.
     * <p>
     * If {@link HeightMode#ROW} is given, Grid will make sure that the body
     * will display as many rows as {@link #getHeightByRows()} defines.
     * <em>Note:</em> If headers/footers are inserted or removed, the widget
     * will resize itself to still display the required amount of rows in its
     * body. It also takes the horizontal scrollbar into account.
     * 
     * @param heightMode
     *            the mode in to which Grid should be set
     */
    public void setHeightMode(HeightMode heightMode) {
        /*
         * This method is a workaround for the fact that Vaadin re-applies
         * widget dimensions (height/width) on each state change event. The
         * original design was to have setHeight an setHeightByRow be equals,
         * and whichever was called the latest was considered in effect.
         * 
         * But, because of Vaadin always calling setHeight on the widget, this
         * approach doesn't work.
         */

        escalator.setHeightMode(heightMode);
    }

    /**
     * Returns the current {@link HeightMode} the Grid is in.
     * <p>
     * Defaults to {@link HeightMode#CSS}.
     * 
     * @return the current HeightMode
     */
    public HeightMode getHeightMode() {
        return escalator.getHeightMode();
    }

    private Set<String> getConsumedEventsForRenderer(Renderer<?> renderer) {
        Set<String> events = new HashSet<String>();
        if (renderer instanceof ComplexRenderer) {
            Collection<String> consumedEvents = ((ComplexRenderer<?>) renderer)
                    .getConsumedEvents();
            if (consumedEvents != null) {
                events.addAll(consumedEvents);
            }
        }
        return events;
    }

    @Override
    public void onBrowserEvent(Event event) {
        if (!isEnabled()) {
            return;
        }

        String eventType = event.getType();

        if (eventType.equals(BrowserEvents.FOCUS)
                || eventType.equals(BrowserEvents.BLUR)) {
            super.onBrowserEvent(event);
            return;
        }

        EventTarget target = event.getEventTarget();

        if (!Element.is(target) || isOrContainsInSpacer(Element.as(target))) {
            return;
        }

        Element e = Element.as(target);
        RowContainer container = escalator.findRowContainer(e);
        Cell cell;

        if (container == null) {
            if (eventType.equals(BrowserEvents.KEYDOWN)
                    || eventType.equals(BrowserEvents.KEYUP)
                    || eventType.equals(BrowserEvents.KEYPRESS)) {
                cell = cellFocusHandler.getFocusedCell();
                container = cellFocusHandler.containerWithFocus;
            } else {
                // Click might be in an editor cell, should still map.
                if (editor.editorOverlay != null
                        && editor.editorOverlay.isOrHasChild(e)) {
                    container = escalator.getBody();
                    int rowIndex = editor.getRow();
                    int colIndex = editor.getElementColumn(e);

                    if (colIndex < 0) {
                        // Click in editor, but not for any column.
                        return;
                    }

                    TableCellElement cellElement = container
                            .getRowElement(rowIndex).getCells()
                            .getItem(colIndex);

                    cell = new Cell(rowIndex, colIndex, cellElement);
                } else {
                    if (escalator.getElement().isOrHasChild(e)) {
                        eventCell.set(new Cell(-1, -1, null), Section.BODY);
                        // Fire native events.
                        super.onBrowserEvent(event);
                    }
                    return;
                }
            }
        } else {
            cell = container.getCell(e);
            if (eventType.equals(BrowserEvents.MOUSEDOWN)) {
                cellOnPrevMouseDown = cell;
            } else if (cell == null && eventType.equals(BrowserEvents.CLICK)) {
                /*
                 * Chrome has an interesting idea on click targets (see
                 * cellOnPrevMouseDown javadoc). Firefox, on the other hand, has
                 * the mousedown target as the click target.
                 */
                cell = cellOnPrevMouseDown;
            }
        }

        assert cell != null : "received " + eventType
                + "-event with a null cell target";
        eventCell.set(cell, getSectionFromContainer(container));

        // Editor can steal focus from Grid and is still handled
        if (isEditorEnabled() && handleEditorEvent(event, container)) {
            return;
        }

        // Fire GridKeyEvents and GridClickEvents. Pass the event to escalator.
        super.onBrowserEvent(event);

        if (!isElementInChildWidget(e)) {

            if (handleHeaderCellDragStartEvent(event, container)) {
                return;
            }

            // Sorting through header Click / KeyUp
            if (handleHeaderDefaultRowEvent(event, container)) {
                return;
            }

            if (handleRendererEvent(event, container)) {
                return;
            }

            if (handleCellFocusEvent(event, container)) {
                return;
            }
        }
    }

    private Section getSectionFromContainer(RowContainer container) {
        assert container != null : "RowContainer should not be null";

        if (container == escalator.getBody()) {
            return Section.BODY;
        } else if (container == escalator.getFooter()) {
            return Section.FOOTER;
        } else if (container == escalator.getHeader()) {
            return Section.HEADER;
        }
        assert false : "RowContainer was not header, footer or body.";
        return null;
    }

    private boolean isOrContainsInSpacer(Node node) {
        Node n = node;
        while (n != null && n != getElement()) {
            boolean isElement = Element.is(n);
            if (isElement) {
                String className = Element.as(n).getClassName();
                if (className.contains(getStylePrimaryName() + "-spacer")) {
                    return true;
                }
            }
            n = n.getParentNode();
        }
        return false;
    }

    public boolean isElementInChildWidget(Element e) {
        Widget w = WidgetUtil.findWidget(e, null);

        if (w == this) {
            return false;
        }

        /*
         * If e is directly inside this grid, but the grid is wrapped in a
         * Composite, findWidget is not going to find this, only the wrapper.
         * Thus we need to check its parents to see if we encounter this; if we
         * don't, the found widget is actually a parent of this, so we should
         * return false.
         */
        while (w != null && w != this) {
            w = w.getParent();
        }
        return w != null;
    }

    private boolean handleEditorEvent(Event event, RowContainer container) {
        Widget w;
        if (editor.focusedColumnIndex < 0) {
            w = null;
        } else {
            w = editor.getWidget(getColumn(editor.focusedColumnIndex));
        }

        EditorDomEvent<T> editorEvent = new EditorDomEvent<T>(event,
                getEventCell(), w);

        return getEditor().getEventHandler().handleEvent(editorEvent);
    }

    private boolean handleRendererEvent(Event event, RowContainer container) {

        if (container == escalator.getBody()) {
            Column<?, T> gridColumn = eventCell.getColumn();
            boolean enterKey = event.getType().equals(BrowserEvents.KEYDOWN)
                    && event.getKeyCode() == KeyCodes.KEY_ENTER;
            boolean doubleClick = event.getType()
                    .equals(BrowserEvents.DBLCLICK);

            if (gridColumn.getRenderer() instanceof ComplexRenderer) {
                ComplexRenderer<?> cplxRenderer = (ComplexRenderer<?>) gridColumn
                        .getRenderer();
                if (cplxRenderer.getConsumedEvents().contains(event.getType())) {
                    if (cplxRenderer.onBrowserEvent(eventCell, event)) {
                        return true;
                    }
                }

                // Calls onActivate if KeyDown and Enter or double click
                if ((enterKey || doubleClick)
                        && cplxRenderer.onActivate(eventCell)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleCellFocusEvent(Event event, RowContainer container) {
        Collection<String> navigation = cellFocusHandler.getNavigationEvents();
        if (navigation.contains(event.getType())) {
            cellFocusHandler.handleNavigationEvent(event, eventCell);
        }
        return false;
    }

    private boolean handleHeaderCellDragStartEvent(Event event,
            RowContainer container) {
        if (!isColumnReorderingAllowed()) {
            return false;
        }
        if (container != escalator.getHeader()) {
            return false;
        }
        if (eventCell.getColumnIndex() < escalator.getColumnConfiguration()
                .getFrozenColumnCount()) {
            return false;
        }

        if (event.getTypeInt() == Event.ONMOUSEDOWN
                && event.getButton() == NativeEvent.BUTTON_LEFT
                || event.getTypeInt() == Event.ONTOUCHSTART) {
            dndHandler.onDragStartOnDraggableElement(event,
                    headerCellDndCallback);
            event.preventDefault();
            event.stopPropagation();
            return true;
        }
        return false;
    }

    private Point rowEventTouchStartingPoint;
    CellStyleGenerator<T> cellStyleGenerator;
    RowStyleGenerator<T> rowStyleGenerator;
    RowReference<T> rowReference = new RowReference<T>(this);
    CellReference<T> cellReference = new CellReference<T>(rowReference);
    RendererCellReference rendererCellReference = new RendererCellReference(
            (RowReference<Object>) rowReference);

    private boolean handleHeaderDefaultRowEvent(Event event,
            RowContainer container) {
        if (container != escalator.getHeader()) {
            return false;
        }
        if (!getHeader().getRow(eventCell.getRowIndex()).isDefault()) {
            return false;
        }
        if (!eventCell.getColumn().isSortable()) {
            // Only handle sorting events if the column is sortable
            return false;
        }

        if (BrowserEvents.MOUSEDOWN.equals(event.getType())
                && event.getShiftKey()) {
            // Don't select text when shift clicking on a header.
            event.preventDefault();
        }

        if (BrowserEvents.TOUCHSTART.equals(event.getType())) {
            if (event.getTouches().length() > 1) {
                return false;
            }

            event.preventDefault();

            Touch touch = event.getChangedTouches().get(0);
            rowEventTouchStartingPoint = new Point(touch.getClientX(),
                    touch.getClientY());

            sorter.sortAfterDelay(GridConstants.LONG_TAP_DELAY, true);

            return true;

        } else if (BrowserEvents.TOUCHMOVE.equals(event.getType())) {
            if (event.getTouches().length() > 1) {
                return false;
            }

            event.preventDefault();

            Touch touch = event.getChangedTouches().get(0);
            double diffX = Math.abs(touch.getClientX()
                    - rowEventTouchStartingPoint.getX());
            double diffY = Math.abs(touch.getClientY()
                    - rowEventTouchStartingPoint.getY());

            // Cancel long tap if finger strays too far from
            // starting point
            if (diffX > GridConstants.LONG_TAP_THRESHOLD
                    || diffY > GridConstants.LONG_TAP_THRESHOLD) {
                sorter.cancelDelayedSort();
            }

            return true;

        } else if (BrowserEvents.TOUCHEND.equals(event.getType())) {
            if (event.getTouches().length() > 1) {
                return false;
            }

            if (sorter.isDelayedSortScheduled()) {
                // Not a long tap yet, perform single sort
                sorter.cancelDelayedSort();
                sorter.sort(eventCell.getColumn(), false);
            }

            return true;

        } else if (BrowserEvents.TOUCHCANCEL.equals(event.getType())) {
            if (event.getTouches().length() > 1) {
                return false;
            }

            sorter.cancelDelayedSort();

            return true;

        } else if (BrowserEvents.CLICK.equals(event.getType())) {

            sorter.sort(eventCell.getColumn(), event.getShiftKey());

            // Click events should go onward to cell focus logic
            return false;
        } else {
            return false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public com.google.gwt.user.client.Element getSubPartElement(String subPart) {

        /*
         * handles details[] (translated to spacer[] for Escalator), cell[],
         * header[] and footer[]
         */

        // "#header[0][0]/DRAGhANDLE"
        Element escalatorElement = escalator.getSubPartElement(subPart
                .replaceFirst("^details\\[", "spacer["));

        if (escalatorElement != null) {

            int detailIdx = subPart.indexOf("/");
            if (detailIdx > 0) {
                String detail = subPart.substring(detailIdx + 1);
                getLogger().severe(
                        "Looking up detail from index " + detailIdx
                                + " onward: \"" + detail + "\"");
                if (detail.equalsIgnoreCase("content")) {
                    // XXX: Fix this to look up by class name!
                    return DOM.asOld(Element.as(escalatorElement.getChild(0)));
                }
                if (detail.equalsIgnoreCase("draghandle")) {
                    // XXX: Fix this to look up by class name!
                    return DOM.asOld(Element.as(escalatorElement.getChild(1)));
                }
            }

            return DOM.asOld(escalatorElement);
        }

        SubPartArguments args = SubPartArguments.create(subPart);
        Element editor = getSubPartElementEditor(args);
        if (editor != null) {
            return DOM.asOld(editor);
        }

        return null;
    }

    private Element getSubPartElementEditor(SubPartArguments args) {

        if (!args.getType().equalsIgnoreCase("editor")
                || editor.getState() != State.ACTIVE) {
            return null;
        }

        if (args.getIndicesLength() == 0) {
            return editor.editorOverlay;
        } else if (args.getIndicesLength() == 1) {
            int index = args.getIndex(0);
            if (index >= columns.size()) {
                return null;
            }

            escalator.scrollToColumn(index, ScrollDestination.ANY, 0);
            Widget widget = editor.getWidget(columns.get(index));

            if (widget != null) {
                return widget.getElement();
            }

            // No widget for the column.
            return null;
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getSubPartName(com.google.gwt.user.client.Element subElement) {

        String escalatorStructureName = escalator.getSubPartName(subElement);
        if (escalatorStructureName != null) {
            return escalatorStructureName.replaceFirst("^spacer", "details");
        }

        String editorName = getSubPartNameEditor(subElement);
        if (editorName != null) {
            return editorName;
        }

        return null;
    }

    private String getSubPartNameEditor(Element subElement) {

        if (editor.getState() != State.ACTIVE
                || !editor.editorOverlay.isOrHasChild(subElement)) {
            return null;
        }

        int i = 0;
        for (Column<?, T> column : columns) {
            if (editor.getWidget(column).getElement().isOrHasChild(subElement)) {
                return "editor[" + i + "]";
            }
            ++i;
        }

        return "editor";
    }

    private void setSelectColumnRenderer(
            final Renderer<Boolean> selectColumnRenderer) {
        if (this.selectColumnRenderer == selectColumnRenderer) {
            return;
        }

        if (this.selectColumnRenderer != null) {
            if (this.selectColumnRenderer instanceof ComplexRenderer) {
                // End of Life for the old selection column renderer.
                ((ComplexRenderer<?>) this.selectColumnRenderer).destroy();
            }

            // Clear field so frozen column logic in the remove method knows
            // what to do
            Column<?, T> colToRemove = selectionColumn;
            selectionColumn = null;
            removeColumnSkipSelectionColumnCheck(colToRemove);
            cellFocusHandler.offsetRangeBy(-1);
        }

        this.selectColumnRenderer = selectColumnRenderer;

        if (selectColumnRenderer != null) {
            cellFocusHandler.offsetRangeBy(1);
            selectionColumn = new SelectionColumn<T>(this, selectColumnRenderer);

            addColumnSkipSelectionColumnCheck(selectionColumn, 0);
            selectionColumn.initDone();
        } else {
            selectionColumn = null;
            refreshBody();
        }

        updateFrozenColumns();
    }

    /**
     * Sets the current selection model.
     * <p>
     * This function will call {@link SelectionModel#setGrid(Grid)}.
     * 
     * @param selectionModel
     *            a selection model implementation.
     * @throws IllegalArgumentException
     *             if selection model argument is null
     */
    public void setSelectionModel(SelectionModel<T> selectionModel) {

        if (selectionModel == null) {
            throw new IllegalArgumentException("Selection model can't be null");
        }

        if (this.selectionModel != null) {
            // Detach selection model from Grid.
            this.selectionModel.setGrid(null);
        }

        this.selectionModel = selectionModel;
        selectionModel.setGrid(this);
        setSelectColumnRenderer(this.selectionModel
                .getSelectionColumnRenderer());

        // Refresh rendered rows to update selection, if it has changed
        refreshBody();
    }

    /**
     * Gets a reference to the current selection model.
     * 
     * @return the currently used SelectionModel instance.
     */
    public SelectionModel<T> getSelectionModel() {
        return selectionModel;
    }

    /**
     * Sets current selection mode.
     * <p>
     * This is a shorthand method for {@link Grid#setSelectionModel}.
     * 
     * @param mode
     *            a selection mode value
     * @see {@link SelectionMode}.
     */
    public void setSelectionMode(SelectionMode mode) {
        SelectionModel<T> model = mode.createModel();
        setSelectionModel(model);
    }

    /**
     * Test if a row is selected.
     * 
     * @param row
     *            a row object
     * @return true, if the current selection model considers the provided row
     *         object selected.
     */
    public boolean isSelected(T row) {
        return selectionModel.isSelected(row);
    }

    /**
     * Select a row using the current selection model.
     * <p>
     * Only selection models implementing {@link SelectionModel.Single} and
     * {@link SelectionModel.Multi} are supported; for anything else, an
     * exception will be thrown.
     * 
     * @param row
     *            a row object
     * @return <code>true</code> iff the current selection changed
     * @throws IllegalStateException
     *             if the current selection model is not an instance of
     *             {@link SelectionModel.Single} or {@link SelectionModel.Multi}
     */
    public boolean select(T row) {
        if (selectionModel instanceof SelectionModel.Single<?>) {
            return ((SelectionModel.Single<T>) selectionModel).select(row);
        } else if (selectionModel instanceof SelectionModel.Multi<?>) {
            return ((SelectionModel.Multi<T>) selectionModel)
                    .select(Collections.singleton(row));
        } else {
            throw new IllegalStateException("Unsupported selection model");
        }
    }

    /**
     * Deselect a row using the current selection model.
     * <p>
     * Only selection models implementing {@link SelectionModel.Single} and
     * {@link SelectionModel.Multi} are supported; for anything else, an
     * exception will be thrown.
     * 
     * @param row
     *            a row object
     * @return <code>true</code> iff the current selection changed
     * @throws IllegalStateException
     *             if the current selection model is not an instance of
     *             {@link SelectionModel.Single} or {@link SelectionModel.Multi}
     */
    public boolean deselect(T row) {
        if (selectionModel instanceof SelectionModel.Single<?>) {
            return ((SelectionModel.Single<T>) selectionModel).deselect(row);
        } else if (selectionModel instanceof SelectionModel.Multi<?>) {
            return ((SelectionModel.Multi<T>) selectionModel)
                    .deselect(Collections.singleton(row));
        } else {
            throw new IllegalStateException("Unsupported selection model");
        }
    }

    /**
     * Deselect all rows using the current selection model.
     * 
     * @param row
     *            a row object
     * @return <code>true</code> iff the current selection changed
     * @throws IllegalStateException
     *             if the current selection model is not an instance of
     *             {@link SelectionModel.Single} or {@link SelectionModel.Multi}
     */
    public boolean deselectAll() {
        if (selectionModel instanceof SelectionModel.Single<?>) {
            Single<T> single = ((SelectionModel.Single<T>) selectionModel);
            if (single.getSelectedRow() != null) {
                return single.deselect(single.getSelectedRow());
            } else {
                return false;
            }
        } else if (selectionModel instanceof SelectionModel.Multi<?>) {
            return ((SelectionModel.Multi<T>) selectionModel).deselectAll();
        } else {
            throw new IllegalStateException("Unsupported selection model");
        }
    }

    /**
     * Gets last selected row from the current SelectionModel.
     * <p>
     * Only selection models implementing {@link SelectionModel.Single} are
     * valid for this method; for anything else, use the
     * {@link Grid#getSelectedRows()} method.
     * 
     * @return a selected row reference, or null, if no row is selected
     * @throws IllegalStateException
     *             if the current selection model is not an instance of
     *             {@link SelectionModel.Single}
     */
    public T getSelectedRow() {
        if (selectionModel instanceof SelectionModel.Single<?>) {
            return ((SelectionModel.Single<T>) selectionModel).getSelectedRow();
        } else {
            throw new IllegalStateException(
                    "Unsupported selection model; can not get single selected row");
        }
    }

    /**
     * Gets currently selected rows from the current selection model.
     * 
     * @return a non-null collection containing all currently selected rows.
     */
    public Collection<T> getSelectedRows() {
        return selectionModel.getSelectedRows();
    }

    @Override
    public HandlerRegistration addSelectionHandler(
            final SelectionHandler<T> handler) {
        return addHandler(handler, SelectionEvent.getType());
    }

    /**
     * Sets the current sort order using the fluid Sort API. Read the
     * documentation for {@link Sort} for more information.
     * 
     * @param s
     *            a sort instance
     */
    public void sort(Sort s) {
        setSortOrder(s.build());
    }

    /**
     * Sorts the Grid data in ascending order along one column.
     * 
     * @param column
     *            a grid column reference
     */
    public <C> void sort(Column<C, T> column) {
        sort(column, SortDirection.ASCENDING);
    }

    /**
     * Sorts the Grid data along one column.
     * 
     * @param column
     *            a grid column reference
     * @param direction
     *            a sort direction value
     */
    public <C> void sort(Column<C, T> column, SortDirection direction) {
        sort(Sort.by(column, direction));
    }

    /**
     * Sets the sort order to use. Setting this causes the Grid to re-sort
     * itself.
     * 
     * @param order
     *            a sort order list. If set to null, the sort order is cleared.
     */
    public void setSortOrder(List<SortOrder> order) {
        setSortOrder(order, false);
    }

    /**
     * Clears the sort order and indicators without re-sorting.
     */
    void clearSortOrder() {
        sortOrder.clear();
        refreshHeader();
    }

    private void setSortOrder(List<SortOrder> order, boolean userOriginated) {
        if (order != sortOrder) {
            sortOrder.clear();
            if (order != null) {
                sortOrder.addAll(order);
            }
        }
        sort(userOriginated);
    }

    /**
     * Get a copy of the current sort order array.
     * 
     * @return a copy of the current sort order array
     */
    public List<SortOrder> getSortOrder() {
        return Collections.unmodifiableList(sortOrder);
    }

    /**
     * Finds the sorting order for this column
     */
    SortOrder getSortOrder(Column<?, ?> column) {
        for (SortOrder order : getSortOrder()) {
            if (order.getColumn() == column) {
                return order;
            }
        }
        return null;
    }

    /**
     * Register a GWT event handler for a sorting event. This handler gets
     * called whenever this Grid needs its data source to provide data sorted in
     * a specific order.
     * 
     * @param handler
     *            a sort event handler
     * @return the registration for the event
     */
    public HandlerRegistration addSortHandler(SortHandler<T> handler) {
        return addHandler(handler, SortEvent.getType());
    }

    /**
     * Register a GWT event handler for a select all event. This handler gets
     * called whenever Grid needs all rows selected.
     * 
     * @param handler
     *            a select all event handler
     */
    public HandlerRegistration addSelectAllHandler(SelectAllHandler<T> handler) {
        return addHandler(handler, SelectAllEvent.getType());
    }

    /**
     * Register a GWT event handler for a data available event. This handler
     * gets called whenever the {@link DataSource} for this Grid has new data
     * available.
     * <p>
     * This handle will be fired with the current available data after
     * registration is done.
     * 
     * @param handler
     *            a data available event handler
     * @return the registartion for the event
     */
    public HandlerRegistration addDataAvailableHandler(
            final DataAvailableHandler handler) {
        // Deferred call to handler with current row range
        Scheduler.get().scheduleFinally(new ScheduledCommand() {
            @Override
            public void execute() {
                if (!dataIsBeingFetched) {
                    handler.onDataAvailable(new DataAvailableEvent(
                            currentDataAvailable));
                }
            }
        });
        return addHandler(handler, DataAvailableEvent.TYPE);
    }

    /**
     * Register a BodyKeyDownHandler to this Grid. The event for this handler is
     * fired when a KeyDown event occurs while cell focus is in the Body of this
     * Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addBodyKeyDownHandler(BodyKeyDownHandler handler) {
        return addHandler(handler, keyDown.getAssociatedType());
    }

    /**
     * Register a BodyKeyUpHandler to this Grid. The event for this handler is
     * fired when a KeyUp event occurs while cell focus is in the Body of this
     * Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addBodyKeyUpHandler(BodyKeyUpHandler handler) {
        return addHandler(handler, keyUp.getAssociatedType());
    }

    /**
     * Register a BodyKeyPressHandler to this Grid. The event for this handler
     * is fired when a KeyPress event occurs while cell focus is in the Body of
     * this Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addBodyKeyPressHandler(
            BodyKeyPressHandler handler) {
        return addHandler(handler, keyPress.getAssociatedType());
    }

    /**
     * Register a HeaderKeyDownHandler to this Grid. The event for this handler
     * is fired when a KeyDown event occurs while cell focus is in the Header of
     * this Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addHeaderKeyDownHandler(
            HeaderKeyDownHandler handler) {
        return addHandler(handler, keyDown.getAssociatedType());
    }

    /**
     * Register a HeaderKeyUpHandler to this Grid. The event for this handler is
     * fired when a KeyUp event occurs while cell focus is in the Header of this
     * Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addHeaderKeyUpHandler(HeaderKeyUpHandler handler) {
        return addHandler(handler, keyUp.getAssociatedType());
    }

    /**
     * Register a HeaderKeyPressHandler to this Grid. The event for this handler
     * is fired when a KeyPress event occurs while cell focus is in the Header
     * of this Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addHeaderKeyPressHandler(
            HeaderKeyPressHandler handler) {
        return addHandler(handler, keyPress.getAssociatedType());
    }

    /**
     * Register a FooterKeyDownHandler to this Grid. The event for this handler
     * is fired when a KeyDown event occurs while cell focus is in the Footer of
     * this Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addFooterKeyDownHandler(
            FooterKeyDownHandler handler) {
        return addHandler(handler, keyDown.getAssociatedType());
    }

    /**
     * Register a FooterKeyUpHandler to this Grid. The event for this handler is
     * fired when a KeyUp event occurs while cell focus is in the Footer of this
     * Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addFooterKeyUpHandler(FooterKeyUpHandler handler) {
        return addHandler(handler, keyUp.getAssociatedType());
    }

    /**
     * Register a FooterKeyPressHandler to this Grid. The event for this handler
     * is fired when a KeyPress event occurs while cell focus is in the Footer
     * of this Grid.
     * 
     * @param handler
     *            the key handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addFooterKeyPressHandler(
            FooterKeyPressHandler handler) {
        return addHandler(handler, keyPress.getAssociatedType());
    }

    /**
     * Register a BodyClickHandler to this Grid. The event for this handler is
     * fired when a Click event occurs in the Body of this Grid.
     * 
     * @param handler
     *            the click handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addBodyClickHandler(BodyClickHandler handler) {
        return addHandler(handler, clickEvent.getAssociatedType());
    }

    /**
     * Register a HeaderClickHandler to this Grid. The event for this handler is
     * fired when a Click event occurs in the Header of this Grid.
     * 
     * @param handler
     *            the click handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addHeaderClickHandler(HeaderClickHandler handler) {
        return addHandler(handler, clickEvent.getAssociatedType());
    }

    /**
     * Register a FooterClickHandler to this Grid. The event for this handler is
     * fired when a Click event occurs in the Footer of this Grid.
     * 
     * @param handler
     *            the click handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addFooterClickHandler(FooterClickHandler handler) {
        return addHandler(handler, clickEvent.getAssociatedType());
    }

    /**
     * Register a BodyDoubleClickHandler to this Grid. The event for this
     * handler is fired when a double click event occurs in the Body of this
     * Grid.
     * 
     * @param handler
     *            the double click handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addBodyDoubleClickHandler(
            BodyDoubleClickHandler handler) {
        return addHandler(handler, doubleClickEvent.getAssociatedType());
    }

    /**
     * Register a HeaderDoubleClickHandler to this Grid. The event for this
     * handler is fired when a double click event occurs in the Header of this
     * Grid.
     * 
     * @param handler
     *            the double click handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addHeaderDoubleClickHandler(
            HeaderDoubleClickHandler handler) {
        return addHandler(handler, doubleClickEvent.getAssociatedType());
    }

    /**
     * Register a FooterDoubleClickHandler to this Grid. The event for this
     * handler is fired when a double click event occurs in the Footer of this
     * Grid.
     * 
     * @param handler
     *            the double click handler to register
     * @return the registration for the event
     */
    public HandlerRegistration addFooterDoubleClickHandler(
            FooterDoubleClickHandler handler) {
        return addHandler(handler, doubleClickEvent.getAssociatedType());
    }

    /**
     * Register a column reorder handler to this Grid. The event for this
     * handler is fired when the Grid's columns are reordered.
     * 
     * @since 7.5.0
     * @param handler
     *            the handler for the event
     * @return the registration for the event
     */
    public HandlerRegistration addColumnReorderHandler(
            ColumnReorderHandler<T> handler) {
        return addHandler(handler, ColumnReorderEvent.getType());
    }

    /**
     * Register a column visibility change handler to this Grid. The event for
     * this handler is fired when the Grid's columns change visibility.
     * 
     * @since 7.5.0
     * @param handler
     *            the handler for the event
     * @return the registration for the event
     */
    public HandlerRegistration addColumnVisibilityChangeHandler(
            ColumnVisibilityChangeHandler<T> handler) {
        return addHandler(handler, ColumnVisibilityChangeEvent.getType());
    }

    /**
     * Register a column resize handler to this Grid. The event for this handler
     * is fired when the Grid's columns are resized.
     * 
     * @since 7.6
     * @param handler
     *            the handler for the event
     * @return the registration for the event
     */
    public HandlerRegistration addColumnResizeHandler(
            ColumnResizeHandler<T> handler) {
        return addHandler(handler, ColumnResizeEvent.getType());
    }

    /**
     * Apply sorting to data source.
     */
    void sort(boolean userOriginated) {
        refreshHeader();
        fireEvent(new SortEvent<T>(this,
                Collections.unmodifiableList(sortOrder), userOriginated));
    }

    int getLastVisibleRowIndex() {
        int lastRowIndex = escalator.getVisibleRowRange().getEnd();
        int footerTop = escalator.getFooter().getElement().getAbsoluteTop();
        Element lastRow;

        do {
            lastRow = escalator.getBody().getRowElement(--lastRowIndex);
        } while (lastRow.getAbsoluteTop() > footerTop);

        return lastRowIndex;
    }

    int getFirstVisibleRowIndex() {
        int firstRowIndex = escalator.getVisibleRowRange().getStart();
        int headerBottom = escalator.getHeader().getElement()
                .getAbsoluteBottom();
        Element firstRow = escalator.getBody().getRowElement(firstRowIndex);

        while (firstRow.getAbsoluteBottom() < headerBottom) {
            firstRow = escalator.getBody().getRowElement(++firstRowIndex);
        }

        return firstRowIndex;
    }

    /**
     * Adds a scroll handler to this grid
     * 
     * @param handler
     *            the scroll handler to add
     * @return a handler registration for the registered scroll handler
     */
    public HandlerRegistration addScrollHandler(ScrollHandler handler) {
        return addHandler(handler, ScrollEvent.TYPE);
    }

    @Override
    public boolean isWorkPending() {
        return escalator.isWorkPending() || dataIsBeingFetched
                || autoColumnWidthsRecalculator.isScheduled()
                || editor.isWorkPending();
    }

    /**
     * Returns whether columns can be reordered with drag and drop.
     * 
     * @since 7.5.0
     * @return <code>true</code> if columns can be reordered, false otherwise
     */
    public boolean isColumnReorderingAllowed() {
        return columnReorderingAllowed;
    }

    /**
     * Sets whether column reordering with drag and drop is allowed or not.
     * 
     * @since 7.5.0
     * @param columnReorderingAllowed
     *            specifies whether column reordering is allowed
     */
    public void setColumnReorderingAllowed(boolean columnReorderingAllowed) {
        this.columnReorderingAllowed = columnReorderingAllowed;
    }

    /**
     * Sets a new column order for the grid. All columns which are not ordered
     * here will remain in the order they were before as the last columns of
     * grid.
     * 
     * @param orderedColumns
     *            array of columns in wanted order
     */
    public void setColumnOrder(Column<?, T>... orderedColumns) {
        ColumnConfiguration conf = getEscalator().getColumnConfiguration();

        // Trigger ComplexRenderer.destroy for old content
        conf.removeColumns(0, conf.getColumnCount());

        List<Column<?, T>> newOrder = new ArrayList<Column<?, T>>();
        if (selectionColumn != null) {
            newOrder.add(selectionColumn);
        }

        int i = 0;
        for (Column<?, T> column : orderedColumns) {
            if (columns.contains(column)) {
                newOrder.add(column);
                ++i;
            } else {
                throw new IllegalArgumentException("Given column at index " + i
                        + " does not exist in Grid");
            }
        }

        if (columns.size() != newOrder.size()) {
            columns.removeAll(newOrder);
            newOrder.addAll(columns);
        }
        columns = newOrder;

        List<Column<?, T>> visibleColumns = getVisibleColumns();

        // Do ComplexRenderer.init and render new content
        conf.insertColumns(0, visibleColumns.size());

        // Number of frozen columns should be kept same #16901
        updateFrozenColumns();

        // Update column widths.
        for (Column<?, T> column : columns) {
            column.reapplyWidth();
        }

        // Recalculate all the colspans
        for (Header.HeaderRow row : header.getRows()) {
            row.calculateColspans();
        }
        for (Footer.FooterRow row : footer.getRows()) {
            row.calculateColspans();
        }

        columnHider.updateTogglesOrder();

        fireEvent(new ColumnReorderEvent<T>());
    }

    /**
     * Sets the style generator that is used for generating styles for cells
     * 
     * @param cellStyleGenerator
     *            the cell style generator to set, or <code>null</code> to
     *            remove a previously set generator
     */
    public void setCellStyleGenerator(CellStyleGenerator<T> cellStyleGenerator) {
        this.cellStyleGenerator = cellStyleGenerator;
        refreshBody();
    }

    /**
     * Gets the style generator that is used for generating styles for cells
     * 
     * @return the cell style generator, or <code>null</code> if no generator is
     *         set
     */
    public CellStyleGenerator<T> getCellStyleGenerator() {
        return cellStyleGenerator;
    }

    /**
     * Sets the style generator that is used for generating styles for rows
     * 
     * @param rowStyleGenerator
     *            the row style generator to set, or <code>null</code> to remove
     *            a previously set generator
     */
    public void setRowStyleGenerator(RowStyleGenerator<T> rowStyleGenerator) {
        this.rowStyleGenerator = rowStyleGenerator;
        refreshBody();
    }

    /**
     * Gets the style generator that is used for generating styles for rows
     * 
     * @return the row style generator, or <code>null</code> if no generator is
     *         set
     */
    public RowStyleGenerator<T> getRowStyleGenerator() {
        return rowStyleGenerator;
    }

    static void setCustomStyleName(Element element, String styleName) {
        assert element != null;

        String oldStyleName = element
                .getPropertyString(CUSTOM_STYLE_PROPERTY_NAME);

        if (!SharedUtil.equals(oldStyleName, styleName)) {
            if (oldStyleName != null && !oldStyleName.isEmpty()) {
                element.removeClassName(oldStyleName);
            }
            if (styleName != null && !styleName.isEmpty()) {
                element.addClassName(styleName);
            }
            element.setPropertyString(CUSTOM_STYLE_PROPERTY_NAME, styleName);
        }

    }

    /**
     * Opens the editor over the row with the given index.
     * 
     * @param rowIndex
     *            the index of the row to be edited
     * 
     * @throws IllegalStateException
     *             if the editor is not enabled
     * @throws IllegalStateException
     *             if the editor is already in edit mode
     */
    public void editRow(int rowIndex) {
        editor.editRow(rowIndex);
    }

    /**
     * Returns whether the editor is currently open on some row.
     * 
     * @return {@code true} if the editor is active, {@code false} otherwise.
     */
    public boolean isEditorActive() {
        return editor.getState() != State.INACTIVE;
    }

    /**
     * Saves any unsaved changes in the editor to the data source.
     * 
     * @throws IllegalStateException
     *             if the editor is not enabled
     * @throws IllegalStateException
     *             if the editor is not in edit mode
     */
    public void saveEditor() {
        editor.save();
    }

    /**
     * Cancels the currently active edit and hides the editor. Any changes that
     * are not {@link #saveEditor() saved} are lost.
     * 
     * @throws IllegalStateException
     *             if the editor is not enabled
     * @throws IllegalStateException
     *             if the editor is not in edit mode
     */
    public void cancelEditor() {
        editor.cancel();
    }

    /**
     * Returns the handler responsible for binding data and editor widgets to
     * the editor.
     * 
     * @return the editor handler or null if not set
     */
    public EditorHandler<T> getEditorHandler() {
        return editor.getHandler();
    }

    /**
     * Sets the handler responsible for binding data and editor widgets to the
     * editor.
     * 
     * @param rowHandler
     *            the new editor handler
     * 
     * @throws IllegalStateException
     *             if the editor is currently in edit mode
     */
    public void setEditorHandler(EditorHandler<T> handler) {
        editor.setHandler(handler);
    }

    /**
     * Returns the enabled state of the editor.
     * 
     * @return true if editing is enabled, false otherwise
     */
    public boolean isEditorEnabled() {
        return editor.isEnabled();
    }

    /**
     * Sets the enabled state of the editor.
     * 
     * @param enabled
     *            true to enable editing, false to disable
     * 
     * @throws IllegalStateException
     *             if in edit mode and trying to disable
     * @throws IllegalStateException
     *             if the editor handler is not set
     */
    public void setEditorEnabled(boolean enabled) {
        editor.setEnabled(enabled);
    }

    /**
     * Returns the editor widget associated with the given column. If the editor
     * is not active, returns null.
     * 
     * @param column
     *            the column
     * @return the widget if the editor is open, null otherwise
     */
    public Widget getEditorWidget(Column<?, T> column) {
        return editor.getWidget(column);
    }

    /**
     * Sets the caption on the save button in the Grid editor.
     * 
     * @param saveCaption
     *            the caption to set
     * @throws IllegalArgumentException
     *             if {@code saveCaption} is {@code null}
     */
    public void setEditorSaveCaption(String saveCaption)
            throws IllegalArgumentException {
        editor.setSaveCaption(saveCaption);
    }

    /**
     * Gets the current caption on the save button in the Grid editor.
     * 
     * @return the current caption on the save button
     */
    public String getEditorSaveCaption() {
        return editor.getSaveCaption();
    }

    /**
     * Sets the caption on the cancel button in the Grid editor.
     * 
     * @param cancelCaption
     *            the caption to set
     * @throws IllegalArgumentException
     *             if {@code cancelCaption} is {@code null}
     */
    public void setEditorCancelCaption(String cancelCaption)
            throws IllegalArgumentException {
        editor.setCancelCaption(cancelCaption);
    }

    /**
     * Gets the caption on the cancel button in the Grid editor.
     * 
     * @return the current caption on the cancel button
     */
    public String getEditorCancelCaption() {
        return editor.getCancelCaption();
    }

    @Override
    protected void onAttach() {
        super.onAttach();

        if (getEscalator().getBody().getRowCount() == 0 && dataSource != null) {
            setEscalatorSizeFromDataSource();
        }

        // Grid was just attached to DOM. Column widths should be calculated.
        recalculateColumnWidths();
    }

    @Override
    protected void onDetach() {
        Set<Integer> details = new HashSet<Integer>(visibleDetails);
        for (int row : details) {
            setDetailsVisible(row, false);
        }

        super.onDetach();
    }

    @Override
    public void onResize() {
        super.onResize();

        /*
         * Delay calculation to be deferred so Escalator can do it's magic.
         */
        Scheduler.get().scheduleFinally(new ScheduledCommand() {

            @Override
            public void execute() {
                if (escalator.getInnerWidth() != autoColumnWidthsRecalculator.lastCalculatedInnerWidth) {
                    recalculateColumnWidths();
                }

                // Vertical resizing could make editor positioning invalid so it
                // needs to be recalculated on resize
                if (isEditorActive()) {
                    editor.updateVerticalScrollPosition();
                }
            }
        });
    }

    /**
     * Grid does not support adding Widgets this way.
     * <p>
     * This method is implemented only because removing widgets from Grid (added
     * via e.g. {@link Renderer}s) requires the {@link HasWidgets} interface.
     * 
     * @param w
     *            irrelevant
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    @Deprecated
    public void add(Widget w) {
        throw new UnsupportedOperationException(
                "Cannot add widgets to Grid with this method");
    }

    /**
     * Grid does not support clearing Widgets this way.
     * <p>
     * This method is implemented only because removing widgets from Grid (added
     * via e.g. {@link Renderer}s) requires the {@link HasWidgets} interface.
     * 
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    @Deprecated
    public void clear() {
        throw new UnsupportedOperationException(
                "Cannot clear widgets from Grid this way");
    }

    /**
     * Grid does not support iterating through Widgets this way.
     * <p>
     * This method is implemented only because removing widgets from Grid (added
     * via e.g. {@link Renderer}s) requires the {@link HasWidgets} interface.
     * 
     * @return never
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    @Deprecated
    public Iterator<Widget> iterator() {
        throw new UnsupportedOperationException(
                "Cannot iterate through widgets in Grid this way");
    }

    /**
     * Grid does not support removing Widgets this way.
     * <p>
     * This method is implemented only because removing widgets from Grid (added
     * via e.g. {@link Renderer}s) requires the {@link HasWidgets} interface.
     * 
     * @return always <code>false</code>
     */
    @Override
    @Deprecated
    public boolean remove(Widget w) {
        /*
         * This is the method that is the sole reason to have Grid implement
         * HasWidget - when Vaadin removes a Component from the hierarchy, the
         * corresponding Widget will call removeFromParent() on itself. GWT will
         * check there that its parent (i.e. Grid) implements HasWidgets, and
         * will call this remove(Widget) method.
         * 
         * tl;dr: all this song and dance to make sure GWT's sanity checks
         * aren't triggered, even though they effectively do nothing interesting
         * from Grid's perspective.
         */
        return false;
    }

    /**
     * Accesses the package private method Widget#setParent()
     * 
     * @param widget
     *            The widget to access
     * @param parent
     *            The parent to set
     */
    static native final void setParent(Widget widget, Grid<?> parent)
    /*-{
        widget.@com.google.gwt.user.client.ui.Widget::setParent(Lcom/google/gwt/user/client/ui/Widget;)(parent);
    }-*/;

    private static native final void onAttach(Widget widget)
    /*-{
        widget.@com.google.gwt.user.client.ui.Widget::onAttach()();
    }-*/;

    private static native final void onDetach(Widget widget)
    /*-{
        widget.@com.google.gwt.user.client.ui.Widget::onDetach()();
    }-*/;

    @Override
    protected void doAttachChildren() {
        if (sidebar.getParent() == this) {
            onAttach(sidebar);
        }
    }

    @Override
    protected void doDetachChildren() {
        if (sidebar.getParent() == this) {
            onDetach(sidebar);
        }
    }

    void attachWidget(Widget w, Element parent) {
        assert w.getParent() == null;

        parent.appendChild(w.getElement());
        setParent(w, this);
    }

    void detachWidget(Widget w) {
        assert w.getParent() == this;

        setParent(w, null);
        w.getElement().removeFromParent();
    }

    /**
     * Resets all cached pixel sizes and reads new values from the DOM. This
     * methods should be used e.g. when styles affecting the dimensions of
     * elements in this grid have been changed.
     */
    public void resetSizesFromDom() {
        getEscalator().resetSizesFromDom();
    }

    /**
     * Sets a new details generator for row details.
     * <p>
     * The currently opened row details will be re-rendered.
     * 
     * @since 7.5.0
     * @param detailsGenerator
     *            the details generator to set
     * @throws IllegalArgumentException
     *             if detailsGenerator is <code>null</code>;
     */
    public void setDetailsGenerator(DetailsGenerator detailsGenerator)
            throws IllegalArgumentException {

        if (detailsGenerator == null) {
            throw new IllegalArgumentException(
                    "Details generator may not be null");
        }

        for (Integer index : visibleDetails) {
            setDetailsVisible(index, false);
        }

        this.detailsGenerator = detailsGenerator;

        // this will refresh all visible spacers
        escalator.getBody().setSpacerUpdater(gridSpacerUpdater);
    }

    /**
     * Gets the current details generator for row details.
     * 
     * @since 7.5.0
     * @return the detailsGenerator the current details generator
     */
    public DetailsGenerator getDetailsGenerator() {
        return detailsGenerator;
    }

    /**
     * Shows or hides the details for a specific row.
     * <p>
     * This method does nothing if trying to set show already-visible details,
     * or hide already-hidden details.
     * 
     * @since 7.5.0
     * @param rowIndex
     *            the index of the affected row
     * @param visible
     *            <code>true</code> to show the details, or <code>false</code>
     *            to hide them
     * @see #isDetailsVisible(int)
     */
    public void setDetailsVisible(int rowIndex, boolean visible) {
        if (DetailsGenerator.NULL.equals(detailsGenerator)) {
            return;
        }

        Integer rowIndexInteger = Integer.valueOf(rowIndex);

        /*
         * We want to prevent opening a details row twice, so any subsequent
         * openings (or closings) of details is a NOOP.
         * 
         * When a details row is opened, it is given an arbitrary height
         * (because Escalator requires a height upon opening). Only when it's
         * opened, Escalator will ask the generator to generate a widget, which
         * we then can measure. When measured, we correct the initial height by
         * the original height.
         * 
         * Without this check, we would override the measured height, and revert
         * back to the initial, arbitrary, height which would most probably be
         * wrong.
         * 
         * see GridSpacerUpdater.init for implementation details.
         */

        boolean isVisible = isDetailsVisible(rowIndex);
        if (visible && !isVisible) {
            escalator.getBody().setSpacer(rowIndex, DETAILS_ROW_INITIAL_HEIGHT);
            visibleDetails.add(rowIndexInteger);
        }

        else if (!visible && isVisible) {
            escalator.getBody().setSpacer(rowIndex, -1);
            visibleDetails.remove(rowIndexInteger);
        }
    }

    /**
     * Check whether the details for a row is visible or not.
     * 
     * @since 7.5.0
     * @param rowIndex
     *            the index of the row for which to check details
     * @return <code>true</code> iff the details for the given row is visible
     * @see #setDetailsVisible(int, boolean)
     */
    public boolean isDetailsVisible(int rowIndex) {
        return visibleDetails.contains(Integer.valueOf(rowIndex));
    }

    /**
     * Requests that the column widths should be recalculated.
     * <p>
     * The actual recalculation is not necessarily done immediately so you
     * cannot rely on the columns being the correct width after the call
     * returns.
     * 
     * @since 7.4.1
     */
    public void recalculateColumnWidths() {
        autoColumnWidthsRecalculator.schedule();
    }

    /**
     * Gets the customizable menu bar that is by default used for toggling
     * column hidability. The application developer is allowed to add their
     * custom items to the end of the menu, but should try to avoid modifying
     * the items in the beginning of the menu that control the column hiding if
     * any columns are marked as hidable. A toggle for opening the menu will be
     * displayed whenever the menu contains at least one item.
     * 
     * @since 7.5.0
     * @return the menu bar
     */
    public MenuBar getSidebarMenu() {
        return sidebar.menuBar;
    }

    /**
     * Tests whether the sidebar menu is currently open.
     * 
     * @since 7.5.0
     * @see #getSidebarMenu()
     * @return <code>true</code> if the sidebar is open; <code>false</code> if
     *         it is closed
     */
    public boolean isSidebarOpen() {
        return sidebar.isOpen();
    }

    /**
     * Sets whether the sidebar menu is open.
     * 
     * 
     * @since 7.5.0
     * @see #getSidebarMenu()
     * @see #isSidebarOpen()
     * @param sidebarOpen
     *            <code>true</code> to open the sidebar; <code>false</code> to
     *            close it
     */
    public void setSidebarOpen(boolean sidebarOpen) {
        if (sidebarOpen) {
            sidebar.open();
        } else {
            sidebar.close();
        }
    }

    @Override
    public int getTabIndex() {
        return FocusUtil.getTabIndex(this);
    }

    @Override
    public void setAccessKey(char key) {
        FocusUtil.setAccessKey(this, key);
    }

    @Override
    public void setFocus(boolean focused) {
        FocusUtil.setFocus(this, focused);
    }

    @Override
    public void setTabIndex(int index) {
        FocusUtil.setTabIndex(this, index);
    }

    @Override
    public void focus() {
        setFocus(true);
    }

    /**
     * Sets the buffered editor mode.
     * 
     * @since 7.6
     * @param editorUnbuffered
     *            <code>true</code> to enable buffered editor,
     *            <code>false</code> to disable it
     */
    public void setEditorBuffered(boolean editorBuffered) {
        editor.setBuffered(editorBuffered);
    }

    /**
     * Gets the buffered editor mode.
     * 
     * @since 7.6
     * @return <code>true</code> if buffered editor is enabled,
     *         <code>false</code> otherwise
     */
    public boolean isEditorBuffered() {
        return editor.isBuffered();
    }

    /**
     * Returns the {@link EventCellReference} for the latest event fired from
     * this Grid.
     * <p>
     * Note: This cell reference will be updated when firing the next event.
     * 
     * @since 7.5
     * @return event cell reference
     */
    public EventCellReference<T> getEventCell() {
        return eventCell;
    }

    /**
     * Returns a CellReference for the cell to which the given element belongs
     * to.
     * 
     * @since 7.6
     * @param element
     *            Element to find from the cell's content.
     * @return CellReference or <code>null</code> if cell was not found.
     */
    public CellReference<T> getCellReference(Element element) {
        RowContainer container = getEscalator().findRowContainer(element);
        if (container != null) {
            Cell cell = container.getCell(element);
            if (cell != null) {
                EventCellReference<T> cellRef = new EventCellReference<T>(this);
                cellRef.set(cell, getSectionFromContainer(container));
                return cellRef;
            }
        }
        return null;
    }
}
