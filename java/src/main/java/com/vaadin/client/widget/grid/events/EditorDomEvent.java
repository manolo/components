package com.vaadin.client.widget.grid.events;

import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.widget.grid.EventCellReference;
import com.vaadin.client.widgets.grid.Editor;

/**
 * A wrapper for native DOM events related to the {@link Editor Grid editor}
 * .
 * 
 * @since 7.6
 * @param <T>
 *            the row type of the grid
 */
public class EditorDomEvent<T> extends GridEvent<T> {

    private final Widget editorWidget;

    public EditorDomEvent(Event event, EventCellReference<T> cell,
            Widget editorWidget) {
        super(event, cell);
        this.editorWidget = editorWidget;
    }

    /**
     * Returns the editor of the Grid this event originated from.
     * 
     * @return the related editor instance
     */
    public Editor<T> getEditor() {
        return getGrid().getEditor();
    }

    /**
     * Returns the currently focused editor widget.
     * 
     * @return the focused editor widget or {@code null} if not editable
     */
    public Widget getEditorWidget() {
        return editorWidget;
    }

    /**
     * Returns the row index the editor is open at. If the editor is not
     * open, returns -1.
     * 
     * @return the index of the edited row or -1 if editor is not open
     */
    public int getRowIndex() {
        return getEditor().rowIndex;
    }

    /**
     * Returns the column index the editor was opened at. If the editor is
     * not open, returns -1.
     * 
     * @return the column index or -1 if editor is not open
     */
    public int getFocusedColumnIndex() {
        return getEditor().focusedColumnIndex;
    }
}