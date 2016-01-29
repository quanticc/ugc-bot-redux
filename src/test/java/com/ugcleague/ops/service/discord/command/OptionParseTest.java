package com.ugcleague.ops.service.discord.command;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OptionParseTest {

    private static final Logger log = LoggerFactory.getLogger(OptionParseTest.class);

    @Test
    public void testComplexArgsParse() {
        String args = "-f \"File name with spaces\" -g group non-opt1 non-opt2 'non opt with spaces 3' yes";
        Pattern pattern = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
        Matcher matcher = pattern.matcher(args);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group().replaceAll("\"|'", ""));
        }
        String[] array = matches.toArray(new String[matches.size()]);
        assertEquals("File name with spaces", array[1]);
        OptionParser parser = new OptionParser();
        parser.accepts("f").withOptionalArg();
        parser.accepts("g").withOptionalArg();
        OptionSet opts = parser.parse(array);
        assertTrue(opts.has("f"));
        assertEquals("File name with spaces", opts.valueOf("f"));
        assertTrue(opts.has("g"));
        assertEquals("group", opts.valueOf("g"));
        assertEquals(4, opts.nonOptionArguments().size());
    }


}
