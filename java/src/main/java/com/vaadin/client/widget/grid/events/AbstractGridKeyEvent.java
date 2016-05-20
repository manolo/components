package com.vaadin.client.widget.grid.events;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.event.dom.client.KeyEvent;
import com.google.gwt.event.dom.client.DomEvent.Type;

import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.client.widgets.grid.Grid;
import com.vaadin.shared.ui.grid.GridConstants.Section;

public abstract class AbstractGridKeyEvent<HANDLER extends AbstractGridKeyEventHandler>
        extends KeyEvent<HANDLER> {

    private final Grid<?> grid;
    private final Type<HANDLER> associatedType = new Type<HANDLER>(
            getBrowserEventType(), this);
    private final CellReference<?> targetCell;

    public AbstractGridKeyEvent(Grid<?> grid, CellReference<?> targetCell) {
        this.grid = grid;
        this.targetCell = targetCell;
    }

    protected abstract String getBrowserEventType();

    /**
     * Gets the Grid instance for this event.
     * 
     * @return grid
     */
    public Grid<?> getGrid() {
        return grid;
    }

    /**
     * Gets the focused cell for this event.
     * 
     * @return focused cell
     */
    public CellReference<?> getFocusedCell() {
        return targetCell;
    }

    @Override
    protected void dispatch(HANDLER handler) {
        EventTarget target = getNativeEvent().getEventTarget();
        if (Element.is(target)
                && !grid.isElementInChildWidget(Element.as(target))) {

            Section section = Section.FOOTER;
            final RowContainer container = grid.cellFocusHandler.containerWithFocus;
            if (container == grid.escalator.getHeader()) {
                section = Section.HEADER;
            } else if (container == grid.escalator.getBody()) {
                section = Section.BODY;
            }

            doDispatch(handler, section);
        }
    }

    protected abstract void doDispatch(HANDLER handler, Section section);

    @Override
    public Type<HANDLER> getAssociatedType() {
        return associatedType;
    }
}