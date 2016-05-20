package com.vaadin.client.widgets.escalator;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.logging.client.LogConfiguration;
import com.google.gwt.user.client.Timer;

import com.vaadin.client.Profiler;
import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.RowContainer.BodyRowContainer;
import com.vaadin.client.widget.escalator.SpacerUpdater;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.ui.grid.ScrollDestination;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class BodyRowContainerImpl extends AbstractRowContainer implements
        BodyRowContainer {

    /*
     * TODO [[optimize]]: check whether a native JsArray might be faster
     * than LinkedList
     */
    /**
     * The order in which row elements are rendered visually in the browser,
     * with the help of CSS tricks. Usually has nothing to do with the DOM
     * order.
     * 
     * @see #sortDomElements()
     */
    final LinkedList<TableRowElement> visualRowOrder = new LinkedList<TableRowElement>();

    /**
     * The logical index of the topmost row.
     * 
     * @deprecated Use the accessors {@link #setTopRowLogicalIndex(int)},
     *             {@link #updateTopRowLogicalIndex(int)} and
     *             {@link #getTopRowLogicalIndex()} instead
     */
    @Deprecated
    private int topRowLogicalIndex = 0;

    void setTopRowLogicalIndex(int topRowLogicalIndex) {
        if (LogConfiguration.loggingIsEnabled(Level.INFO)) {
            Logger.getLogger("Escalator.BodyRowContainer").fine(
                    "topRowLogicalIndex: " + this.topRowLogicalIndex
                            + " -> " + topRowLogicalIndex);
        }
        assert topRowLogicalIndex >= 0 : "topRowLogicalIndex became negative (top left cell contents: "
                + visualRowOrder.getFirst().getCells().getItem(0)
                        .getInnerText() + ") ";
        /*
         * if there's a smart way of evaluating and asserting the max index,
         * this would be a nice place to put it. I haven't found out an
         * effective and generic solution.
         */

        this.topRowLogicalIndex = topRowLogicalIndex;
    }

    public int getTopRowLogicalIndex() {
        return topRowLogicalIndex;
    }

    private void updateTopRowLogicalIndex(int diff) {
        setTopRowLogicalIndex(topRowLogicalIndex + diff);
    }

    class DeferredDomSorter extends Timer implements ScheduledCommand {
        // 1000/50 (@50fps)
        private static final int SORT_DELAY_MILLIS = 20;

        public void reschedule() {
            schedule(SORT_DELAY_MILLIS);
        }

        @Override
        public void run() {
            Scheduler.get().scheduleFinally(this);
        }

        @Override
        public void execute() {
            sortDomElements();
            BodyRowContainerImpl.this.escalator.bodyElem.removeClassName("scrolling");
        }
    }

    DeferredDomSorter domSorter = new DeferredDomSorter();

    final SpacerContainer spacerContainer = new SpacerContainer(this.escalator);

    public BodyRowContainerImpl(Escalator escalator, final TableSectionElement bodyElement) {
        super(escalator, bodyElement);
    }

    @Override
    public void setStylePrimaryName(String primaryStyleName) {
        super.setStylePrimaryName(primaryStyleName);
        JsniUtil.setStylePrimaryName(root, primaryStyleName + "-body");
        spacerContainer.setStylePrimaryName(primaryStyleName);
    }

    public void updateEscalatorRowsOnScroll() {
        if (visualRowOrder.isEmpty()) {
            return;
        }

        boolean rowsWereMoved = false;

        final double topElementPosition;
        final double nextRowBottomOffset;
        SpacerContainer.SpacerImpl topSpacer = spacerContainer
                .getSpacer(getTopRowLogicalIndex() - 1);

        if (topSpacer != null) {
            topElementPosition = topSpacer.getTop();
            nextRowBottomOffset = topSpacer.getHeight()
                    + getDefaultRowHeight();
        } else {
            topElementPosition = getRowTop(visualRowOrder.getFirst());
            nextRowBottomOffset = getDefaultRowHeight();
        }

        // TODO [[mpixscroll]]
        final double scrollTop = this.escalator.tBodyScrollTop;
        final double viewportOffset = topElementPosition - scrollTop;

        /*
         * TODO [[optimize]] this if-else can most probably be refactored
         * into a neater block of code
         */

        if (viewportOffset > 0) {
            // there's empty room on top

            double rowPx = getRowHeightsSumBetweenPx(scrollTop,
                    topElementPosition);
            int originalRowsToMove = (int) Math.ceil(rowPx
                    / getDefaultRowHeight());
            int rowsToMove = Math.min(originalRowsToMove,
                    visualRowOrder.size());

            final int end = visualRowOrder.size();
            final int start = end - rowsToMove;
            final int logicalRowIndex = getLogicalRowIndex(scrollTop);

            moveAndUpdateEscalatorRows(Range.between(start, end), 0,
                    logicalRowIndex);

            setTopRowLogicalIndex(logicalRowIndex);

            rowsWereMoved = true;
        }

        else if (viewportOffset + nextRowBottomOffset <= 0) {
            /*
             * the viewport has been scrolled more than the topmost visual
             * row.
             */

            double rowPx = getRowHeightsSumBetweenPx(topElementPosition,
                    scrollTop);

            int originalRowsToMove = (int) (rowPx / getDefaultRowHeight());
            int rowsToMove = Math.min(originalRowsToMove,
                    visualRowOrder.size());

            int logicalRowIndex;
            if (rowsToMove < visualRowOrder.size()) {
                /*
                 * We scroll so little that we can just keep adding the rows
                 * below the current escalator
                 */
                logicalRowIndex = getLogicalRowIndex(visualRowOrder
                        .getLast()) + 1;
            } else {
                /*
                 * Since we're moving all escalator rows, we need to
                 * calculate the first logical row index from the scroll
                 * position.
                 */
                logicalRowIndex = getLogicalRowIndex(scrollTop);
            }

            /*
             * Since we're moving the viewport downwards, the visual index
             * is always at the bottom. Note: Due to how
             * moveAndUpdateEscalatorRows works, this will work out even if
             * we move all the rows, and try to place them "at the end".
             */
            final int targetVisualIndex = visualRowOrder.size();

            // make sure that we don't move rows over the data boundary
            boolean aRowWasLeftBehind = false;
            if (logicalRowIndex + rowsToMove > getRowCount()) {
                /*
                 * TODO [[spacer]]: with constant row heights, there's
                 * always exactly one row that will be moved beyond the data
                 * source, when viewport is scrolled to the end. This,
                 * however, isn't guaranteed anymore once row heights start
                 * varying.
                 */
                rowsToMove--;
                aRowWasLeftBehind = true;
            }

            /*
             * Make sure we don't scroll beyond the row content. This can
             * happen if we have spacers for the last rows.
             */
            rowsToMove = Math.max(0,
                    Math.min(rowsToMove, getRowCount() - logicalRowIndex));

            moveAndUpdateEscalatorRows(Range.between(0, rowsToMove),
                    targetVisualIndex, logicalRowIndex);

            if (aRowWasLeftBehind) {
                /*
                 * To keep visualRowOrder as a spatially contiguous block of
                 * rows, let's make sure that the one row we didn't move
                 * visually still stays with the pack.
                 */
                final Range strayRow = Range.withOnly(0);

                /*
                 * We cannot trust getLogicalRowIndex, because it hasn't yet
                 * been updated. But since we're leaving rows behind, it
                 * means we've scrolled to the bottom. So, instead, we
                 * simply count backwards from the end.
                 */
                final int topLogicalIndex = getRowCount()
                        - visualRowOrder.size();
                moveAndUpdateEscalatorRows(strayRow, 0, topLogicalIndex);
            }

            final int naiveNewLogicalIndex = getTopRowLogicalIndex()
                    + originalRowsToMove;
            final int maxLogicalIndex = getRowCount()
                    - visualRowOrder.size();
            setTopRowLogicalIndex(Math.min(naiveNewLogicalIndex,
                    maxLogicalIndex));

            rowsWereMoved = true;
        }

        if (rowsWereMoved) {
            this.escalator.fireRowVisibilityChangeEvent();
            domSorter.reschedule();
        }
    }

    private double getRowHeightsSumBetweenPx(double y1, double y2) {
        assert y1 < y2 : "y1 must be smaller than y2";

        double viewportPx = y2 - y1;
        double spacerPx = spacerContainer.getSpacerHeightsSumBetweenPx(y1,
                SpacerContainer.SpacerInclusionStrategy.PARTIAL, y2,
                SpacerContainer.SpacerInclusionStrategy.PARTIAL);

        return viewportPx - spacerPx;
    }

    private int getLogicalRowIndex(final double px) {
        double rowPx = px - spacerContainer.getSpacerHeightsSumUntilPx(px);
        return (int) (rowPx / getDefaultRowHeight());
    }

    @Override
    protected void paintInsertRows(final int index, final int numberOfRows) {
        if (numberOfRows == 0) {
            return;
        }

        spacerContainer.shiftSpacersByRows(index, numberOfRows);

        /*
         * TODO: this method should probably only add physical rows, and not
         * populate them - let everything be populated as appropriate by the
         * logic that follows.
         * 
         * This also would lead to the fact that paintInsertRows wouldn't
         * need to return anything.
         */
        final List<TableRowElement> addedRows = fillAndPopulateEscalatorRowsIfNeeded(
                index, numberOfRows);

        /*
         * insertRows will always change the number of rows - update the
         * scrollbar sizes.
         */
        this.escalator.scroller.recalculateScrollbarsForVirtualViewport();

        final boolean addedRowsAboveCurrentViewport = index
                * getDefaultRowHeight() < this.escalator.getScrollTop();
        final boolean addedRowsBelowCurrentViewport = index
                * getDefaultRowHeight() > this.escalator.getScrollTop()
                + getHeightOfSection();

        if (addedRowsAboveCurrentViewport) {
            /*
             * We need to tweak the virtual viewport (scroll handle
             * positions, table "scroll position" and row locations), but
             * without re-evaluating any rows.
             */

            final double yDelta = numberOfRows * getDefaultRowHeight();
            moveViewportAndContent(yDelta);
            updateTopRowLogicalIndex(numberOfRows);
        }

        else if (addedRowsBelowCurrentViewport) {
            // NOOP, we already recalculated scrollbars.
        }

        else { // some rows were added inside the current viewport

            final int unupdatedLogicalStart = index + addedRows.size();
            final int visualOffset = getLogicalRowIndex(visualRowOrder
                    .getFirst());

            /*
             * At this point, we have added new escalator rows, if so
             * needed.
             * 
             * If more rows were added than the new escalator rows can
             * account for, we need to start to spin the escalator to update
             * the remaining rows aswell.
             */
            final int rowsStillNeeded = numberOfRows - addedRows.size();

            if (rowsStillNeeded > 0) {
                final Range unupdatedVisual = convertToVisual(Range
                        .withLength(unupdatedLogicalStart, rowsStillNeeded));
                final int end = getDomRowCount();
                final int start = end - unupdatedVisual.length();
                final int visualTargetIndex = unupdatedLogicalStart
                        - visualOffset;
                moveAndUpdateEscalatorRows(Range.between(start, end),
                        visualTargetIndex, unupdatedLogicalStart);

                // move the surrounding rows to their correct places.
                double rowTop = (unupdatedLogicalStart + (end - start))
                        * getDefaultRowHeight();

                // TODO: Get rid of this try/catch block by fixing the
                // underlying issue. The reason for this erroneous behavior
                // might be that Escalator actually works 'by mistake', and
                // the order of operations is, in fact, wrong.
                try {
                    final ListIterator<TableRowElement> i = visualRowOrder
                            .listIterator(visualTargetIndex + (end - start));

                    int logicalRowIndexCursor = unupdatedLogicalStart;
                    while (i.hasNext()) {
                        rowTop += spacerContainer
                                .getSpacerHeight(logicalRowIndexCursor++);

                        final TableRowElement tr = i.next();
                        setRowPosition(tr, 0, rowTop);
                        rowTop += getDefaultRowHeight();
                    }
                } catch (Exception e) {
                    Logger logger = this.escalator.getLogger();
                    logger.warning("Ignored out-of-bounds row element access");
                    logger.warning("Escalator state: start=" + start
                            + ", end=" + end + ", visualTargetIndex="
                            + visualTargetIndex
                            + ", visualRowOrder.size()="
                            + visualRowOrder.size());
                    logger.warning(e.toString());
                }
            }

            this.escalator.fireRowVisibilityChangeEvent();
            sortDomElements();
        }
    }

    /**
     * Move escalator rows around, and make sure everything gets
     * appropriately repositioned and repainted.
     * 
     * @param visualSourceRange
     *            the range of rows to move to a new place
     * @param visualTargetIndex
     *            the visual index where the rows will be placed to
     * @param logicalTargetIndex
     *            the logical index to be assigned to the first moved row
     */
    private void moveAndUpdateEscalatorRows(final Range visualSourceRange,
            final int visualTargetIndex, final int logicalTargetIndex)
            throws IllegalArgumentException {

        if (visualSourceRange.isEmpty()) {
            return;
        }

        assert visualSourceRange.getStart() >= 0 : "Visual source start "
                + "must be 0 or greater (was "
                + visualSourceRange.getStart() + ")";

        assert logicalTargetIndex >= 0 : "Logical target must be 0 or "
                + "greater (was " + logicalTargetIndex + ")";

        assert visualTargetIndex >= 0 : "Visual target must be 0 or greater (was "
                + visualTargetIndex + ")";

        assert visualTargetIndex <= getDomRowCount() : "Visual target "
                + "must not be greater than the number of escalator rows (was "
                + visualTargetIndex + ", escalator rows "
                + getDomRowCount() + ")";

        assert logicalTargetIndex + visualSourceRange.length() <= getRowCount() : "Logical "
                + "target leads to rows outside of the data range ("
                + Range.withLength(logicalTargetIndex,
                        visualSourceRange.length())
                + " goes beyond "
                + Range.withLength(0, getRowCount()) + ")";

        /*
         * Since we move a range into another range, the indices might move
         * about. Having 10 rows, if we move 0..1 to index 10 (to the end of
         * the collection), the target range will end up being 8..9, instead
         * of 10..11.
         * 
         * This applies only if we move elements forward in the collection,
         * not backward.
         */
        final int adjustedVisualTargetIndex;
        if (visualSourceRange.getStart() < visualTargetIndex) {
            adjustedVisualTargetIndex = visualTargetIndex
                    - visualSourceRange.length();
        } else {
            adjustedVisualTargetIndex = visualTargetIndex;
        }

        if (visualSourceRange.getStart() != adjustedVisualTargetIndex) {

            /*
             * Reorder the rows to their correct places within
             * visualRowOrder (unless rows are moved back to their original
             * places)
             */

            /*
             * TODO [[optimize]]: move whichever set is smaller: the ones
             * explicitly moved, or the others. So, with 10 escalator rows,
             * if we are asked to move idx[0..8] to the end of the list,
             * it's faster to just move idx[9] to the beginning.
             */

            final List<TableRowElement> removedRows = new ArrayList<TableRowElement>(
                    visualSourceRange.length());
            for (int i = 0; i < visualSourceRange.length(); i++) {
                final TableRowElement tr = visualRowOrder
                        .remove(visualSourceRange.getStart());
                removedRows.add(tr);
            }
            visualRowOrder.addAll(adjustedVisualTargetIndex, removedRows);
        }

        { // Refresh the contents of the affected rows
            final ListIterator<TableRowElement> iter = visualRowOrder
                    .listIterator(adjustedVisualTargetIndex);
            for (int logicalIndex = logicalTargetIndex; logicalIndex < logicalTargetIndex
                    + visualSourceRange.length(); logicalIndex++) {
                final TableRowElement tr = iter.next();
                refreshRow(tr, logicalIndex);
            }
        }

        { // Reposition the rows that were moved
            double newRowTop = getRowTop(logicalTargetIndex);

            final ListIterator<TableRowElement> iter = visualRowOrder
                    .listIterator(adjustedVisualTargetIndex);
            for (int i = 0; i < visualSourceRange.length(); i++) {
                final TableRowElement tr = iter.next();
                setRowPosition(tr, 0, newRowTop);

                newRowTop += getDefaultRowHeight();
                newRowTop += spacerContainer
                        .getSpacerHeight(logicalTargetIndex + i);
            }
        }
    }

    /**
     * Adjust the scroll position and move the contained rows.
     * <p>
     * The difference between using this method and simply scrolling is that
     * this method "takes the rows and spacers with it" and renders them
     * appropriately. The viewport may be scrolled any arbitrary amount, and
     * the contents are moved appropriately, but always snapped into a
     * plausible place.
     * <p>
     * <dl>
     * <dt>Example 1</dt>
     * <dd>An Escalator with default row height 20px. Adjusting the scroll
     * position with 7.5px will move the viewport 7.5px down, but leave the
     * row where it is.</dd>
     * <dt>Example 2</dt>
     * <dd>An Escalator with default row height 20px. Adjusting the scroll
     * position with 27.5px will move the viewport 27.5px down, and place
     * the row at 20px.</dd>
     * </dl>
     * 
     * @param yDelta
     *            the delta of pixels by which to move the viewport and
     *            content. A positive value moves everything downwards,
     *            while a negative value moves everything upwards
     */
    public void moveViewportAndContent(final double yDelta) {

        if (yDelta == 0) {
            return;
        }

        double newTop = this.escalator.tBodyScrollTop + yDelta;
        this.escalator.verticalScrollbar.setScrollPos(newTop);

        final double defaultRowHeight = getDefaultRowHeight();
        double rowPxDelta = yDelta - (yDelta % defaultRowHeight);
        int rowIndexDelta = (int) (yDelta / defaultRowHeight);
        if (!WidgetUtil.pixelValuesEqual(rowPxDelta, 0)) {

            Collection<SpacerContainer.SpacerImpl> spacers = spacerContainer
                    .getSpacersAfterPx(this.escalator.tBodyScrollTop,
                            SpacerContainer.SpacerInclusionStrategy.PARTIAL);
            for (SpacerContainer.SpacerImpl spacer : spacers) {
                spacer.setPositionDiff(0, rowPxDelta);
                spacer.setRowIndex(spacer.getRow() + rowIndexDelta);
            }

            for (TableRowElement tr : visualRowOrder) {
                setRowPosition(tr, 0, getRowTop(tr) + rowPxDelta);
            }
        }

        setBodyScrollPosition(this.escalator.tBodyScrollLeft, newTop);
    }

    /**
     * Adds new physical escalator rows to the DOM at the given index if
     * there's still a need for more escalator rows.
     * <p>
     * If Escalator already is at (or beyond) max capacity, this method does
     * nothing to the DOM.
     * 
     * @param index
     *            the index at which to add new escalator rows.
     *            <em>Note:</em>It is assumed that the index is both the
     *            visual index and the logical index.
     * @param numberOfRows
     *            the number of rows to add at <code>index</code>
     * @return a list of the added rows
     */
    private List<TableRowElement> fillAndPopulateEscalatorRowsIfNeeded(
            final int index, final int numberOfRows) {

        final int escalatorRowsStillFit = getMaxEscalatorRowCapacity()
                - getDomRowCount();
        final int escalatorRowsNeeded = Math.min(numberOfRows,
                escalatorRowsStillFit);

        if (escalatorRowsNeeded > 0) {

            final List<TableRowElement> addedRows = paintInsertStaticRows(
                    index, escalatorRowsNeeded);
            visualRowOrder.addAll(index, addedRows);

            double y = index * getDefaultRowHeight()
                    + spacerContainer.getSpacerHeightsSumUntilIndex(index);
            for (int i = index; i < visualRowOrder.size(); i++) {

                final TableRowElement tr;
                if (i - index < addedRows.size()) {
                    tr = addedRows.get(i - index);
                } else {
                    tr = visualRowOrder.get(i);
                }

                setRowPosition(tr, 0, y);
                y += getDefaultRowHeight();
                y += spacerContainer.getSpacerHeight(i);
            }

            return addedRows;
        } else {
            return Collections.emptyList();
        }
    }

    int getMaxEscalatorRowCapacity() {
        final int maxEscalatorRowCapacity = (int) Math
                .ceil(getHeightOfSection() / getDefaultRowHeight()) + 1;

        /*
         * maxEscalatorRowCapacity can become negative if the headers and
         * footers start to overlap. This is a crazy situation, but Vaadin
         * blinks the components a lot, so it's feasible.
         */
        return Math.max(0, maxEscalatorRowCapacity);
    }

    @Override
    protected void paintRemoveRows(final int index, final int numberOfRows) {
        if (numberOfRows == 0) {
            return;
        }

        final Range viewportRange = this.escalator.getVisibleRowRange();
        final Range removedRowsRange = Range
                .withLength(index, numberOfRows);

        /*
         * Removing spacers as the very first step will correct the
         * scrollbars and row offsets right away.
         * 
         * TODO: actually, it kinda sounds like a Grid feature that a spacer
         * would be associated with a particular row. Maybe it would be
         * better to have a spacer separate from rows, and simply collapse
         * them if they happen to end up on top of each other. This would
         * probably make supporting the -1 row pretty easy, too.
         */
        spacerContainer.paintRemoveSpacers(removedRowsRange);

        final Range[] partitions = removedRowsRange
                .partitionWith(viewportRange);
        final Range removedAbove = partitions[0];
        final Range removedLogicalInside = partitions[1];
        final Range removedVisualInside = convertToVisual(removedLogicalInside);

        /*
         * TODO: extract the following if-block to a separate method. I'll
         * leave this be inlined for now, to make linediff-based code
         * reviewing easier. Probably will be moved in the following patch
         * set.
         */

        /*
         * Adjust scroll position in one of two scenarios:
         * 
         * 1) Rows were removed above. Then we just need to adjust the
         * scrollbar by the height of the removed rows.
         * 
         * 2) There are no logical rows above, and at least the first (if
         * not more) visual row is removed. Then we need to snap the scroll
         * position to the first visible row (i.e. reset scroll position to
         * absolute 0)
         * 
         * The logic is optimized in such a way that the
         * moveViewportAndContent is called only once, to avoid extra
         * reflows, and thus the code might seem a bit obscure.
         */
        final boolean firstVisualRowIsRemoved = !removedVisualInside
                .isEmpty() && removedVisualInside.getStart() == 0;

        if (!removedAbove.isEmpty() || firstVisualRowIsRemoved) {
            final double yDelta = removedAbove.length()
                    * getDefaultRowHeight();
            final double firstLogicalRowHeight = getDefaultRowHeight();
            final boolean removalScrollsToShowFirstLogicalRow = this.escalator.verticalScrollbar
                    .getScrollPos() - yDelta < firstLogicalRowHeight;

            if (removedVisualInside.isEmpty()
                    && (!removalScrollsToShowFirstLogicalRow || !firstVisualRowIsRemoved)) {
                /*
                 * rows were removed from above the viewport, so all we need
                 * to do is to adjust the scroll position to account for the
                 * removed rows
                 */
                moveViewportAndContent(-yDelta);
            } else if (removalScrollsToShowFirstLogicalRow) {
                /*
                 * It seems like we've removed all rows from above, and also
                 * into the current viewport. This means we'll need to even
                 * out the scroll position to exactly 0 (i.e. adjust by the
                 * current negative scrolltop, presto!), so that it isn't
                 * aligned funnily
                 */
                moveViewportAndContent(-this.escalator.verticalScrollbar.getScrollPos());
            }
        }

        // ranges evaluated, let's do things.
        if (!removedVisualInside.isEmpty()) {
            int escalatorRowCount = this.escalator.body.getDomRowCount();

            /*
             * remember: the rows have already been subtracted from the row
             * count at this point
             */
            int rowsLeft = getRowCount();
            if (rowsLeft < escalatorRowCount) {
                int escalatorRowsToRemove = escalatorRowCount - rowsLeft;
                for (int i = 0; i < escalatorRowsToRemove; i++) {
                    final TableRowElement tr = visualRowOrder
                            .remove(removedVisualInside.getStart());

                    paintRemoveRow(tr, index);
                    removeRowPosition(tr);
                }
                escalatorRowCount -= escalatorRowsToRemove;

                /*
                 * Because we're removing escalator rows, we don't have
                 * anything to scroll by. Let's make sure the viewport is
                 * scrolled to top, to render any rows possibly left above.
                 */
                this.escalator.body.setBodyScrollPosition(this.escalator.tBodyScrollLeft, 0);

                /*
                 * We might have removed some rows from the middle, so let's
                 * make sure we're not left with any holes. Also remember:
                 * visualIndex == logicalIndex applies now.
                 */
                final int dirtyRowsStart = removedLogicalInside.getStart();
                double y = getRowTop(dirtyRowsStart);
                for (int i = dirtyRowsStart; i < escalatorRowCount; i++) {
                    final TableRowElement tr = visualRowOrder.get(i);
                    setRowPosition(tr, 0, y);
                    y += getDefaultRowHeight();
                    y += spacerContainer.getSpacerHeight(i);
                }

                /*
                 * this is how many rows appeared into the viewport from
                 * below
                 */
                final int rowsToUpdateDataOn = numberOfRows
                        - escalatorRowsToRemove;
                final int start = Math.max(0, escalatorRowCount
                        - rowsToUpdateDataOn);
                final int end = escalatorRowCount;
                for (int i = start; i < end; i++) {
                    final TableRowElement tr = visualRowOrder.get(i);
                    refreshRow(tr, i);
                }
            }

            else {
                // No escalator rows need to be removed.

                /*
                 * Two things (or a combination thereof) can happen:
                 * 
                 * 1) We're scrolled to the bottom, the last rows are
                 * removed. SOLUTION: moveAndUpdateEscalatorRows the
                 * bottommost rows, and place them at the top to be
                 * refreshed.
                 * 
                 * 2) We're scrolled somewhere in the middle, arbitrary rows
                 * are removed. SOLUTION: moveAndUpdateEscalatorRows the
                 * removed rows, and place them at the bottom to be
                 * refreshed.
                 * 
                 * Since a combination can also happen, we need to handle
                 * this in a smart way, all while avoiding
                 * double-refreshing.
                 */

                final double contentBottom = getRowCount()
                        * getDefaultRowHeight();
                final double viewportBottom = this.escalator.tBodyScrollTop
                        + getHeightOfSection();
                if (viewportBottom <= contentBottom) {
                    /*
                     * We're in the middle of the row container, everything
                     * is added to the bottom
                     */
                    paintRemoveRowsAtMiddle(removedLogicalInside,
                            removedVisualInside, 0);
                }

                else if (removedVisualInside.contains(0)
                        && numberOfRows >= visualRowOrder.size()) {
                    /*
                     * We're removing so many rows that the viewport is
                     * pushed up more than a screenful. This means we can
                     * simply scroll up and everything will work without a
                     * sweat.
                     */

                    double left = this.escalator.horizontalScrollbar.getScrollPos();
                    double top = contentBottom - visualRowOrder.size()
                            * getDefaultRowHeight();
                    setBodyScrollPosition(left, top);

                    Range allEscalatorRows = Range.withLength(0,
                            visualRowOrder.size());
                    int logicalTargetIndex = getRowCount()
                            - allEscalatorRows.length();
                    moveAndUpdateEscalatorRows(allEscalatorRows, 0,
                            logicalTargetIndex);

                    /*
                     * moveAndUpdateEscalatorRows recalculates the rows, but
                     * logical top row index bookkeeping is handled in this
                     * method.
                     * 
                     * TODO: Redesign how to keep it easy to track this.
                     */
                    updateTopRowLogicalIndex(-removedLogicalInside.length());

                    /*
                     * Scrolling the body to the correct location will be
                     * fixed automatically. Because the amount of rows is
                     * decreased, the viewport is pushed up as the scrollbar
                     * shrinks. So no need to do anything there.
                     * 
                     * TODO [[optimize]]: This might lead to a double body
                     * refresh. Needs investigation.
                     */
                }

                else if (contentBottom
                        + (numberOfRows * getDefaultRowHeight())
                        - viewportBottom < getDefaultRowHeight()) {
                    /*
                     * We're at the end of the row container, everything is
                     * added to the top.
                     */

                    /*
                     * FIXME [[spacer]]: above if-clause is coded to only
                     * work with default row heights - will not work with
                     * variable row heights
                     */

                    paintRemoveRowsAtBottom(removedLogicalInside,
                            removedVisualInside);
                    updateTopRowLogicalIndex(-removedLogicalInside.length());
                }

                else {
                    /*
                     * We're in a combination, where we need to both scroll
                     * up AND show new rows at the bottom.
                     * 
                     * Example: Scrolled down to show the second to last
                     * row. Remove two. Viewport scrolls up, revealing the
                     * row above row. The last element collapses up and into
                     * view.
                     * 
                     * Reminder: this use case handles only the case when
                     * there are enough escalator rows to still render a
                     * full view. I.e. all escalator rows will _always_ be
                     * populated
                     */
                    /*-
                     *  1       1      |1| <- newly rendered
                     * |2|     |2|     |2|
                     * |3| ==> |*| ==> |5| <- newly rendered
                     * |4|     |*|
                     *  5       5
                     *  
                     *  1       1      |1| <- newly rendered
                     * |2|     |*|     |4|
                     * |3| ==> |*| ==> |5| <- newly rendered
                     * |4|     |4|
                     *  5       5
                     */

                    /*
                     * STEP 1:
                     * 
                     * reorganize deprecated escalator rows to bottom, but
                     * don't re-render anything yet
                     */
                    /*-
                     *  1       1       1
                     * |2|     |*|     |4|
                     * |3| ==> |*| ==> |*|
                     * |4|     |4|     |*|
                     *  5       5       5
                     */
                    double newTop = getRowTop(visualRowOrder
                            .get(removedVisualInside.getStart()));
                    for (int i = 0; i < removedVisualInside.length(); i++) {
                        final TableRowElement tr = visualRowOrder
                                .remove(removedVisualInside.getStart());
                        visualRowOrder.addLast(tr);
                    }

                    for (int i = removedVisualInside.getStart(); i < escalatorRowCount; i++) {
                        final TableRowElement tr = visualRowOrder.get(i);
                        setRowPosition(tr, 0, (int) newTop);
                        newTop += getDefaultRowHeight();
                        newTop += spacerContainer.getSpacerHeight(i
                                + removedLogicalInside.getStart());
                    }

                    /*
                     * STEP 2:
                     * 
                     * manually scroll
                     */
                    /*-
                     *  1      |1| <-- newly rendered (by scrolling)
                     * |4|     |4|
                     * |*| ==> |*|
                     * |*|       
                     *  5       5
                     */
                    final double newScrollTop = contentBottom
                            - getHeightOfSection();
                    this.escalator.setScrollTop(newScrollTop);
                    /*
                     * Manually call the scroll handler, so we get immediate
                     * effects in the escalator.
                     */
                    this.escalator.scroller.onScroll();

                    /*
                     * Move the bottommost (n+1:th) escalator row to top,
                     * because scrolling up doesn't handle that for us
                     * automatically
                     */
                    moveAndUpdateEscalatorRows(
                            Range.withOnly(escalatorRowCount - 1),
                            0,
                            getLogicalRowIndex(visualRowOrder.getFirst()) - 1);
                    updateTopRowLogicalIndex(-1);

                    /*
                     * STEP 3:
                     * 
                     * update remaining escalator rows
                     */
                    /*-
                     * |1|     |1|
                     * |4| ==> |4|
                     * |*|     |5| <-- newly rendered
                     *           
                     *  5
                     */

                    final int rowsScrolled = (int) (Math
                            .ceil((viewportBottom - contentBottom)
                                    / getDefaultRowHeight()));
                    final int start = escalatorRowCount
                            - (removedVisualInside.length() - rowsScrolled);
                    final Range visualRefreshRange = Range.between(start,
                            escalatorRowCount);
                    final int logicalTargetIndex = getLogicalRowIndex(visualRowOrder
                            .getFirst()) + start;
                    // in-place move simply re-renders the rows.
                    moveAndUpdateEscalatorRows(visualRefreshRange, start,
                            logicalTargetIndex);
                }
            }

            this.escalator.fireRowVisibilityChangeEvent();
            sortDomElements();
        }

        updateTopRowLogicalIndex(-removedAbove.length());

        /*
         * this needs to be done after the escalator has been shrunk down,
         * or it won't work correctly (due to setScrollTop invocation)
         */
        this.escalator.scroller.recalculateScrollbarsForVirtualViewport();
    }

    private void paintRemoveRowsAtMiddle(final Range removedLogicalInside,
            final Range removedVisualInside, final int logicalOffset) {
        /*-
         *  :       :       :
         * |2|     |2|     |2|
         * |3| ==> |*| ==> |4|
         * |4|     |4|     |6| <- newly rendered
         *  :       :       :
         */

        final int escalatorRowCount = visualRowOrder.size();

        final int logicalTargetIndex = getLogicalRowIndex(visualRowOrder
                .getLast())
                - (removedVisualInside.length() - 1)
                + logicalOffset;
        moveAndUpdateEscalatorRows(removedVisualInside, escalatorRowCount,
                logicalTargetIndex);

        // move the surrounding rows to their correct places.
        final ListIterator<TableRowElement> iterator = visualRowOrder
                .listIterator(removedVisualInside.getStart());

        double rowTop = getRowTop(removedLogicalInside.getStart()
                + logicalOffset);
        for (int i = removedVisualInside.getStart(); i < escalatorRowCount
                - removedVisualInside.length(); i++) {
            final TableRowElement tr = iterator.next();
            setRowPosition(tr, 0, rowTop);
            rowTop += getDefaultRowHeight();
            rowTop += spacerContainer.getSpacerHeight(i
                    + removedLogicalInside.getStart());
        }
    }

    private void paintRemoveRowsAtBottom(final Range removedLogicalInside,
            final Range removedVisualInside) {
        /*-
         *                  :
         *  :       :      |4| <- newly rendered
         * |5|     |5|     |5|
         * |6| ==> |*| ==> |7|
         * |7|     |7|     
         */

        final int logicalTargetIndex = getLogicalRowIndex(visualRowOrder
                .getFirst()) - removedVisualInside.length();
        moveAndUpdateEscalatorRows(removedVisualInside, 0,
                logicalTargetIndex);

        // move the surrounding rows to their correct places.
        int firstUpdatedIndex = removedVisualInside.getEnd();
        final ListIterator<TableRowElement> iterator = visualRowOrder
                .listIterator(firstUpdatedIndex);

        double rowTop = getRowTop(removedLogicalInside.getStart());
        int i = 0;
        while (iterator.hasNext()) {
            final TableRowElement tr = iterator.next();
            setRowPosition(tr, 0, rowTop);
            rowTop += getDefaultRowHeight();
            rowTop += spacerContainer.getSpacerHeight(firstUpdatedIndex
                    + i++);
        }
    }

    @Override
    protected int getLogicalRowIndex(final TableRowElement tr) {
        assert tr.getParentNode() == root : "The given element isn't a row element in the body";
        int internalIndex = visualRowOrder.indexOf(tr);
        return getTopRowLogicalIndex() + internalIndex;
    }

    @Override
    protected void recalculateSectionHeight() {
        // NOOP for body, since it doesn't make any sense.
    }

    /**
     * Adjusts the row index and number to be relevant for the current
     * virtual viewport.
     * <p>
     * It converts a logical range of rows index to the matching visual
     * range, truncating the resulting range with the viewport.
     * <p>
     * <ul>
     * <li>Escalator contains logical rows 0..100
     * <li>Current viewport showing logical rows 20..29
     * <li>convertToVisual([20..29]) &rarr; [0..9]
     * <li>convertToVisual([15..24]) &rarr; [0..4]
     * <li>convertToVisual([25..29]) &rarr; [5..9]
     * <li>convertToVisual([26..39]) &rarr; [6..9]
     * <li>convertToVisual([0..5]) &rarr; [0..-1] <em>(empty)</em>
     * <li>convertToVisual([35..1]) &rarr; [0..-1] <em>(empty)</em>
     * <li>convertToVisual([0..100]) &rarr; [0..9]
     * </ul>
     * 
     * @return a logical range converted to a visual range, truncated to the
     *         current viewport. The first visual row has the index 0.
     */
    private Range convertToVisual(final Range logicalRange) {

        if (logicalRange.isEmpty()) {
            return logicalRange;
        } else if (visualRowOrder.isEmpty()) {
            // empty range
            return Range.withLength(0, 0);
        }

        /*
         * TODO [[spacer]]: these assumptions will be totally broken with
         * spacers.
         */
        final int maxEscalatorRows = getMaxEscalatorRowCapacity();
        final int currentTopRowIndex = getLogicalRowIndex(visualRowOrder
                .getFirst());

        final Range[] partitions = logicalRange.partitionWith(Range
                .withLength(currentTopRowIndex, maxEscalatorRows));
        final Range insideRange = partitions[1];
        return insideRange.offsetBy(-currentTopRowIndex);
    }

    @Override
    protected String getCellElementTagName() {
        return "td";
    }

    @Override
    protected double getHeightOfSection() {
        final int tableHeight = this.escalator.tableWrapper.getOffsetHeight();
        final double footerHeight = this.escalator.footer.getHeightOfSection();
        final double headerHeight = this.escalator.header.getHeightOfSection();

        double heightOfSection = tableHeight - footerHeight - headerHeight;
        return Math.max(0, heightOfSection);
    }

    @Override
    protected void refreshCells(Range logicalRowRange, Range colRange) {
        Profiler.enter("Escalator.BodyRowContainer.refreshRows");

        final Range visualRange = convertToVisual(logicalRowRange);

        if (!visualRange.isEmpty()) {
            final int firstLogicalRowIndex = getLogicalRowIndex(visualRowOrder
                    .getFirst());
            for (int rowNumber = visualRange.getStart(); rowNumber < visualRange
                    .getEnd(); rowNumber++) {
                refreshRow(visualRowOrder.get(rowNumber),
                        firstLogicalRowIndex + rowNumber, colRange);
            }
        }

        Profiler.leave("Escalator.BodyRowContainer.refreshRows");
    }

    @Override
    protected TableRowElement getTrByVisualIndex(final int index)
            throws IndexOutOfBoundsException {
        if (index >= 0 && index < visualRowOrder.size()) {
            return visualRowOrder.get(index);
        } else {
            throw new IndexOutOfBoundsException("No such visual index: "
                    + index);
        }
    }

    @Override
    public TableRowElement getRowElement(int index) {
        if (index < 0 || index >= getRowCount()) {
            throw new IndexOutOfBoundsException("No such logical index: "
                    + index);
        }
        int visualIndex = index
                - getLogicalRowIndex(visualRowOrder.getFirst());
        if (visualIndex >= 0 && visualIndex < visualRowOrder.size()) {
            return super.getRowElement(visualIndex);
        } else {
            throw new IllegalStateException("Row with logical index "
                    + index + " is currently not available in the DOM");
        }
    }

    void setBodyScrollPosition(final double scrollLeft,
            final double scrollTop) {
        this.escalator.tBodyScrollLeft = scrollLeft;
        this.escalator.tBodyScrollTop = scrollTop;
        this.escalator.position.set(this.escalator.bodyElem, -this.escalator.tBodyScrollLeft, -this.escalator.tBodyScrollTop);
        this.escalator.position.set(this.escalator.spacerDecoContainer, 0, -this.escalator.tBodyScrollTop);
    }

    /**
     * Make sure that there is a correct amount of escalator rows: Add more
     * if needed, or remove any superfluous ones.
     * <p>
     * This method should be called when e.g. the height of the Escalator
     * changes.
     * <p>
     * <em>Note:</em> This method will make sure that the escalator rows are
     * placed in the proper places. By default new rows are added below, but
     * if the content is scrolled down, the rows are populated on top
     * instead.
     */
    public void verifyEscalatorCount() {
        /*
         * This method indeed has a smell very similar to paintRemoveRows
         * and paintInsertRows.
         * 
         * Unfortunately, those the code can't trivially be shared, since
         * there are some slight differences in the respective
         * responsibilities. The "paint" methods fake the addition and
         * removal of rows, and make sure to either push existing data out
         * of view, or draw new data into view. Only in some special cases
         * will the DOM element count change.
         * 
         * This method, however, has the explicit responsibility to verify
         * that when "something" happens, we still have the correct amount
         * of escalator rows in the DOM, and if not, we make sure to modify
         * that count. Only in some special cases do we need to take into
         * account other things than simply modifying the DOM element count.
         */

        Profiler.enter("Escalator.BodyRowContainer.verifyEscalatorCount");

        if (!this.escalator.isAttached()) {
            return;
        }

        final int maxEscalatorRows = getMaxEscalatorRowCapacity();
        final int neededEscalatorRows = Math.min(maxEscalatorRows,
                this.escalator.body.getRowCount());
        final int neededEscalatorRowsDiff = neededEscalatorRows
                - visualRowOrder.size();

        if (neededEscalatorRowsDiff > 0) {
            // needs more

            /*
             * This is a workaround for the issue where we might be scrolled
             * to the bottom, and the widget expands beyond the content
             * range
             */

            final int index = visualRowOrder.size();
            final int nextLastLogicalIndex;
            if (!visualRowOrder.isEmpty()) {
                nextLastLogicalIndex = getLogicalRowIndex(visualRowOrder
                        .getLast()) + 1;
            } else {
                nextLastLogicalIndex = 0;
            }

            final boolean contentWillFit = nextLastLogicalIndex < getRowCount()
                    - neededEscalatorRowsDiff;
            if (contentWillFit) {
                final List<TableRowElement> addedRows = fillAndPopulateEscalatorRowsIfNeeded(
                        index, neededEscalatorRowsDiff);

                /*
                 * Since fillAndPopulateEscalatorRowsIfNeeded operates on
                 * the assumption that index == visual index == logical
                 * index, we thank for the added escalator rows, but since
                 * they're painted in the wrong CSS position, we need to
                 * move them to their actual locations.
                 * 
                 * Note: this is the second (see body.paintInsertRows)
                 * occasion where fillAndPopulateEscalatorRowsIfNeeded would
                 * behave "more correctly" if it only would add escalator
                 * rows to the DOM and appropriate bookkeping, and not
                 * actually populate them :/
                 */
                moveAndUpdateEscalatorRows(
                        Range.withLength(index, addedRows.size()), index,
                        nextLastLogicalIndex);
            } else {
                /*
                 * TODO [[optimize]]
                 * 
                 * We're scrolled so far down that all rows can't be simply
                 * appended at the end, since we might start displaying
                 * escalator rows that don't exist. To avoid the mess that
                 * is body.paintRemoveRows, this is a dirty hack that dumbs
                 * the problem down to a more basic and already-solved
                 * problem:
                 * 
                 * 1) scroll all the way up 2) add the missing escalator
                 * rows 3) scroll back to the original position.
                 * 
                 * Letting the browser scroll back to our original position
                 * will automatically solve any possible overflow problems,
                 * since the browser will not allow us to scroll beyond the
                 * actual content.
                 */

                final double oldScrollTop = this.escalator.getScrollTop();
                this.escalator.setScrollTop(0);
                this.escalator.scroller.onScroll();
                fillAndPopulateEscalatorRowsIfNeeded(index,
                        neededEscalatorRowsDiff);
                this.escalator.setScrollTop(oldScrollTop);
                this.escalator.scroller.onScroll();
            }
        }

        else if (neededEscalatorRowsDiff < 0) {
            // needs less

            final ListIterator<TableRowElement> iter = visualRowOrder
                    .listIterator(visualRowOrder.size());
            for (int i = 0; i < -neededEscalatorRowsDiff; i++) {
                final Element last = iter.previous();
                last.removeFromParent();
                iter.remove();
            }

            /*
             * If we were scrolled to the bottom so that we didn't have an
             * extra escalator row at the bottom, we'll probably end up with
             * blank space at the bottom of the escalator, and one extra row
             * above the header.
             * 
             * Experimentation idea #1: calculate "scrollbottom" vs content
             * bottom and remove one row from top, rest from bottom. This
             * FAILED, since setHeight has already happened, thus we never
             * will detect ourselves having been scrolled all the way to the
             * bottom.
             */

            if (!visualRowOrder.isEmpty()) {
                final double firstRowTop = getRowTop(visualRowOrder
                        .getFirst());
                final double firstRowMinTop = this.escalator.tBodyScrollTop
                        - getDefaultRowHeight();
                if (firstRowTop < firstRowMinTop) {
                    final int newLogicalIndex = getLogicalRowIndex(visualRowOrder
                            .getLast()) + 1;
                    moveAndUpdateEscalatorRows(Range.withOnly(0),
                            visualRowOrder.size(), newLogicalIndex);
                }
            }
        }

        if (neededEscalatorRowsDiff != 0) {
            this.escalator.fireRowVisibilityChangeEvent();
        }

        Profiler.leave("Escalator.BodyRowContainer.verifyEscalatorCount");
    }

    @Override
    protected void reapplyDefaultRowHeights() {
        if (visualRowOrder.isEmpty()) {
            return;
        }

        Profiler.enter("Escalator.BodyRowContainer.reapplyDefaultRowHeights");

        /* step 1: resize and reposition rows */
        for (int i = 0; i < visualRowOrder.size(); i++) {
            TableRowElement tr = visualRowOrder.get(i);
            reapplyRowHeight(tr, getDefaultRowHeight());

            final int logicalIndex = getTopRowLogicalIndex() + i;
            setRowPosition(tr, 0, logicalIndex * getDefaultRowHeight());
        }

        /*
         * step 2: move scrollbar so that it corresponds to its previous
         * place
         */

        /*
         * This ratio needs to be calculated with the scrollsize (not max
         * scroll position) in order to align the top row with the new
         * scroll position.
         */
        double scrollRatio = this.escalator.verticalScrollbar.getScrollPos()
                / this.escalator.verticalScrollbar.getScrollSize();
        this.escalator.scroller.recalculateScrollbarsForVirtualViewport();
        this.escalator.verticalScrollbar.setScrollPos((int) (getDefaultRowHeight()
                * getRowCount() * scrollRatio));
        setBodyScrollPosition(this.escalator.horizontalScrollbar.getScrollPos(),
                this.escalator.verticalScrollbar.getScrollPos());
        this.escalator.scroller.onScroll();

        /* step 3: make sure we have the correct amount of escalator rows. */
        verifyEscalatorCount();

        int logicalLogical = (int) (getRowTop(visualRowOrder.getFirst()) / getDefaultRowHeight());
        setTopRowLogicalIndex(logicalLogical);

        Profiler.leave("Escalator.BodyRowContainer.reapplyDefaultRowHeights");
    }

    /**
     * Sorts the rows in the DOM to correspond to the visual order.
     * 
     * @see #visualRowOrder
     */
    void sortDomElements() {
        final String profilingName = "Escalator.BodyRowContainer.sortDomElements";
        Profiler.enter(profilingName);

        /*
         * Focus is lost from an element if that DOM element is (or any of
         * its parents are) removed from the document. Therefore, we sort
         * everything around that row instead.
         */
        final TableRowElement focusedRow = getRowWithFocus();

        if (focusedRow != null) {
            assert focusedRow.getParentElement() == root : "Trying to sort around a row that doesn't exist in body";
            assert visualRowOrder.contains(focusedRow)
                    || this.escalator.body.spacerContainer.isSpacer(focusedRow) : "Trying to sort around a row that doesn't exist in visualRowOrder or is not a spacer.";
        }

        /*
         * Two cases handled simultaneously:
         * 
         * 1) No focus on rows. We iterate visualRowOrder backwards, and
         * take the respective element in the DOM, and place it as the first
         * child in the body element. Then we take the next-to-last from
         * visualRowOrder, and put that first, pushing the previous row as
         * the second child. And so on...
         * 
         * 2) Focus on some row within Escalator body. Again, we iterate
         * visualRowOrder backwards. This time, we use the focused row as a
         * pivot: Instead of placing rows from the bottom of visualRowOrder
         * and placing it first, we place it underneath the focused row.
         * Once we hit the focused row, we don't move it (to not reset
         * focus) but change sorting mode. After that, we place all rows as
         * the first child.
         */

        List<TableRowElement> orderedBodyRows = new ArrayList<TableRowElement>(
                visualRowOrder);
        Map<Integer, SpacerContainer.SpacerImpl> spacers = this.escalator.body.spacerContainer
                .getSpacers();

        /*
         * Start at -1 to include a spacer that is rendered above the
         * viewport, but its parent row is still not shown
         */
        for (int i = -1; i < visualRowOrder.size(); i++) {
            SpacerContainer.SpacerImpl spacer = spacers.remove(Integer
                    .valueOf(getTopRowLogicalIndex() + i));

            if (spacer != null) {
                orderedBodyRows.add(i + 1, spacer.getRootElement());
                spacer.show();
            }
        }
        /*
         * At this point, invisible spacers aren't reordered, so their
         * position in the DOM will remain undefined.
         */

        // If a spacer was not reordered, it means that it's out of view.
        for (SpacerContainer.SpacerImpl unmovedSpacer : spacers.values()) {
            unmovedSpacer.hide();
        }

        /*
         * If we have a focused row, start in the mode where we put
         * everything underneath that row. Otherwise, all rows are placed as
         * first child.
         */
        boolean insertFirst = (focusedRow == null);

        final ListIterator<TableRowElement> i = orderedBodyRows
                .listIterator(orderedBodyRows.size());
        while (i.hasPrevious()) {
            TableRowElement tr = i.previous();

            if (tr == focusedRow) {
                insertFirst = true;
            } else if (insertFirst) {
                root.insertFirst(tr);
            } else {
                root.insertAfter(tr, focusedRow);
            }
        }

        Profiler.leave(profilingName);
    }

    /**
     * Get the {@literal <tbody>} row that contains (or has) focus.
     * 
     * @return The {@literal <tbody>} row that contains a focused DOM
     *         element, or <code>null</code> if focus is outside of a body
     *         row.
     */
    private TableRowElement getRowWithFocus() {
        TableRowElement rowContainingFocus = null;

        final Element focusedElement = WidgetUtil.getFocusedElement();

        if (focusedElement != null && root.isOrHasChild(focusedElement)) {
            Element e = focusedElement;

            while (e != null && e != root) {
                /*
                 * You never know if there's several tables embedded in a
                 * cell... We'll take the deepest one.
                 */
                if (TableRowElement.is(e)) {
                    rowContainingFocus = TableRowElement.as(e);
                }
                e = e.getParentElement();
            }
        }

        return rowContainingFocus;
    }

    @Override
    public Cell getCell(Element element) {
        Cell cell = super.getCell(element);
        if (cell == null) {
            return null;
        }

        // Convert DOM coordinates to logical coordinates for rows
        TableRowElement rowElement = (TableRowElement) cell.getElement()
                .getParentElement();
        return new Cell(getLogicalRowIndex(rowElement), cell.getColumn(),
                cell.getElement());
    }

    @Override
    public void setSpacer(int rowIndex, double height)
            throws IllegalArgumentException {
        spacerContainer.setSpacer(rowIndex, height);
    }

    @Override
    public void setSpacerUpdater(SpacerUpdater spacerUpdater)
            throws IllegalArgumentException {
        spacerContainer.setSpacerUpdater(spacerUpdater);
    }

    @Override
    public SpacerUpdater getSpacerUpdater() {
        return spacerContainer.getSpacerUpdater();
    }

    /**
     * <em>Calculates</em> the correct top position of a row at a logical
     * index, regardless if there is one there or not.
     * <p>
     * A correct result requires that both {@link #getDefaultRowHeight()} is
     * consistent, and the placement and height of all spacers above the
     * given logical index are consistent.
     * 
     * @param logicalIndex
     *            the logical index of the row for which to calculate the
     *            top position
     * @return the position at which to place a row in {@code logicalIndex}
     * @see #getRowTop(TableRowElement)
     */
    double getRowTop(int logicalIndex) {
        double top = spacerContainer
                .getSpacerHeightsSumUntilIndex(logicalIndex);
        return top + (logicalIndex * getDefaultRowHeight());
    }

    public void shiftRowPositions(int row, double diff) {
        for (TableRowElement tr : getVisibleRowsAfter(row)) {
            setRowPosition(tr, 0, getRowTop(tr) + diff);
        }
    }

    private List<TableRowElement> getVisibleRowsAfter(int logicalRow) {
        Range visibleRowLogicalRange = this.escalator.getVisibleRowRange();

        boolean allRowsAreInView = logicalRow < visibleRowLogicalRange
                .getStart();
        boolean noRowsAreInView = logicalRow >= visibleRowLogicalRange
                .getEnd() - 1;

        if (allRowsAreInView) {
            return Collections.unmodifiableList(visualRowOrder);
        } else if (noRowsAreInView) {
            return Collections.emptyList();
        } else {
            int fromIndex = (logicalRow - visibleRowLogicalRange.getStart()) + 1;
            int toIndex = visibleRowLogicalRange.length();
            List<TableRowElement> sublist = visualRowOrder.subList(
                    fromIndex, toIndex);
            return Collections.unmodifiableList(sublist);
        }
    }

    @Override
    public int getDomRowCount() {
        return root.getChildCount()
                - spacerContainer.getSpacersInDom().size();
    }

    @Override
    protected boolean rowCanBeFrozen(TableRowElement tr) {
        return visualRowOrder.contains(tr);
    }

    void reapplySpacerWidths() {
        spacerContainer.reapplySpacerWidths();
    }

    void scrollToSpacer(int spacerIndex, ScrollDestination destination,
            int padding) {
        spacerContainer.scrollToSpacer(spacerIndex, destination, padding);
    }
}