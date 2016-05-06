package com.ugcleague.ops.service.discord.command;

import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionDescriptor;

import java.util.Collection;

public class CustomHelpFormatter extends BuiltinHelpFormatter {
    /**
     * Makes a formatter with a given overall row width and column separator width.
     *
     * @param desiredOverallWidth         how many characters wide to make the overall help display
     * @param desiredColumnSeparatorWidth how many characters wide to make the separation between option column and
     */
    public CustomHelpFormatter(int desiredOverallWidth, int desiredColumnSeparatorWidth) {
        super(desiredOverallWidth, desiredColumnSeparatorWidth);
    }

    @Override
    protected void addHeaders(Collection<? extends OptionDescriptor> options) {
        if (hasRequiredOption(options)) {
            addOptionRow("Parameters:\n*Option* (* = required)", "*Description*");
        } else {
            addOptionRow("Parameters:\n*Option*", "*Description*");
        }
    }

    @Override
    protected void addOptions(Collection<? extends OptionDescriptor> options) {
        for (OptionDescriptor each : options) {
            if (!each.representsNonOptions()) {
                addOptionRow("**" + createOptionDisplay(each) + "**", createDescriptionDisplay(each));
            }
        }
    }

    @Override
    protected String optionLeader(String option) {
        return option.length() > 1 ? "" : "-";
    }
}
