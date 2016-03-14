package com.ugcleague.ops.service.discord;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageParserTest {

    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@([0-9]+)>");

    @Test
    public void testResolveMentions() {
        Assert.assertEquals("Hey @12615678914564, all good?", resolveMentions("Hey <@12615678914564>, all good?"));
        Assert.assertEquals("@12615678914564 and @1568468788960 are you there?", resolveMentions("<@12615678914564> and <@1568468788960> are you there?"));
    }

    private String resolveMentions(String content) {
        Matcher matcher = USER_MENTION_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1);
            matcher.appendReplacement(buffer, "@" + id);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
