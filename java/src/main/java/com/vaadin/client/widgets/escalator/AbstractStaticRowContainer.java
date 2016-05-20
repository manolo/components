package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;

import com.vaadin.client.Profiler;
import com.vaadin.shared.ui.grid.Range;

abstract class AbstractStaticRowContainer extends
        AbstractRowContainer {

    /** The height of the combined rows in the DOM. Never negative. */
    private double heightOfSection = 0;

    public AbstractStaticRowContainer(Escalator escalator, final TableSectionElement headElement) {
        super(escalator, headElement);
    }

    @Override
    public int getDomRowCount() {
        return root.getChildCount();
    }

    @Override
    protected void paintRemoveRows(final int index, final int numberOfRows) {
        for (int i = index; i < index + numberOfRows; i++) {
            final TableRowElement tr = root.getRows().getItem(index);
            paintRemoveRow(tr, index);
        }
        recalculateSectionHeight();
    }

    @Override
    protected TableRowElement getTrByVisualIndex(final int index)
            throws IndexOutOfBoundsException {
        if (index >= 0 && index < root.getChildCount()) {
            return root.getRows().getItem(index);
        } else {
            throw new IndexOutOfBoundsException("No such visual index: "
                    + index);
        }
    }

    @Override
    public void insertRows(int index, int numberOfRows) {
        super.insertRows(index, numberOfRows);
        this.escalator.recalculateElementSizes();
        this.escalator.applyHeightByRows();
    }

    @Override
    public void removeRows(int index, int numberOfRows) {

        /*
         * While the rows in a static section are removed, the scrollbar is
         * temporarily shrunk and then re-expanded. This leads to the fact
         * that the scroll position is scooted up a bit. This means that we
         * need to reset the position here.
         * 
         * If Escalator, at some point, gets a JIT evaluation functionality,
         * this re-setting is a strong candidate for removal.
         */
        double oldScrollPos = this.escalator.verticalScrollbar.getScrollPos();

        super.removeRows(index, numberOfRows);
        this.escalator.recalculateElementSizes();
        this.escalator.applyHeightByRows();

        this.escalator.verticalScrollbar.setScrollPos(oldScrollPos);
    }

    @Override
    protected void reapplyDefaultRowHeights() {
        if (root.getChildCount() == 0) {
            return;
        }

        Profiler.enter("Escalator.AbstractStaticRowContainer.reapplyDefaultRowHeights");

        Element tr = root.getRows().getItem(0);
        while (tr != null) {
            reapplyRowHeight(TableRowElement.as(tr), getDefaultRowHeight());
            tr = tr.getNextSiblingElement();
        }

        /*
         * Because all rows are immediately displayed in the static row
         * containers, the section's overall height has most probably
         * changed.
         */
        recalculateSectionHeight();

        Profiler.leave("Escalator.AbstractStaticRowContainer.reapplyDefaultRowHeights");
    }

    @Override
    protected void recalculateSectionHeight() {
        Profiler.enter("Escalator.AbstractStaticRowContainer.recalculateSectionHeight");

        double newHeight = calculateTotalRowHeight();
        if (newHeight != heightOfSection) {
            heightOfSection = newHeight;
            sectionHeightCalculated();

            /*
             * We need to update the scrollbar dimension at this point. If
             * we are scrolled too far down and the static section shrinks,
             * the body will try to render rows that don't exist during
             * body.verifyEscalatorCount. This is because the logical row
             * indices are calculated from the scrollbar position.
             */
            this.escalator.verticalScrollbar.setOffsetSize(this.escalator.heightOfEscalator
                    - this.escalator.header.getHeightOfSection()
                    - this.escalator.footer.getHeightOfSection());

            this.escalator.body.verifyEscalatorCount();
            this.escalator.body.spacerContainer.updateSpacerDecosVisibility();
        }

        Profiler.leave("Escalator.AbstractStaticRowContainer.recalculateSectionHeight");
    }

    /**
     * Informs the row container that the height of its respective table
     * section has changed.
     * <p>
     * These calculations might affect some layouting logic, such as the
     * body is being offset by the footer, the footer needs to be readjusted
     * according to its height, and so on.
     * <p>
     * A table section is either header, body or footer.
     */
    protected abstract void sectionHeightCalculated();

    @Override
    protected void refreshCells(Range logicalRowRange, Range colRange) {
        Profiler.enter("Escalator.AbstractStaticRowContainer.refreshRows");

        assertArgumentsAreValidAndWithinRange(logicalRowRange.getStart(),
                logicalRowRange.length());

        if (!this.escalator.isAttached()) {
            return;
        }

        if (this.escalator.hasColumnAndRowData()) {
            for (int row = logicalRowRange.getStart(); row < logicalRowRange
                    .getEnd(); row++) {
                final TableRowElement tr = getTrByVisualIndex(row);
                refreshRow(tr, row, colRange);
            }
        }

        Profiler.leave("Escalator.AbstractStaticRowContainer.refreshRows");
    }

    @Override
    protected void paintInsertRows(int visualIndex, int numberOfRows) {
        paintInsertStaticRows(visualIndex, numberOfRows);
    }

    @Override
    protected boolean rowCanBeFrozen(TableRowElement tr) {
        assert root.isOrHasChild(tr) : "Row does not belong to this table section";
        return true;
    }

    @Override
    protected double getHeightOfSection() {
        return Math.max(0, heightOfSection);
    }
}