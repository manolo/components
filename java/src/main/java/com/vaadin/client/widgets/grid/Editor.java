package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.BrowserInfo;
import com.vaadin.client.DeferredWorker;
import com.vaadin.client.Focusable;
import com.vaadin.client.WidgetUtil;
import com.vaadin.client.data.DataSource.RowHandle;
import com.vaadin.client.renderers.WidgetRenderer;
import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.ScrollbarBundle.Direction;
import com.vaadin.client.widget.grid.events.DefaultEditorEventHandler;
import com.vaadin.client.widget.grid.events.EditorDomEvent;
import com.vaadin.client.widget.grid.events.EditorHandler;
import com.vaadin.client.widget.grid.events.EditorHandler.EditorRequest;
import com.vaadin.client.widget.grid.events.ScrollEvent;
import com.vaadin.client.widget.grid.events.ScrollHandler;
import com.vaadin.client.widget.grid.selection.MultiSelectionRenderer;
import com.vaadin.client.widgets.escalator.AbstractRowContainer;
import com.vaadin.client.widgets.escalator.Escalator;
import com.vaadin.shared.ui.grid.GridConstants;
import com.vaadin.shared.ui.grid.ScrollDestination;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An editor UI for Grid rows. A single Grid row at a time can be opened for
 * editing.
 * 
 * @since 7.6
 * @param <T>
 *            the row type of the grid
 */
public class Editor<T> implements DeferredWorker {

    public static final int KEYCODE_SHOW = KeyCodes.KEY_ENTER;
    public static final int KEYCODE_HIDE = KeyCodes.KEY_ESCAPE;

    private static final String ERROR_CLASS_NAME = "error";
    private static final String NOT_EDITABLE_CLASS_NAME = "not-editable";

    ScheduledCommand fieldFocusCommand = new ScheduledCommand() {
        private int count = 0;

        @Override
        public void execute() {
            Element focusedElement = WidgetUtil.getFocusedElement();
            if (focusedElement == grid.getElement()
                    || focusedElement == Document.get().getBody()
                    || count > 2) {
                focusColumn(focusedColumnIndex);
            } else {
                ++count;
                Scheduler.get().scheduleDeferred(this);
            }
        }
    };

    /**
     * A handler for events related to the Grid editor. Responsible for
     * opening, moving or closing the editor based on the received event.
     * 
     * @since 7.6
     * @author Vaadin Ltd
     * @param <T>
     *            the row type of the grid
     */
    public interface EventHandler<T> {
        /**
         * Handles editor-related events in an appropriate way. Opens,
         * moves, or closes the editor based on the given event.
         * 
         * @param event
         *            the received event
         * @return true if the event was handled and nothing else should be
         *         done, false otherwise
         */
        boolean handleEvent(EditorDomEvent<T> event);
    }

    protected enum State {
        INACTIVE, ACTIVATING, BINDING, ACTIVE, SAVING
    }

    private Grid<T> grid;
    private EditorHandler<T> handler;
    private Editor.EventHandler<T> eventHandler = GWT
            .create(DefaultEditorEventHandler.class);

    DivElement editorOverlay = DivElement.as(DOM.createDiv());
    private DivElement cellWrapper = DivElement.as(DOM.createDiv());
    private DivElement frozenCellWrapper = DivElement.as(DOM.createDiv());

    private DivElement messageAndButtonsWrapper = DivElement.as(DOM
            .createDiv());

    private DivElement messageWrapper = DivElement.as(DOM.createDiv());
    private DivElement buttonsWrapper = DivElement.as(DOM.createDiv());

    // Element which contains the error message for the editor
    // Should only be added to the DOM when there's a message to show
    private DivElement message = DivElement.as(DOM.createDiv());

    private Map<Column<?, T>, Widget> columnToWidget = new HashMap<Column<?, T>, Widget>();
    private List<HandlerRegistration> focusHandlers = new ArrayList<HandlerRegistration>();

    private boolean enabled = false;
    private Editor.State state = State.INACTIVE;
    public int rowIndex = -1;
    public int focusedColumnIndex = -1;
    private String styleName = null;

    private HandlerRegistration hScrollHandler;
    private HandlerRegistration vScrollHandler;

    private final Button saveButton;
    private final Button cancelButton;

    private static final int SAVE_TIMEOUT_MS = 5000;
    private final Timer saveTimeout = new Timer() {
        @Override
        public void run() {
            Grid.getLogger().warning(
                    "Editor save action is taking longer than expected ("
                            + SAVE_TIMEOUT_MS + "ms). Does your "
                            + EditorHandler.class.getSimpleName()
                            + " remember to call success() or fail()?");
        }
    };

    private final EditorRequestImpl.RequestCallback<T> saveRequestCallback = new EditorRequestImpl.RequestCallback<T>() {
        @Override
        public void onSuccess(EditorRequest<T> request) {
            if (state == State.SAVING) {
                cleanup();
                cancel();
                grid.clearSortOrder();
            }
        }

        @Override
        public void onError(EditorRequest<T> request) {
            if (state == State.SAVING) {
                cleanup();
            }
        }

        private void cleanup() {
            state = State.ACTIVE;
            setButtonsEnabled(true);
            saveTimeout.cancel();
        }
    };

    private static final int BIND_TIMEOUT_MS = 5000;
    private final Timer bindTimeout = new Timer() {
        @Override
        public void run() {
            Grid.getLogger().warning(
                    "Editor bind action is taking longer than expected ("
                            + BIND_TIMEOUT_MS + "ms). Does your "
                            + EditorHandler.class.getSimpleName()
                            + " remember to call success() or fail()?");
        }
    };

    private final EditorRequestImpl.RequestCallback<T> bindRequestCallback = new EditorRequestImpl.RequestCallback<T>() {
        @Override
        public void onSuccess(EditorRequest<T> request) {
            if (state == State.BINDING) {
                state = State.ACTIVE;
                bindTimeout.cancel();

                rowIndex = request.getRowIndex();
                focusedColumnIndex = request.getColumnIndex();
                if (focusedColumnIndex >= 0) {
                    // Update internal focus of Grid
                    grid.focusCell(rowIndex, focusedColumnIndex);
                }

                showOverlay();
            }
        }

        @Override
        public void onError(EditorRequest<T> request) {
            if (state == State.BINDING) {
                if (rowIndex == -1) {
                    doCancel();
                } else {
                    state = State.ACTIVE;
                    // TODO: Maybe restore focus?
                }
                bindTimeout.cancel();
            }
        }
    };

    /** A set of all the columns that display an error flag. */
    private final Set<Column<?, T>> columnErrors = new HashSet<Column<?, T>>();
    private boolean buffered = true;

    /** Original position of editor */
    private double originalTop;
    /** Original scroll position of grid when editor was opened */
    private double originalScrollTop;
    private RowHandle<T> pinnedRowHandle;

    public Editor() {
        saveButton = new Button();
        saveButton.setText(GridConstants.DEFAULT_SAVE_CAPTION);
        saveButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save();
            }
        });

        cancelButton = new Button();
        cancelButton.setText(GridConstants.DEFAULT_CANCEL_CAPTION);
        cancelButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                cancel();
            }
        });
    }

    public void setEditorError(String errorMessage,
            Collection<Column<?, T>> errorColumns) {

        if (errorMessage == null) {
            message.removeFromParent();
        } else {
            message.setInnerText(errorMessage);
            if (message.getParentElement() == null) {
                messageWrapper.appendChild(message);
            }
        }
        // In unbuffered mode only show message wrapper if there is an error
        if (!isBuffered()) {
            setMessageAndButtonsWrapperVisible(errorMessage != null);
        }

        if (state == State.ACTIVE || state == State.SAVING) {
            for (Column<?, T> c : grid.getColumns()) {
                grid.getEditor().setEditorColumnError(c,
                        errorColumns.contains(c));
            }
        }
    }

    public int getRow() {
        return rowIndex;
    }

    /**
     * If a cell of this Grid had focus once this editRow call was
     * triggered, the editor component at the previously focused column
     * index will be focused.
     * 
     * If a Grid cell was not focused prior to calling this method, it will
     * be equivalent to {@code editRow(rowIndex, -1)}.
     * 
     * @see #editRow(int, int)
     */
    public void editRow(int rowIndex) {
        // Focus the last focused column in the editor iff grid or its child
        // was focused before the edit request
        Cell focusedCell = grid.cellFocusHandler.getFocusedCell();
        Element focusedElement = WidgetUtil.getFocusedElement();
        if (focusedCell != null && focusedElement != null
                && grid.getElement().isOrHasChild(focusedElement)) {
            editRow(rowIndex, focusedCell.getColumn());
        } else {
            editRow(rowIndex, -1);
        }
    }

    /**
     * Opens the editor over the row with the given index and attempts to
     * focus the editor widget in the given column index. Does not move
     * focus if the widget is not focusable or if the column index is -1.
     * 
     * @param rowIndex
     *            the index of the row to be edited
     * @param columnIndex
     *            the column index of the editor widget that should be
     *            initially focused or -1 to not set focus
     * 
     * @throws IllegalStateException
     *             if this editor is not enabled
     * @throws IllegalStateException
     *             if this editor is already in edit mode and in buffered
     *             mode
     * 
     * @since 7.5
     */
    public void editRow(final int rowIndex, final int columnIndex) {
        if (!enabled) {
            throw new IllegalStateException(
                    "Cannot edit row: editor is not enabled");
        }

        if (isWorkPending()) {
            // Request pending a response, don't move try to start another
            // request.
            return;
        }

        if (state != State.INACTIVE && this.rowIndex != rowIndex) {
            if (isBuffered()) {
                throw new IllegalStateException(
                        "Cannot edit row: editor already in edit mode");
            } else if (!columnErrors.isEmpty()) {
                // Don't move row if errors are present

                // FIXME: Should attempt bind if error field values have
                // changed.

                return;
            }
        }
        if (columnIndex >= grid.getVisibleColumns().size()) {
            throw new IllegalArgumentException("Edited column index "
                    + columnIndex
                    + " was bigger than visible column count.");
        }

        if (this.rowIndex == rowIndex && focusedColumnIndex == columnIndex) {
            // NO-OP
            return;
        }

        if (this.rowIndex == rowIndex) {
            if (focusedColumnIndex != columnIndex) {
                if (columnIndex >= grid.getFrozenColumnCount()) {
                    // Scroll to new focused column.
                    grid.getEscalator().scrollToColumn(columnIndex,
                            ScrollDestination.ANY, 0);
                }

                focusedColumnIndex = columnIndex;
            }

            updateHorizontalScrollPosition();

            // Update Grid internal focus and focus widget if possible
            if (focusedColumnIndex >= 0) {
                grid.focusCell(rowIndex, focusedColumnIndex);
                focusColumn(focusedColumnIndex);
            }

            // No need to request anything from the editor handler.
            return;
        }
        state = State.ACTIVATING;

        final Escalator escalator = grid.getEscalator();
        if (escalator.getVisibleRowRange().contains(rowIndex)) {
            show(rowIndex, columnIndex);
        } else {
            vScrollHandler = grid.addScrollHandler(new ScrollHandler() {
                @Override
                public void onScroll(ScrollEvent event) {
                    if (escalator.getVisibleRowRange().contains(rowIndex)) {
                        show(rowIndex, columnIndex);
                        vScrollHandler.removeHandler();
                    }
                }
            });
            grid.scrollToRow(rowIndex,
                    isBuffered() ? ScrollDestination.MIDDLE
                            : ScrollDestination.ANY);
        }
    }

    /**
     * Cancels the currently active edit and hides the editor. Any changes
     * that are not {@link #save() saved} are lost.
     * 
     * @throws IllegalStateException
     *             if this editor is not enabled
     * @throws IllegalStateException
     *             if this editor is not in edit mode
     */
    public void cancel() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Cannot cancel edit: editor is not enabled");
        }
        if (state == State.INACTIVE) {
            throw new IllegalStateException(
                    "Cannot cancel edit: editor is not in edit mode");
        }
        handler.cancel(new EditorRequestImpl<T>(grid, rowIndex,
                focusedColumnIndex, null));
        doCancel();
    }

    private void doCancel() {
        hideOverlay();
        state = State.INACTIVE;
        rowIndex = -1;
        focusedColumnIndex = -1;
        grid.getEscalator().setScrollLocked(Direction.VERTICAL, false);
        updateSelectionCheckboxesAsNeeded(true);
    }

    private void updateSelectionCheckboxesAsNeeded(boolean isEnabled) {
        // FIXME: This is too much guessing. Define a better way to do this.
        if (grid.selectionColumn != null
                && grid.selectionColumn.getRenderer() instanceof MultiSelectionRenderer) {
            grid.refreshBody();
            CheckBox checkBox = (CheckBox) grid.getDefaultHeaderRow()
                    .getCell(grid.selectionColumn).getWidget();
            checkBox.setEnabled(isEnabled);
        }
    }

    /**
     * Saves any unsaved changes to the data source and hides the editor.
     * 
     * @throws IllegalStateException
     *             if this editor is not enabled
     * @throws IllegalStateException
     *             if this editor is not in edit mode
     */
    public void save() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Cannot save: editor is not enabled");
        }
        if (state != State.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot save: editor is not in edit mode");
        }

        state = State.SAVING;
        setButtonsEnabled(false);
        saveTimeout.schedule(SAVE_TIMEOUT_MS);
        EditorRequest<T> request = new EditorRequestImpl<T>(grid, rowIndex,
                focusedColumnIndex, saveRequestCallback);
        handler.save(request);
        updateSelectionCheckboxesAsNeeded(true);
    }

    /**
     * Returns the handler responsible for binding data and editor widgets
     * to this editor.
     * 
     * @return the editor handler or null if not set
     */
    public EditorHandler<T> getHandler() {
        return handler;
    }

    /**
     * Sets the handler responsible for binding data and editor widgets to
     * this editor.
     * 
     * @param rowHandler
     *            the new editor handler
     * 
     * @throws IllegalStateException
     *             if this editor is currently in edit mode
     */
    public void setHandler(EditorHandler<T> rowHandler) {
        if (state != State.INACTIVE) {
            throw new IllegalStateException(
                    "Cannot set EditorHandler: editor is currently in edit mode");
        }
        handler = rowHandler;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled state of this editor.
     * 
     * @param enabled
     *            true if enabled, false otherwise
     * 
     * @throws IllegalStateException
     *             if in edit mode and trying to disable
     * @throws IllegalStateException
     *             if the editor handler is not set
     */
    public void setEnabled(boolean enabled) {
        if (enabled == false && state != State.INACTIVE) {
            throw new IllegalStateException(
                    "Cannot disable: editor is in edit mode");
        } else if (enabled == true && getHandler() == null) {
            throw new IllegalStateException(
                    "Cannot enable: EditorHandler not set");
        }
        this.enabled = enabled;
    }

    protected void show(int rowIndex, int columnIndex) {
        if (state == State.ACTIVATING) {
            state = State.BINDING;
            bindTimeout.schedule(BIND_TIMEOUT_MS);
            EditorRequest<T> request = new EditorRequestImpl<T>(grid,
                    rowIndex, columnIndex, bindRequestCallback);
            handler.bind(request);
            grid.getEscalator().setScrollLocked(Direction.VERTICAL,
                    isBuffered());
            updateSelectionCheckboxesAsNeeded(false);
        }
    }

    protected void setGrid(final Grid<T> grid) {
        assert grid != null : "Grid cannot be null";
        assert this.grid == null : "Can only attach editor to Grid once";

        this.grid = grid;
    }

    protected Editor.State getState() {
        return state;
    }

    protected void setState(Editor.State state) {
        this.state = state;
    }

    /**
     * Returns the editor widget associated with the given column. If the
     * editor is not active or the column is not
     * {@link Column#isEditable() editable}, returns null.
     * 
     * @param column
     *            the column
     * @return the widget if the editor is open and the column is editable,
     *         null otherwise
     */
    protected Widget getWidget(Column<?, T> column) {
        return columnToWidget.get(column);
    }

    /**
     * Equivalent to {@code showOverlay()}. The argument is ignored.
     * 
     * @param unused
     *            ignored argument
     * 
     * @deprecated As of 7.5, use {@link #showOverlay()} instead.
     */
    @Deprecated
    protected void showOverlay(TableRowElement unused) {
        showOverlay();
    }

    /**
     * Opens the editor overlay over the table row indicated by
     * {@link #getRow()}.
     * 
     * @since 7.5
     */
    protected void showOverlay() {
        // Ensure overlay is hidden initially
        hideOverlay();
        DivElement gridElement = DivElement.as(grid.getElement());

        TableRowElement tr = grid.getEscalator().getBody()
                .getRowElement(rowIndex);

        hScrollHandler = grid.addScrollHandler(new ScrollHandler() {
            @Override
            public void onScroll(ScrollEvent event) {
                updateHorizontalScrollPosition();
                updateVerticalScrollPosition();
            }
        });

        gridElement.appendChild(editorOverlay);
        editorOverlay.appendChild(frozenCellWrapper);
        editorOverlay.appendChild(cellWrapper);
        editorOverlay.appendChild(messageAndButtonsWrapper);

        updateBufferedStyleName();

        int frozenColumns = grid.getVisibleFrozenColumnCount();
        double frozenColumnsWidth = 0;
        double cellHeight = 0;

        for (int i = 0; i < tr.getCells().getLength(); i++) {
            Element cell = createCell(tr.getCells().getItem(i));
            cellHeight = Math.max(cellHeight, WidgetUtil
                    .getRequiredHeightBoundingClientRectDouble(tr
                            .getCells().getItem(i)));

            Column<?, T> column = grid.getVisibleColumn(i);

            if (i < frozenColumns) {
                frozenCellWrapper.appendChild(cell);
                frozenColumnsWidth += WidgetUtil
                        .getRequiredWidthBoundingClientRectDouble(tr
                                .getCells().getItem(i));
            } else {
                cellWrapper.appendChild(cell);
            }

            if (column.isEditable()) {
                Widget editor = getHandler().getWidget(column);

                if (editor != null) {
                    columnToWidget.put(column, editor);
                    grid.attachWidget(editor, cell);
                }

                if (i == focusedColumnIndex) {
                    if (BrowserInfo.get().isIE8()) {
                        Scheduler.get().scheduleDeferred(fieldFocusCommand);
                    } else {
                        focusColumn(focusedColumnIndex);
                    }
                }
            } else {
                cell.addClassName(NOT_EDITABLE_CLASS_NAME);
                cell.addClassName(tr.getCells().getItem(i).getClassName());
                // If the focused or frozen stylename is present it should
                // not be inherited by the editor cell as it is not useful
                // in the editor and would look broken without additional
                // style rules. This is a bit of a hack.
                cell.removeClassName(grid.cellFocusStyleName);
                cell.removeClassName("frozen");

                if (column == grid.selectionColumn) {
                    // Duplicate selection column CheckBox

                    pinnedRowHandle = grid.getDataSource().getHandle(
                            grid.getDataSource().getRow(rowIndex));
                    pinnedRowHandle.pin();

                    // We need to duplicate the selection CheckBox for the
                    // editor overlay since the original one is hidden by
                    // the overlay
                    final CheckBox checkBox = GWT.create(CheckBox.class);
                    checkBox.setValue(grid.isSelected(pinnedRowHandle
                            .getRow()));
                    checkBox.sinkEvents(Event.ONCLICK);

                    checkBox.addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            T row = pinnedRowHandle.getRow();
                            if (grid.isSelected(row)) {
                                grid.deselect(row);
                            } else {
                                grid.select(row);
                            }
                        }
                    });
                    grid.attachWidget(checkBox, cell);
                    columnToWidget.put(column, checkBox);

                    // Only enable CheckBox in non-buffered mode
                    checkBox.setEnabled(!isBuffered());

                } else if (!(column.getRenderer() instanceof WidgetRenderer)) {
                    // Copy non-widget content directly
                    cell.setInnerHTML(tr.getCells().getItem(i)
                            .getInnerHTML());
                }
            }
        }

        setBounds(frozenCellWrapper, 0, 0, frozenColumnsWidth, 0);
        setBounds(cellWrapper, frozenColumnsWidth, 0, tr.getOffsetWidth()
                - frozenColumnsWidth, cellHeight);

        // Only add these elements once
        if (!messageAndButtonsWrapper.isOrHasChild(messageWrapper)) {
            messageAndButtonsWrapper.appendChild(messageWrapper);
            messageAndButtonsWrapper.appendChild(buttonsWrapper);
        }

        if (isBuffered()) {
            grid.attachWidget(saveButton, buttonsWrapper);
            grid.attachWidget(cancelButton, buttonsWrapper);
        }

        setMessageAndButtonsWrapperVisible(isBuffered());

        updateHorizontalScrollPosition();

        AbstractRowContainer body = (AbstractRowContainer) grid
                .getEscalator().getBody();
        double rowTop = body.getRowTop(tr);

        int bodyTop = body.getElement().getAbsoluteTop();
        int gridTop = gridElement.getAbsoluteTop();
        double overlayTop = rowTop + bodyTop - gridTop;

        originalScrollTop = grid.getScrollTop();
        if (!isBuffered() || buttonsShouldBeRenderedBelow(tr)) {
            // Default case, editor buttons are below the edited row
            editorOverlay.getStyle().setTop(overlayTop, Unit.PX);
            originalTop = overlayTop;
            editorOverlay.getStyle().clearBottom();
        } else {
            // Move message and buttons wrapper on top of cell wrapper if
            // there is not enough space visible space under and fix the
            // overlay from the bottom
            editorOverlay.insertFirst(messageAndButtonsWrapper);
            int gridHeight = grid.getElement().getOffsetHeight();
            editorOverlay.getStyle()
                    .setBottom(
                            gridHeight - overlayTop - tr.getOffsetHeight(),
                            Unit.PX);
            editorOverlay.getStyle().clearTop();
        }

        // Do not render over the vertical scrollbar
        editorOverlay.getStyle().setWidth(grid.escalator.getInnerWidth(),
                Unit.PX);
    }

    private void focusColumn(int colIndex) {
        if (colIndex < 0 || colIndex >= grid.getVisibleColumns().size()) {
            // NO-OP
            return;
        }

        Widget editor = getWidget(grid.getVisibleColumn(colIndex));
        if (editor instanceof Focusable) {
            ((Focusable) editor).focus();
        } else if (editor instanceof com.google.gwt.user.client.ui.Focusable) {
            ((com.google.gwt.user.client.ui.Focusable) editor)
                    .setFocus(true);
        } else {
            grid.focus();
        }
    }

    private boolean buttonsShouldBeRenderedBelow(TableRowElement tr) {
        TableSectionElement tfoot = grid.escalator.getFooter().getElement();
        double tfootPageTop = WidgetUtil.getBoundingClientRect(tfoot)
                .getTop();
        double trPageBottom = WidgetUtil.getBoundingClientRect(tr)
                .getBottom();
        int messageAndButtonsHeight = messageAndButtonsWrapper
                .getOffsetHeight();
        double bottomOfButtons = trPageBottom + messageAndButtonsHeight;

        return bottomOfButtons < tfootPageTop;
    }

    protected void hideOverlay() {
        if (editorOverlay.getParentElement() == null) {
            return;
        }

        if (pinnedRowHandle != null) {
            pinnedRowHandle.unpin();
            pinnedRowHandle = null;
        }

        for (HandlerRegistration r : focusHandlers) {
            r.removeHandler();
        }
        focusHandlers.clear();

        for (Widget w : columnToWidget.values()) {
            Grid.setParent(w, null);
        }
        columnToWidget.clear();

        if (isBuffered()) {
            grid.detachWidget(saveButton);
            grid.detachWidget(cancelButton);
        }

        editorOverlay.removeAllChildren();
        cellWrapper.removeAllChildren();
        frozenCellWrapper.removeAllChildren();
        editorOverlay.removeFromParent();

        hScrollHandler.removeHandler();

        clearEditorColumnErrors();
    }

    private void updateBufferedStyleName() {
        if (isBuffered()) {
            editorOverlay.removeClassName("unbuffered");
            editorOverlay.addClassName("buffered");
        } else {
            editorOverlay.removeClassName("buffered");
            editorOverlay.addClassName("unbuffered");
        }
    }

    protected void setStylePrimaryName(String primaryName) {
        if (styleName != null) {
            editorOverlay.removeClassName(styleName);

            cellWrapper.removeClassName(styleName + "-cells");
            frozenCellWrapper.removeClassName(styleName + "-cells");
            messageAndButtonsWrapper.removeClassName(styleName + "-footer");

            messageWrapper.removeClassName(styleName + "-message");
            buttonsWrapper.removeClassName(styleName + "-buttons");

            saveButton.removeStyleName(styleName + "-save");
            cancelButton.removeStyleName(styleName + "-cancel");
        }
        styleName = primaryName + "-editor";
        editorOverlay.setClassName(styleName);

        cellWrapper.setClassName(styleName + "-cells");
        frozenCellWrapper.setClassName(styleName + "-cells frozen");
        messageAndButtonsWrapper.setClassName(styleName + "-footer");

        messageWrapper.setClassName(styleName + "-message");
        buttonsWrapper.setClassName(styleName + "-buttons");

        saveButton.setStyleName(styleName + "-save");
        cancelButton.setStyleName(styleName + "-cancel");
    }

    /**
     * Creates an editor cell corresponding to the given table cell. The
     * returned element is empty and has the same dimensions and position as
     * the table cell.
     * 
     * @param td
     *            the table cell used as a reference
     * @return an editor cell corresponding to the given cell
     */
    protected Element createCell(TableCellElement td) {
        DivElement cell = DivElement.as(DOM.createDiv());
        double width = WidgetUtil
                .getRequiredWidthBoundingClientRectDouble(td);
        double height = WidgetUtil
                .getRequiredHeightBoundingClientRectDouble(td);
        setBounds(cell, td.getOffsetLeft(), td.getOffsetTop(), width,
                height);
        return cell;
    }

    private static void setBounds(Element e, double left, double top,
            double width, double height) {
        Style style = e.getStyle();
        style.setLeft(left, Unit.PX);
        style.setTop(top, Unit.PX);
        style.setWidth(width, Unit.PX);
        style.setHeight(height, Unit.PX);
    }

    private void updateHorizontalScrollPosition() {
        double scrollLeft = grid.getScrollLeft();
        cellWrapper.getStyle().setLeft(
                frozenCellWrapper.getOffsetWidth() - scrollLeft, Unit.PX);
    }

    /**
     * Moves the editor overlay on scroll so that it stays on top of the
     * edited row. This will also snap the editor to top or bottom of the
     * row container if the edited row is scrolled out of the visible area.
     */
    void updateVerticalScrollPosition() {
        if (isBuffered()) {
            return;
        }

        double newScrollTop = grid.getScrollTop();

        int gridTop = grid.getElement().getAbsoluteTop();
        int editorHeight = editorOverlay.getOffsetHeight();

        Escalator escalator = grid.getEscalator();
        TableSectionElement header = escalator.getHeader().getElement();
        int footerTop = escalator.getFooter().getElement().getAbsoluteTop();
        int headerBottom = header.getAbsoluteBottom();

        double newTop = originalTop - (newScrollTop - originalScrollTop);

        if (newTop + gridTop < headerBottom) {
            // Snap editor to top of the row container
            newTop = header.getOffsetHeight();
        } else if (newTop + gridTop > footerTop - editorHeight) {
            // Snap editor to the bottom of the row container
            newTop = footerTop - editorHeight - gridTop;
        }

        editorOverlay.getStyle().setTop(newTop, Unit.PX);
    }

    protected void setGridEnabled(boolean enabled) {
        // TODO: This should be informed to handler as well so possible
        // fields can be disabled.
        setButtonsEnabled(enabled);
    }

    private void setButtonsEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
    }

    public void setSaveCaption(String saveCaption)
            throws IllegalArgumentException {
        if (saveCaption == null) {
            throw new IllegalArgumentException(
                    "Save caption cannot be null");
        }
        saveButton.setText(saveCaption);
    }

    public String getSaveCaption() {
        return saveButton.getText();
    }

    public void setCancelCaption(String cancelCaption)
            throws IllegalArgumentException {
        if (cancelCaption == null) {
            throw new IllegalArgumentException(
                    "Cancel caption cannot be null");
        }
        cancelButton.setText(cancelCaption);
    }

    public String getCancelCaption() {
        return cancelButton.getText();
    }

    public void setEditorColumnError(Column<?, T> column, boolean hasError) {
        if (state != State.ACTIVE && state != State.SAVING) {
            throw new IllegalStateException("Cannot set cell error "
                    + "status: editor is neither active nor saving.");
        }

        if (isEditorColumnError(column) == hasError) {
            return;
        }

        Element editorCell = getWidget(column).getElement()
                .getParentElement();
        if (hasError) {
            editorCell.addClassName(ERROR_CLASS_NAME);
            columnErrors.add(column);
        } else {
            editorCell.removeClassName(ERROR_CLASS_NAME);
            columnErrors.remove(column);
        }
    }

    public void clearEditorColumnErrors() {

        /*
         * editorOverlay has no children if it's not active, effectively
         * making this loop a NOOP.
         */
        Element e = editorOverlay.getFirstChildElement();
        while (e != null) {
            e.removeClassName(ERROR_CLASS_NAME);
            e = e.getNextSiblingElement();
        }

        columnErrors.clear();
    }

    public boolean isEditorColumnError(Column<?, T> column) {
        return columnErrors.contains(column);
    }

    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
        setMessageAndButtonsWrapperVisible(buffered);
    }

    public boolean isBuffered() {
        return buffered;
    }

    private void setMessageAndButtonsWrapperVisible(boolean visible) {
        if (visible) {
            messageAndButtonsWrapper.getStyle().clearDisplay();
        } else {
            messageAndButtonsWrapper.getStyle().setDisplay(Display.NONE);
        }
    }

    /**
     * Sets the event handler for this Editor.
     * 
     * @since 7.6
     * @param handler
     *            the new event handler
     */
    public void setEventHandler(Editor.EventHandler<T> handler) {
        eventHandler = handler;
    }

    /**
     * Returns the event handler of this Editor.
     * 
     * @since 7.6
     * @return the current event handler
     */
    public Editor.EventHandler<T> getEventHandler() {
        return eventHandler;
    }

    @Override
    public boolean isWorkPending() {
        return saveTimeout.isRunning() || bindTimeout.isRunning();
    }

    protected int getElementColumn(Element e) {
        int frozenCells = frozenCellWrapper.getChildCount();
        if (frozenCellWrapper.isOrHasChild(e)) {
            for (int i = 0; i < frozenCells; ++i) {
                if (frozenCellWrapper.getChild(i).isOrHasChild(e)) {
                    return i;
                }
            }
        }

        if (cellWrapper.isOrHasChild(e)) {
            for (int i = 0; i < cellWrapper.getChildCount(); ++i) {
                if (cellWrapper.getChild(i).isOrHasChild(e)) {
                    return i + frozenCells;
                }
            }
        }

        return -1;
    }
}