package com.vaadin.client.widgets.grid;

import static com.google.gwt.query.client.GQuery.console;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.renderers.ComplexRenderer;
import com.vaadin.client.renderers.Renderer;
import com.vaadin.client.renderers.WidgetRenderer;
import com.vaadin.client.widget.escalator.ColumnConfiguration;
import com.vaadin.client.widget.grid.RendererCellReference;
import com.vaadin.client.widget.grid.events.ColumnVisibilityChangeEvent;
import com.vaadin.shared.ui.grid.GridConstants;

import jsinterop.annotations.JsProperty;

/**
 * Base class for grid columns internally used by the Grid. The user should
 * use {@link Column} when creating new columns.
 * 
 * @param <C>
 *            the column type
 * 
 * @param <T>
 *            the row type
 */
public abstract class Column<C, T> {

    /**
     * Default renderer for GridColumns. Renders everything into text
     * through {@link Object#toString()}.
     */
    private final class DefaultTextRenderer implements Renderer<Object> {
        boolean warned = false;
        private final String DEFAULT_RENDERER_WARNING = "This column uses a dummy default TextRenderer. "
                + "A more suitable renderer should be set using the setRenderer() method.";

        @Override
        public void render(RendererCellReference cell, Object data) {
            if (!warned && !(data instanceof String)) {
                Grid.getLogger().warning(
                        Column.this.toString() + ": "
                                + DEFAULT_RENDERER_WARNING);
                warned = true;
            }

            final String text;
            if (data == null) {
                text = "";
            } else {
                text = data.toString();
            }

            cell.getElement().setInnerText(text);
        }
    }

    /**
     * the column is associated with
     */
    private Grid<T> grid;

    /**
     * Width of column in pixels as {@link #setWidth(double)} has been
     * called
     */
    private double widthUser = GridConstants.DEFAULT_COLUMN_WIDTH_PX;

    /**
     * Renderer for rendering a value into the cell
     */
    private Renderer<? super C> bodyRenderer;

    private boolean sortable = false;

    private boolean editable = true;

    private boolean resizable = true;

    private boolean hidden = false;

    private boolean hidable = false;

    String headerCaption = "";

    private String hidingToggleCaption = null;

    private double minimumWidthPx = GridConstants.DEFAULT_MIN_WIDTH;
    private double maximumWidthPx = GridConstants.DEFAULT_MAX_WIDTH;
    private int expandRatio = GridConstants.DEFAULT_EXPAND_RATIO;

    /**
     * Constructs a new column with a simple TextRenderer.
     */
    public Column() {
        setRenderer(new DefaultTextRenderer());
    }

    /**
     * Constructs a new column with a simple TextRenderer.
     * 
     * @param caption
     *            The header caption for this column
     * 
     * @throws IllegalArgumentException
     *             if given header caption is null
     */
    public Column(String caption) throws IllegalArgumentException {
        this();
        setHeaderCaption(caption);
    }

    /**
     * Constructs a new column with a custom renderer.
     * 
     * @param renderer
     *            The renderer to use for rendering the cells
     * 
     * @throws IllegalArgumentException
     *             if given Renderer is null
     */
    public Column(Renderer<? super C> renderer)
            throws IllegalArgumentException {
        setRenderer(renderer);
    }

    /**
     * Constructs a new column with a custom renderer.
     * 
     * @param renderer
     *            The renderer to use for rendering the cells
     * @param caption
     *            The header caption for this column
     * 
     * @throws IllegalArgumentException
     *             if given Renderer or header caption is null
     */
    public Column(String caption, Renderer<? super C> renderer)
            throws IllegalArgumentException {
        this(renderer);
        setHeaderCaption(caption);
    }

    /**
     * Internally used by the grid to set itself
     * 
     * @param grid
     */
    void setGrid(Grid<T> grid) {
        if (this.grid != null && grid != null) {
            // Trying to replace grid
            throw new IllegalStateException("Column already is attached "
                    + "to a grid. Remove the column first from the grid "
                    + "and then add it. (in: " + toString() + ")");
        }

        if (this.grid != null) {
            this.grid.recalculateColumnWidths();
        }
        this.grid = grid;
        if (this.grid != null) {
            this.grid.recalculateColumnWidths();
        }
    }

    /**
     * Sets a header caption for this column.
     * 
     * @param caption
     *            The header caption for this column
     * @return the column itself
     * 
     */
    public Column<C, T> setHeaderCaption(String caption) {
        if (caption == null) {
            caption = "";
        }

        if (!this.headerCaption.equals(caption)) {
            this.headerCaption = caption;
            if (grid != null) {
                updateHeader();
            }
        }

        return this;
    }

    /**
     * Returns the current header caption for this column
     * 
     * @since 7.6
     * @return the header caption string
     */
    public String getHeaderCaption() {
        return headerCaption;
    }

    private void updateHeader() {
        Header.HeaderRow row = grid.getHeader().getDefaultRow();
        if (row != null) {
            row.getCell(this).setText(headerCaption);
            if (isHidable()) {
                grid.columnHider.updateHidingToggle(this);
            }
        }
    }

    /**
     * Returns the data that should be rendered into the cell. By default
     * returning Strings and Widgets are supported. If the return type is a
     * String then it will be treated as preformatted text.
     * <p>
     * To support other types you will need to pass a custom renderer to the
     * column via the column constructor.
     * 
     * @param row
     *            The row object that provides the cell content.
     * 
     * @return The cell content
     */
    public abstract C getValue(T row);

    /**
     * The renderer to render the cell with. By default renders the data as
     * a String or adds the widget into the cell if the column type is of
     * widget type.
     * 
     * @return The renderer to render the cell content with
     */
    public Renderer<? super C> getRenderer() {
        return bodyRenderer;
    }

    /**
     * Sets a custom {@link Renderer} for this column.
     * 
     * @param renderer
     *            The renderer to use for rendering the cells
     * @return the column itself
     * 
     * @throws IllegalArgumentException
     *             if given Renderer is null
     */
    public Column<C, T> setRenderer(Renderer<? super C> renderer)
            throws IllegalArgumentException {
        if (renderer == null) {
            throw new IllegalArgumentException("Renderer cannot be null.");
        }

        if (renderer != bodyRenderer) {
            // Variables used to restore removed column.
            boolean columnRemoved = false;
            double widthInConfiguration = 0.0d;
            ColumnConfiguration conf = null;
            int index = 0;

            if (grid != null
                    && (bodyRenderer instanceof WidgetRenderer || renderer instanceof WidgetRenderer)) {
                // Column needs to be recreated.
                index = grid.getColumns().indexOf(this);
                conf = grid.getEscalator().getColumnConfiguration();
                widthInConfiguration = conf.getColumnWidth(index);

                conf.removeColumns(index, 1);
                columnRemoved = true;
            }

            // Complex renderers need to be destroyed.
            if (bodyRenderer instanceof ComplexRenderer) {
                ((ComplexRenderer) bodyRenderer).destroy();
            }

            bodyRenderer = renderer;

            if (columnRemoved) {
                // Restore the column.
                conf.insertColumns(index, 1);
                conf.setColumnWidth(index, widthInConfiguration);
            }

            if (grid != null) {
                grid.refreshBody();
            }
        }
        return this;
    }

    /**
     * Sets the pixel width of the column. Use a negative value for the grid
     * to autosize column based on content and available space.
     * <p>
     * This action is done "finally", once the current execution loop
     * returns. This is done to reduce overhead of unintentionally always
     * recalculate all columns, when modifying several columns at once.
     * <p>
     * If the column is currently {@link #isHidden() hidden}, then this set
     * width has effect only once the column has been made visible again.
     * 
     * @param pixels
     *            the width in pixels or negative for auto sizing
     */
    public Column<C, T> setWidth(double pixels) {
        if (!WidgetUtil.pixelValuesEqual(widthUser, pixels)) {
            widthUser = pixels;
            if (!isHidden()) {
                scheduleColumnWidthRecalculator();
            }
        }
        return this;
    }

    void doSetWidth(double pixels) {
        assert !isHidden() : "applying width for a hidden column";
        if (grid != null) {
            int index = grid.getVisibleColumns().indexOf(this);
            ColumnConfiguration conf = grid.getEscalator()
                    .getColumnConfiguration();
            conf.setColumnWidth(index, pixels);
        }
    }

    /**
     * Returns the pixel width of the column as given by the user.
     * <p>
     * <em>Note:</em> If a negative value was given to
     * {@link #setWidth(double)}, that same negative value is returned here.
     * <p>
     * <em>Note:</em> Returns the value, even if the column is currently
     * {@link #isHidden() hidden}.
     * 
     * @return pixel width of the column, or a negative number if the column
     *         width has been automatically calculated.
     * @see #setWidth(double)
     * @see #getWidthActual()
     */
    @JsProperty
    public double getWidth() {
        return widthUser;
    }

    /**
     * Returns the effective pixel width of the column.
     * <p>
     * This differs from {@link #getWidth()} only when the column has been
     * automatically resized, or when the column is currently
     * {@link #isHidden() hidden}, when the value is 0.
     * 
     * @return pixel width of the column.
     */
    public double getWidthActual() {
        if (isHidden()) {
            return 0;
        }
        return grid.getEscalator().getColumnConfiguration()
                .getColumnWidthActual(
                        grid.getVisibleColumns().indexOf(this));
    }

    void reapplyWidth() {
        scheduleColumnWidthRecalculator();
    }

    /**
     * Sets whether the column should be sortable by the user. The grid can
     * be sorted by a sortable column by clicking or tapping the column's
     * default header. Programmatic sorting using the Grid#sort methods is
     * not affected by this setting.
     * 
     * @param sortable
     *            {@code true} if the user should be able to sort the
     *            column, {@code false} otherwise
     * @return the column itself
     */
    public Column<C, T> setSortable(boolean sortable) {
        if (this.sortable != sortable) {
            this.sortable = sortable;
            if (grid != null) {
                grid.refreshHeader();
            }
        }
        return this;
    }

    /**
     * Returns whether the user can sort the grid by this column.
     * <p>
     * <em>Note:</em> it is possible to sort by this column programmatically
     * using the Grid#sort methods regardless of the returned value.
     * 
     * @return {@code true} if the column is sortable by the user,
     *         {@code false} otherwise
     */
    @JsProperty
    public boolean isSortable() {
        return sortable;
    }

    /**
     * Sets whether this column can be resized by the user.
     * 
     * @since 7.6
     * 
     * @param resizable
     *            {@code true} if this column should be resizable,
     *            {@code false} otherwise
     */
    public Column<C, T> setResizable(boolean resizable) {
        if (this.resizable != resizable) {
            this.resizable = resizable;
            if (grid != null) {
                grid.refreshHeader();
            }
        }
        return this;
    }

    /**
     * Returns whether this column can be resized by the user. Default is
     * {@code true}.
     * <p>
     * <em>Note:</em> the column can be programmatically resized using
     * {@link #setWidth(double)} and {@link #setWidthUndefined()} regardless
     * of the returned value.
     * 
     * @since 7.6
     * 
     * @return {@code true} if this column is resizable, {@code false}
     *         otherwise
     */
    public boolean isResizable() {
        return resizable;
    }

    /**
     * Hides or shows the column. By default columns are visible before
     * explicitly hiding them.
     * 
     * @since 7.5.0
     * @param hidden
     *            <code>true</code> to hide the column, <code>false</code>
     *            to show
     */
    public Column<C, T> setHidden(boolean hidden) {
        setHidden(hidden, false);
        return this;
    }

    void setHidden(boolean hidden, boolean userOriginated) {
        if (this.hidden != hidden) {
            if (hidden) {
                grid.getEscalator().getColumnConfiguration().removeColumns(
                        grid.getVisibleColumns().indexOf(this), 1);
                this.hidden = hidden;
            } else {
                this.hidden = hidden;

                final int columnIndex = grid.getVisibleColumns().indexOf(
                        this);
                grid.getEscalator().getColumnConfiguration().insertColumns(
                        columnIndex, 1);

                // make sure column is set to frozen if it needs to be,
                // escalator doesn't handle situation where the added column
                // would be the last frozen column
                int gridFrozenColumns = grid.getFrozenColumnCount();
                int escalatorFrozenColumns = grid.getEscalator()
                        .getColumnConfiguration().getFrozenColumnCount();
                if (gridFrozenColumns > escalatorFrozenColumns
                        && escalatorFrozenColumns == columnIndex) {
                    grid.getEscalator().getColumnConfiguration()
                            .setFrozenColumnCount(++escalatorFrozenColumns);
                }
            }
            grid.columnHider.updateHidingToggle(this);
            grid.header.updateColSpans();
            grid.footer.updateColSpans();
            scheduleColumnWidthRecalculator();
            this.grid.fireEvent(new ColumnVisibilityChangeEvent<T>(this,
                    hidden, userOriginated));
        }
    }

    /**
     * Returns whether this column is hidden. Default is {@code false}.
     * 
     * @since 7.5.0
     * @return {@code true} if the column is currently hidden, {@code false}
     *         otherwise
     */
    @JsProperty
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Set whether it is possible for the user to hide this column or not.
     * Default is {@code false}.
     * <p>
     * <em>Note:</em> it is still possible to hide the column
     * programmatically using {@link #setHidden(boolean)}.
     * 
     * @since 7.5.0
     * @param hidable
     *            {@code true} the user can hide this column, {@code false}
     *            otherwise
     */
    public Column<C, T> setHidable(boolean hidable) {
        if (this.hidable != hidable) {
            this.hidable = hidable;
            grid.columnHider.updateColumnHidable(this);
        }
        return this;
    }

    /**
     * Is it possible for the the user to hide this column. Default is
     * {@code false}.
     * <p>
     * <em>Note:</em> the column can be programmatically hidden using
     * {@link #setHidden(boolean)} regardless of the returned value.
     * 
     * @since 7.5.0
     * @return <code>true</code> if the user can hide the column,
     *         <code>false</code> if not
     */
    @JsProperty
    public boolean isHidable() {
        return hidable;
    }

    /**
     * Sets the hiding toggle's caption for this column. Shown in the toggle
     * for this column in the grid's sidebar when the column is
     * {@link #isHidable() hidable}.
     * <p>
     * The default value is <code>null</code>. In this case the header
     * caption is used, see {@link #setHeaderCaption(String)}.
     * 
     * @since 7.5.0
     * @param hidingToggleCaption
     *            the caption for the hiding toggle for this column
     */
    public Column<C, T> setHidingToggleCaption(String hidingToggleCaption) {
        this.hidingToggleCaption = hidingToggleCaption;
        if (isHidable()) {
            grid.columnHider.updateHidingToggle(this);
        }
        return this;
    }

    /**
     * Gets the hiding toggle caption for this column.
     * 
     * @since 7.5.0
     * @see #setHidingToggleCaption(String)
     * @return the hiding toggle's caption for this column
     */
    public String getHidingToggleCaption() {
        return hidingToggleCaption;
    }

    @Override
    public String toString() {
        String details = "";

        if (headerCaption != null && !headerCaption.isEmpty()) {
            details += "header:\"" + headerCaption + "\" ";
        } else {
            details += "header:empty ";
        }

        if (grid != null) {
            int index = grid.getColumns().indexOf(this);
            if (index != -1) {
                details += "attached:#" + index + " ";
            } else {
                details += "attached:unindexed ";
            }
        } else {
            details += "detached ";
        }

        details += "sortable:" + sortable + " ";

        return getClass().getSimpleName() + "[" + details.trim() + "]";
    }

    /**
     * Sets the minimum width for this column.
     * <p>
     * This defines the minimum guaranteed pixel width of the column
     * <em>when it is set to expand</em>.
     * <p>
     * This action is done "finally", once the current execution loop
     * returns. This is done to reduce overhead of unintentionally always
     * recalculate all columns, when modifying several columns at once.
     * 
     * @param pixels
     *            the minimum width
     * @return this column
     */
    public Column<C, T> setMinimumWidth(double pixels) {
        final double maxwidth = getMaximumWidth();
        if (pixels >= 0 && pixels > maxwidth && maxwidth >= 0) {
            throw new IllegalArgumentException("New minimum width ("
                    + pixels + ") was greater than maximum width ("
                    + maxwidth + ")");
        }

        if (minimumWidthPx != pixels) {
            minimumWidthPx = pixels;
            scheduleColumnWidthRecalculator();
        }
        return this;
    }

    /**
     * Sets the maximum width for this column.
     * <p>
     * This defines the maximum allowed pixel width of the column
     * <em>when it is set to expand</em>.
     * <p>
     * This action is done "finally", once the current execution loop
     * returns. This is done to reduce overhead of unintentionally always
     * recalculate all columns, when modifying several columns at once.
     * 
     * @param pixels
     *            the maximum width
     * @param immediately
     *            <code>true</code> if the widths should be executed
     *            immediately (ignoring lazy loading completely), or
     *            <code>false</code> if the command should be run after a
     *            while (duplicate non-immediately invocations are ignored).
     * @return this column
     */
    public Column<C, T> setMaximumWidth(double pixels) {
        final double minwidth = getMinimumWidth();
        if (pixels >= 0 && pixels < minwidth && minwidth >= 0) {
            throw new IllegalArgumentException("New maximum width ("
                    + pixels + ") was less than minimum width (" + minwidth
                    + ")");
        }

        if (maximumWidthPx != pixels) {
            maximumWidthPx = pixels;
            scheduleColumnWidthRecalculator();
        }
        return this;
    }

    /**
     * Sets the ratio with which the column expands.
     * <p>
     * By default, all columns expand equally (treated as if all of them had
     * an expand ratio of 1). Once at least one column gets a defined expand
     * ratio, the implicit expand ratio is removed, and only the defined
     * expand ratios are taken into account.
     * <p>
     * If a column has a defined width ({@link #setWidth(double)}), it
     * overrides this method's effects.
     * <p>
     * <em>Example:</em> A grid with three columns, with expand ratios 0, 1
     * and 2, respectively. The column with a <strong>ratio of 0 is exactly
     * as wide as its contents requires</strong>. The column with a ratio of
     * 1 is as wide as it needs, <strong>plus a third of any excess
     * space</strong>, bceause we have 3 parts total, and this column
     * reservs only one of those. The column with a ratio of 2, is as wide
     * as it needs to be, <strong>plus two thirds</strong> of the excess
     * width.
     * <p>
     * This action is done "finally", once the current execution loop
     * returns. This is done to reduce overhead of unintentionally always
     * recalculate all columns, when modifying several columns at once.
     * 
     * @param expandRatio
     *            the expand ratio of this column. {@code 0} to not have it
     *            expand at all. A negative number to clear the expand
     *            value.
     * @return this column
     */
    public Column<C, T> setExpandRatio(int ratio) {
        if (expandRatio != ratio) {
            expandRatio = ratio;
            scheduleColumnWidthRecalculator();
        }
        return this;
    }

    /**
     * Clears the column's expand ratio.
     * <p>
     * Same as calling {@link #setExpandRatio(int) setExpandRatio(-1)}
     * 
     * @return this column
     */
    public Column<C, T> clearExpandRatio() {
        return setExpandRatio(-1);
    }

    /**
     * Gets the minimum width for this column.
     * 
     * @return the minimum width for this column
     * @see #setMinimumWidth(double)
     */
    @JsProperty(name = "minWidth")
    public double getMinimumWidth() {
        return minimumWidthPx;
    }

    /**
     * Gets the maximum width for this column.
     * 
     * @return the maximum width for this column
     * @see #setMaximumWidth(double)
     */
    @JsProperty(name = "maxWidth")
    public double getMaximumWidth() {
        return maximumWidthPx;
    }

    /**
     * Gets the expand ratio for this column.
     * 
     * @return the expand ratio for this column
     * @see #setExpandRatio(int)
     */
    public int getExpandRatio() {
        return expandRatio;
    }

    /**
     * Sets whether the values in this column should be editable by the user
     * when the row editor is active. By default columns are editable.
     * 
     * @param editable
     *            {@code true} to set this column editable, {@code false}
     *            otherwise
     * @return this column
     * 
     * @throws IllegalStateException
     *             if the editor is currently active
     * 
     * @see Grid#editRow(int)
     * @see Grid#isEditorActive()
     */
    public Column<C, T> setEditable(boolean editable) {
        if (editable != this.editable && grid.isEditorActive()) {
            throw new IllegalStateException(
                    "Cannot change column editable status while the editor is active");
        }
        this.editable = editable;
        return this;
    }

    /**
     * Returns whether the values in this column are editable by the user
     * when the row editor is active.
     * 
     * @return {@code true} if this column is editable, {@code false}
     *         otherwise
     * 
     * @see #setEditable(boolean)
     */
    public boolean isEditable() {
        return editable;
    }

    private void scheduleColumnWidthRecalculator() {
        if (grid != null) {
            grid.recalculateColumnWidths();
        } else {
            /*
             * NOOP
             * 
             * Since setGrid() will call reapplyWidths as the colum is
             * attached to a grid, it will call setWidth, which, in turn,
             * will call this method again. Therefore, it's guaranteed that
             * the recalculation is scheduled eventually, once the column is
             * attached to a grid.
             */
        }
    }

    /**
     * Resets the default header cell contents to column header captions.
     * 
     * @since 7.5.1
     * @param cell
     *            default header cell for this column
     */
    protected void setDefaultHeaderContent(Header.HeaderCell cell) {
        cell.setText(headerCaption);
    }

}