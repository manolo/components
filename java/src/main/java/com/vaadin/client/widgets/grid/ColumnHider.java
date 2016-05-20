package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.ui.MenuItem;

import java.util.HashMap;

/**
 * UI and functionality related to hiding columns with toggles in the
 * sidebar.
 */
final class ColumnHider<T> {

    /**
     * 
     */
    private final Grid<T> grid;

    /**
     * @param grid
     */
    ColumnHider(Grid<T> grid) {
        this.grid = grid;
    }

    /** Map from columns to their hiding toggles, component might change */
    private HashMap<Column<?, T>, MenuItem> columnToHidingToggleMap = new HashMap<Column<?, T>, MenuItem>();

    /**
     * When column is being hidden with a toggle, do not refresh toggles for
     * no reason. Also helps for keeping the keyboard navigation working.
     */
    private boolean hidingColumn;

    void updateColumnHidable(final Column<?, T> column) {
        if (column.isHidable()) {
            MenuItem toggle = columnToHidingToggleMap.get(column);
            if (toggle == null) {
                toggle = createToggle(column);
            }
            toggle.setStyleName("hidden", column.isHidden());
        } else if (columnToHidingToggleMap.containsKey(column)) {
            this.grid.sidebar.menuBar.removeItem((columnToHidingToggleMap
                    .remove(column)));
        }
        updateTogglesOrder();
    }

    private MenuItem createToggle(final Column<?, T> column) {
        MenuItem toggle = new MenuItem(createHTML(column), true,
                new ScheduledCommand() {

                    @Override
                    public void execute() {
                        hidingColumn = true;
                        column.setHidden(!column.isHidden(), true);
                        hidingColumn = false;
                    }
                });
        toggle.addStyleName("column-hiding-toggle");
        columnToHidingToggleMap.put(column, toggle);
        return toggle;
    }

    private String createHTML(Column<?, T> column) {
        final StringBuffer buf = new StringBuffer();
        buf.append("<span class=\"");
        if (column.isHidden()) {
            buf.append("v-off");
        } else {
            buf.append("v-on");
        }
        buf.append("\"><div>");
        String caption = column.getHidingToggleCaption();
        if (caption == null) {
            caption = column.headerCaption;
        }
        buf.append(caption);
        buf.append("</div></span>");

        return buf.toString();
    }

    void updateTogglesOrder() {
        if (!hidingColumn) {
            int lastIndex = 0;
            for (Column<?, T> column : this.grid.getColumns()) {
                if (column.isHidable()) {
                    final MenuItem menuItem = columnToHidingToggleMap
                            .get(column);
                    this.grid.sidebar.menuBar.removeItem(menuItem);
                    this.grid.sidebar.menuBar.insertItem(menuItem, lastIndex++);
                }
            }
        }
    }

    void updateHidingToggle(Column<?, T> column) {
        if (column.isHidable()) {
            MenuItem toggle = columnToHidingToggleMap.get(column);
            toggle.setHTML(createHTML(column));
            toggle.setStyleName("hidden", column.isHidden());
        } // else we can just ignore
    }

    void removeColumnHidingToggle(Column<?, T> column) {
        this.grid.sidebar.menuBar.removeItem(columnToHidingToggleMap.get(column));
    }

}