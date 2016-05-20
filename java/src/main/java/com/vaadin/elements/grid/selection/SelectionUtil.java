package com.vaadin.elements.grid.selection;

import com.vaadin.client.data.DataSource.RowHandle;
import com.vaadin.client.widgets.grid.Grid;
import com.vaadin.elements.grid.data.GridDataSource;

public final class SelectionUtil {

    private SelectionUtil() {
    }

    static int getRowIndex(Grid<Object> grid, RowHandle<Object> rowHandle) {
        rowHandle.pin();
        int result = getRowIndexByRow(grid, rowHandle.getRow());
        rowHandle.unpin();
        return result;
    }

    static int getRowIndexByRow(Grid<Object> grid, Object row) {
        return ((GridDataSource) grid.getDataSource()).indexOf(row);
    }
}
