package com.vaadin.client.widgets.grid;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.CheckBox;

import com.vaadin.client.renderers.Renderer;
import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.client.widget.grid.events.GridClickEvent;
import com.vaadin.client.widget.grid.events.GridKeyUpEvent;
import com.vaadin.client.widget.grid.events.HeaderClickHandler;
import com.vaadin.client.widget.grid.events.HeaderKeyUpHandler;
import com.vaadin.client.widget.grid.events.SelectAllEvent;
import com.vaadin.client.widget.grid.selection.SelectionModel;
import com.vaadin.client.widget.grid.selection.SelectionModel.Multi;
import com.vaadin.shared.ui.grid.GridStaticCellType;

public final class SelectionColumn<T> extends Column<Boolean, T> {

    /**
     * 
     */
    private final Grid<T> grid;
    private boolean initDone = false;
    private boolean selected = false;
    private CheckBox selectAllCheckBox;

    SelectionColumn(Grid<T> grid, final Renderer<Boolean> selectColumnRenderer) {
        super(selectColumnRenderer);
        this.grid = grid;
    }

    void initDone() {
        setWidth(-1);

        setEditable(false);
        setResizable(false);

        initDone = true;
    }

    @Override
    protected void setDefaultHeaderContent(Header.HeaderCell selectionCell) {
        /*
         * TODO: Currently the select all check box is shown when multi
         * selection is in use. This might result in malfunctions if no
         * SelectAllHandlers are present.
         * 
         * Later on this could be fixed so that it check such handlers
         * exist.
         */
        final SelectionModel.Multi<T> model = (Multi<T>) this.grid.getSelectionModel();

        if (selectAllCheckBox == null) {
            selectAllCheckBox = GWT.create(CheckBox.class);
            selectAllCheckBox.setStylePrimaryName(this.grid.getStylePrimaryName()
                    + Grid.SELECT_ALL_CHECKBOX_CLASSNAME);
            selectAllCheckBox
                    .addValueChangeHandler(new ValueChangeHandler<Boolean>() {

                        @Override
                        public void onValueChange(
                                ValueChangeEvent<Boolean> event) {
                            if (event.getValue()) {
                                SelectionColumn.this.grid.fireEvent(new SelectAllEvent<T>(model));
                                selected = true;
                            } else {
                                model.deselectAll();
                                selected = false;
                            }
                        }
                    });
            selectAllCheckBox.setValue(selected);

            this.grid.addHeaderClickHandler(new HeaderClickHandler() {
                @Override
                public void onClick(GridClickEvent event) {
                    CellReference<?> targetCell = event.getTargetCell();
                    int defaultRowIndex = SelectionColumn.this.grid.getHeader().getRows().indexOf(
                            SelectionColumn.this.grid.getDefaultHeaderRow());

                    if (targetCell.getColumnIndex() == 0
                            && targetCell.getRowIndex() == defaultRowIndex) {
                        selectAllCheckBox.setValue(
                                !selectAllCheckBox.getValue(), true);
                    }
                }
            });

            // Select all with space when "select all" cell is active
            this.grid.addHeaderKeyUpHandler(new HeaderKeyUpHandler() {
                @Override
                public void onKeyUp(GridKeyUpEvent event) {
                    if (event.getNativeKeyCode() != KeyCodes.KEY_SPACE) {
                        return;
                    }
                    Header.HeaderRow targetHeaderRow = SelectionColumn.this.grid.getHeader().getRow(
                            event.getFocusedCell().getRowIndex());
                    if (!targetHeaderRow.isDefault()) {
                        return;
                    }
                    if (event.getFocusedCell().getColumn() == SelectionColumn.this) {
                        // Send events to ensure state is updated
                        selectAllCheckBox.setValue(
                                !selectAllCheckBox.getValue(), true);
                    }
                }
            });
        } else {
            for (Header.HeaderRow row : this.grid.header.getRows()) {
                if (row.getCell(this).getType() == GridStaticCellType.WIDGET) {
                    // Detach from old header.
                    row.getCell(this).setText("");
                }
            }
        }

        selectionCell.setWidget(selectAllCheckBox);
    }

    @Override
    public Column<Boolean, T> setWidth(double pixels) {
        if (pixels != getWidth() && initDone) {
            throw new UnsupportedOperationException("The selection "
                    + "column cannot be modified after init");
        } else {
            super.setWidth(pixels);
        }

        return this;
    }

    @Override
    public Boolean getValue(T row) {
        return Boolean.valueOf(this.grid.isSelected(row));
    }

    @Override
    public Column<Boolean, T> setExpandRatio(int ratio) {
        throw new UnsupportedOperationException(
                "can't change the expand ratio of the selection column");
    }

    @Override
    public int getExpandRatio() {
        return 0;
    }

    @Override
    public Column<Boolean, T> setMaximumWidth(double pixels) {
        throw new UnsupportedOperationException(
                "can't change the maximum width of the selection column");
    }

    @Override
    public double getMaximumWidth() {
        return -1;
    }

    @Override
    public Column<Boolean, T> setMinimumWidth(double pixels) {
        throw new UnsupportedOperationException(
                "can't change the minimum width of the selection column");
    }

    @Override
    public double getMinimumWidth() {
        return -1;
    }

    @Override
    public Column<Boolean, T> setEditable(boolean editable) {
        if (initDone) {
            throw new UnsupportedOperationException(
                    "can't set the selection column editable");
        }
        super.setEditable(editable);
        return this;
    }
}