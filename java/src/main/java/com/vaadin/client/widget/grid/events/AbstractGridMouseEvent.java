package com.vaadin.client.widget.grid.events;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.dom.client.DomEvent.Type;

import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.client.widgets.grid.Grid;
import com.vaadin.shared.ui.grid.GridConstants.Section;

public abstract class AbstractGridMouseEvent<HANDLER extends AbstractGridMouseEventHandler>
        extends MouseEvent<HANDLER> {

    private Grid<?> grid;
    private final CellReference<?> targetCell;
    private final Type<HANDLER> associatedType = new Type<HANDLER>(
            getBrowserEventType(), this);

    public AbstractGridMouseEvent(Grid<?> grid, CellReference<?> targetCell) {
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
     * Gets the reference of target cell for this event.
     * 
     * @return target cell
     */
    public CellReference<?> getTargetCell() {
        return targetCell;
    }

    @Override
    protected void dispatch(HANDLER handler) {
        EventTarget target = getNativeEvent().getEventTarget();
        if (!Element.is(target)) {
            // Target is not an element
            return;
        }

        Element targetElement = Element.as(target);
        if (grid.isElementInChildWidget(targetElement)) {
            // Target is some widget inside of Grid
            return;
        }

        final RowContainer container = grid.getEscalator()
                .findRowContainer(targetElement);
        if (container == null) {
            // No container for given element
            return;
        }

        Section section = Section.FOOTER;
        if (container == grid.getEscalator().getHeader()) {
            section = Section.HEADER;
        } else if (container == grid.getEscalator().getBody()) {
            section = Section.BODY;
        }

        doDispatch(handler, section);
    }

    protected abstract void doDispatch(HANDLER handler, Section section);

    @Override
    public Type<HANDLER> getAssociatedType() {
        return associatedType;
    }
}