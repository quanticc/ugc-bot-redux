package com.ugcleague.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcleague.ops.config.JacksonConfiguration;
import com.ugcleague.ops.domain.document.SizzStats;
import com.ugcleague.ops.web.rest.JsonSizzStatsResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SizzParseTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() {
        mapper = new JacksonConfiguration().jackson2ObjectMapperBuilder().build();
    }

    @Test
    public void testParseStats() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sizz_stats/445094.json")) {
            JsonSizzStatsResponse deserial = mapper.readValue(input, JsonSizzStatsResponse.class);
            SizzStats stats = deserial.getStats();
            assertTrue(stats != null);
            assertEquals(445094, (long) stats.getId());
            ZonedDateTime expected = ZonedDateTime.of(2016, 2, 8, 9, 34, 57, 839 * 1_000_000, ZoneId.of("GMT"));
            ZonedDateTime expectedZulu = ZonedDateTime.of(2016, 2, 8, 9, 34, 57, 839 * 1_000_000, ZoneId.of("Z"));
            assertTrue(expected.equals(stats.getUpdated()));
            assertTrue(expectedZulu.toInstant().equals(stats.getUpdated().toInstant()));
        }
    }
}
