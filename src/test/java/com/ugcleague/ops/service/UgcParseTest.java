package com.ugcleague.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcleague.ops.web.rest.JsonUgcResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UgcParseTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    public void testReadRawTeamRoster() throws IOException {
        String content = loadFromResource("/ugc_output/roster.txt");
        JsonUgcResponse response = mapper.readValue(content, JsonUgcResponse.class);
        assertEquals(7, response.getColumns().size()); // 7 columns
        assertEquals(4, response.getData().size()); // 4 members
        assertEquals(7, response.getData().get(0).size()); // a member has 7 values (columns)
    }

    @Test
    public void testReadRawTeamPage() throws IOException {
        String content = loadFromResource("/ugc_output/team.txt");
        content = content.replaceAll("(^onLoad\\()|(\\)$)", ""); // clean first
        JsonUgcResponse response = mapper.readValue(content, JsonUgcResponse.class);
        assertEquals(17, response.getColumns().size());
        assertEquals(1, response.getData().size());
        assertEquals(17, response.getData().get(0).size());
    }

    @Test
    public void testDateFormatting() {
        String text = "September, 04 2012 00:03:25";
        LocalDateTime expected = LocalDateTime.of(2012, 9, 4, 0, 3, 25);
        LocalDateTime actual = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("MMMM, dd yyyy HH:mm:ss", Locale.ENGLISH));
        assertTrue(expected.isEqual(actual));
    }

    @Test
    public void testReadRawEmptyResults() throws IOException {
        String content = loadFromResource("/ugc_output/results.empty.txt");
        JsonUgcResponse response = mapper.readValue(content, JsonUgcResponse.class);
        assertEquals(16, response.getColumns().size());
        assertEquals(0, response.getData().size());
    }

    @Test
    public void testReadRawFullResults() throws IOException {
        String content = loadFromResource("/ugc_output/results.full.txt");
        JsonUgcResponse response = mapper.readValue(content, JsonUgcResponse.class);
        assertEquals(16, response.getColumns().size());
        assertEquals(188, response.getData().size());
        assertEquals(16, response.getData().get(0).size());
    }

    private String loadFromResource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
