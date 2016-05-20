package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.RootPanel;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.Spacer;
import com.vaadin.client.widget.escalator.SpacerUpdater;
import com.vaadin.client.widget.grid.events.ScrollEvent;
import com.vaadin.client.widget.grid.events.ScrollHandler;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.ui.grid.ScrollDestination;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

class SpacerContainer {

    /**
     * 
     */
    private final Escalator escalator;

    /**
     * @param escalator
     */
    SpacerContainer(Escalator escalator) {
        this.escalator = escalator;
    }

    /** This is used mainly for testing purposes */
    private static final String SPACER_LOGICAL_ROW_PROPERTY = "vLogicalRow";

    final class SpacerImpl implements Spacer {
        private TableCellElement spacerElement;
        private TableRowElement root;
        private DivElement deco;
        private int rowIndex;
        private double height = -1;
        private boolean domHasBeenSetup = false;
        private double decoHeight;
        private double defaultCellBorderBottomSize = -1;

        public SpacerImpl(int rowIndex) {
            this.rowIndex = rowIndex;

            root = TableRowElement.as(DOM.createTR());
            spacerElement = TableCellElement.as(DOM.createTD());
            root.appendChild(spacerElement);
            root.setPropertyInt(SPACER_LOGICAL_ROW_PROPERTY, rowIndex);
            deco = DivElement.as(DOM.createDiv());
        }

        public void setPositionDiff(double x, double y) {
            setPosition(getLeft() + x, getTop() + y);
        }

        public void setupDom(double height) {
            assert !domHasBeenSetup : "DOM can't be set up twice.";
            assert RootPanel.get().getElement().isOrHasChild(root) : "Root element should've been attached to the DOM by now.";
            domHasBeenSetup = true;

            getRootElement().getStyle().setWidth(SpacerContainer.this.escalator.getInnerWidth(), Unit.PX);
            setHeight(height);

            spacerElement.setColSpan(SpacerContainer.this.escalator.getColumnConfiguration()
                    .getColumnCount());

            setStylePrimaryName(SpacerContainer.this.escalator.getStylePrimaryName());
        }

        public TableRowElement getRootElement() {
            return root;
        }

        @Override
        public Element getDecoElement() {
            return deco;
        }

        public void setPosition(double x, double y) {
            SpacerContainer.this.escalator.positions.set(getRootElement(), x, y);
            SpacerContainer.this.escalator.positions
                    .set(getDecoElement(), 0, y - getSpacerDecoTopOffset());
        }

        private double getSpacerDecoTopOffset() {
            return SpacerContainer.this.escalator.getBody().getDefaultRowHeight();
        }

        public void setStylePrimaryName(String style) {
            JsniUtil.setStylePrimaryName(root, style + "-spacer");
            JsniUtil.setStylePrimaryName(deco, style + "-spacer-deco");
        }

        public void setHeight(double height) {

            assert height >= 0 : "Height must be more >= 0 (was " + height
                    + ")";

            final double heightDiff = height - Math.max(0, this.height);
            final double oldHeight = this.height;

            this.height = height;

            // since the spacer might be rendered on top of the previous
            // rows border (done with css), need to increase height the
            // amount of the border thickness
            if (defaultCellBorderBottomSize < 0) {
                defaultCellBorderBottomSize = WidgetUtil
                        .getBorderBottomThickness(SpacerContainer.this.escalator.body.getRowElement(
                                SpacerContainer.this.escalator.getVisibleRowRange().getStart())
                                .getFirstChildElement());
            }
            root.getStyle().setHeight(height + defaultCellBorderBottomSize,
                    Unit.PX);

            // move the visible spacers getRow row onwards.
            shiftSpacerPositionsAfterRow(getRow(), heightDiff);

            /*
             * If we're growing, we'll adjust the scroll size first, then
             * adjust scrolling. If we're shrinking, we do it after the
             * second if-clause.
             */
            boolean spacerIsGrowing = heightDiff > 0;
            if (spacerIsGrowing) {
                SpacerContainer.this.escalator.verticalScrollbar.setScrollSize(SpacerContainer.this.escalator.verticalScrollbar
                        .getScrollSize() + heightDiff);
            }

            /*
             * Don't modify the scrollbars if we're expanding the -1 spacer
             * while we're scrolled to the top.
             */
            boolean minusOneSpacerException = spacerIsGrowing
                    && getRow() == -1 && SpacerContainer.this.escalator.body.getTopRowLogicalIndex() == 0;

            boolean viewportNeedsScrolling = getRow() < SpacerContainer.this.escalator.body
                    .getTopRowLogicalIndex() && !minusOneSpacerException;
            if (viewportNeedsScrolling) {

                /*
                 * We can't use adjustScrollPos here, probably because of a
                 * bookkeeping-related race condition.
                 * 
                 * This particular situation is easier, however, since we
                 * know exactly how many pixels we need to move (heightDiff)
                 * and all elements below the spacer always need to move
                 * that pixel amount.
                 */

                for (TableRowElement row : SpacerContainer.this.escalator.body.visualRowOrder) {
                    SpacerContainer.this.escalator.body.setRowPosition(row, 0, SpacerContainer.this.escalator.body.getRowTop(row)
                            + heightDiff);
                }

                double top = getTop();
                double bottom = top + oldHeight;
                double scrollTop = SpacerContainer.this.escalator.verticalScrollbar.getScrollPos();

                boolean viewportTopIsAtMidSpacer = top < scrollTop
                        && scrollTop < bottom;

                final double moveDiff;
                if (viewportTopIsAtMidSpacer && !spacerIsGrowing) {

                    /*
                     * If the scroll top is in the middle of the modified
                     * spacer, we want to scroll the viewport up as usual,
                     * but we don't want to scroll past the top of it.
                     * 
                     * Math.max ensures this (remember: the result is going
                     * to be negative).
                     */

                    moveDiff = Math.max(heightDiff, top - scrollTop);
                } else {
                    moveDiff = heightDiff;
                }
                SpacerContainer.this.escalator.body.setBodyScrollPosition(SpacerContainer.this.escalator.tBodyScrollLeft, SpacerContainer.this.escalator.tBodyScrollTop
                        + moveDiff);
                SpacerContainer.this.escalator.verticalScrollbar.setScrollPosByDelta(moveDiff);

            } else {
                SpacerContainer.this.escalator.body.shiftRowPositions(getRow(), heightDiff);
            }

            if (!spacerIsGrowing) {
                SpacerContainer.this.escalator.verticalScrollbar.setScrollSize(SpacerContainer.this.escalator.verticalScrollbar
                        .getScrollSize() + heightDiff);
            }

            updateDecoratorGeometry(height);
        }

        /** Resizes and places the decorator. */
        private void updateDecoratorGeometry(double detailsHeight) {
            Style style = deco.getStyle();
            decoHeight = detailsHeight + SpacerContainer.this.escalator.getBody().getDefaultRowHeight();
            style.setHeight(decoHeight, Unit.PX);
        }

        @Override
        public Element getElement() {
            return spacerElement;
        }

        @Override
        public int getRow() {
            return rowIndex;
        }

        public double getHeight() {
            assert height >= 0 : "Height was not previously set by setHeight.";
            return height;
        }

        public double getTop() {
            return SpacerContainer.this.escalator.positions.getTop(getRootElement());
        }

        public double getLeft() {
            return SpacerContainer.this.escalator.positions.getLeft(getRootElement());
        }

        /**
         * Sets a new row index for this spacer. Also updates the bookeeping
         * at {@link SpacerContainer#rowIndexToSpacer}.
         */
        @SuppressWarnings("boxing")
        public void setRowIndex(int rowIndex) {
            SpacerImpl spacer = rowIndexToSpacer.remove(this.rowIndex);
            assert this == spacer : "trying to move an unexpected spacer.";
            this.rowIndex = rowIndex;
            root.setPropertyInt(SPACER_LOGICAL_ROW_PROPERTY, rowIndex);
            rowIndexToSpacer.put(this.rowIndex, this);
        }

        /**
         * Updates the spacer's visibility parameters, based on whether it
         * is being currently visible or not.
         */
        public void updateVisibility() {
            if (isInViewport()) {
                show();
            } else {
                hide();
            }
        }

        private boolean isInViewport() {
            int top = (int) Math.ceil(getTop());
            int height = (int) Math.floor(getHeight());
            Range location = Range.withLength(top, height);
            return SpacerContainer.this.escalator.getViewportPixels().intersects(location);
        }

        public void show() {
            getRootElement().getStyle().clearDisplay();
            getDecoElement().getStyle().clearDisplay();
        }

        public void hide() {
            getRootElement().getStyle().setDisplay(Display.NONE);
            getDecoElement().getStyle().setDisplay(Display.NONE);
        }

        /**
         * Crop the decorator element so that it doesn't overlap the header
         * and footer sections.
         * 
         * @param bodyTop
         *            the top cordinate of the escalator body
         * @param bodyBottom
         *            the bottom cordinate of the escalator body
         * @param decoWidth
         *            width of the deco
         */
        private void updateDecoClip(final double bodyTop,
                final double bodyBottom, final double decoWidth) {
            final int top = deco.getAbsoluteTop();
            final int bottom = deco.getAbsoluteBottom();
            /*
             * FIXME
             * 
             * Height and its use is a workaround for the issue where
             * coordinates of the deco are not calculated yet. This will
             * prevent a deco from being displayed when it's added to DOM
             */
            final int height = bottom - top;
            if (top < bodyTop || bottom > bodyBottom) {
                final double topClip = Math.max(0.0D, bodyTop - top);
                final double bottomClip = height
                        - Math.max(0.0D, bottom - bodyBottom);
                // TODO [optimize] not sure how GWT compiles this
                final String clip = new StringBuilder("rect(")
                        .append(topClip).append("px,").append(decoWidth)
                        .append("px,").append(bottomClip).append("px,0)")
                        .toString();
                deco.getStyle().setProperty("clip", clip);
            } else {
                deco.getStyle().setProperty("clip", "auto");
            }
        }
    }

    /**
     * A decision on how to measure a spacer when it is partially within a
     * designated range.
     * <p>
     * The meaning of each value may differ depending on the context it is being
     * used in. Check that particular method's JavaDoc.
     */
    enum SpacerInclusionStrategy {
        /** A representation of "the entire spacer". */
        COMPLETE,
    
        /** A representation of "a partial spacer". */
        PARTIAL,
    
        /** A representation of "no spacer at all". */
        NONE
    }

    private final TreeMap<Integer, SpacerImpl> rowIndexToSpacer = new TreeMap<Integer, SpacerImpl>();

    private SpacerUpdater spacerUpdater = SpacerUpdater.NULL;

    private final ScrollHandler spacerScroller = new ScrollHandler() {
        private double prevScrollX = 0;

        @Override
        public void onScroll(ScrollEvent event) {
            if (WidgetUtil.pixelValuesEqual(SpacerContainer.this.escalator.getScrollLeft(), prevScrollX)) {
                return;
            }

            prevScrollX = SpacerContainer.this.escalator.getScrollLeft();
            for (SpacerImpl spacer : rowIndexToSpacer.values()) {
                spacer.setPosition(prevScrollX, spacer.getTop());
            }
        }
    };
    private HandlerRegistration spacerScrollerRegistration;

    /** Width of the spacers' decos. Calculated once then cached. */
    private double spacerDecoWidth = 0.0D;

    public void setSpacer(int rowIndex, double height)
            throws IllegalArgumentException {

        if (rowIndex < -1 || rowIndex >= this.escalator.getBody().getRowCount()) {
            throw new IllegalArgumentException("invalid row index: "
                    + rowIndex + ", while the body only has "
                    + this.escalator.getBody().getRowCount() + " rows.");
        }

        if (height >= 0) {
            if (!spacerExists(rowIndex)) {
                insertNewSpacer(rowIndex, height);
            } else {
                updateExistingSpacer(rowIndex, height);
            }
        } else if (spacerExists(rowIndex)) {
            removeSpacer(rowIndex);
        }

        updateSpacerDecosVisibility();
    }

    /** Checks if a given element is a spacer element */
    public boolean isSpacer(Element row) {

        /*
         * If this needs optimization, we could do a more heuristic check
         * based on stylenames and stuff, instead of iterating through the
         * map.
         */

        for (SpacerImpl spacer : rowIndexToSpacer.values()) {
            if (spacer.getRootElement().equals(row)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("boxing")
    void scrollToSpacer(int spacerIndex, ScrollDestination destination,
            int padding) {

        assert !destination.equals(ScrollDestination.MIDDLE)
                || padding != 0 : "destination/padding check should be done before this method";

        if (!rowIndexToSpacer.containsKey(spacerIndex)) {
            throw new IllegalArgumentException("No spacer open at index "
                    + spacerIndex);
        }

        SpacerImpl spacer = rowIndexToSpacer.get(spacerIndex);
        double targetStartPx = spacer.getTop();
        double targetEndPx = targetStartPx + spacer.getHeight();

        Range viewportPixels = this.escalator.getViewportPixels();
        double viewportStartPx = viewportPixels.getStart();
        double viewportEndPx = viewportPixels.getEnd();

        double scrollTop = Escalator.getScrollPos(destination, targetStartPx,
                targetEndPx, viewportStartPx, viewportEndPx, padding);

        this.escalator.setScrollTop(scrollTop);
    }

    public void reapplySpacerWidths() {
        // FIXME #16266 , spacers get couple pixels too much because borders
        final double width = this.escalator.getInnerWidth() - spacerDecoWidth;
        for (SpacerImpl spacer : rowIndexToSpacer.values()) {
            spacer.getRootElement().getStyle().setWidth(width, Unit.PX);
        }
    }

    public void paintRemoveSpacers(Range removedRowsRange) {
        removeSpacers(removedRowsRange);
        shiftSpacersByRows(removedRowsRange.getStart(),
                -removedRowsRange.length());
    }

    @SuppressWarnings("boxing")
    public void removeSpacers(Range removedRange) {

        Map<Integer, SpacerImpl> removedSpacers = rowIndexToSpacer
                .subMap(removedRange.getStart(), true,
                        removedRange.getEnd(), false);

        if (removedSpacers.isEmpty()) {
            return;
        }

        for (SpacerImpl spacer : removedSpacers.values()) {
            /*
             * [[optimization]] TODO: Each invocation of the setHeight
             * method has a cascading effect in the DOM. if this proves to
             * be slow, the DOM offset could be updated as a batch.
             */

            destroySpacerContent(spacer);
            spacer.setHeight(0); // resets row offsets
            spacer.getRootElement().removeFromParent();
            spacer.getDecoElement().removeFromParent();
        }

        removedSpacers.clear();

        if (rowIndexToSpacer.isEmpty()) {
            assert spacerScrollerRegistration != null : "Spacer scroller registration was null";
            spacerScrollerRegistration.removeHandler();
            spacerScrollerRegistration = null;
        }
    }

    public Map<Integer, SpacerImpl> getSpacers() {
        return new HashMap<Integer, SpacerImpl>(rowIndexToSpacer);
    }

    /**
     * Calculates the sum of all spacers.
     * 
     * @return sum of all spacers, or 0 if no spacers present
     */
    public double getSpacerHeightsSum() {
        return getHeights(rowIndexToSpacer.values());
    }

    /**
     * Calculates the sum of all spacers from one row index onwards.
     * 
     * @param logicalRowIndex
     *            the spacer to include as the first calculated spacer
     * @return the sum of all spacers from {@code logicalRowIndex} and
     *         onwards, or 0 if no suitable spacers were found
     */
    @SuppressWarnings("boxing")
    public Collection<SpacerImpl> getSpacersForRowAndAfter(
            int logicalRowIndex) {
        return new ArrayList<SpacerImpl>(rowIndexToSpacer.tailMap(
                logicalRowIndex, true).values());
    }

    /**
     * Get all spacers from one pixel point onwards.
     * <p>
     * 
     * In this method, the {@link SpacerContainer.SpacerInclusionStrategy} has the following
     * meaning when a spacer lies in the middle of either pixel argument:
     * <dl>
     * <dt>{@link SpacerInclusionStrategy#COMPLETE COMPLETE}
     * <dd>include the spacer
     * <dt>{@link SpacerInclusionStrategy#PARTIAL PARTIAL}
     * <dd>include the spacer
     * <dt>{@link SpacerInclusionStrategy#NONE NONE}
     * <dd>ignore the spacer
     * </dl>
     * 
     * @param px
     *            the pixel point after which to return all spacers
     * @param strategy
     *            the inclusion strategy regarding the {@code px}
     * @return a collection of the spacers that exist after {@code px}
     */
    public Collection<SpacerImpl> getSpacersAfterPx(final double px,
            final SpacerContainer.SpacerInclusionStrategy strategy) {

        ArrayList<SpacerImpl> spacers = new ArrayList<SpacerImpl>(
                rowIndexToSpacer.values());

        for (int i = 0; i < spacers.size(); i++) {
            SpacerImpl spacer = spacers.get(i);

            double top = spacer.getTop();
            double bottom = top + spacer.getHeight();

            if (top > px) {
                return spacers.subList(i, spacers.size());
            } else if (bottom > px) {
                if (strategy == SpacerContainer.SpacerInclusionStrategy.NONE) {
                    return spacers.subList(i + 1, spacers.size());
                } else {
                    return spacers.subList(i, spacers.size());
                }
            }
        }

        return Collections.emptySet();
    }

    /**
     * Gets the spacers currently rendered in the DOM.
     * 
     * @return an unmodifiable (but live) collection of the spacers
     *         currently in the DOM
     */
    public Collection<SpacerImpl> getSpacersInDom() {
        return Collections
                .unmodifiableCollection(rowIndexToSpacer.values());
    }

    /**
     * Gets the amount of pixels occupied by spacers between two pixel
     * points.
     * <p>
     * In this method, the {@link SpacerContainer.SpacerInclusionStrategy} has the following
     * meaning when a spacer lies in the middle of either pixel argument:
     * <dl>
     * <dt>{@link SpacerInclusionStrategy#COMPLETE COMPLETE}
     * <dd>take the entire spacer into account
     * <dt>{@link SpacerInclusionStrategy#PARTIAL PARTIAL}
     * <dd>take only the visible area into account
     * <dt>{@link SpacerInclusionStrategy#NONE NONE}
     * <dd>ignore that spacer
     * </dl>
     * 
     * @param rangeTop
     *            the top pixel point
     * @param topInclusion
     *            the inclusion strategy regarding {@code rangeTop}.
     * @param rangeBottom
     *            the bottom pixel point
     * @param bottomInclusion
     *            the inclusion strategy regarding {@code rangeBottom}.
     * @return the pixels occupied by spacers between {@code rangeTop} and
     *         {@code rangeBottom}
     */
    public double getSpacerHeightsSumBetweenPx(double rangeTop,
            SpacerContainer.SpacerInclusionStrategy topInclusion, double rangeBottom,
            SpacerContainer.SpacerInclusionStrategy bottomInclusion) {

        assert rangeTop <= rangeBottom : "rangeTop must be less than rangeBottom";

        double heights = 0;

        /*
         * TODO [[optimize]]: this might be somewhat inefficient (due to
         * iterator-based scanning, instead of using the treemap's search
         * functionalities). But it should be easy to write, read, verify
         * and maintain.
         */
        for (SpacerImpl spacer : rowIndexToSpacer.values()) {
            double top = spacer.getTop();
            double height = spacer.getHeight();
            double bottom = top + height;

            /*
             * If we happen to implement a DoubleRange (in addition to the
             * int-based Range) at some point, the following logic should
             * probably be converted into using the
             * Range.partitionWith-equivalent.
             */

            boolean topIsAboveRange = top < rangeTop;
            boolean topIsInRange = rangeTop <= top && top <= rangeBottom;
            boolean topIsBelowRange = rangeBottom < top;

            boolean bottomIsAboveRange = bottom < rangeTop;
            boolean bottomIsInRange = rangeTop <= bottom
                    && bottom <= rangeBottom;
            boolean bottomIsBelowRange = rangeBottom < bottom;

            assert topIsAboveRange ^ topIsBelowRange ^ topIsInRange : "Bad top logic";
            assert bottomIsAboveRange ^ bottomIsBelowRange
                    ^ bottomIsInRange : "Bad bottom logic";

            if (bottomIsAboveRange) {
                continue;
            } else if (topIsBelowRange) {
                return heights;
            }

            else if (topIsAboveRange && bottomIsInRange) {
                switch (topInclusion) {
                case PARTIAL:
                    heights += bottom - rangeTop;
                    break;
                case COMPLETE:
                    heights += height;
                    break;
                default:
                    break;
                }
            }

            else if (topIsAboveRange && bottomIsBelowRange) {

                /*
                 * Here we arbitrarily decide that the top inclusion will
                 * have the honor of overriding the bottom inclusion if
                 * happens to be a conflict of interests.
                 */
                switch (topInclusion) {
                case NONE:
                    return 0;
                case COMPLETE:
                    return height;
                case PARTIAL:
                    return rangeBottom - rangeTop;
                default:
                    throw new IllegalArgumentException(
                            "Unexpected inclusion state :" + topInclusion);
                }

            } else if (topIsInRange && bottomIsInRange) {
                heights += height;
            }

            else if (topIsInRange && bottomIsBelowRange) {
                switch (bottomInclusion) {
                case PARTIAL:
                    heights += rangeBottom - top;
                    break;
                case COMPLETE:
                    heights += height;
                    break;
                default:
                    break;
                }

                return heights;
            }

            else {
                assert false : "Unnaccounted-for situation";
            }
        }

        return heights;
    }

    /**
     * Gets the amount of pixels occupied by spacers from the top until a
     * certain spot from the top of the body.
     * 
     * @param px
     *            pixels counted from the top
     * @return the pixels occupied by spacers up until {@code px}
     */
    public double getSpacerHeightsSumUntilPx(double px) {
        return getSpacerHeightsSumBetweenPx(0,
                SpacerContainer.SpacerInclusionStrategy.PARTIAL, px,
                SpacerContainer.SpacerInclusionStrategy.PARTIAL);
    }

    /**
     * Gets the amount of pixels occupied by spacers until a logical row
     * index.
     * 
     * @param logicalIndex
     *            a logical row index
     * @return the pixels occupied by spacers up until {@code logicalIndex}
     */
    @SuppressWarnings("boxing")
    public double getSpacerHeightsSumUntilIndex(int logicalIndex) {
        return getHeights(rowIndexToSpacer.headMap(logicalIndex, false)
                .values());
    }

    private double getHeights(Collection<SpacerImpl> spacers) {
        double heights = 0;
        for (SpacerImpl spacer : spacers) {
            heights += spacer.getHeight();
        }
        return heights;
    }

    /**
     * Gets the height of the spacer for a row index.
     * 
     * @param rowIndex
     *            the index of the row where the spacer should be
     * @return the height of the spacer at index {@code rowIndex}, or 0 if
     *         there is no spacer there
     */
    public double getSpacerHeight(int rowIndex) {
        SpacerImpl spacer = getSpacer(rowIndex);
        if (spacer != null) {
            return spacer.getHeight();
        } else {
            return 0;
        }
    }

    private boolean spacerExists(int rowIndex) {
        return rowIndexToSpacer.containsKey(Integer.valueOf(rowIndex));
    }

    @SuppressWarnings("boxing")
    private void insertNewSpacer(int rowIndex, double height) {

        if (spacerScrollerRegistration == null) {
            spacerScrollerRegistration = this.escalator.addScrollHandler(spacerScroller);
        }

        final SpacerImpl spacer = new SpacerImpl(rowIndex);

        rowIndexToSpacer.put(rowIndex, spacer);
        // set the position before adding it to DOM
        this.escalator.positions.set(spacer.getRootElement(), this.escalator.getScrollLeft(),
                calculateSpacerTop(rowIndex));

        TableRowElement spacerRoot = spacer.getRootElement();
        spacerRoot.getStyle().setWidth(
                this.escalator.columnConfiguration.calculateRowWidth(), Unit.PX);
        this.escalator.body.getElement().appendChild(spacerRoot);
        spacer.setupDom(height);
        // set the deco position, requires that spacer is in the DOM
        this.escalator.positions.set(spacer.getDecoElement(), 0,
                spacer.getTop() - spacer.getSpacerDecoTopOffset());

        this.escalator.spacerDecoContainer.appendChild(spacer.getDecoElement());
        if (this.escalator.spacerDecoContainer.getParentElement() == null) {
            this.escalator.getElement().appendChild(this.escalator.spacerDecoContainer);
            // calculate the spacer deco width, it won't change
            spacerDecoWidth = WidgetUtil
                    .getRequiredWidthBoundingClientRectDouble(spacer
                            .getDecoElement());
        }

        initSpacerContent(spacer);

        this.escalator.body.sortDomElements();
    }

    private void updateExistingSpacer(int rowIndex, double newHeight) {
        getSpacer(rowIndex).setHeight(newHeight);
    }

    public SpacerImpl getSpacer(int rowIndex) {
        return rowIndexToSpacer.get(Integer.valueOf(rowIndex));
    }

    private void removeSpacer(int rowIndex) {
        removeSpacers(Range.withOnly(rowIndex));
    }

    public void setStylePrimaryName(String style) {
        for (SpacerImpl spacer : rowIndexToSpacer.values()) {
            spacer.setStylePrimaryName(style);
        }
    }

    public void setSpacerUpdater(SpacerUpdater spacerUpdater)
            throws IllegalArgumentException {
        if (spacerUpdater == null) {
            throw new IllegalArgumentException(
                    "spacer updater cannot be null");
        }

        destroySpacerContent(rowIndexToSpacer.values());
        this.spacerUpdater = spacerUpdater;
        initSpacerContent(rowIndexToSpacer.values());
    }

    public SpacerUpdater getSpacerUpdater() {
        return spacerUpdater;
    }

    private void destroySpacerContent(Iterable<SpacerImpl> spacers) {
        for (SpacerImpl spacer : spacers) {
            destroySpacerContent(spacer);
        }
    }

    private void destroySpacerContent(SpacerImpl spacer) {
        assert this.escalator.getElement().isOrHasChild(spacer.getRootElement()) : "Spacer's root element somehow got detached from Escalator before detaching";
        assert this.escalator.getElement().isOrHasChild(spacer.getElement()) : "Spacer element somehow got detached from Escalator before detaching";
        spacerUpdater.destroy(spacer);
        assert this.escalator.getElement().isOrHasChild(spacer.getRootElement()) : "Spacer's root element somehow got detached from Escalator before detaching";
        assert this.escalator.getElement().isOrHasChild(spacer.getElement()) : "Spacer element somehow got detached from Escalator before detaching";
    }

    private void initSpacerContent(Iterable<SpacerImpl> spacers) {
        for (SpacerImpl spacer : spacers) {
            initSpacerContent(spacer);
        }
    }

    private void initSpacerContent(SpacerImpl spacer) {
        assert this.escalator.getElement().isOrHasChild(spacer.getRootElement()) : "Spacer's root element somehow got detached from Escalator before attaching";
        assert this.escalator.getElement().isOrHasChild(spacer.getElement()) : "Spacer element somehow got detached from Escalator before attaching";
        spacerUpdater.init(spacer);
        assert this.escalator.getElement().isOrHasChild(spacer.getRootElement()) : "Spacer's root element somehow got detached from Escalator during attaching";
        assert this.escalator.getElement().isOrHasChild(spacer.getElement()) : "Spacer element somehow got detached from Escalator during attaching";

        spacer.updateVisibility();
    }

    public String getSubPartName(Element subElement) {
        for (SpacerImpl spacer : rowIndexToSpacer.values()) {
            if (spacer.getRootElement().isOrHasChild(subElement)) {
                return "spacer[" + spacer.getRow() + "]";
            }
        }
        return null;
    }

    public Element getSubPartElement(int index) {
        SpacerImpl spacer = rowIndexToSpacer.get(Integer.valueOf(index));
        if (spacer != null) {
            return spacer.getElement();
        } else {
            return null;
        }
    }

    private double calculateSpacerTop(int logicalIndex) {
        return this.escalator.body.getRowTop(logicalIndex) + this.escalator.body.getDefaultRowHeight();
    }

    @SuppressWarnings("boxing")
    private void shiftSpacerPositionsAfterRow(int changedRowIndex,
            double diffPx) {
        for (SpacerImpl spacer : rowIndexToSpacer.tailMap(changedRowIndex,
                false).values()) {
            spacer.setPositionDiff(0, diffPx);
        }
    }

    /**
     * Shifts spacers at and after a specific row by an amount of rows.
     * <p>
     * This moves both their associated row index and also their visual
     * placement.
     * <p>
     * <em>Note:</em> This method does not check for the validity of any
     * arguments.
     * 
     * @param index
     *            the index of first row to move
     * @param numberOfRows
     *            the number of rows to shift the spacers with. A positive
     *            value is downwards, a negative value is upwards.
     */
    public void shiftSpacersByRows(int index, int numberOfRows) {
        final double pxDiff = numberOfRows * this.escalator.body.getDefaultRowHeight();
        for (SpacerContainer.SpacerImpl spacer : getSpacersForRowAndAfter(index)) {
            spacer.setPositionDiff(0, pxDiff);
            spacer.setRowIndex(spacer.getRow() + numberOfRows);
        }
    }

    void updateSpacerDecosVisibility() {
        final Range visibleRowRange = this.escalator.getVisibleRowRange();
        Collection<SpacerImpl> visibleSpacers = rowIndexToSpacer.subMap(
                visibleRowRange.getStart() - 1,
                visibleRowRange.getEnd() + 1).values();
        if (!visibleSpacers.isEmpty()) {
            final double top = this.escalator.tableWrapper.getAbsoluteTop()
                    + this.escalator.header.getHeightOfSection();
            final double bottom = this.escalator.tableWrapper.getAbsoluteBottom()
                    - this.escalator.footer.getHeightOfSection();
            for (SpacerImpl spacer : visibleSpacers) {
                spacer.updateDecoClip(top, bottom, spacerDecoWidth);
            }
        }
    }
}