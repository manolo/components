package com.vaadin.client.widgets.grid;

import static com.google.gwt.query.client.GQuery.console;

import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.renderers.ComplexRenderer;
import com.vaadin.client.renderers.Renderer;
import com.vaadin.client.renderers.WidgetRenderer;
import com.vaadin.client.renderers.ComplexRenderer.ComplexRendererInternal;
import com.vaadin.client.widget.escalator.EscalatorUpdater;
import com.vaadin.client.widget.escalator.FlyweightCell;
import com.vaadin.client.widget.escalator.Row;

import java.util.logging.Level;

class BodyUpdater<T> implements EscalatorUpdater {

    private final Grid<T> grid;

    BodyUpdater(Grid<T> grid) {
        this.grid = grid;
    }

    @Override
    public void preAttach(Row row, Iterable<FlyweightCell> cellsToAttach) {
        int rowIndex = row.getRow();
        grid.rowReference.set(rowIndex, grid.getDataSource().getRow(rowIndex),
                row.getElement());
        for (FlyweightCell cell : cellsToAttach) {
            Renderer<?> renderer = grid.findRenderer(cell);
            console.log("AAA", renderer.getClass().getName());
            if (renderer instanceof ComplexRenderer.ComplexRendererInternal) {
                try {
                    Column<?, T> column = grid.getVisibleColumn(cell.getColumn());
                    grid.rendererCellReference.set(cell,
                            grid.getColumns().indexOf(column), column);
                    console.log("init");
                    ((ComplexRenderer<?>) renderer)
                            .init(grid.rendererCellReference);
                } catch (RuntimeException e) {
                    Grid.getLogger().log(
                            Level.SEVERE,
                            "Error initing cell in column "
                                    + cell.getColumn(), e);
                }
            }
        }
    }

    @Override
    public void postAttach(Row row, Iterable<FlyweightCell> attachedCells) {
        for (FlyweightCell cell : attachedCells) {
            Renderer<?> renderer = grid.findRenderer(cell);
            if (renderer instanceof WidgetRenderer) {
                try {
                    WidgetRenderer<?, ?> widgetRenderer = (WidgetRenderer<?, ?>) renderer;

                    Widget widget = widgetRenderer.createWidget();
                    assert widget != null : "WidgetRenderer.createWidget() returned null. It should return a widget.";
                    assert widget.getParent() == null : "WidgetRenderer.createWidget() returned a widget which already is attached.";
                    assert cell.getElement().getChildCount() == 0 : "Cell content should be empty when adding Widget";

                    // Physical attach
                    cell.getElement().appendChild(widget.getElement());

                    // Logical attach
                    Grid.setParent(widget, grid);
                } catch (RuntimeException e) {
                    Grid.getLogger().log(
                            Level.SEVERE,
                            "Error attaching child widget in column "
                                    + cell.getColumn(), e);
                }
            }
        }
    }

    @Override
    public void update(Row row, Iterable<FlyweightCell> cellsToUpdate) {
        int rowIndex = row.getRow();
        TableRowElement rowElement = row.getElement();
        T rowData = grid.dataSource.getRow(rowIndex);

        boolean hasData = rowData != null;

        /*
         * TODO could be more efficient to build a list of all styles that
         * should be used and update the element only once instead of
         * attempting to update only the ones that have changed.
         */

        // Assign stylename for rows with data
        boolean usedToHaveData = rowElement
                .hasClassName(grid.rowHasDataStyleName);

        if (usedToHaveData != hasData) {
            if (hasData) {
                rowElement.addClassName(grid.rowHasDataStyleName);
            } else {
                rowElement.removeClassName(grid.rowHasDataStyleName);
            }
        }

        boolean isEvenIndex = (row.getRow() % 2 == 0);
        if (!isEvenIndex) {
            rowElement.addClassName(grid.rowStripeStyleName);
        } else {
            rowElement.removeClassName(grid.rowStripeStyleName);
        }

        grid.rowReference.set(rowIndex, rowData, rowElement);

        if (hasData) {
            if (grid.isSelected(rowData)) {
                rowElement.addClassName(grid.rowSelectedStyleName);
            } else {
                rowElement.removeClassName(grid.rowSelectedStyleName);
            }

            if (grid.rowStyleGenerator != null) {
                try {
                    String rowStylename = grid.rowStyleGenerator
                            .getStyle(grid.rowReference);
                    Grid.setCustomStyleName(rowElement, rowStylename);
                } catch (RuntimeException e) {
                    Grid.getLogger().log(
                            Level.SEVERE,
                            "Error generating styles for row "
                                    + row.getRow(), e);
                }
            } else {
                // Remove in case there was a generator previously
                Grid.setCustomStyleName(rowElement, null);
            }
        } else if (usedToHaveData) {
            rowElement.removeClassName(grid.rowSelectedStyleName);
            Grid.setCustomStyleName(rowElement, null);
        }

        grid.cellFocusHandler.updateFocusedRowStyle(row);

        for (FlyweightCell cell : cellsToUpdate) {
            Column<?, T> column = grid.getVisibleColumn(cell.getColumn());
            final int columnIndex = grid.getColumns().indexOf(column);

            assert column != null : "Column was not found from cell ("
                    + cell.getColumn() + "," + cell.getRow() + ")";

            grid.cellFocusHandler.updateFocusedCellStyle(cell,
                    grid.getEscalator().getBody());

            if (hasData && grid.cellStyleGenerator != null) {
                try {
                    grid.cellReference
                            .set(cell.getColumn(), columnIndex, column);
                    String generatedStyle = grid.cellStyleGenerator
                            .getStyle(grid.cellReference);
                    Grid.setCustomStyleName(cell.getElement(), generatedStyle);
                } catch (RuntimeException e) {
                    Grid.getLogger().log(
                            Level.SEVERE,
                            "Error generating style for cell in column "
                                    + cell.getColumn(), e);
                }
            } else if (hasData || usedToHaveData) {
                Grid.setCustomStyleName(cell.getElement(), null);
            }

            Renderer renderer = column.getRenderer();

            try {
                grid.rendererCellReference.set(cell, columnIndex, column);
                if (renderer instanceof ComplexRendererInternal) {
                    // Hide cell content if needed
                    ComplexRenderer clxRenderer = ((ComplexRendererInternal) renderer).complexRenderer;
                    
                    console.log("update", clxRenderer.getClass().getName());
                    if (hasData) {
                        if (!usedToHaveData) {
                            // Prepare cell for rendering
                            clxRenderer.setContentVisible(
                                    grid.rendererCellReference, true);
                        }

                        Object value = column.getValue(rowData);
                        clxRenderer.renderer.render(grid.rendererCellReference, value);

                    } else {
                        // Prepare cell for no data
                        clxRenderer.setContentVisible(
                                grid.rendererCellReference, false);
                    }

                } else if (hasData) {
                    // Simple renderers just render
                    Object value = column.getValue(rowData);
                    renderer.render(grid.rendererCellReference, value);

                } else {
                    // Clear cell if there is no data
                    cell.getElement().removeAllChildren();
                }
            } catch (RuntimeException e) {
                Grid.getLogger().log(
                        Level.SEVERE,
                        "Error rendering cell in column "
                                + cell.getColumn(), e);
            }
        }
    }

    @Override
    public void preDetach(Row row, Iterable<FlyweightCell> cellsToDetach) {
        for (FlyweightCell cell : cellsToDetach) {
            Renderer<?> renderer = grid.findRenderer(cell);
            if (renderer instanceof WidgetRenderer) {
                try {
                    Widget w = WidgetUtil.findWidget(cell.getElement()
                            .getFirstChildElement(), null);
                    if (w != null) {

                        // Logical detach
                        Grid.setParent(w, null);

                        // Physical detach
                        cell.getElement().removeChild(w.getElement());
                    }
                } catch (RuntimeException e) {
                    Grid.getLogger().log(
                            Level.SEVERE,
                            "Error detaching widget in column "
                                    + cell.getColumn(), e);
                }
            }
        }
    }

    @Override
    public void postDetach(Row row, Iterable<FlyweightCell> detachedCells) {
        int rowIndex = row.getRow();
        // Passing null row data since it might not exist in the data source
        // any more
        grid.rowReference.set(rowIndex, null, row.getElement());
        for (FlyweightCell cell : detachedCells) {
            Renderer<?> renderer = grid.findRenderer(cell);
            if (renderer instanceof ComplexRenderer) {
                try {
                    Column<?, T> column = grid.getVisibleColumn(cell.getColumn());
                    grid.rendererCellReference.set(cell,
                            grid.getColumns().indexOf(column), column);
                    ((ComplexRenderer) renderer)
                            .destroy(grid.rendererCellReference);
                } catch (RuntimeException e) {
                    Grid.getLogger().log(
                            Level.SEVERE,
                            "Error destroying cell in column "
                                    + cell.getColumn(), e);
                }
            }
        }
    }
}