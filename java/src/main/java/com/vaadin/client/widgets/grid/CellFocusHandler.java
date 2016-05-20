package com.vaadin.client.widgets.grid;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;

import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.FlyweightCell;
import com.vaadin.client.widget.escalator.Row;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.grid.CellReference;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.ui.grid.ScrollDestination;

import java.util.Arrays;
import java.util.Collection;

public class CellFocusHandler<T> {

    private final Grid<T> grid;
    public RowContainer containerWithFocus; 
    private int rowWithFocus = 0;
    private Range cellFocusRange = Range.withLength(0, 1);
    private int lastFocusedBodyRow = 0;
    private int lastFocusedHeaderRow = 0;
    private int lastFocusedFooterRow = 0;
    private TableCellElement cellWithFocusStyle = null;
    private TableRowElement rowWithFocusStyle = null;

    public CellFocusHandler(Grid<T> grid) {
        this.grid = grid;
        this.grid.sinkEvents(getNavigationEvents());
        this.containerWithFocus = this.grid.escalator.getBody();
    }

    Cell getFocusedCell() {
        return new Cell(rowWithFocus, cellFocusRange.getStart(),
                cellWithFocusStyle);
    }

    /**
     * Sets style names for given cell when needed.
     */
    public void updateFocusedCellStyle(FlyweightCell cell,
            RowContainer cellContainer) {
        int cellRow = cell.getRow();
        int cellColumn = cell.getColumn();
        int colSpan = cell.getColSpan();
        boolean columnHasFocus = Range.withLength(cellColumn, colSpan)
                .intersects(cellFocusRange);

        if (cellContainer == containerWithFocus) {
            // Cell is in the current container
            if (cellRow == rowWithFocus && columnHasFocus) {
                if (cellWithFocusStyle != cell.getElement()) {
                    // Cell is correct but it does not have focused style
                    if (cellWithFocusStyle != null) {
                        // Remove old focus style
                        cellWithFocusStyle.removeClassName(this.grid.cellFocusStyleName);
                    }
                    cellWithFocusStyle = cell.getElement();

                    // Add focus style to correct cell.
                    cellWithFocusStyle.addClassName(this.grid.cellFocusStyleName);
                }
            } else if (cellWithFocusStyle == cell.getElement()) {
                // Due to escalator reusing cells, a new cell has the same
                // element but is not the focused cell.
                cellWithFocusStyle.removeClassName(this.grid.cellFocusStyleName);
                cellWithFocusStyle = null;
            }
        }
    }

    /**
     * Sets focus style for the given row if needed.
     * 
     * @param row
     *            a row object
     */
    public void updateFocusedRowStyle(Row row) {
        if (rowWithFocus == row.getRow()
                && containerWithFocus == this.grid.escalator.getBody()) {
            if (row.getElement() != rowWithFocusStyle) {
                // Row should have focus style but does not have it.
                if (rowWithFocusStyle != null) {
                    rowWithFocusStyle.removeClassName(this.grid.rowFocusStyleName);
                }
                rowWithFocusStyle = row.getElement();
                rowWithFocusStyle.addClassName(this.grid.rowFocusStyleName);
            }
        } else if (rowWithFocusStyle == row.getElement()
                || (containerWithFocus != this.grid.escalator.getBody() && rowWithFocusStyle != null)) {
            // Remove focus style.
            rowWithFocusStyle.removeClassName(this.grid.rowFocusStyleName);
            rowWithFocusStyle = null;
        }
    }

    /**
     * Sets the currently focused.
     * <p>
     * <em>NOTE:</em> the column index is the index in DOM, not the logical
     * column index which includes hidden columns.
     * 
     * @param rowIndex
     *            the index of the row having focus
     * @param columnIndexDOM
     *            the index of the cell having focus
     * @param container
     *            the row container having focus
     */
    void setCellFocus(int rowIndex, int columnIndexDOM,
            RowContainer container) {
        if (rowIndex == rowWithFocus
                && cellFocusRange.contains(columnIndexDOM)
                && container == this.containerWithFocus) {
            refreshRow(rowWithFocus);
            return;
        }

        int oldRow = rowWithFocus;
        rowWithFocus = rowIndex;
        Range oldRange = cellFocusRange;

        if (container == this.grid.escalator.getBody()) {
            this.grid.scrollToRow(rowWithFocus);
            cellFocusRange = Range.withLength(columnIndexDOM, 1);
        } else {
            int i = 0;
            Element cell = container.getRowElement(rowWithFocus)
                    .getFirstChildElement();
            do {
                int colSpan = cell
                        .getPropertyInt(FlyweightCell.COLSPAN_ATTR);
                Range cellRange = Range.withLength(i, colSpan);
                if (cellRange.contains(columnIndexDOM)) {
                    cellFocusRange = cellRange;
                    break;
                }
                cell = cell.getNextSiblingElement();
                ++i;
            } while (cell != null);
        }
        int columnIndex = this.grid.getColumns().indexOf(
                this.grid.getVisibleColumn(columnIndexDOM));
        if (columnIndex >= this.grid.escalator.getColumnConfiguration()
                .getFrozenColumnCount()) {
            this.grid.escalator.scrollToColumn(columnIndexDOM, ScrollDestination.ANY,
                    10);
        }

        if (this.containerWithFocus == container) {
            if (oldRange.equals(cellFocusRange) && oldRow != rowWithFocus) {
                refreshRow(oldRow);
            } else {
                this.grid.refreshHeader();
                this.grid.refreshFooter();
            }
        } else {
            RowContainer oldContainer = this.containerWithFocus;
            this.containerWithFocus = container;

            if (oldContainer == this.grid.escalator.getBody()) {
                lastFocusedBodyRow = oldRow;
            } else if (oldContainer == this.grid.escalator.getHeader()) {
                lastFocusedHeaderRow = oldRow;
            } else {
                lastFocusedFooterRow = oldRow;
            }

            if (!oldRange.equals(cellFocusRange)) {
                this.grid.refreshHeader();
                this.grid.refreshFooter();
                if (oldContainer == this.grid.escalator.getBody()) {
                    oldContainer.refreshRows(oldRow, 1);
                }
            } else {
                oldContainer.refreshRows(oldRow, 1);
            }
        }
        refreshRow(rowWithFocus);
    }

    /**
     * Sets focus on a cell.
     * 
     * <p>
     * <em>Note</em>: cell focus is not the same as JavaScript's
     * {@code document.activeElement}.
     * 
     * @param cell
     *            a cell object
     */
    public void setCellFocus(CellReference<T> cell) {
        setCellFocus(cell.getRowIndex(), cell.getColumnIndexDOM(),
                this.grid.escalator.findRowContainer(cell.getElement()));
    }

    /**
     * Gets list of events that can be used for cell focusing.
     * 
     * @return list of navigation related event types
     */
    public Collection<String> getNavigationEvents() {
        return Arrays.asList(BrowserEvents.KEYDOWN, BrowserEvents.CLICK);
    }

    /**
     * Handle events that can move the cell focus.
     */
    public void handleNavigationEvent(Event event, CellReference<T> cell) {
        if (event.getType().equals(BrowserEvents.CLICK)) {
            setCellFocus(cell);
            // Grid should have focus when clicked.
            this.grid.getElement().focus();
        } else if (event.getType().equals(BrowserEvents.KEYDOWN)) {
            int newRow = rowWithFocus;
            RowContainer newContainer = containerWithFocus;
            int newColumn = cellFocusRange.getStart();

            switch (event.getKeyCode()) {
            case KeyCodes.KEY_DOWN:
                ++newRow;
                break;
            case KeyCodes.KEY_UP:
                --newRow;
                break;
            case KeyCodes.KEY_RIGHT:
                if (cellFocusRange.getEnd() >= this.grid.getVisibleColumns().size()) {
                    return;
                }
                newColumn = cellFocusRange.getEnd();
                break;
            case KeyCodes.KEY_LEFT:
                if (newColumn == 0) {
                    return;
                }
                --newColumn;
                break;
            case KeyCodes.KEY_TAB:
                if (event.getShiftKey()) {
                    newContainer = getPreviousContainer(containerWithFocus);
                } else {
                    newContainer = getNextContainer(containerWithFocus);
                }

                if (newContainer == containerWithFocus) {
                    return;
                }
                break;
            case KeyCodes.KEY_HOME:
                if (newContainer.getRowCount() > 0) {
                    newRow = 0;
                }
                break;
            case KeyCodes.KEY_END:
                if (newContainer.getRowCount() > 0) {
                    newRow = newContainer.getRowCount() - 1;
                }
                break;
            case KeyCodes.KEY_PAGEDOWN:
            case KeyCodes.KEY_PAGEUP:
                if (newContainer.getRowCount() > 0) {
                    boolean down = event.getKeyCode() == KeyCodes.KEY_PAGEDOWN;
                    // If there is a visible focused cell, scroll by one
                    // page from its position. Otherwise, use the first or
                    // the last visible row as the scroll start position.
                    // This avoids jumping when using both keyboard and the
                    // scroll bar for scrolling.
                    int firstVisible = this.grid.getFirstVisibleRowIndex();
                    int lastVisible = this.grid.getLastVisibleRowIndex();
                    if (newRow < firstVisible || newRow > lastVisible) {
                        newRow = down ? lastVisible : firstVisible;
                    }
                    // Scroll by a little less than the visible area to
                    // account for the possibility that the top and the
                    // bottom row are only partially visible.
                    int moveFocusBy = Math.max(1, lastVisible
                            - firstVisible - 1);
                    moveFocusBy *= down ? 1 : -1;
                    newRow += moveFocusBy;
                    newRow = Math.max(0, Math.min(
                            newContainer.getRowCount() - 1, newRow));
                }
                break;
            default:
                return;
            }

            if (newContainer != containerWithFocus) {
                if (newContainer == this.grid.escalator.getBody()) {
                    newRow = lastFocusedBodyRow;
                } else if (newContainer == this.grid.escalator.getHeader()) {
                    newRow = lastFocusedHeaderRow;
                } else {
                    newRow = lastFocusedFooterRow;
                }
            } else if (newRow < 0) {
                newContainer = getPreviousContainer(newContainer);

                if (newContainer == containerWithFocus) {
                    newRow = 0;
                } else if (newContainer == this.grid.escalator.getBody()) {
                    newRow = this.grid.getLastVisibleRowIndex();
                } else {
                    newRow = newContainer.getRowCount() - 1;
                }
            } else if (newRow >= containerWithFocus.getRowCount()) {
                newContainer = getNextContainer(newContainer);

                if (newContainer == containerWithFocus) {
                    newRow = containerWithFocus.getRowCount() - 1;
                } else if (newContainer == this.grid.escalator.getBody()) {
                    newRow = this.grid.getFirstVisibleRowIndex();
                } else {
                    newRow = 0;
                }
            }

            if (newContainer.getRowCount() == 0) {
                /*
                 * There are no rows in the container. Can't change the
                 * focused cell.
                 */
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            setCellFocus(newRow, newColumn, newContainer);
        }

    }

    private RowContainer getPreviousContainer(RowContainer current) {
        if (current == this.grid.escalator.getFooter()) {
            current = this.grid.escalator.getBody();
        } else if (current == this.grid.escalator.getBody()) {
            current = this.grid.escalator.getHeader();
        } else {
            return current;
        }

        if (current.getRowCount() == 0) {
            return getPreviousContainer(current);
        }
        return current;
    }

    private RowContainer getNextContainer(RowContainer current) {
        if (current == this.grid.escalator.getHeader()) {
            current = this.grid.escalator.getBody();
        } else if (current == this.grid.escalator.getBody()) {
            current = this.grid.escalator.getFooter();
        } else {
            return current;
        }

        if (current.getRowCount() == 0) {
            return getNextContainer(current);
        }
        return current;
    }

    private void refreshRow(int row) {
        containerWithFocus.refreshRows(row, 1);
    }

    /**
     * Offsets the focused cell's range.
     * 
     * @param offset
     *            offset for fixing focused cell's range
     */
    public void offsetRangeBy(int offset) {
        cellFocusRange = cellFocusRange.offsetBy(offset);
    }

    /**
     * Informs {@link CellFocusHandler} that certain range of rows has been
     * added to the Grid body. {@link CellFocusHandler} will fix indices
     * accordingly.
     * 
     * @param added
     *            a range of added rows
     */
    public void rowsAddedToBody(Range added) {
        boolean bodyHasFocus = (containerWithFocus == this.grid.escalator.getBody());
        boolean insertionIsAboveFocusedCell = (added.getStart() <= rowWithFocus);
        if (bodyHasFocus && insertionIsAboveFocusedCell) {
            rowWithFocus += added.length();
            rowWithFocus = Math.min(rowWithFocus, this.grid.escalator.getBody()
                    .getRowCount() - 1);
            refreshRow(rowWithFocus);
        }
    }

    /**
     * Informs {@link CellFocusHandler} that certain range of rows has been
     * removed from the Grid body. {@link CellFocusHandler} will fix indices
     * accordingly.
     * 
     * @param removed
     *            a range of removed rows
     */
    public void rowsRemovedFromBody(Range removed) {
        if (containerWithFocus != this.grid.escalator.getBody()) {
            return;
        } else if (!removed.contains(rowWithFocus)) {
            if (removed.getStart() > rowWithFocus) {
                return;
            }
            rowWithFocus = rowWithFocus - removed.length();
        } else {
            if (containerWithFocus.getRowCount() > removed.getEnd()) {
                rowWithFocus = removed.getStart();
            } else if (removed.getStart() > 0) {
                rowWithFocus = removed.getStart() - 1;
            } else {
                if (this.grid.escalator.getHeader().getRowCount() > 0) {
                    rowWithFocus = Math.min(lastFocusedHeaderRow, this.grid.escalator
                            .getHeader().getRowCount() - 1);
                    containerWithFocus = this.grid.escalator.getHeader();
                } else if (this.grid.escalator.getFooter().getRowCount() > 0) {
                    rowWithFocus = Math.min(lastFocusedFooterRow, this.grid.escalator
                            .getFooter().getRowCount() - 1);
                    containerWithFocus = this.grid.escalator.getFooter();
                }
            }
        }
        refreshRow(rowWithFocus);
    }
}