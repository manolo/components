package com.vaadin.client.widgets.escalator;

import java.util.Arrays;

/**
 * Utility class for parsing and storing SubPart request string attributes
 * for Grid and Escalator.
 * 
 * @since 7.5.0
 */
public class SubPartArguments {
    private String type;
    private int[] indices;

    private SubPartArguments(String type, int[] indices) {
        /*
         * The constructor is private so that no third party would by
         * mistake start using this parsing scheme, since it's not official
         * by TestBench (yet?).
         */

        this.type = type;
        this.indices = indices;
    }

    public String getType() {
        return type;
    }

    public int getIndicesLength() {
        return indices.length;
    }

    public int getIndex(int i) {
        return indices[i];
    }

    public int[] getIndices() {
        return Arrays.copyOf(indices, indices.length);
    }

    public static SubPartArguments create(String subPart) {
        String[] splitArgs = subPart.split("\\[");
        String type = splitArgs[0];
        int[] indices = new int[splitArgs.length - 1];
        for (int i = 0; i < indices.length; ++i) {
            String tmp = splitArgs[i + 1];
            indices[i] = Integer.parseInt(tmp.substring(0,
                    tmp.indexOf("]", 1)));
        }
        return new SubPartArguments(type, indices);
    }
}