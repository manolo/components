package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;

import java.util.Arrays;
import java.util.Collection;

/**
 * Represents the header section of a Grid. A header consists of a single
 * header row containing a header cell for each column. Each cell has a
 * simple textual caption.
 */
public class Header extends StaticSection<Header.HeaderRow> {
    /**
     * A single row in a grid header section.
     * 
     */
    public static class HeaderRow extends StaticSection.StaticRow<Header.HeaderCell> {
    
        private boolean isDefault = false;
    
        protected void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
            if (isDefault) {
                for (Column<?, ?> column : getSection().grid.getColumns()) {
                    column.setDefaultHeaderContent(getCell(column));
                }
            }
        }
    
        public boolean isDefault() {
            return isDefault;
        }
    
        @Override
        protected Header.HeaderCell createCell() {
            return new Header.HeaderCell();
        }
    }

    /**
     * A single cell in a grid header row. Has a caption and, if it's in a
     * default row, a drag handle.
     */
    public static class HeaderCell extends StaticCell {
    }

    private Header.HeaderRow defaultRow;

    boolean markAsDirty = false;

    @Override
    public void removeRow(int index) {
        Header.HeaderRow removedRow = getRow(index);
        super.removeRow(index);
        if (removedRow == defaultRow) {
            setDefaultRow(null);
        }
    }

    /**
     * Sets the default row of this header. The default row is a special
     * header row providing a user interface for sorting columns.
     * 
     * @param row
     *            the new default row, or null for no default row
     * 
     * @throws IllegalArgumentException
     *             this header does not contain the row
     */
    public void setDefaultRow(Header.HeaderRow row) {
        if (row == defaultRow) {
            return;
        }
        if (row != null && !getRows().contains(row)) {
            throw new IllegalArgumentException(
                    "Cannot set a default row that does not exist in the container");
        }
        if (defaultRow != null) {
            defaultRow.setDefault(false);
        }
        if (row != null) {
            row.setDefault(true);
        }

        defaultRow = row;
        requestSectionRefresh();
    }

    /**
     * Returns the current default row of this header. The default row is a
     * special header row providing a user interface for sorting columns.
     * 
     * @return the default row or null if no default row set
     */
    public Header.HeaderRow getDefaultRow() {
        return defaultRow;
    }

    @Override
    protected Header.HeaderRow createRow() {
        return new Header.HeaderRow();
    }

    @Override
    protected void requestSectionRefresh() {
        markAsDirty = true;

        /*
         * Defer the refresh so if we multiple times call refreshSection()
         * (for example when updating cell values) we only get one actual
         * refresh in the end.
         */
        Scheduler.get().scheduleFinally(new Scheduler.ScheduledCommand() {

            @Override
            public void execute() {
                if (markAsDirty) {
                    markAsDirty = false;
                    getGrid().refreshHeader();
                }
            }
        });
    }

    /**
     * Returns the events consumed by the header
     * 
     * @return a collection of BrowserEvents
     */
    public Collection<String> getConsumedEvents() {
        return Arrays.asList(BrowserEvents.TOUCHSTART,
                BrowserEvents.TOUCHMOVE, BrowserEvents.TOUCHEND,
                BrowserEvents.TOUCHCANCEL, BrowserEvents.CLICK);
    }

    @Override
    protected void addColumn(Column<?, ?> column) {
        super.addColumn(column);

        // Add default content for new columns.
        if (defaultRow != null) {
            column.setDefaultHeaderContent(defaultRow.getCell(column));
        }
    }
}