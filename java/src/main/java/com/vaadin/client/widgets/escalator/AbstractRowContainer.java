package com.vaadin.client.widgets.escalator;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.user.client.DOM;

import com.vaadin.client.BrowserInfo;
import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.EscalatorUpdater;
import com.vaadin.client.widget.escalator.FlyweightCell;
import com.vaadin.client.widget.escalator.Row;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.util.SharedUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractRowContainer implements RowContainer {
    /**
     * 
     */
    protected final Escalator escalator;

    private EscalatorUpdater updater = EscalatorUpdater.NULL;

    private int rows;

    /**
     * The table section element ({@code <thead>}, {@code <tbody>} or
     * {@code <tfoot>}) the rows (i.e. {@code <tr>} tags) are contained in.
     */
    protected final TableSectionElement root;

    /**
     * The primary style name of the escalator. Most commonly provided by
     * Escalator as "v-escalator".
     */
    private String primaryStyleName = null;

    private boolean defaultRowHeightShouldBeAutodetected = true;

    private double defaultRowHeight = INITIAL_DEFAULT_ROW_HEIGHT;

    private boolean autodetectRowHeightLaterQueued = false;

    public AbstractRowContainer(
            Escalator escalator, final TableSectionElement rowContainerElement) {
        this.escalator = escalator;
        root = rowContainerElement;
    }

    @Override
    public TableSectionElement getElement() {
        return root;
    }

    /**
     * Gets the tag name of an element to represent a cell in a row.
     * <p>
     * Usually {@code "th"} or {@code "td"}.
     * <p>
     * <em>Note:</em> To actually <em>create</em> such an element, use
     * {@link #createCellElement(int, int)} instead.
     * 
     * @return the tag name for the element to represent cells as
     * @see #createCellElement(int, int)
     */
    protected abstract String getCellElementTagName();

    @Override
    public EscalatorUpdater getEscalatorUpdater() {
        return updater;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <em>Implementation detail:</em> This method does no DOM modifications
     * (i.e. is very cheap to call) if there is no data for rows or columns
     * when this method is called.
     * 
     * @see #hasColumnAndRowData()
     */
    @Override
    public void setEscalatorUpdater(final EscalatorUpdater escalatorUpdater) {
        if (escalatorUpdater == null) {
            throw new IllegalArgumentException(
                    "escalator updater cannot be null");
        }

        updater = escalatorUpdater;

        if (this.escalator.hasColumnAndRowData() && getRowCount() > 0) {
            refreshRows(0, getRowCount());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <em>Implementation detail:</em> This method does no DOM modifications
     * (i.e. is very cheap to call) if there are no rows in the DOM when
     * this method is called.
     * 
     * @see #hasSomethingInDom()
     */
    @Override
    public void removeRows(final int index, final int numberOfRows) {
        assertArgumentsAreValidAndWithinRange(index, numberOfRows);

        rows -= numberOfRows;

        if (!this.escalator.isAttached()) {
            return;
        }

        if (this.escalator.hasSomethingInDom()) {
            paintRemoveRows(index, numberOfRows);
        }
    }

    /**
     * Removes those row elements from the DOM that correspond to the given
     * range of logical indices. This may be fewer than {@code numberOfRows}
     * , even zero, if not all the removed rows are actually visible.
     * <p>
     * The implementation must call {@link #paintRemoveRow(Element, int)}
     * for each row that is removed from the DOM.
     * 
     * @param index
     *            the logical index of the first removed row
     * @param numberOfRows
     *            number of logical rows to remove
     */
    protected abstract void paintRemoveRows(final int index,
            final int numberOfRows);

    /**
     * Removes a row element from the DOM, invoking
     * {@link #getEscalatorUpdater()}
     * {@link EscalatorUpdater#preDetach(Row, Iterable) preDetach} and
     * {@link EscalatorUpdater#postDetach(Row, Iterable) postDetach} before
     * and after removing the row, respectively.
     * <p>
     * This method must be called for each removed DOM row by any
     * {@link #paintRemoveRows(int, int)} implementation.
     * 
     * @param tr
     *            the row element to remove.
     */
    protected void paintRemoveRow(final TableRowElement tr,
            final int logicalRowIndex) {

        this.escalator.flyweightRow.setup(tr, logicalRowIndex,
                this.escalator.columnConfiguration.getCalculatedColumnWidths());

        getEscalatorUpdater().preDetach(this.escalator.flyweightRow,
                this.escalator.flyweightRow.getCells());

        tr.removeFromParent();

        getEscalatorUpdater().postDetach(this.escalator.flyweightRow,
                this.escalator.flyweightRow.getCells());

        /*
         * the "assert" guarantees that this code is run only during
         * development/debugging.
         */
        assert this.escalator.flyweightRow.teardown();

    }

    protected void assertArgumentsAreValidAndWithinRange(final int index,
            final int numberOfRows) throws IllegalArgumentException,
            IndexOutOfBoundsException {
        if (numberOfRows < 1) {
            throw new IllegalArgumentException(
                    "Number of rows must be 1 or greater (was "
                            + numberOfRows + ")");
        }

        if (index < 0 || index + numberOfRows > getRowCount()) {
            throw new IndexOutOfBoundsException("The given "
                    + "row range (" + index + ".." + (index + numberOfRows)
                    + ") was outside of the current number of rows ("
                    + getRowCount() + ")");
        }
    }

    @Override
    public int getRowCount() {
        return rows;
    }

    /**
     * This method calculates the current row count directly from the DOM.
     * <p>
     * While Escalator is stable, this value should equal to
     * {@link #getRowCount()}, but while row counts are being updated, these
     * two values might differ for a short while.
     * <p>
     * Any extra content, such as spacers for the body, should not be
     * included in this count.
     * 
     * @since 7.5.0
     * 
     * @return the actual DOM count of rows
     */
    public abstract int getDomRowCount();

    /**
     * {@inheritDoc}
     * <p>
     * <em>Implementation detail:</em> This method does no DOM modifications
     * (i.e. is very cheap to call) if there is no data for columns when
     * this method is called.
     * 
     * @see #hasColumnAndRowData()
     */
    @Override
    public void insertRows(final int index, final int numberOfRows) {
        if (index < 0 || index > getRowCount()) {
            throw new IndexOutOfBoundsException("The given index (" + index
                    + ") was outside of the current number of rows (0.."
                    + getRowCount() + ")");
        }

        if (numberOfRows < 1) {
            throw new IllegalArgumentException(
                    "Number of rows must be 1 or greater (was "
                            + numberOfRows + ")");
        }

        rows += numberOfRows;

        /*
         * only add items in the DOM if the widget itself is attached to the
         * DOM. We can't calculate sizes otherwise.
         */
        if (this.escalator.isAttached()) {
            paintInsertRows(index, numberOfRows);

            if (rows == numberOfRows) {
                /*
                 * We are inserting the first rows in this container. We
                 * potentially need to set the widths for the cells for the
                 * first time.
                 */
                Map<Integer, Double> colWidths = new HashMap<Integer, Double>();
                for (int i = 0; i < this.escalator.getColumnConfiguration()
                        .getColumnCount(); i++) {
                    Double width = Double.valueOf(this.escalator.getColumnConfiguration()
                            .getColumnWidth(i));
                    Integer col = Integer.valueOf(i);
                    colWidths.put(col, width);
                }
                this.escalator.getColumnConfiguration().setColumnWidths(colWidths);
            }
        }
    }

    /**
     * Actually add rows into the DOM, now that everything can be
     * calculated.
     * 
     * @param visualIndex
     *            the DOM index to add rows into
     * @param numberOfRows
     *            the number of rows to insert
     * @return a list of the added row elements
     */
    protected abstract void paintInsertRows(final int visualIndex,
            final int numberOfRows);

    protected List<TableRowElement> paintInsertStaticRows(
            final int visualIndex, final int numberOfRows) {
        assert this.escalator.isAttached() : "Can't paint rows if Escalator is not attached";

        final List<TableRowElement> addedRows = new ArrayList<TableRowElement>();

        if (numberOfRows < 1) {
            return addedRows;
        }

        Node referenceRow;
        if (root.getChildCount() != 0 && visualIndex != 0) {
            // get the row node we're inserting stuff after
            referenceRow = root.getChild(visualIndex - 1);
        } else {
            // index is 0, so just prepend.
            referenceRow = null;
        }

        for (int row = visualIndex; row < visualIndex + numberOfRows; row++) {
            final TableRowElement tr = TableRowElement.as(DOM.createTR());
            addedRows.add(tr);
            tr.addClassName(getStylePrimaryName() + "-row");

            for (int col = 0; col < this.escalator.columnConfiguration.getColumnCount(); col++) {
                final double colWidth = this.escalator.columnConfiguration
                        .getColumnWidthActual(col);
                final TableCellElement cellElem = createCellElement(colWidth);
                tr.appendChild(cellElem);

                // Set stylename and position if new cell is frozen
                if (col < this.escalator.columnConfiguration.frozenColumns) {
                    cellElem.addClassName("frozen");
                    this.escalator.position.set(cellElem, this.escalator.scroller.lastScrollLeft, 0);
                }
                if (this.escalator.columnConfiguration.frozenColumns > 0
                        && col == this.escalator.columnConfiguration.frozenColumns - 1) {
                    cellElem.addClassName("last-frozen");
                }
            }

            referenceRow = paintInsertRow(referenceRow, tr, row);
        }
        reapplyRowWidths();

        recalculateSectionHeight();

        return addedRows;
    }

    /**
     * Inserts a single row into the DOM, invoking
     * {@link #getEscalatorUpdater()}
     * {@link EscalatorUpdater#preAttach(Row, Iterable) preAttach} and
     * {@link EscalatorUpdater#postAttach(Row, Iterable) postAttach} before
     * and after inserting the row, respectively. The row should have its
     * cells already inserted.
     * 
     * @param referenceRow
     *            the row after which to insert or null if insert as first
     * @param tr
     *            the row to be inserted
     * @param logicalRowIndex
     *            the logical index of the inserted row
     * @return the inserted row to be used as the new reference
     */
    protected Node paintInsertRow(Node referenceRow,
            final TableRowElement tr, int logicalRowIndex) {
        this.escalator.flyweightRow.setup(tr, logicalRowIndex,
                this.escalator.columnConfiguration.getCalculatedColumnWidths());

        getEscalatorUpdater().preAttach(this.escalator.flyweightRow,
                this.escalator.flyweightRow.getCells());

        referenceRow = insertAfterReferenceAndUpdateIt(root, tr,
                referenceRow);

        getEscalatorUpdater().postAttach(this.escalator.flyweightRow,
                this.escalator.flyweightRow.getCells());
        updater.update(this.escalator.flyweightRow, this.escalator.flyweightRow.getCells());

        /*
         * the "assert" guarantees that this code is run only during
         * development/debugging.
         */
        assert this.escalator.flyweightRow.teardown();
        return referenceRow;
    }

    private Node insertAfterReferenceAndUpdateIt(final Element parent,
            final Element elem, final Node referenceNode) {
        if (referenceNode != null) {
            parent.insertAfter(elem, referenceNode);
        } else {
            /*
             * referencenode being null means we have offset 0, i.e. make it
             * the first row
             */
            /*
             * TODO [[optimize]]: Is insertFirst or append faster for an
             * empty root?
             */
            parent.insertFirst(elem);
        }
        return elem;
    }

    abstract protected void recalculateSectionHeight();

    /**
     * Returns the height of all rows in the row container.
     */
    protected double calculateTotalRowHeight() {
        return getDefaultRowHeight() * getRowCount();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <em>Implementation detail:</em> This method does no DOM modifications
     * (i.e. is very cheap to call) if there is no data for columns when
     * this method is called.
     * 
     * @see #hasColumnAndRowData()
     */
    @Override
    // overridden because of JavaDoc
    public void refreshRows(final int index, final int numberOfRows) {
        Range rowRange = Range.withLength(index, numberOfRows);
        Range colRange = Range.withLength(0, this.escalator.getColumnConfiguration()
                .getColumnCount());
        refreshCells(rowRange, colRange);
    }

    protected abstract void refreshCells(Range logicalRowRange,
            Range colRange);

    void refreshRow(TableRowElement tr, int logicalRowIndex) {
        refreshRow(tr, logicalRowIndex, Range.withLength(0,
                this.escalator.getColumnConfiguration().getColumnCount()));
    }

    void refreshRow(final TableRowElement tr, final int logicalRowIndex,
            Range colRange) {
        this.escalator.flyweightRow.setup(tr, logicalRowIndex,
                this.escalator.columnConfiguration.getCalculatedColumnWidths());
        Iterable<FlyweightCell> cellsToUpdate = this.escalator.flyweightRow.getCells(
                colRange.getStart(), colRange.length());
        updater.update(this.escalator.flyweightRow, cellsToUpdate);

        /*
         * the "assert" guarantees that this code is run only during
         * development/debugging.
         */
        assert this.escalator.flyweightRow.teardown();
    }

    /**
     * Create and setup an empty cell element.
     * 
     * @param width
     *            the width of the cell, in pixels
     * 
     * @return a set-up empty cell element
     */
    public TableCellElement createCellElement(final double width) {
        final TableCellElement cellElem = TableCellElement.as(DOM
                .createElement(getCellElementTagName()));

        final double height = getDefaultRowHeight();
        assert height >= 0 : "defaultRowHeight was negative. There's a setter leak somewhere.";
        cellElem.getStyle().setHeight(height, Unit.PX);

        if (width >= 0) {
            cellElem.getStyle().setWidth(width, Unit.PX);
        }
        cellElem.addClassName(getStylePrimaryName() + "-cell");
        return cellElem;
    }

    @Override
    public TableRowElement getRowElement(int index) {
        return getTrByVisualIndex(index);
    }

    /**
     * Gets the child element that is visually at a certain index
     * 
     * @param index
     *            the index of the element to retrieve
     * @return the element at position {@code index}
     * @throws IndexOutOfBoundsException
     *             if {@code index} is not valid within {@link #root}
     */
    protected abstract TableRowElement getTrByVisualIndex(int index)
            throws IndexOutOfBoundsException;

    protected void paintRemoveColumns(final int offset,
            final int numberOfColumns) {
        for (int i = 0; i < getDomRowCount(); i++) {
            TableRowElement row = getTrByVisualIndex(i);
            this.escalator.flyweightRow.setup(row, i,
                    this.escalator.columnConfiguration.getCalculatedColumnWidths());

            Iterable<FlyweightCell> attachedCells = this.escalator.flyweightRow.getCells(
                    offset, numberOfColumns);
            getEscalatorUpdater().preDetach(this.escalator.flyweightRow, attachedCells);

            for (int j = 0; j < numberOfColumns; j++) {
                row.getCells().getItem(offset).removeFromParent();
            }

            Iterable<FlyweightCell> detachedCells = this.escalator.flyweightRow
                    .getUnattachedCells(offset, numberOfColumns);
            getEscalatorUpdater().postDetach(this.escalator.flyweightRow, detachedCells);

            assert this.escalator.flyweightRow.teardown();
        }
    }

    protected void paintInsertColumns(final int offset,
            final int numberOfColumns, boolean frozen) {

        for (int row = 0; row < getDomRowCount(); row++) {
            final TableRowElement tr = getTrByVisualIndex(row);
            int logicalRowIndex = getLogicalRowIndex(tr);
            paintInsertCells(tr, logicalRowIndex, offset, numberOfColumns);
        }
        reapplyRowWidths();

        if (frozen) {
            for (int col = offset; col < offset + numberOfColumns; col++) {
                setColumnFrozen(col, true);
            }
        }
    }

    /**
     * Inserts new cell elements into a single row element, invoking
     * {@link #getEscalatorUpdater()}
     * {@link EscalatorUpdater#preAttach(Row, Iterable) preAttach} and
     * {@link EscalatorUpdater#postAttach(Row, Iterable) postAttach} before
     * and after inserting the cells, respectively.
     * <p>
     * Precondition: The row must be already attached to the DOM and the
     * FlyweightCell instances corresponding to the new columns added to
     * {@code flyweightRow}.
     * 
     * @param tr
     *            the row in which to insert the cells
     * @param logicalRowIndex
     *            the index of the row
     * @param offset
     *            the index of the first cell
     * @param numberOfCells
     *            the number of cells to insert
     */
    private void paintInsertCells(final TableRowElement tr,
            int logicalRowIndex, final int offset, final int numberOfCells) {

        assert root.isOrHasChild(tr) : "The row must be attached to the document";

        this.escalator.flyweightRow.setup(tr, logicalRowIndex,
                this.escalator.columnConfiguration.getCalculatedColumnWidths());

        Iterable<FlyweightCell> cells = this.escalator.flyweightRow.getUnattachedCells(
                offset, numberOfCells);

        for (FlyweightCell cell : cells) {
            final double colWidth = this.escalator.columnConfiguration
                    .getColumnWidthActual(cell.getColumn());
            final TableCellElement cellElem = createCellElement(colWidth);
            cell.setElement(cellElem);
        }

        getEscalatorUpdater().preAttach(this.escalator.flyweightRow, cells);

        Node referenceCell;
        if (offset != 0) {
            referenceCell = tr.getChild(offset - 1);
        } else {
            referenceCell = null;
        }

        for (FlyweightCell cell : cells) {
            referenceCell = insertAfterReferenceAndUpdateIt(tr,
                    cell.getElement(), referenceCell);
        }

        getEscalatorUpdater().postAttach(this.escalator.flyweightRow, cells);
        getEscalatorUpdater().update(this.escalator.flyweightRow, cells);

        assert this.escalator.flyweightRow.teardown();
    }

    public void setColumnFrozen(int column, boolean frozen) {
        toggleFrozenColumnClass(column, frozen, "frozen");

        if (frozen) {
            updateFreezePosition(column, this.escalator.scroller.lastScrollLeft);
        }
    }

    private void toggleFrozenColumnClass(int column, boolean frozen,
            String className) {
        final NodeList<TableRowElement> childRows = root.getRows();

        for (int row = 0; row < childRows.getLength(); row++) {
            final TableRowElement tr = childRows.getItem(row);
            if (!rowCanBeFrozen(tr)) {
                continue;
            }

            TableCellElement cell = tr.getCells().getItem(column);
            if (frozen) {
                cell.addClassName(className);
            } else {
                cell.removeClassName(className);
                this.escalator.position.reset(cell);
            }
        }
    }

    public void setColumnLastFrozen(int column, boolean lastFrozen) {
        toggleFrozenColumnClass(column, lastFrozen, "last-frozen");
    }

    public void updateFreezePosition(int column, double scrollLeft) {
        final NodeList<TableRowElement> childRows = root.getRows();

        for (int row = 0; row < childRows.getLength(); row++) {
            final TableRowElement tr = childRows.getItem(row);

            if (rowCanBeFrozen(tr)) {
                TableCellElement cell = tr.getCells().getItem(column);
                this.escalator.position.set(cell, scrollLeft, 0);
            }
        }
    }

    /**
     * Checks whether a row is an element, or contains such elements, that
     * can be frozen.
     * <p>
     * In practice, this applies for all header and footer rows. For body
     * rows, it applies for all rows except spacer rows.
     * 
     * @since 7.5.0
     * 
     * @param tr
     *            the row element to check for if it is or has elements that
     *            can be frozen
     * @return <code>true</code> iff this the given element, or any of its
     *         descendants, can be frozen
     */
    abstract protected boolean rowCanBeFrozen(TableRowElement tr);

    /**
     * Iterates through all the cells in a column and returns the width of
     * the widest element in this RowContainer.
     * 
     * @param index
     *            the index of the column to inspect
     * @return the pixel width of the widest element in the indicated column
     */
    public double calculateMaxColWidth(int index) {
        TableRowElement row = TableRowElement.as(root
                .getFirstChildElement());
        double maxWidth = 0;
        while (row != null) {
            final TableCellElement cell = row.getCells().getItem(index);
            final boolean isVisible = !cell.getStyle().getDisplay()
                    .equals(Display.NONE.getCssName());
            if (isVisible) {
                maxWidth = Math.max(maxWidth, WidgetUtil
                        .getRequiredWidthBoundingClientRectDouble(cell));
            }
            row = TableRowElement.as(row.getNextSiblingElement());
        }
        return maxWidth;
    }

    /**
     * Reapplies all the cells' widths according to the calculated widths in
     * the column configuration.
     */
    public void reapplyColumnWidths() {
        Element row = root.getFirstChildElement();
        while (row != null) {
            // Only handle non-spacer rows
            if (!this.escalator.body.spacerContainer.isSpacer(row)) {
                Element cell = row.getFirstChildElement();
                int columnIndex = 0;
                while (cell != null) {
                    final double width = getCalculatedColumnWidthWithColspan(
                            cell, columnIndex);

                    /*
                     * TODO Should Escalator implement ProvidesResize at
                     * some point, this is where we need to do that.
                     */
                    cell.getStyle().setWidth(width, Unit.PX);

                    cell = cell.getNextSiblingElement();
                    columnIndex++;
                }
            }
            row = row.getNextSiblingElement();
        }

        reapplyRowWidths();
    }

    private double getCalculatedColumnWidthWithColspan(final Element cell,
            final int columnIndex) {
        final int colspan = cell.getPropertyInt(FlyweightCell.COLSPAN_ATTR);
        Range spannedColumns = Range.withLength(columnIndex, colspan);

        /*
         * Since browsers don't explode with overflowing colspans, escalator
         * shouldn't either.
         */
        if (spannedColumns.getEnd() > this.escalator.columnConfiguration.getColumnCount()) {
            spannedColumns = Range.between(columnIndex,
                    this.escalator.columnConfiguration.getColumnCount());
        }
        return this.escalator.columnConfiguration
                .getCalculatedColumnsWidth(spannedColumns);
    }

    /**
     * Applies the total length of the columns to each row element.
     * <p>
     * <em>Note:</em> In contrast to {@link #reapplyColumnWidths()}, this
     * method only modifies the width of the {@code <tr>} element, not the
     * cells within.
     */
    protected void reapplyRowWidths() {
        double rowWidth = this.escalator.columnConfiguration.calculateRowWidth();
        if (rowWidth < 0) {
            return;
        }

        Element row = root.getFirstChildElement();
        while (row != null) {
            // IF there is a rounding error when summing the columns, we
            // need to round the tr width up to ensure that columns fit and
            // do not wrap
            // E.g.122.95+123.25+103.75+209.25+83.52+88.57+263.45+131.21+126.85+113.13=1365.9299999999998
            // For this we must set 1365.93 or the last column will wrap
            row.getStyle().setWidth(WidgetUtil.roundSizeUp(rowWidth),
                    Unit.PX);
            row = row.getNextSiblingElement();
        }
    }

    /**
     * The primary style name for the container.
     * 
     * @param primaryStyleName
     *            the style name to use as prefix for all row and cell style
     *            names.
     */
    protected void setStylePrimaryName(String primaryStyleName) {
        String oldStyle = getStylePrimaryName();
        if (SharedUtil.equals(oldStyle, primaryStyleName)) {
            return;
        }

        this.primaryStyleName = primaryStyleName;

        // Update already rendered rows and cells
        Element row = root.getRows().getItem(0);
        while (row != null) {
            JsniUtil.setStylePrimaryName(row, primaryStyleName + "-row");
            Element cell = TableRowElement.as(row).getCells().getItem(0);
            while (cell != null) {
                assert TableCellElement.is(cell);
                JsniUtil.setStylePrimaryName(cell, primaryStyleName
                        + "-cell");
                cell = cell.getNextSiblingElement();
            }
            row = row.getNextSiblingElement();
        }
    }

    /**
     * Returns the primary style name of the container.
     * 
     * @return The primary style name or <code>null</code> if not set.
     */
    protected String getStylePrimaryName() {
        return primaryStyleName;
    }

    @Override
    public void setDefaultRowHeight(double px)
            throws IllegalArgumentException {
        if (px < 1) {
            throw new IllegalArgumentException("Height must be positive. "
                    + px + " was given.");
        }

        defaultRowHeightShouldBeAutodetected = false;
        defaultRowHeight = px;
        reapplyDefaultRowHeights();
    }

    @Override
    public double getDefaultRowHeight() {
        return defaultRowHeight;
    }

    /**
     * The default height of rows has (most probably) changed.
     * <p>
     * Make sure that the displayed rows with a default height are updated
     * in height and top position.
     * <p>
     * <em>Note:</em>This implementation should not call
     * {@link Escalator#recalculateElementSizes()} - it is done by the
     * discretion of the caller of this method.
     */
    protected abstract void reapplyDefaultRowHeights();

    protected void reapplyRowHeight(final TableRowElement tr,
            final double heightPx) {
        assert heightPx >= 0 : "Height must not be negative";

        Element cellElem = tr.getFirstChildElement();
        while (cellElem != null) {
            cellElem.getStyle().setHeight(heightPx, Unit.PX);
            cellElem = cellElem.getNextSiblingElement();
        }

        /*
         * no need to apply height to tr-element, it'll be resized
         * implicitly.
         */
    }

    protected void setRowPosition(final TableRowElement tr, final int x,
            final double y) {
        this.escalator.positions.set(tr, x, y);
    }

    /**
     * Returns <em>the assigned</em> top position for the given element.
     * <p>
     * <em>Note:</em> This method does not calculate what a row's top
     * position should be. It just returns an assigned value, correct or
     * not.
     * 
     * @param tr
     *            the table row element to measure
     * @return the current top position for {@code tr}
     * @see BodyRowContainerImpl#getRowTop(int)
     */
    public double getRowTop(final TableRowElement tr) {
        return this.escalator.positions.getTop(tr);
    }

    protected void removeRowPosition(TableRowElement tr) {
        this.escalator.positions.remove(tr);
    }

    public void autodetectRowHeightLater() {
        if (!autodetectRowHeightLaterQueued) {
            autodetectRowHeightLaterQueued = true;
            Scheduler.get().scheduleFinally(new Scheduler.ScheduledCommand() {
                @Override
                public void execute() {
                    if (AbstractRowContainer.this.escalator.isAttached()) {
                        autodetectRowHeightLaterQueued = false;
                        autodetectRowHeightNow();
                    }
                }
            });
        }
    }

    private Element detectionTr, cellElem;

    public void autodetectRowHeightNow() {
        if (!defaultRowHeightShouldBeAutodetected || !this.escalator.isAttached()) {
            return;
        }

        if (detectionTr == null) {
            detectionTr = DOM.createTR();
            detectionTr.setClassName(getStylePrimaryName() + "-row");
            cellElem = DOM.createElement(getCellElementTagName());
            cellElem.setClassName(getStylePrimaryName() + "-cell");
            cellElem.setInnerText("Ij");
            detectionTr.appendChild(cellElem);
        }

        root.appendChild(detectionTr);
        double boundingHeight = WidgetUtil
                .getRequiredHeightBoundingClientRectDouble(cellElem);
        root.removeChild(detectionTr);

        // Height lesser than 1px causes serious performance problems.
        if (boundingHeight >= 1) {
            defaultRowHeight = boundingHeight;
            defaultRowHeightShouldBeAutodetected = false;
            if (root.hasChildNodes()) {
                reapplyDefaultRowHeights();
                this.escalator.applyHeightByRows();
            }
        }
    }

    @Override
    public Cell getCell(final Element element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }

        /*
         * Ensure that element is not root nor the direct descendant of root
         * (a row) and ensure the element is inside the dom hierarchy of the
         * root element. If not, return.
         */
        if (root == element || element.getParentElement() == root
                || !root.isOrHasChild(element)) {
            return null;
        }

        /*
         * Ensure element is the cell element by iterating up the DOM
         * hierarchy until reaching cell element.
         */
        Element cellElementCandidate = element;
        while (cellElementCandidate.getParentElement().getParentElement() != root) {
            cellElementCandidate = cellElementCandidate.getParentElement();
        }
        final TableCellElement cellElement = TableCellElement
                .as(cellElementCandidate);

        // Find dom column
        int domColumnIndex = -1;
        for (Element e = cellElement; e != null; e = e
                .getPreviousSiblingElement()) {
            domColumnIndex++;
        }

        // Find dom row
        int domRowIndex = -1;
        for (Element e = cellElement.getParentElement(); e != null; e = e
                .getPreviousSiblingElement()) {
            domRowIndex++;
        }

        return new Cell(domRowIndex, domColumnIndex, cellElement);
    }

    double measureCellWidth(TableCellElement cell, boolean withContent) {
        /*
         * To get the actual width of the contents, we need to get the cell
         * content without any hardcoded height or width.
         * 
         * But we don't want to modify the existing column, because that
         * might trigger some unnecessary listeners and whatnot. So,
         * instead, we make a deep clone of that cell, but without any
         * explicit dimensions, and measure that instead.
         */

        TableCellElement cellClone = TableCellElement.as((Element) cell
                .cloneNode(withContent));
        cellClone.getStyle().clearHeight();
        cellClone.getStyle().clearWidth();

        cell.getParentElement().insertBefore(cellClone, cell);
        double requiredWidth = WidgetUtil
                .getRequiredWidthBoundingClientRectDouble(cellClone);
        if (BrowserInfo.get().isIE()) {
            /*
             * IE browsers have some issues with subpixels. Occasionally
             * content is overflown even if not necessary. Increase the
             * counted required size by 0.01 just to be on the safe side.
             */
            requiredWidth += 0.01;
        }

        cellClone.removeFromParent();

        return requiredWidth;
    }

    /**
     * Gets the minimum width needed to display the cell properly.
     * 
     * @param colIndex
     *            index of column to measure
     * @param withContent
     *            <code>true</code> if content is taken into account,
     *            <code>false</code> if not
     * @return cell width needed for displaying correctly
     */
    double measureMinCellWidth(int colIndex, boolean withContent) {
        assert this.escalator.isAttached() : "Can't measure max width of cell, since Escalator is not attached to the DOM.";

        double minCellWidth = -1;
        NodeList<TableRowElement> rows = root.getRows();

        for (int row = 0; row < rows.getLength(); row++) {

            TableCellElement cell = rows.getItem(row).getCells()
                    .getItem(colIndex);

            if (cell != null && !cellIsPartOfSpan(cell)) {
                double cellWidth = measureCellWidth(cell, withContent);
                minCellWidth = Math.max(minCellWidth, cellWidth);
            }
        }

        return minCellWidth;
    }

    private boolean cellIsPartOfSpan(TableCellElement cell) {
        boolean cellHasColspan = cell.getColSpan() > 1;
        boolean cellIsHidden = Display.NONE.getCssName().equals(
                cell.getStyle().getDisplay());
        return cellHasColspan || cellIsHidden;
    }

    void refreshColumns(int index, int numberOfColumns) {
        if (getRowCount() > 0) {
            Range rowRange = Range.withLength(0, getRowCount());
            Range colRange = Range.withLength(index, numberOfColumns);
            refreshCells(rowRange, colRange);
        }
    }

    /**
     * The height of this table section.
     * <p>
     * Note that {@link Escalator#getBody() the body} will calculate its
     * height, while the others will return a precomputed value.
     * 
     * @since 7.5.0
     * 
     * @return the height of this table section
     */
    protected abstract double getHeightOfSection();

    protected int getLogicalRowIndex(final TableRowElement tr) {
        return tr.getSectionRowIndex();
    };

}