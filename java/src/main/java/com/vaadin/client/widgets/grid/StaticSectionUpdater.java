package com.vaadin.client.widgets.grid;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.ui.dd.DragHandle;
import com.vaadin.client.ui.dd.DragHandle.DragHandleCallback;
import com.vaadin.client.widget.escalator.EscalatorUpdater;
import com.vaadin.client.widget.escalator.FlyweightCell;
import com.vaadin.client.widget.escalator.Row;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.grid.events.ColumnResizeEvent;
import com.vaadin.client.widget.grid.sort.SortOrder;
import com.vaadin.shared.data.sort.SortDirection;
import com.vaadin.shared.ui.grid.GridStaticCellType;

import java.util.Arrays;
import java.util.List;

class StaticSectionUpdater<T> implements EscalatorUpdater {

    private final Grid<T> grid;
    private StaticSection<?> section;
    private RowContainer container;

    public StaticSectionUpdater(Grid<T> grid, StaticSection<?> section,
            RowContainer container) {
        super();
        this.grid = grid;
        this.section = section;
        this.container = container;
    }

    @Override
    public void update(Row row, Iterable<FlyweightCell> cellsToUpdate) {
        StaticSection.StaticRow<?> staticRow = section.getRow(row.getRow());
        final List<Column<?, T>> columns = this.grid.getVisibleColumns();

        Grid.setCustomStyleName(row.getElement(), staticRow.getStyleName());

        for (FlyweightCell cell : cellsToUpdate) {
            final StaticCell metadata = staticRow
                    .getCell(columns.get(cell.getColumn()));

            // Decorate default row with sorting indicators
            if (staticRow instanceof Header.HeaderRow) {
                addSortingIndicatorsToHeaderRow((Header.HeaderRow) staticRow, cell);
            }

            // Assign colspan to cell before rendering
            cell.setColSpan(metadata.getColspan());

            Element td = cell.getElement();
            td.removeAllChildren();
            Grid.setCustomStyleName(td, metadata.getStyleName());

            Element content;
            // Wrap text or html content in default header to isolate
            // the content from the possible column resize drag handle
            // next to it
            if (metadata.getType() != GridStaticCellType.WIDGET) {
                content = DOM.createDiv();

                if (staticRow instanceof Header.HeaderRow) {
                    content.setClassName(this.grid.getStylePrimaryName()
                            + "-column-header-content");
                    if (((Header.HeaderRow) staticRow).isDefault()) {
                        content.setClassName(content.getClassName() + " "
                                + this.grid.getStylePrimaryName()
                                + "-column-default-header-content");
                    }
                } else if (staticRow instanceof Footer.FooterRow) {
                    content.setClassName(this.grid.getStylePrimaryName()
                            + "-column-footer-content");
                } else {
                    Grid.getLogger().severe(
                            "Unhandled static row type "
                                    + staticRow.getClass()
                                            .getCanonicalName());
                }

                td.appendChild(content);
            } else {
                content = td;
            }

            switch (metadata.getType()) {
            case TEXT:
                content.setInnerText(metadata.getText());
                break;
            case HTML:
                content.setInnerHTML(metadata.getHtml());
                break;
            case WIDGET:
                preDetach(row, Arrays.asList(cell));
                content.setInnerHTML("");
                postAttach(row, Arrays.asList(cell));
                break;
            }

            // XXX: Should add only once in preAttach/postAttach or when
            // resizable status changes
            // Only add resize handles to default header row for now
            if (columns.get(cell.getColumn()).isResizable()
                    && staticRow instanceof Header.HeaderRow
                    && ((Header.HeaderRow) staticRow).isDefault()) {

                final int column = cell.getColumn();
                DragHandle dragger = new DragHandle(this.grid.getStylePrimaryName()
                        + "-column-resize-handle",
                        new DragHandleCallback() {

                            private Column<?, T> col = StaticSectionUpdater.this.grid.getVisibleColumn(column);
                            private double initialWidth = 0;
                            private double minCellWidth;

                            @Override
                            public void onUpdate(double deltaX,
                                    double deltaY) {
                                col.setWidth(Math.max(minCellWidth,
                                        initialWidth + deltaX));
                            }

                            @Override
                            public void onStart() {
                                initialWidth = col.getWidthActual();

                                minCellWidth = StaticSectionUpdater.this.grid.getEscalator()
                                        .getMinCellWidth(StaticSectionUpdater.this.grid.getColumns()
                                                .indexOf(col));
                                for (Column<?, T> c : StaticSectionUpdater.this.grid.getColumns()) {
                                    if (StaticSectionUpdater.this.grid.selectionColumn == c) {
                                        // Don't modify selection column.
                                        continue;
                                    }

                                    if (c.getWidth() < 0) {
                                        c.setWidth(c.getWidthActual());
                                        StaticSectionUpdater.this.grid.fireEvent(new ColumnResizeEvent<T>(
                                                c));
                                    }
                                }

                                WidgetUtil.setTextSelectionEnabled(
                                        StaticSectionUpdater.this.grid.getElement(), false);
                            }

                            @Override
                            public void onComplete() {
                                StaticSectionUpdater.this.grid.fireEvent(new ColumnResizeEvent<T>(col));

                                WidgetUtil.setTextSelectionEnabled(
                                        StaticSectionUpdater.this.grid.getElement(), true);
                            }

                            @Override
                            public void onCancel() {
                                col.setWidth(initialWidth);

                                WidgetUtil.setTextSelectionEnabled(
                                        StaticSectionUpdater.this.grid.getElement(), true);
                            }
                        });
                dragger.addTo(td);
            }

            this.grid.cellFocusHandler.updateFocusedCellStyle(cell, container);
        }
    }

    private void addSortingIndicatorsToHeaderRow(Header.HeaderRow headerRow,
            FlyweightCell cell) {

        Element cellElement = cell.getElement();

        boolean sortedBefore = cellElement.hasClassName("sort-asc")
                || cellElement.hasClassName("sort-desc");

        cleanup(cell);
        if (!headerRow.isDefault()) {
            // Nothing more to do if not in the default row
            return;
        }

        final Column<?, T> column = this.grid.getVisibleColumn(cell.getColumn());
        SortOrder sortingOrder = getSortOrder(column);
        boolean sortable = column.isSortable();

        if (sortable) {
            cellElement.addClassName("sortable");
        }

        if (!sortable || sortingOrder == null) {
            // Only apply sorting indicators to sortable header columns
            return;
        }

        if (SortDirection.ASCENDING == sortingOrder.getDirection()) {
            cellElement.addClassName("sort-asc");
        } else {
            cellElement.addClassName("sort-desc");
        }

        int sortIndex = grid.getSortOrder().indexOf(sortingOrder);
        if (sortIndex > -1 && grid.getSortOrder().size() > 1) {
            // Show sort order indicator if column is
            // sorted and other sorted columns also exists.
            cellElement.setAttribute("sort-order",
                    String.valueOf(sortIndex + 1));
        }

        if (!sortedBefore) {
            verifyColumnWidth(column);
        }
    }

    /**
     * Sort indicator requires a bit more space from the cell than normally.
     * This method check that the now sorted column has enough width.
     * 
     * @param column
     *            sorted column
     */
    private void verifyColumnWidth(Column<?, T> column) {
        int colIndex = this.grid.getColumns().indexOf(column);
        double minWidth = this.grid.getEscalator().getMinCellWidth(colIndex);
        if (column.getWidthActual() < minWidth) {
            // Fix column size
            this.grid.getEscalator().getColumnConfiguration().setColumnWidth(colIndex,
                    minWidth);

            this.grid.fireEvent(new ColumnResizeEvent<T>(column));
        }
    }

    /**
     * Finds the sort order for this column
     */
    private SortOrder getSortOrder(Column<?, ?> column) {
        for (SortOrder order : grid.getSortOrder()) {
            if (order.getColumn() == column) {
                return order;
            }
        }
        return null;
    }

    private void cleanup(FlyweightCell cell) {
        Element cellElement = cell.getElement();
        cellElement.removeAttribute("sort-order");
        cellElement.removeClassName("sort-desc");
        cellElement.removeClassName("sort-asc");
        cellElement.removeClassName("sortable");
    }

    @Override
    public void preAttach(Row row, Iterable<FlyweightCell> cellsToAttach) {
    }

    @Override
    public void postAttach(Row row, Iterable<FlyweightCell> attachedCells) {
        StaticSection.StaticRow<?> gridRow = section.getRow(row.getRow());
        List<Column<?, T>> columns = this.grid.getVisibleColumns();

        for (FlyweightCell cell : attachedCells) {
            StaticCell metadata = gridRow.getCell(columns
                    .get(cell.getColumn()));
            /*
             * If the cell contains widgets that are not currently attached
             * then attach them now.
             */
            if (GridStaticCellType.WIDGET.equals(metadata.getType())) {
                final Widget widget = metadata.getWidget();
                if (widget != null && !widget.isAttached()) {
                    getGrid().attachWidget(metadata.getWidget(),
                            cell.getElement());
                }
            }
        }
    }

    @Override
    public void preDetach(Row row, Iterable<FlyweightCell> cellsToDetach) {
        if (section.getRowCount() > row.getRow()) {
            StaticSection.StaticRow<?> gridRow = section.getRow(row
                    .getRow());
            List<Column<?, T>> columns = this.grid.getVisibleColumns();
            for (FlyweightCell cell : cellsToDetach) {
                StaticCell metadata = gridRow.getCell(columns
                        .get(cell.getColumn()));

                if (GridStaticCellType.WIDGET.equals(metadata.getType())
                        && metadata.getWidget() != null
                        && metadata.getWidget().isAttached()) {

                    getGrid().detachWidget(metadata.getWidget());
                }
            }
        }
    }

    protected Grid getGrid() {
        return section.grid;
    }

    @Override
    public void postDetach(Row row, Iterable<FlyweightCell> detachedCells) {
    }
}