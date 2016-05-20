package com.vaadin.client.widgets.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for Grid header and footer sections.
 * 
 * @since 7.5.0
 * 
 * @param <ROWTYPE>
 *            the type of the rows in the section
 */
public abstract class StaticSection<ROWTYPE extends StaticSection.StaticRow<?>> {

    /**
     * Abstract base class for Grid header and footer rows.
     * 
     * @param <CELLTYPE>
     *            the type of the cells in the row
     */
    public abstract static class StaticRow<CELLTYPE extends StaticCell> {

        private Map<Column<?, ?>, CELLTYPE> cells = new HashMap<Column<?, ?>, CELLTYPE>();

        private StaticSection<?> section;

        /**
         * Map from set of spanned columns to cell meta data.
         */
        private Map<Set<Column<?, ?>>, CELLTYPE> cellGroups = new HashMap<Set<Column<?, ?>>, CELLTYPE>();

        /**
         * A custom style name for the row or null if none is set.
         */
        private String styleName = null;

        /**
         * Returns the cell on given GridColumn. If the column is merged
         * returned cell is the cell for the whole group.
         * 
         * @param column
         *            the column in grid
         * @return the cell on given column, merged cell for merged columns,
         *         null if not found
         */
        public CELLTYPE getCell(Column<?, ?> column) {
            Set<Column<?, ?>> cellGroup = getCellGroupForColumn(column);
            if (cellGroup != null) {
                return cellGroups.get(cellGroup);
            }
            return cells.get(column);
        }

        /**
         * Returns <code>true</code> if this row contains spanned cells.
         * 
         * @since 7.5.0
         * @return does this row contain spanned cells
         */
        public boolean hasSpannedCells() {
            return !cellGroups.isEmpty();
        }

        /**
         * Merges columns cells in a row
         * 
         * @param columns
         *            the columns which header should be merged
         * @return the remaining visible cell after the merge, or the cell
         *         on first column if all are hidden
         */
        public CELLTYPE join(Column<?, ?>... columns) {
            if (columns.length <= 1) {
                throw new IllegalArgumentException(
                        "You can't merge less than 2 columns together.");
            }

            HashSet<Column<?, ?>> columnGroup = new HashSet<Column<?, ?>>();
            // NOTE: this doesn't care about hidden columns, those are
            // filtered in calculateColspans()
            for (Column<?, ?> column : columns) {
                if (!cells.containsKey(column)) {
                    throw new IllegalArgumentException(
                            "Given column does not exists on row " + column);
                } else if (getCellGroupForColumn(column) != null) {
                    throw new IllegalStateException(
                            "Column is already in a group.");
                }
                columnGroup.add(column);
            }

            CELLTYPE joinedCell = createCell();
            cellGroups.put(columnGroup, joinedCell);
            joinedCell.setSection(getSection());

            calculateColspans();

            return joinedCell;
        }

        /**
         * Merges columns cells in a row
         * 
         * @param cells
         *            The cells to merge. Must be from the same row.
         * @return The remaining visible cell after the merge, or the first
         *         cell if all columns are hidden
         */
        public CELLTYPE join(CELLTYPE... cells) {
            if (cells.length <= 1) {
                throw new IllegalArgumentException(
                        "You can't merge less than 2 cells together.");
            }

            Column<?, ?>[] columns = new Column<?, ?>[cells.length];

            int j = 0;
            for (Column<?, ?> column : this.cells.keySet()) {
                CELLTYPE cell = this.cells.get(column);
                if (!this.cells.containsValue(cells[j])) {
                    throw new IllegalArgumentException(
                            "Given cell does not exists on row");
                } else if (cell.equals(cells[j])) {
                    columns[j++] = column;
                    if (j == cells.length) {
                        break;
                    }
                }
            }

            return join(columns);
        }

        private Set<Column<?, ?>> getCellGroupForColumn(Column<?, ?> column) {
            for (Set<Column<?, ?>> group : cellGroups.keySet()) {
                if (group.contains(column)) {
                    return group;
                }
            }
            return null;
        }

        void calculateColspans() {
            // Reset all cells
            for (CELLTYPE cell : this.cells.values()) {
                cell.setColspan(1);
            }
            // Set colspan for grouped cells
            for (Set<Column<?, ?>> group : cellGroups.keySet()) {
                if (!checkMergedCellIsContinuous(group)) {
                    // on error simply break the merged cell
                    cellGroups.get(group).setColspan(1);
                } else {
                    int colSpan = 0;
                    for (Column<?, ?> column : group) {
                        if (!column.isHidden()) {
                            colSpan++;
                        }
                    }
                    // colspan can't be 0
                    cellGroups.get(group).setColspan(Math.max(1, colSpan));
                }
            }

        }

        private boolean checkMergedCellIsContinuous(
                Set<Column<?, ?>> mergedCell) {
            // no matter if hidden or not, just check for continuous order
            final List<Column<?, ?>> columnOrder = new ArrayList<Column<?, ?>>(
                    section.grid.getColumns());

            if (!columnOrder.containsAll(mergedCell)) {
                return false;
            }

            for (int i = 0; i < columnOrder.size(); ++i) {
                if (!mergedCell.contains(columnOrder.get(i))) {
                    continue;
                }

                for (int j = 1; j < mergedCell.size(); ++j) {
                    if (!mergedCell.contains(columnOrder.get(i + j))) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        protected void addCell(Column<?, ?> column) {
            CELLTYPE cell = createCell();
            cell.setSection(getSection());
            cells.put(column, cell);
        }

        protected void removeCell(Column<?, ?> column) {
            cells.remove(column);
        }

        protected abstract CELLTYPE createCell();

        protected StaticSection<?> getSection() {
            return section;
        }

        protected void setSection(StaticSection<?> section) {
            this.section = section;
        }

        /**
         * Returns the custom style name for this row.
         * 
         * @return the style name or null if no style name has been set
         */
        public String getStyleName() {
            return styleName;
        }

        /**
         * Sets a custom style name for this row.
         * 
         * @param styleName
         *            the style name to set or null to not use any style
         *            name
         */
        public void setStyleName(String styleName) {
            this.styleName = styleName;
            section.requestSectionRefresh();
        }

        /**
         * Called when the row is detached from the grid
         * 
         * @since 7.6.3
         */
        void detach() {
            // Avoid calling detach twice for a merged cell
            HashSet<CELLTYPE> cells = new HashSet<CELLTYPE>();
            for (Column<?, ?> column : getSection().grid.getColumns()) {
                cells.add(getCell(column));
            }
            for (CELLTYPE cell : cells) {
                cell.detach();
            }
        }
    }

    Grid<?> grid;

    private List<ROWTYPE> rows = new ArrayList<ROWTYPE>();

    private boolean visible = true;

    /**
     * Creates and returns a new instance of the row type.
     * 
     * @return the created row
     */
    protected abstract ROWTYPE createRow();

    /**
     * Informs the grid that this section should be re-rendered.
     * <p>
     * <b>Note</b> that re-render means calling update() on each cell,
     * preAttach()/postAttach()/preDetach()/postDetach() is not called as
     * the cells are not removed from the DOM.
     */
    protected abstract void requestSectionRefresh();

    /**
     * Sets the visibility of the whole section.
     * 
     * @param visible
     *            true to show this section, false to hide
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        requestSectionRefresh();
    }

    /**
     * Returns the visibility of this section.
     * 
     * @return true if visible, false otherwise.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Inserts a new row at the given position. Shifts the row currently at
     * that position and any subsequent rows down (adds one to their
     * indices).
     * 
     * @param index
     *            the position at which to insert the row
     * @return the new row
     * 
     * @throws IndexOutOfBoundsException
     *             if the index is out of bounds
     * @see #appendRow()
     * @see #prependRow()
     * @see #removeRow(int)
     * @see #removeRow(StaticRow)
     */
    public ROWTYPE addRowAt(int index) {
        ROWTYPE row = createRow();
        row.setSection(this);
        for (int i = 0; i < getGrid().getColumnCount(); ++i) {
            row.addCell(grid.getColumn(i));
        }
        rows.add(index, row);

        requestSectionRefresh();
        return row;
    }

    /**
     * Adds a new row at the top of this section.
     * 
     * @return the new row
     * @see #appendRow()
     * @see #addRowAt(int)
     * @see #removeRow(int)
     * @see #removeRow(StaticRow)
     */
    public ROWTYPE prependRow() {
        return addRowAt(0);
    }

    /**
     * Adds a new row at the bottom of this section.
     * 
     * @return the new row
     * @see #prependRow()
     * @see #addRowAt(int)
     * @see #removeRow(int)
     * @see #removeRow(StaticRow)
     */
    public ROWTYPE appendRow() {
        return addRowAt(rows.size());
    }

    /**
     * Removes the row at the given position.
     * 
     * @param index
     *            the position of the row
     * 
     * @throws IndexOutOfBoundsException
     *             if the index is out of bounds
     * @see #addRowAt(int)
     * @see #appendRow()
     * @see #prependRow()
     * @see #removeRow(StaticRow)
     */
    public void removeRow(int index) {
        ROWTYPE row = rows.remove(index);
        row.detach();
        requestSectionRefresh();
    }

    /**
     * Removes the given row from the section.
     * 
     * @param row
     *            the row to be removed
     * 
     * @throws IllegalArgumentException
     *             if the row does not exist in this section
     * @see #addRowAt(int)
     * @see #appendRow()
     * @see #prependRow()
     * @see #removeRow(int)
     */
    public void removeRow(ROWTYPE row) {
        try {
            removeRow(rows.indexOf(row));
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(
                    "Section does not contain the given row");
        }
    }

    /**
     * Returns the row at the given position.
     * 
     * @param index
     *            the position of the row
     * @return the row with the given index
     * 
     * @throws IndexOutOfBoundsException
     *             if the index is out of bounds
     */
    public ROWTYPE getRow(int index) {
        try {
            return rows.get(index);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Row with index " + index
                    + " does not exist");
        }
    }

    /**
     * Returns the number of rows in this section.
     * 
     * @return the number of rows
     */
    public int getRowCount() {
        return rows.size();
    }

    protected List<ROWTYPE> getRows() {
        return rows;
    }

    protected int getVisibleRowCount() {
        return isVisible() ? getRowCount() : 0;
    }

    protected void addColumn(Column<?, ?> column) {
        for (ROWTYPE row : rows) {
            row.addCell(column);
        }
    }

    protected void removeColumn(Column<?, ?> column) {
        for (ROWTYPE row : rows) {
            row.removeCell(column);
        }
    }

    protected void setGrid(Grid<?> grid) {
        this.grid = grid;
    }

    protected Grid<?> getGrid() {
        assert grid != null;
        return grid;
    }

    protected void updateColSpans() {
        for (ROWTYPE row : rows) {
            if (row.hasSpannedCells()) {
                row.calculateColspans();
            }
        }
    }
}