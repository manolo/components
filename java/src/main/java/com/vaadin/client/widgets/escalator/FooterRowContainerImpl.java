package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableSectionElement;

class FooterRowContainerImpl extends AbstractStaticRowContainer {

    public FooterRowContainerImpl(Escalator escalator, final TableSectionElement footElement) {
        super(escalator, footElement);
    }

    @Override
    public void setStylePrimaryName(String primaryStyleName) {
        super.setStylePrimaryName(primaryStyleName);
        JsniUtil.setStylePrimaryName(root, primaryStyleName + "-footer");
    }

    @Override
    protected String getCellElementTagName() {
        return "td";
    }

    @Override
    protected void sectionHeightCalculated() {
        double headerHeight = this.escalator.header.getHeightOfSection();
        double footerHeight = this.escalator.footer.getHeightOfSection();
        int vscrollHeight = (int) Math.floor(this.escalator.heightOfEscalator
                - headerHeight - footerHeight);

        final boolean horizontalScrollbarNeeded = this.escalator.columnConfiguration
                .calculateRowWidth() > this.escalator.widthOfEscalator;
        if (horizontalScrollbarNeeded) {
            vscrollHeight -= this.escalator.horizontalScrollbar.getScrollbarThickness();
        }

        this.escalator.footerDeco.getStyle().setHeight(this.escalator.footer.getHeightOfSection(),
                Unit.PX);

        this.escalator.verticalScrollbar.setOffsetSize(vscrollHeight);
    }
}