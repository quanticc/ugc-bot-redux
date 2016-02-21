package com.ugcleague.ops.service.discord.command;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import java.util.List;

public class SplitMessageTest {

    @Test
    public void testNewlineSplits() throws InterruptedException {
        String alpha = RandomStringUtils.randomAlphanumeric(100);
        StringBuilder builder = new StringBuilder(alpha);
        for (int i = 0; i < 8; i++) {
            builder.insert(i * 10, "-\n-");
        }
        SplitMessage message = new SplitMessage(builder.toString());
        List<String> splits = message.split(33);
    }
}
