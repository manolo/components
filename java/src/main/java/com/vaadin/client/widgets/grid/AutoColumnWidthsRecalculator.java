package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;

import com.vaadin.client.BrowserInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @see Grid#autoColumnWidthsRecalculator
 */
class AutoColumnWidthsRecalculator<T> {

    private final Grid<T> grid;

    /**
     * @param grid
     */
    AutoColumnWidthsRecalculator(Grid<T> grid) {
        this.grid = grid;
    }

    double lastCalculatedInnerWidth = -1;

    private final ScheduledCommand calculateCommand = new ScheduledCommand() {

        @Override
        public void execute() {
            if (!isScheduled) {
                // something cancelled running this.
                return;
            }

            if (AutoColumnWidthsRecalculator.this.grid.header.markAsDirty || AutoColumnWidthsRecalculator.this.grid.footer.markAsDirty) {
                if (rescheduleCount < 10) {
                    /*
                     * Headers and footers are rendered as finally, this way
                     * we re-schedule this loop as finally, at the end of
                     * the queue, so that the headers have a chance to
                     * render themselves.
                     */
                    Scheduler.get().scheduleFinally(this);
                    rescheduleCount++;
                } else {
                    /*
                     * We've tried too many times reschedule finally. Seems
                     * like something is being deferred. Let the queue
                     * execute and retry again.
                     */
                    rescheduleCount = 0;
                    Scheduler.get().scheduleDeferred(this);
                }
            } else if (AutoColumnWidthsRecalculator.this.grid.dataIsBeingFetched) {
                Scheduler.get().scheduleDeferred(this);
            } else {
                calculate();
            }
        }
    };

    private int rescheduleCount = 0;
    private boolean isScheduled;

    /**
     * Calculates and applies column widths, taking into account fixed
     * widths and column expand rules
     * 
     * @param immediately
     *            <code>true</code> if the widths should be executed
     *            immediately (ignoring lazy loading completely), or
     *            <code>false</code> if the command should be run after a
     *            while (duplicate non-immediately invocations are ignored).
     * @see Column#setWidth(double)
     * @see Column#setExpandRatio(int)
     * @see Column#setMinimumWidth(double)
     * @see Column#setMaximumWidth(double)
     */
    public void schedule() {
        if (!isScheduled && this.grid.isAttached()) {
            isScheduled = true;
            Scheduler.get().scheduleFinally(calculateCommand);
        }
    }

    private void calculate() {
        isScheduled = false;
        rescheduleCount = 0;

        assert !this.grid.dataIsBeingFetched : "Trying to calculate column widths even though data is still being fetched.";

        if (columnsAreGuaranteedToBeWiderThanGrid()) {
            applyColumnWidths();
        } else {
            applyColumnWidthsWithExpansion();
        }

        // Update latest width to prevent recalculate on height change.
        lastCalculatedInnerWidth = this.grid.escalator.getInnerWidth();
    }

    private boolean columnsAreGuaranteedToBeWiderThanGrid() {
        double freeSpace = this.grid.escalator.getInnerWidth();
        for (Column<?, ?> column : this.grid.getVisibleColumns()) {
            if (column.getWidth() >= 0) {
                freeSpace -= column.getWidth();
            } else if (column.getMinimumWidth() >= 0) {
                freeSpace -= column.getMinimumWidth();
            }
        }
        return freeSpace < 0;
    }

    @SuppressWarnings("boxing")
    private void applyColumnWidths() {

        /* Step 1: Apply all column widths as they are. */

        Map<Integer, Double> selfWidths = new LinkedHashMap<Integer, Double>();
        List<Column<?, T>> columns = this.grid.getVisibleColumns();
        for (int index = 0; index < columns.size(); index++) {
            selfWidths.put(index, columns.get(index).getWidth());
        }
        grid.escalator.getColumnConfiguration().setColumnWidths(
                selfWidths);

        /*
         * Step 2: Make sure that each column ends up obeying their min/max
         * width constraints if defined as autowidth. If constraints are
         * violated, fix it.
         */

        Map<Integer, Double> constrainedWidths = new LinkedHashMap<Integer, Double>();
        for (int index = 0; index < columns.size(); index++) {
            Column<?, T> column = columns.get(index);

            boolean hasAutoWidth = column.getWidth() < 0;
            if (!hasAutoWidth) {
                continue;
            }

            // TODO: bug: these don't honor the CSS max/min. :(
            double actualWidth = column.getWidthActual();
            if (actualWidth < getMinWidth(column)) {
                constrainedWidths.put(index, column.getMinimumWidth());
            } else if (actualWidth > getMaxWidth(column)) {
                constrainedWidths.put(index, column.getMaximumWidth());
            }
        }
        grid.escalator.getColumnConfiguration().setColumnWidths(
                constrainedWidths);
    }

    private void applyColumnWidthsWithExpansion() {
        boolean defaultExpandRatios = true;
        int totalRatios = 0;
        double reservedPixels = 0;
        final Set<Column<?, T>> columnsToExpand = new HashSet<Column<?, T>>();
        List<Column<?, T>> nonFixedColumns = new ArrayList<Column<?, T>>();
        Map<Integer, Double> columnSizes = new HashMap<Integer, Double>();
        final List<Column<?, T>> visibleColumns = this.grid.getVisibleColumns();

        /*
         * Set all fixed widths and also calculate the size-to-fit widths
         * for the autocalculated columns.
         * 
         * This way we know with how many pixels we have left to expand the
         * rest.
         */
        for (Column<?, T> column : visibleColumns) {
            final double widthAsIs = column.getWidth();
            final boolean isFixedWidth = widthAsIs >= 0;
            // Check for max width just to be sure we don't break the limits
            final double widthFixed = Math.max(
                    Math.min(getMaxWidth(column), widthAsIs),
                    column.getMinimumWidth());
            defaultExpandRatios = defaultExpandRatios
                    && (column.getExpandRatio() == -1 || column == this.grid.selectionColumn);

            if (isFixedWidth) {
                columnSizes.put(visibleColumns.indexOf(column), widthFixed);
                reservedPixels += widthFixed;
            } else {
                nonFixedColumns.add(column);
                columnSizes.put(visibleColumns.indexOf(column), -1.0d);
            }
        }

        setColumnSizes(columnSizes);

        for (Column<?, T> column : nonFixedColumns) {
            final int expandRatio = (defaultExpandRatios ? 1 : column
                    .getExpandRatio());
            final double maxWidth = getMaxWidth(column);
            final double newWidth = Math.min(maxWidth,
                    column.getWidthActual());
            boolean shouldExpand = newWidth < maxWidth && expandRatio > 0
                    && column != this.grid.selectionColumn;
            if (shouldExpand) {
                totalRatios += expandRatio;
                columnsToExpand.add(column);
            }
            reservedPixels += newWidth;
            columnSizes.put(visibleColumns.indexOf(column), newWidth);
        }

        /*
         * Now that we know how many pixels we need at the very least, we
         * can distribute the remaining pixels to all columns according to
         * their expand ratios.
         */
        double pixelsToDistribute = this.grid.escalator.getInnerWidth()
                - reservedPixels;
        if (pixelsToDistribute <= 0 || totalRatios <= 0) {
            if (pixelsToDistribute <= 0) {
                // Set column sizes for expanding columns
                setColumnSizes(columnSizes);
            }

            return;
        }

        /*
         * Check for columns that hit their max width. Adjust
         * pixelsToDistribute and totalRatios accordingly. Recheck. Stop
         * when no new columns hit their max width
         */
        boolean aColumnHasMaxedOut;
        do {
            aColumnHasMaxedOut = false;
            final double widthPerRatio = pixelsToDistribute / totalRatios;
            final Iterator<Column<?, T>> i = columnsToExpand.iterator();
            while (i.hasNext()) {
                final Column<?, T> column = i.next();
                final int expandRatio = getExpandRatio(column,
                        defaultExpandRatios);
                final int columnIndex = visibleColumns.indexOf(column);
                final double autoWidth = columnSizes.get(columnIndex);
                final double maxWidth = getMaxWidth(column);
                double expandedWidth = autoWidth + widthPerRatio
                        * expandRatio;

                if (maxWidth <= expandedWidth) {
                    i.remove();
                    totalRatios -= expandRatio;
                    aColumnHasMaxedOut = true;
                    pixelsToDistribute -= maxWidth - autoWidth;
                    columnSizes.put(columnIndex, maxWidth);
                }
            }
        } while (aColumnHasMaxedOut);

        if (totalRatios <= 0 && columnsToExpand.isEmpty()) {
            setColumnSizes(columnSizes);
            return;
        }
        assert pixelsToDistribute > 0 : "We've run out of pixels to distribute ("
                + pixelsToDistribute
                + "px to "
                + totalRatios
                + " ratios between " + columnsToExpand.size() + " columns)";
        assert totalRatios > 0 && !columnsToExpand.isEmpty() : "Bookkeeping out of sync. Ratios: "
                + totalRatios + " Columns: " + columnsToExpand.size();

        /*
         * If we still have anything left, distribute the remaining pixels
         * to the remaining columns.
         */
        final double widthPerRatio;
        int leftOver = 0;
        if (BrowserInfo.get().isIE8() || BrowserInfo.get().isIE9()
                || BrowserInfo.getBrowserString().contains("PhantomJS")) {
            // These browsers report subpixels as integers. this usually
            // results into issues..
            widthPerRatio = (int) (pixelsToDistribute / totalRatios);
            leftOver = (int) (pixelsToDistribute - widthPerRatio
                    * totalRatios);
        } else {
            widthPerRatio = pixelsToDistribute / totalRatios;
        }
        for (Column<?, T> column : columnsToExpand) {
            final int expandRatio = getExpandRatio(column,
                    defaultExpandRatios);
            final int columnIndex = visibleColumns.indexOf(column);
            final double autoWidth = columnSizes.get(columnIndex);
            double totalWidth = autoWidth + widthPerRatio * expandRatio;
            if (leftOver > 0) {
                totalWidth += 1;
                leftOver--;
            }
            columnSizes.put(columnIndex, totalWidth);

            totalRatios -= expandRatio;
        }
        assert totalRatios == 0 : "Bookkeeping error: there were still some ratios left undistributed: "
                + totalRatios;

        /*
         * Check the guarantees for minimum width and scoot back the columns
         * that don't care.
         */
        boolean minWidthsCausedReflows;
        do {
            minWidthsCausedReflows = false;

            /*
             * First, let's check which columns were too cramped, and expand
             * them. Also keep track on how many pixels we grew - we need to
             * remove those pixels from other columns
             */
            double pixelsToRemoveFromOtherColumns = 0;
            for (Column<?, T> column : visibleColumns) {
                /*
                 * We can't iterate over columnsToExpand, even though that
                 * would be convenient. This is because some column without
                 * an expand ratio might still have a min width - those
                 * wouldn't show up in that set.
                 */

                double minWidth = getMinWidth(column);
                final int columnIndex = visibleColumns.indexOf(column);
                double currentWidth = columnSizes.get(columnIndex);
                boolean hasAutoWidth = column.getWidth() < 0;
                if (hasAutoWidth && currentWidth < minWidth) {
                    columnSizes.put(columnIndex, minWidth);
                    pixelsToRemoveFromOtherColumns += (minWidth - currentWidth);
                    minWidthsCausedReflows = true;

                    /*
                     * Remove this column form the set if it exists. This
                     * way we make sure that it doesn't get shrunk in the
                     * next step.
                     */
                    columnsToExpand.remove(column);
                }
            }

            /*
             * Now we need to shrink the remaining columns according to
             * their ratios. Recalculate the sum of remaining ratios.
             */
            totalRatios = 0;
            for (Column<?, ?> column : columnsToExpand) {
                totalRatios += getExpandRatio(column, defaultExpandRatios);
            }
            final double pixelsToRemovePerRatio = pixelsToRemoveFromOtherColumns
                    / totalRatios;
            for (Column<?, T> column : columnsToExpand) {
                final double pixelsToRemove = pixelsToRemovePerRatio
                        * getExpandRatio(column, defaultExpandRatios);
                int colIndex = visibleColumns.indexOf(column);
                columnSizes.put(colIndex, columnSizes.get(colIndex)
                        - pixelsToRemove);
            }

        } while (minWidthsCausedReflows);

        // Finally set all the column sizes.
        setColumnSizes(columnSizes);
    }

    private void setColumnSizes(Map<Integer, Double> columnSizes) {
        // Set all widths at once
        this.grid.escalator.getColumnConfiguration().setColumnWidths(columnSizes);
    }

    private int getExpandRatio(Column<?, ?> column,
            boolean defaultExpandRatios) {
        int expandRatio = column.getExpandRatio();
        if (expandRatio > 0) {
            return expandRatio;
        } else if (expandRatio < 0) {
            assert defaultExpandRatios : "No columns should've expanded";
            return 1;
        } else {
            assert false : "this method should've not been called at all if expandRatio is 0";
            return 0;
        }
    }

    /**
     * Returns the maximum width of the column, or {@link Double#MAX_VALUE}
     * if defined as negative.
     */
    private double getMaxWidth(Column<?, ?> column) {
        double maxWidth = column.getMaximumWidth();
        if (maxWidth >= 0) {
            return maxWidth;
        } else {
            return Double.MAX_VALUE;
        }
    }

    /**
     * Returns the minimum width of the column, or {@link Double#MIN_VALUE}
     * if defined as negative.
     */
    private double getMinWidth(Column<?, ?> column) {
        double minWidth = column.getMinimumWidth();
        if (minWidth >= 0) {
            return minWidth;
        } else {
            return Double.MIN_VALUE;
        }
    }

    /**
     * Check whether the auto width calculation is currently scheduled.
     * 
     * @return <code>true</code> if auto width calculation is currently
     *         scheduled
     */
    public boolean isScheduled() {
        return isScheduled;
    }
}