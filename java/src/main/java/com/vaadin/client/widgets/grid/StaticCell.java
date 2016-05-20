package com.vaadin.client.widgets.grid;

import com.google.gwt.user.client.ui.Widget;

import com.vaadin.shared.ui.grid.GridStaticCellType;

/**
 * A header or footer cell. Has a simple textual caption.
 * 
 */
public class StaticCell {

    private Object content = null;

    private int colspan = 1;

    private StaticSection<?> section;

    private GridStaticCellType type = GridStaticCellType.TEXT;

    private String styleName = null;

    /**
     * Sets the text displayed in this cell.
     * 
     * @param text
     *            a plain text caption
     */
    public void setText(String text) {
        this.content = text;
        this.type = GridStaticCellType.TEXT;
        section.requestSectionRefresh();
    }

    /**
     * Returns the text displayed in this cell.
     * 
     * @return the plain text caption
     */
    public String getText() {
        if (type != GridStaticCellType.TEXT) {
            throw new IllegalStateException(
                    "Cannot fetch Text from a cell with type " + type);
        }
        return (String) content;
    }

    protected StaticSection<?> getSection() {
        assert section != null;
        return section;
    }

    protected void setSection(StaticSection<?> section) {
        this.section = section;
    }

    /**
     * Returns the amount of columns the cell spans. By default is 1.
     * 
     * @return The amount of columns the cell spans.
     */
    public int getColspan() {
        return colspan;
    }

    /**
     * Sets the amount of columns the cell spans. Must be more or equal
     * to 1. By default is 1.
     * 
     * @param colspan
     *            the colspan to set
     */
    public void setColspan(int colspan) {
        if (colspan < 1) {
            throw new IllegalArgumentException(
                    "Colspan cannot be less than 1");
        }

        this.colspan = colspan;
        section.requestSectionRefresh();
    }

    /**
     * Returns the html inside the cell.
     * 
     * @throws IllegalStateException
     *             if trying to retrive HTML from a cell with a type
     *             other than {@link GridStaticCellType#HTML}.
     * @return the html content of the cell.
     */
    public String getHtml() {
        if (type != GridStaticCellType.HTML) {
            throw new IllegalStateException(
                    "Cannot fetch HTML from a cell with type " + type);
        }
        return (String) content;
    }

    /**
     * Sets the content of the cell to the provided html. All previous
     * content is discarded and the cell type is set to
     * {@link GridStaticCellType#HTML}.
     * 
     * @param html
     *            The html content of the cell
     */
    public void setHtml(String html) {
        this.content = html;
        this.type = GridStaticCellType.HTML;
        section.requestSectionRefresh();
    }

    /**
     * Returns the widget in the cell.
     * 
     * @throws IllegalStateException
     *             if the cell is not {@link GridStaticCellType#WIDGET}
     * 
     * @return the widget in the cell
     */
    public Widget getWidget() {
        if (type != GridStaticCellType.WIDGET) {
            throw new IllegalStateException(
                    "Cannot fetch Widget from a cell with type " + type);
        }
        return (Widget) content;
    }

    /**
     * Set widget as the content of the cell. The type of the cell
     * becomes {@link GridStaticCellType#WIDGET}. All previous content
     * is discarded.
     * 
     * @param widget
     *            The widget to add to the cell. Should not be
     *            previously attached anywhere (widget.getParent ==
     *            null).
     */
    public void setWidget(Widget widget) {
        if (this.content == widget) {
            return;
        }

        if (this.content instanceof Widget) {
            // Old widget in the cell, detach it first
            section.getGrid().detachWidget((Widget) this.content);
        }
        this.content = widget;
        this.type = GridStaticCellType.WIDGET;
        section.requestSectionRefresh();
    }

    /**
     * Returns the type of the cell.
     * 
     * @return the type of content the cell contains.
     */
    public GridStaticCellType getType() {
        return type;
    }

    /**
     * Returns the custom style name for this cell.
     * 
     * @return the style name or null if no style name has been set
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * Sets a custom style name for this cell.
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
     * Called when the cell is detached from the row
     * 
     * @since 7.6.3
     */
    void detach() {
        if (this.content instanceof Widget) {
            // Widget in the cell, detach it
            section.getGrid().detachWidget((Widget) this.content);
        }
    }
}