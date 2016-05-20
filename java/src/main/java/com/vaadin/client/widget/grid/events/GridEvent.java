package com.vaadin.client.widget.grid.events;

import com.google.gwt.user.client.Event;

import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.client.widget.grid.EventCellReference;
import com.vaadin.client.widgets.grid.Grid;

/**
 * A wrapper for native DOM events originating from Grid. In addition to the
 * native event, contains a {@link CellReference} instance specifying which
 * cell the event originated from.
 * 
 * @since 7.6
 * @param <T>
 *            The row type of the grid
 */
public class GridEvent<T> {
    private Event event;
    private EventCellReference<T> cell;

    protected GridEvent(Event event, EventCellReference<T> cell) {
        this.event = event;
        this.cell = cell;
    }

    /**
     * Returns the wrapped DOM event.
     * 
     * @return the DOM event
     */
    public Event getDomEvent() {
        return event;
    }

    /**
     * Returns the Grid cell this event originated from.
     * 
     * @return the event cell
     */
    public EventCellReference<T> getCell() {
        return cell;
    }

    /**
     * Returns the Grid instance this event originated from.
     * 
     * @return the grid
     */
    public Grid<T> getGrid() {
        return cell.getGrid();
    }
}