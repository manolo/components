package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler;

/**
 * Represents the footer section of a Grid. The footer is always empty.
 */
public class Footer extends StaticSection<Footer.FooterRow> {
    /**
     * A single cell in a grid Footer row. Has a textual caption.
     * 
     */
    public static class FooterCell extends StaticCell {
    }

    /**
     * A single row in a grid Footer section.
     * 
     */
    public static class FooterRow extends StaticSection.StaticRow<Footer.FooterCell> {
    
        @Override
        protected Footer.FooterCell createCell() {
            return new Footer.FooterCell();
        }
    }

    boolean markAsDirty = false;

    @Override
    protected Footer.FooterRow createRow() {
        return new Footer.FooterRow();
    }

    @Override
    protected void requestSectionRefresh() {
        markAsDirty = true;

        /*
         * Defer the refresh so if we multiple times call refreshSection()
         * (for example when updating cell values) we only get one actual
         * refresh in the end.
         */
        Scheduler.get().scheduleFinally(new Scheduler.ScheduledCommand() {

            @Override
            public void execute() {
                if (markAsDirty) {
                    markAsDirty = false;
                    getGrid().refreshFooter();
                }
            }
        });
    }
}