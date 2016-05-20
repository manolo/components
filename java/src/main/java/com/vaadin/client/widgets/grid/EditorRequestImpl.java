package com.vaadin.client.widgets.grid;

import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.widget.grid.events.EditorHandler.EditorRequest;

import java.util.Collection;
import java.util.Collections;

class EditorRequestImpl<T> implements EditorRequest<T> {

    /**
     * A callback interface used to notify the invoker of the editor handler
     * of completed editor requests.
     * 
     * @param <T>
     *            the row data type
     */
    public static interface RequestCallback<T> {
        /**
         * The method that must be called when the request has been
         * processed correctly.
         * 
         * @param request
         *            the original request object
         */
        public void onSuccess(EditorRequest<T> request);

        /**
         * The method that must be called when processing the request has
         * produced an aborting error.
         * 
         * @param request
         *            the original request object
         */
        public void onError(EditorRequest<T> request);
    }

    private Grid<T> grid;
    private final int rowIndex;
    private final int columnIndex;
    private EditorRequestImpl.RequestCallback<T> callback;
    private boolean completed = false;

    public EditorRequestImpl(Grid<T> grid, int rowIndex, int columnIndex,
            EditorRequestImpl.RequestCallback<T> callback) {
        this.grid = grid;
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.callback = callback;
    }

    @Override
    public int getRowIndex() {
        return rowIndex;
    }

    @Override
    public int getColumnIndex() {
        return columnIndex;
    }

    @Override
    public T getRow() {
        return grid.getDataSource().getRow(rowIndex);
    }

    @Override
    public Grid<T> getGrid() {
        return grid;
    }

    @Override
    public Widget getWidget(Column<?, T> column) {
        Widget w = grid.getEditorWidget(column);
        assert w != null;
        return w;
    }

    private void complete(String errorMessage,
            Collection<Column<?, T>> errorColumns) {
        if (completed) {
            throw new IllegalStateException(
                    "An EditorRequest must be completed exactly once");
        }
        completed = true;

        if (errorColumns == null) {
            errorColumns = Collections.emptySet();
        }
        grid.getEditor().setEditorError(errorMessage, errorColumns);
    }

    @Override
    public void success() {
        complete(null, null);
        if (callback != null) {
            callback.onSuccess(this);
        }
    }

    @Override
    public void failure(String errorMessage,
            Collection<Column<?, T>> errorColumns) {
        complete(errorMessage, errorColumns);
        if (callback != null) {
            callback.onError(this);
        }
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }
}