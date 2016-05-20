package com.vaadin.client.widgets.escalator;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.ColumnConfiguration;
import com.vaadin.shared.ui.grid.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class ColumnConfigurationImpl implements ColumnConfiguration {
    /**
     * 
     */
    private final Escalator escalator;

    /**
     * @param escalator
     */
    ColumnConfigurationImpl(Escalator escalator) {
        this.escalator = escalator;
    }

    public class Column {
        public static final double DEFAULT_COLUMN_WIDTH_PX = 100;

        private double definedWidth = -1;
        private double calculatedWidth = DEFAULT_COLUMN_WIDTH_PX;
        private boolean measuringRequested = false;

        public void setWidth(double px) {
            definedWidth = px;

            if (px < 0) {
                if (ColumnConfigurationImpl.this.escalator.isAttached()) {
                    calculateWidth();
                } else {
                    /*
                     * the column's width is calculated at Escalator.onLoad
                     * via measureAndSetWidthIfNeeded!
                     */
                    measuringRequested = true;
                }
            } else {
                calculatedWidth = px;
            }
        }

        public double getDefinedWidth() {
            return definedWidth;
        }

        /**
         * Returns the actual width in the DOM.
         * 
         * @return the width in pixels in the DOM. Returns -1 if the column
         *         needs measuring, but has not been yet measured
         */
        public double getCalculatedWidth() {
            /*
             * This might return an untrue value (e.g. during init/onload),
             * since we haven't had a proper chance to actually calculate
             * widths yet.
             * 
             * This is fixed during Escalator.onLoad, by the call to
             * "measureAndSetWidthIfNeeded", which fixes "everything".
             */
            if (!measuringRequested) {
                return calculatedWidth;
            } else {
                return -1;
            }
        }

        /**
         * Checks if the column needs measuring, and then measures it.
         * <p>
         * Called by {@link Escalator#onLoad()}.
         */
        public boolean measureAndSetWidthIfNeeded() {
            assert ColumnConfigurationImpl.this.escalator.isAttached() : "Column.measureAndSetWidthIfNeeded() was called even though Escalator was not attached!";

            if (measuringRequested) {
                measuringRequested = false;
                setWidth(definedWidth);
                return true;
            }
            return false;
        }

        private void calculateWidth() {
            calculatedWidth = getMaxCellWidth(columns.indexOf(this));
        }
    }

    final List<Column> columns = new ArrayList<Column>();
    int frozenColumns = 0;

    /*
     * TODO: this is a bit of a duplicate functionality with the
     * Column.calculatedWidth caching. Probably should use one or the other,
     * not both
     */
    /**
     * A cached array of all the calculated column widths.
     * 
     * @see #getCalculatedColumnWidths()
     */
    private double[] widthsArray = null;

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
    public void removeColumns(final int index, final int numberOfColumns) {
        // Validate
        assertArgumentsAreValidAndWithinRange(index, numberOfColumns);

        // Move the horizontal scrollbar to the left, if removed columns are
        // to the left of the viewport
        removeColumnsAdjustScrollbar(index, numberOfColumns);

        // Remove from DOM
        this.escalator.header.paintRemoveColumns(index, numberOfColumns);
        this.escalator.body.paintRemoveColumns(index, numberOfColumns);
        this.escalator.footer.paintRemoveColumns(index, numberOfColumns);

        // Remove from bookkeeping
        this.escalator.flyweightRow.removeCells(index, numberOfColumns);
        columns.subList(index, index + numberOfColumns).clear();

        // Adjust frozen columns
        if (index < getFrozenColumnCount()) {
            if (index + numberOfColumns < frozenColumns) {
                /*
                 * Last removed column was frozen, meaning that all removed
                 * columns were frozen. Just decrement the number of frozen
                 * columns accordingly.
                 */
                frozenColumns -= numberOfColumns;
            } else {
                /*
                 * If last removed column was not frozen, we have removed
                 * columns beyond the frozen range, so all remaining frozen
                 * columns are to the left of the removed columns.
                 */
                frozenColumns = index;
            }
        }

        this.escalator.scroller.recalculateScrollbarsForVirtualViewport();
        this.escalator.body.verifyEscalatorCount();

        if (this.escalator.getColumnConfiguration().getColumnCount() > 0) {
            reapplyRowWidths(this.escalator.header);
            reapplyRowWidths(this.escalator.body);
            reapplyRowWidths(this.escalator.footer);
        }

        /*
         * Colspans make any kind of automatic clever content re-rendering
         * impossible: As soon as anything has colspans, removing one might
         * reveal further colspans, modifying the DOM structure once again,
         * ending in a cascade of updates. Because we don't know how the
         * data is updated.
         * 
         * So, instead, we don't do anything. The client code is responsible
         * for re-rendering the content (if so desired). Everything Just
         * Works (TM) if colspans aren't used.
         */
    }

    private void reapplyRowWidths(AbstractRowContainer container) {
        if (container.getRowCount() > 0) {
            container.reapplyRowWidths();
        }
    }

    private void removeColumnsAdjustScrollbar(int index, int numberOfColumns) {
        if (this.escalator.horizontalScrollbar.getOffsetSize() >= this.escalator.horizontalScrollbar
                .getScrollSize()) {
            return;
        }

        double leftPosOfFirstColumnToRemove = getCalculatedColumnsWidth(Range
                .between(0, index));
        double widthOfColumnsToRemove = getCalculatedColumnsWidth(Range
                .withLength(index, numberOfColumns));

        double scrollLeft = this.escalator.horizontalScrollbar.getScrollPos();

        if (scrollLeft <= leftPosOfFirstColumnToRemove) {
            /*
             * viewport is scrolled to the left of the first removed column,
             * so there's no need to adjust anything
             */
            return;
        }

        double adjustedScrollLeft = Math.max(leftPosOfFirstColumnToRemove,
                scrollLeft - widthOfColumnsToRemove);
        this.escalator.horizontalScrollbar.setScrollPos(adjustedScrollLeft);
    }

    /**
     * Calculate the width of a row, as the sum of columns' widths.
     * 
     * @return the width of a row, in pixels
     */
    public double calculateRowWidth() {
        return getCalculatedColumnsWidth(Range.between(0, getColumnCount()));
    }

    private void assertArgumentsAreValidAndWithinRange(final int index,
            final int numberOfColumns) {
        if (numberOfColumns < 1) {
            throw new IllegalArgumentException(
                    "Number of columns can't be less than 1 (was "
                            + numberOfColumns + ")");
        }

        if (index < 0 || index + numberOfColumns > getColumnCount()) {
            throw new IndexOutOfBoundsException("The given "
                    + "column range (" + index + ".."
                    + (index + numberOfColumns)
                    + ") was outside of the current "
                    + "number of columns (" + getColumnCount() + ")");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <em>Implementation detail:</em> This method does no DOM modifications
     * (i.e. is very cheap to call) if there is no data for rows when this
     * method is called.
     * 
     * @see #hasColumnAndRowData()
     */
    @Override
    public void insertColumns(final int index, final int numberOfColumns) {
        // Validate
        if (index < 0 || index > getColumnCount()) {
            throw new IndexOutOfBoundsException("The given index(" + index
                    + ") was outside of the current number of columns (0.."
                    + getColumnCount() + ")");
        }

        if (numberOfColumns < 1) {
            throw new IllegalArgumentException(
                    "Number of columns must be 1 or greater (was "
                            + numberOfColumns);
        }

        // Add to bookkeeping
        this.escalator.flyweightRow.addCells(index, numberOfColumns);
        for (int i = 0; i < numberOfColumns; i++) {
            columns.add(index, new Column());
        }

        // Adjust frozen columns
        boolean frozen = index < frozenColumns;
        if (frozen) {
            frozenColumns += numberOfColumns;
        }

        // this needs to be before the scrollbar adjustment.
        boolean scrollbarWasNeeded = this.escalator.horizontalScrollbar.getOffsetSize() < this.escalator.horizontalScrollbar
                .getScrollSize();
        this.escalator.scroller.recalculateScrollbarsForVirtualViewport();
        boolean scrollbarIsNowNeeded = this.escalator.horizontalScrollbar.getOffsetSize() < this.escalator.horizontalScrollbar
                .getScrollSize();
        if (!scrollbarWasNeeded && scrollbarIsNowNeeded) {
            this.escalator.body.verifyEscalatorCount();
        }

        // Add to DOM
        this.escalator.header.paintInsertColumns(index, numberOfColumns, frozen);
        this.escalator.body.paintInsertColumns(index, numberOfColumns, frozen);
        this.escalator.footer.paintInsertColumns(index, numberOfColumns, frozen);

        // fix initial width
        if (this.escalator.header.getRowCount() > 0 || this.escalator.body.getRowCount() > 0
                || this.escalator.footer.getRowCount() > 0) {

            Map<Integer, Double> colWidths = new HashMap<Integer, Double>();
            Double width = Double.valueOf(Column.DEFAULT_COLUMN_WIDTH_PX);
            for (int i = index; i < index + numberOfColumns; i++) {
                Integer col = Integer.valueOf(i);
                colWidths.put(col, width);
            }
            this.escalator.getColumnConfiguration().setColumnWidths(colWidths);
        }

        // Adjust scrollbar
        double pixelsToInsertedColumn = this.escalator.columnConfiguration
                .getCalculatedColumnsWidth(Range.withLength(0, index));
        final boolean columnsWereAddedToTheLeftOfViewport = this.escalator.scroller.lastScrollLeft > pixelsToInsertedColumn;

        if (columnsWereAddedToTheLeftOfViewport) {
            double insertedColumnsWidth = this.escalator.columnConfiguration
                    .getCalculatedColumnsWidth(Range.withLength(index,
                            numberOfColumns));
            this.escalator.horizontalScrollbar.setScrollPos(this.escalator.scroller.lastScrollLeft
                    + insertedColumnsWidth);
        }

        /*
         * Colspans make any kind of automatic clever content re-rendering
         * impossible: As soon as anything has colspans, adding one might
         * affect surrounding colspans, modifying the DOM structure once
         * again, ending in a cascade of updates. Because we don't know how
         * the data is updated.
         * 
         * So, instead, we don't do anything. The client code is responsible
         * for re-rendering the content (if so desired). Everything Just
         * Works (TM) if colspans aren't used.
         */
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public void setFrozenColumnCount(int count)
            throws IllegalArgumentException {
        if (count < 0 || count > getColumnCount()) {
            throw new IllegalArgumentException(
                    "count must be between 0 and the current number of columns ("
                            + getColumnCount() + ")");
        }
        int oldCount = frozenColumns;
        if (count == oldCount) {
            return;
        }

        frozenColumns = count;

        if (this.escalator.hasSomethingInDom()) {
            // Are we freezing or unfreezing?
            boolean frozen = count > oldCount;

            int firstAffectedCol;
            int firstUnaffectedCol;

            if (frozen) {
                firstAffectedCol = oldCount;
                firstUnaffectedCol = count;
            } else {
                firstAffectedCol = count;
                firstUnaffectedCol = oldCount;
            }

            if (oldCount > 0) {
                this.escalator.header.setColumnLastFrozen(oldCount - 1, false);
                this.escalator.body.setColumnLastFrozen(oldCount - 1, false);
                this.escalator.footer.setColumnLastFrozen(oldCount - 1, false);
            }
            if (count > 0) {
                this.escalator.header.setColumnLastFrozen(count - 1, true);
                this.escalator.body.setColumnLastFrozen(count - 1, true);
                this.escalator.footer.setColumnLastFrozen(count - 1, true);
            }

            for (int col = firstAffectedCol; col < firstUnaffectedCol; col++) {
                this.escalator.header.setColumnFrozen(col, frozen);
                this.escalator.body.setColumnFrozen(col, frozen);
                this.escalator.footer.setColumnFrozen(col, frozen);
            }
        }

        this.escalator.scroller.recalculateScrollbarsForVirtualViewport();
    }

    @Override
    public int getFrozenColumnCount() {
        return frozenColumns;
    }

    @Override
    public void setColumnWidth(int index, double px)
            throws IllegalArgumentException {
        setColumnWidths(Collections.singletonMap(Integer.valueOf(index),
                Double.valueOf(px)));
    }

    @Override
    public void setColumnWidths(Map<Integer, Double> indexWidthMap)
            throws IllegalArgumentException {

        if (indexWidthMap == null) {
            throw new IllegalArgumentException("indexWidthMap was null");
        }

        if (indexWidthMap.isEmpty()) {
            return;
        }

        for (Entry<Integer, Double> entry : indexWidthMap.entrySet()) {
            int index = entry.getKey().intValue();
            double width = entry.getValue().doubleValue();

            checkValidColumnIndex(index);

            // Not all browsers will accept any fractional size..
            width = WidgetUtil.roundSizeDown(width);
            columns.get(index).setWidth(width);

        }

        widthsArray = null;
        this.escalator.header.reapplyColumnWidths();
        this.escalator.body.reapplyColumnWidths();
        this.escalator.footer.reapplyColumnWidths();

        this.escalator.recalculateElementSizes();
    }

    private void checkValidColumnIndex(int index)
            throws IllegalArgumentException {
        if (!Range.withLength(0, getColumnCount()).contains(index)) {
            throw new IllegalArgumentException("The given column index ("
                    + index + ") does not exist");
        }
    }

    @Override
    public double getColumnWidth(int index) throws IllegalArgumentException {
        checkValidColumnIndex(index);
        return columns.get(index).getDefinedWidth();
    }

    @Override
    public double getColumnWidthActual(int index) {
        return columns.get(index).getCalculatedWidth();
    }

    private double getMaxCellWidth(int colIndex)
            throws IllegalArgumentException {
        double headerWidth = this.escalator.header.measureMinCellWidth(colIndex, true);
        double bodyWidth = this.escalator.body.measureMinCellWidth(colIndex, true);
        double footerWidth = this.escalator.footer.measureMinCellWidth(colIndex, true);

        double maxWidth = Math.max(headerWidth,
                Math.max(bodyWidth, footerWidth));
        assert maxWidth >= 0 : "Got a negative max width for a column, which should be impossible.";
        return maxWidth;
    }

    double getMinCellWidth(int colIndex)
            throws IllegalArgumentException {
        double headerWidth = this.escalator.header.measureMinCellWidth(colIndex, false);
        double bodyWidth = this.escalator.body.measureMinCellWidth(colIndex, false);
        double footerWidth = this.escalator.footer.measureMinCellWidth(colIndex, false);

        double minWidth = Math.max(headerWidth,
                Math.max(bodyWidth, footerWidth));
        assert minWidth >= 0 : "Got a negative max width for a column, which should be impossible.";
        return minWidth;
    }

    /**
     * Calculates the width of the columns in a given range.
     * 
     * @param columns
     *            the columns to calculate
     * @return the total width of the columns in the given
     *         <code>columns</code>
     */
    double getCalculatedColumnsWidth(final Range columns) {
        /*
         * This is an assert instead of an exception, since this is an
         * internal method.
         */
        assert columns.isSubsetOf(Range.between(0, getColumnCount())) : "Range "
                + "was outside of current column range (i.e.: "
                + Range.between(0, getColumnCount())
                + ", but was given :"
                + columns;

        double sum = 0;
        for (int i = columns.getStart(); i < columns.getEnd(); i++) {
            double columnWidthActual = getColumnWidthActual(i);
            sum += columnWidthActual;
        }
        return sum;
    }

    double[] getCalculatedColumnWidths() {
        if (widthsArray == null || widthsArray.length != getColumnCount()) {
            widthsArray = new double[getColumnCount()];
            for (int i = 0; i < columns.size(); i++) {
                widthsArray[i] = columns.get(i).getCalculatedWidth();
            }
        }
        return widthsArray;
    }

    @Override
    public void refreshColumns(int index, int numberOfColumns)
            throws IndexOutOfBoundsException, IllegalArgumentException {
        if (numberOfColumns < 1) {
            throw new IllegalArgumentException(
                    "Number of columns must be 1 or greater (was "
                            + numberOfColumns + ")");
        }

        if (index < 0 || index + numberOfColumns > getColumnCount()) {
            throw new IndexOutOfBoundsException("The given "
                    + "column range (" + index + ".."
                    + (index + numberOfColumns)
                    + ") was outside of the current number of columns ("
                    + getColumnCount() + ")");
        }

        this.escalator.header.refreshColumns(index, numberOfColumns);
        this.escalator.body.refreshColumns(index, numberOfColumns);
        this.escalator.footer.refreshColumns(index, numberOfColumns);
    }
}