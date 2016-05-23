package com.vaadin.elements.grid.table;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.TextOverflow;
import com.google.gwt.query.client.js.JsUtils;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTML;

import com.vaadin.client.widgets.grid.Column;
import com.vaadin.elements.common.js.JS;
import com.vaadin.elements.common.js.JSArray;
import com.vaadin.elements.common.js.JSFunction;
import com.vaadin.elements.grid.GridElement;
import com.vaadin.elements.grid.config.JSCell;
import com.vaadin.elements.grid.config.JSColumn;
import com.vaadin.elements.grid.config.JSStaticCell;
import com.vaadin.elements.grid.data.GridDataSource;
import com.vaadin.elements.grid.data.GridDomTableDataSource;
import com.vaadin.shared.ui.grid.GridConstants;

import java.util.Arrays;
import java.util.List;

import jsinterop.annotations.JsProperty;

public final class GridColumn extends Column<Object, Object> {

    private final JSColumn jsColumn;
    private  GridElement gridElement;

    /**
     * Create a new GridColumn associated with a JSColumn configuration.
     *
     * @param cfg it accepts either a JSO or a JSColumn, the object is
     *   promoted to a JSColumn to add customised setters/getters.
     */
    public static GridColumn createColumn(Object cfg, GridElement gridElement) {
        JSColumn jsColumn = JSColumn.promote(cfg);
        GridColumn column = new GridColumn(jsColumn, gridElement);
        gridElement.getGrid().addColumn(column,
                gridElement.getGrid().getVisibleColumns().size());
        jsColumn.configure(gridElement, column);
        return column;
    }

    private GridColumn(JSColumn jsColumn, GridElement gridElement) {
        this.jsColumn = jsColumn;
        this.gridElement = gridElement;

        // Default renderer
        setRenderer((cell, data) -> {
            Element element = cell.getElement();
            String content = JS.isUndefinedOrNull(data) ? "" : data.toString();

            if (gridElement.getDataSource() instanceof GridDomTableDataSource
                    && new HTML(content).getElement().getFirstChildElement() != null) {
                element.setInnerHTML(content);
            } else {
                Element wrapper = element.getFirstChildElement();
                if (wrapper == null || !wrapper.getPropertyBoolean("iswrapper")) {
                    // Need to create a new wrapper
                    wrapper = DOM.createSpan();
                    wrapper.getStyle().setOverflow(Overflow.HIDDEN);
                    wrapper.getStyle().setTextOverflow(TextOverflow.ELLIPSIS);
                    wrapper.setPropertyBoolean("iswrapper", true);
                    element.removeAllChildren();
                    element.appendChild(wrapper);
                }
                wrapper.setInnerText(content);
            }
        });
    }

    public JSStaticCell getDefaultHeaderCellReference() {
        GridStaticSection staticSection = gridElement.getStaticSection();
        return staticSection.getHeaderCellByColumn(
                staticSection.getDefaultHeader(), this);
    }

    public void nameChanged(String name) {
        // Need to invoke the logic that determines whether default header cell
        // should show content or name. Invocation must be deferred because
        // the logic happens synchronously and the actual name property hasn't
        // been yet applied
        JSStaticCell reference = getDefaultHeaderCellReference();
        Scheduler.get().scheduleDeferred(
                () -> reference.setContent(reference.getContent()));

    }

    public JSColumn getJsColumn() {
        return jsColumn;
    }

    @Override
    public Object getValue(Object dataItem) {
        dataItem = GridDataSource.extractDataItem(dataItem);

        Object result = null;
        if (JS.isPrimitiveType(dataItem)) {
            if (getColumnIndex() == 0) {
                result = dataItem;
            }
        } else {
            if (JsUtils.isArray((JavaScriptObject) dataItem)) {
                result = ((JSArray<Object>) dataItem).get(getColumnIndex());
            } else {
                result = getNestedProperty(dataItem,
                        Arrays.asList(jsColumn.getName().split("\\.")));
            }
        }
        return result;
    }

    private Object getNestedProperty(Object o, List<String> props) {
        Object result = null;
        if (props.isEmpty()) {
            result = o;
        } else if (JS.isObject(o)) {
            result = getNestedProperty(
                    JsUtils.prop((JavaScriptObject) o, props.get(0)),
                    props.subList(1, props.size()));
        }
        return result;
    }

    private int getColumnIndex() {
        return gridElement.getColumns().indexOf(jsColumn);
    }
    
    public static GridColumn promote(Object o) {
        return JS.promoteTo(o, GridColumn.class);
    }

    private String name;
    private JSFunction<?, JSCell> renderer;

    public void configure(GridElement gridElement, GridColumn column) {
        this.gridElement = gridElement;
        JS.reassignProperties(this);
    }

    @JsProperty
    public String getName() {
        return name;
    }

    @JsProperty
    public void setName(String s) {
        name = s;
        nameChanged(s);
    }

    @JsProperty(name = "renderer")
    public JSFunction<?, JSCell> getJsRenderer() {
        return renderer;
    }

    @JsProperty(name = "renderer")
    public void setJsRenderer(JSFunction<?, JSCell> o) {
        renderer = o;
        setRenderer((cell, data) -> {
            renderer.f(new JSCell(cell, gridElement.getContainer()));
        });
    }

    @JsProperty
    public String getHidingToggleText() {
        return getHidingToggleCaption();
    }

    @JsProperty
    public void setHidingToggleText(String s) {
        setHidingToggleCaption(s == null ? null : s.toString());
    }

    @JsProperty
    public int getFlex() {
        return getExpandRatio();
    }

    @JsProperty
    public void setFlex(int f) {
        setExpandRatio(f);
    }

    @JsProperty(name = "sortable")
    public void setJsSortable(boolean b) {
        setSortable(b);
    }


    @JsProperty(name = "hidable")
    public void seJstHidable(boolean b) {
        super.setHidable(b);
    }

    @JsProperty
    public boolean getReadonly() {
        return !isEditable();
    }

    @JsProperty
    public void setReadonly(boolean b) {
        setEditable(!b);
    }


    @JsProperty
    public void setMinWidth(double d) {
        if (getMinimumWidth() != d) {
            setMinimumWidth(JS.isUndefinedOrNull(d) ? GridConstants.DEFAULT_MIN_WIDTH: d);
            gridElement.updateWidth();
        }
    }


    @JsProperty
    public void setMaxWidth(double d) {
        if (getMaximumWidth() != d) {
            setMaximumWidth(JS.isUndefinedOrNull(d) ? GridConstants.DEFAULT_MAX_WIDTH: d);
            gridElement.updateWidth();
        }
    }

    @JsProperty
    public void setJsWidth(double d) {
        if (getWidth() != d) {
            setWidth(JS.isUndefinedOrNull(d) ? GridConstants.DEFAULT_COLUMN_WIDTH_PX: d);
            gridElement.updateWidth();
        }
    }

    @JsProperty(name="hidden")
    public void setJsHidden(boolean b) {
        setHidden(b);
        gridElement.updateWidth();
    }

}
