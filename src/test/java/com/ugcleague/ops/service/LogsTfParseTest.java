package com.ugcleague.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcleague.ops.config.JacksonConfiguration;
import com.ugcleague.ops.domain.document.LogsTfStats;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogsTfParseTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() {
        mapper = new JacksonConfiguration().jackson2ObjectMapperBuilder().build();
    }

    @Test
    public void testParseStats() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/logs_tf/123457.json")) {
            LogsTfStats stats = mapper.readValue(input, LogsTfStats.class);
            assertTrue(stats != null);
            assertEquals(1774, (long) stats.getLength());
        }
    }

    @Test
    public void testParseNewerStats() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/logs_tf/1283315.json")) {
            LogsTfStats stats = mapper.readValue(input, LogsTfStats.class);
            assertTrue(stats != null);
            assertEquals(2051, (long) stats.getLength());
        }
    }
}
