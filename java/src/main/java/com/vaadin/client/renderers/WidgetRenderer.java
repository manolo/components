/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.renderers;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.FlyweightCell;
import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.client.widget.grid.RendererCellReference;

import java.util.Collection;
import java.util.Collections;

/**
 * A renderer for rendering widgets into cells.
 * 
 * @since 7.4
 * @author Vaadin Ltd
 * @param <T>
 *            the row data type
 * @param <W>
 *            the Widget type
 */
public abstract class WidgetRenderer<T, W extends Widget> implements Renderer<T> {


    /**
     * Creates a widget to attach to a cell. The widgets will be attached to the
     * cell after the cell element has been attached to DOM.
     * 
     * @return widget to attach to a cell. All returned instances should be new
     *         widget instances without a parent.
     */
    public abstract W createWidget();

    @Override
    public void render(RendererCellReference cell, T data) {
        W w = getWidget(cell.getElement());
        assert w != null : "Widget not found in cell (" + cell.getColumn()
                + "," + cell.getRow() + ")";
        render(cell, data, w);
    }

    /**
     * Renders a cell with a widget. This provides a way to update any
     * information in the widget that is cell specific. Do not detach the Widget
     * here, it will be done automatically by the Grid when the widget is no
     * longer needed.
     * <p>
     * For optimal performance, work done in this method should be kept to a
     * minimum since it will be called continuously while the user is scrolling.
     * The renderer can use {@link Widget#setLayoutData(Object)} to store cell
     * data that might be needed in e.g. event listeners.
     * 
     * @param cell
     *            The cell to render. Note that the cell is a flyweight and
     *            should not be stored and used outside of this method as its
     *            contents will change.
     * @param data
     *            the data of the cell
     * @param widget
     *            the widget embedded in the cell
     */
    public abstract void render(RendererCellReference cell, T data, W widget);

    /**
     * Returns the widget contained inside the given cell element. Cannot be
     * called for cells that do not contain a widget.
     * 
     * @param e
     *            the element inside which to find a widget
     * @return the widget inside the element
     */
    protected W getWidget(TableCellElement e) {
        W w = getWidget(e, null);
        assert w != null : "Widget not found inside cell";
        return w;
    }

    /**
     * Returns the widget contained inside the given cell element, or null if it
     * is not an instance of the given class. Cannot be called for cells that do
     * not contain a widget.
     * 
     * @param e
     *            the element inside to find a widget
     * @param klass
     *            the type of the widget to find
     * @return the widget inside the element, or null if its type does not match
     */
    protected static <W extends Widget> W getWidget(TableCellElement e,
            Class<W> klass) {
        W w = WidgetUtil.findWidget(e.getFirstChildElement(), klass);
        assert w == null || w.getElement() == e.getFirstChildElement() : "Widget not found inside cell";
        return w;
    }
    

    /**
     * Called after the cell is deemed to be destroyed and no longer used by the
     * Grid. Called after the cell element is detached from the DOM.
     * <p>
     * The row object in the cell reference will be <code>null</code> since the
     * row might no longer be present in the data source.
     * 
     * @param cell
     *            The cell. Note that the cell is not to be stored outside of
     *            the method as the cell instance will change. See
     *            {@link FlyweightCell}
     */
    public void destroy(RendererCellReference cell) {
        // Implement if needed
    }

    /**
     * Returns the events that the renderer should consume. These are also the
     * events that the Grid will pass to
     * {@link #onBrowserEvent(Cell, NativeEvent)} when they occur.
     * 
     * @return a list of consumed events
     * 
     * @see com.google.gwt.dom.client.BrowserEvents
     */
    public Collection<String> getConsumedEvents() {
        return Collections.emptyList();
    }

    /**
     * Called whenever a registered event is triggered in the column the
     * renderer renders.
     * <p>
     * The events that triggers this needs to be returned by the
     * {@link #getConsumedEvents()} method.
     * <p>
     * Returns boolean telling if the event has been completely handled and
     * should not cause any other actions.
     * 
     * @param cell
     *            Object containing information about the cell the event was
     *            triggered on.
     * 
     * @param event
     *            The original DOM event
     * @return true if event should not be handled by grid
     */
    public boolean onBrowserEvent(CellReference<?> cell, NativeEvent event) {
        return false;
    }

    /**
     * Used by Grid to toggle whether to show actual data or just an empty
     * placeholder while data is loading. This method is invoked whenever a cell
     * changes between data being available and data missing.
     * <p>
     * Default implementation hides content by setting visibility: hidden to all
     * elements inside the cell. Text nodes are left as is - renderers that add
     * such to the root element need to implement explicit support hiding them.
     * 
     * @param cell
     *            The cell
     * @param hasData
     *            Has the cell content been loaded from the data source
     * 
     */
    public void setContentVisible(RendererCellReference cell, boolean hasData) {
        Element cellElement = cell.getElement();
        for (int n = 0; n < cellElement.getChildCount(); n++) {
            Node node = cellElement.getChild(n);
            if (Element.is(node)) {
                Element e = Element.as(node);
                if (hasData) {
                    e.getStyle().clearVisibility();
                } else {
                    e.getStyle().setVisibility(Visibility.HIDDEN);
                }
            }
        }
    }

    /**
     * Called when the cell is activated by pressing <code>enter</code>, double
     * clicking or performing a double tap on the cell.
     * 
     * @param cell
     *            the activated cell
     * @return <code>true</code> if event was handled and should not be
     *         interpreted as a generic gesture by Grid.
     */
    public boolean onActivate(CellReference<?> cell) {
        return false;
    }

    /**
     * Called when the renderer is deemed to be destroyed and no longer used by
     * the Grid.
     */
    public void destroy() {
        // Implement if needed
    }
}
