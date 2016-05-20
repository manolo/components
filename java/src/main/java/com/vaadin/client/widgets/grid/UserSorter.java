package com.vaadin.client.widgets.grid;

import com.google.gwt.user.client.Timer;

import com.vaadin.client.widget.grid.sort.SortOrder;

/**
 * Helper class for performing sorting through the user interface. Controls
 * the sort() method, reporting USER as the event originator. This is a
 * completely internal class, and is, as such, safe to re-name should a more
 * descriptive name come to mind.
 */
final class UserSorter<T> {

    private final Grid<T> grid;
    private final Timer timer;
    private boolean scheduledMultisort;
    private Column<?, T> column;

    UserSorter(Grid<T> grid) {
        this.grid = grid;
        timer = new Timer() {

            @Override
            public void run() {
                UserSorter.this.sort(column, scheduledMultisort);
            }
        };
    }

    /**
     * Toggle sorting for a cell. If the multisort parameter is set to true,
     * the cell's sort order is modified as a natural part of a multi-sort
     * chain. If false, the sorting order is set to ASCENDING for that
     * cell's column. If that column was already the only sorted column in
     * the Grid, the sort direction is flipped.
     * 
     * @param cell
     *            a valid cell reference
     * @param multisort
     *            whether the sort command should act as a multi-sort stack
     *            or not
     */
    public void sort(Column<?, ?> column, boolean multisort) {

        if (!this.grid.columns.contains(column)) {
            throw new IllegalArgumentException(
                    "Given column is not a column in this grid. "
                            + column.toString());
        }

        if (!column.isSortable()) {
            return;
        }

        final SortOrder so = this.grid.getSortOrder(column);

        if (multisort) {

            // If the sort order exists, replace existing value with its
            // opposite
            if (so != null) {
                final int idx = this.grid.sortOrder.indexOf(so);
                this.grid.sortOrder.set(idx, so.getOpposite());
            } else {
                // If it doesn't, just add a new sort order to the end of
                // the list
                this.grid.sortOrder.add(new SortOrder(column));
            }

        } else {

            // Since we're doing single column sorting, first clear the
            // list. Then, if the sort order existed, add its opposite,
            // otherwise just add a new sort value

            int items = this.grid.sortOrder.size();
            this.grid.sortOrder.clear();
            if (so != null && items == 1) {
                this.grid.sortOrder.add(so.getOpposite());
            } else {
                this.grid.sortOrder.add(new SortOrder(column));
            }
        }

        // sortOrder has been changed; tell the Grid to re-sort itself by
        // user request.
        grid.sort(true);
    }

    /**
     * Perform a sort after a delay.
     * 
     * @param delay
     *            delay, in milliseconds
     */
    public void sortAfterDelay(int delay, boolean multisort) {
        column = this.grid.eventCell.getColumn();
        scheduledMultisort = multisort;
        timer.schedule(delay);
    }

    /**
     * Check if a delayed sort command has been issued but not yet carried
     * out.
     * 
     * @return a boolean value
     */
    public boolean isDelayedSortScheduled() {
        return timer.isRunning();
    }

    /**
     * Cancel a scheduled sort.
     */
    public void cancelDelayedSort() {
        timer.cancel();
    }

}