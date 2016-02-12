package com.ugcleague.ops.service;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.community.SteamId;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class StatusParserTest {

    private static final Logger log = LoggerFactory.getLogger(StatusParserTest.class);
    private final Pattern FULL_PATTERN = Pattern.compile("^.+\"(.+)\"\\s+(\\[([a-zA-Z]):([0-5]):([0-9]+)(:[0-9]+)?\\])\\s+.*$");
    private final Pattern SHORT_PATTERN = Pattern.compile("^.+\\s+(\\[U:([0-5]):([0-9]+)(:[0-9]+)?\\])\\s+.*$");
    private final Pattern FULL_PATTERN_MULTILINE = Pattern.compile("^.+\"(.+)\"\\s+(\\[([a-zA-Z]):([0-5]):([0-9]+)(:[0-9]+)?\\])\\s+.*$", Pattern.MULTILINE);
    private final Pattern SHORT_PATTERN_MULTILINE = Pattern.compile("^.+\\s+(\\[U:([0-5]):([0-9]+)(:[0-9]+)?\\])\\s+.*$", Pattern.MULTILINE);

    @Test
    public void testParseNameIdWithReader() throws IOException {
        Map<String, String> players = new LinkedHashMap<>();
        try (InputStream input = getClass().getResourceAsStream("/status/clean_output.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = FULL_PATTERN.matcher(line);
                if (matcher.matches() && matcher.groupCount() > 1) {
                    players.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        assertEquals(12, players.size());
        assertEquals("[U:1:222415707]", players.get("Mega Man"));
        assertEquals("[U:1:125785245]", players.get("CroLoBoL"));
        assertEquals("[U:1:141866318]", players.get("MayOnixer"));
        assertEquals("[U:1:245292181]", players.get("XxXLunaGamerXxX"));
        assertEquals("[U:1:163687408]", players.get("kie"));
        assertEquals("[U:1:128988813]", players.get("' [T.M.D.B] ' Camilo.uN"));
        assertEquals("[U:1:317693120]", players.get("FranILove"));
        assertEquals("[U:1:157813542]", players.get("Uncle shaggy #TostadoraQl"));
        assertEquals("[U:1:294908163]", players.get("beaR"));
        assertEquals("[U:1:51827133]", players.get("UGC quantic"));
        assertEquals("[U:1:90257875]", players.get("[Kx!]Slyme ?"));
        assertEquals("[U:1:183189726]", players.get("Heaven King"));
    }

    @Test
    public void testParseIdWithReader() throws IOException {
        List<String> players = new ArrayList<>();
        try (InputStream input = getClass().getResourceAsStream("/status/clean_output.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = SHORT_PATTERN.matcher(line);
                if (matcher.matches() && matcher.groupCount() >= 1) {
                    players.add(matcher.group(1));
                }
            }
        }
        assertEquals(12, players.size());
        assertEquals("[U:1:222415707]", players.get(0));
        assertEquals("[U:1:125785245]", players.get(1));
        assertEquals("[U:1:141866318]", players.get(2));
        assertEquals("[U:1:245292181]", players.get(3));
        assertEquals("[U:1:163687408]", players.get(4));
        assertEquals("[U:1:128988813]", players.get(5));
        assertEquals("[U:1:317693120]", players.get(6));
        assertEquals("[U:1:157813542]", players.get(7));
        assertEquals("[U:1:294908163]", players.get(8));
        assertEquals("[U:1:51827133]", players.get(9));
        assertEquals("[U:1:90257875]", players.get(10));
        assertEquals("[U:1:183189726]", players.get(11));
    }

    @Test
    public void testParseIdSplittingLines() throws IOException {
        List<String> players = new ArrayList<>();
        try (InputStream input = getClass().getResourceAsStream("/status/clean_output.txt")) {
            String data = StreamUtils.copyToString(input, Charset.forName("UTF-8"));
            for (String line : data.split("\n")) {
                Matcher innerMatcher = SHORT_PATTERN.matcher(line);
                if (innerMatcher.matches() && innerMatcher.groupCount() >= 1) {
                    players.add(innerMatcher.group(1));
                }
            }
            assertEquals(12, players.size());
            assertEquals("[U:1:222415707]", players.get(0));
            assertEquals("[U:1:125785245]", players.get(1));
            assertEquals("[U:1:141866318]", players.get(2));
            assertEquals("[U:1:245292181]", players.get(3));
            assertEquals("[U:1:163687408]", players.get(4));
            assertEquals("[U:1:128988813]", players.get(5));
            assertEquals("[U:1:317693120]", players.get(6));
            assertEquals("[U:1:157813542]", players.get(7));
            assertEquals("[U:1:294908163]", players.get(8));
            assertEquals("[U:1:51827133]", players.get(9));
            assertEquals("[U:1:90257875]", players.get(10));
            assertEquals("[U:1:183189726]", players.get(11));
        }
    }

    @Test
    public void testParseNameIdSingleMatcher() throws IOException, SteamCondenserException {
        Map<String, String> players = new LinkedHashMap<>();
        Map<String, Long> steamIdToCommunityMap = new LinkedHashMap<>();
        try (InputStream input = getClass().getResourceAsStream("/status/clean_output.txt")) {
            String data = StreamUtils.copyToString(input, Charset.forName("UTF-8"));
            Matcher matcher = FULL_PATTERN_MULTILINE.matcher(data);
            while (matcher.find()) {
                /*
                Matches lines that contain an INDIVIDUAL ("U" class) steam3 id
                    matcher.group();       // matched line
                    matcher.group(1);      // name
                    matcher.group(2);      // full steam id
                    matcher.group(3);      // universe id
                    matcher.group(4);      // account id
                    matcher.group(5);      // instance id, commonly null for individual ids
                 */
                players.put(matcher.group(1), matcher.group(2));
                steamIdToCommunityMap.put(matcher.group(2), SteamId.convertSteamIdToCommunityId(matcher.group(2)));
            }
            assertEquals(12, players.size());
            assertEquals("[U:1:222415707]", players.get("Mega Man"));
            assertEquals("[U:1:125785245]", players.get("CroLoBoL"));
            assertEquals("[U:1:141866318]", players.get("MayOnixer"));
            assertEquals("[U:1:245292181]", players.get("XxXLunaGamerXxX"));
            assertEquals("[U:1:163687408]", players.get("kie"));
            assertEquals("[U:1:128988813]", players.get("' [T.M.D.B] ' Camilo.uN"));
            assertEquals("[U:1:317693120]", players.get("FranILove"));
            assertEquals("[U:1:157813542]", players.get("Uncle shaggy #TostadoraQl"));
            assertEquals("[U:1:294908163]", players.get("beaR"));
            assertEquals("[U:1:51827133]", players.get("UGC quantic"));
            assertEquals("[U:1:90257875]", players.get("[Kx!]Slyme ?"));
            assertEquals("[U:1:183189726]", players.get("Heaven King"));
            assertEquals(12, steamIdToCommunityMap.size());
            assertEquals(76561198182681435L, (long) steamIdToCommunityMap.get("[U:1:222415707]"));
            assertEquals(76561198086050973L, (long) steamIdToCommunityMap.get("[U:1:125785245]"));
            assertEquals(76561198102132046L, (long) steamIdToCommunityMap.get("[U:1:141866318]"));
            assertEquals(76561198205557909L, (long) steamIdToCommunityMap.get("[U:1:245292181]"));
            assertEquals(76561198123953136L, (long) steamIdToCommunityMap.get("[U:1:163687408]"));
            assertEquals(76561198089254541L, (long) steamIdToCommunityMap.get("[U:1:128988813]"));
            assertEquals(76561198277958848L, (long) steamIdToCommunityMap.get("[U:1:317693120]"));
            assertEquals(76561198118079270L, (long) steamIdToCommunityMap.get("[U:1:157813542]"));
            assertEquals(76561198255173891L, (long) steamIdToCommunityMap.get("[U:1:294908163]"));
            assertEquals(76561198012092861L, (long) steamIdToCommunityMap.get("[U:1:51827133]"));
            assertEquals(76561198050523603L, (long) steamIdToCommunityMap.get("[U:1:90257875]"));
            assertEquals(76561198143455454L, (long) steamIdToCommunityMap.get("[U:1:183189726]"));
        }
    }

    @Test
    public void testParseIdSingleMatcher() throws IOException {
        List<String> players = new ArrayList<>();
        try (InputStream input = getClass().getResourceAsStream("/status/clean_output.txt")) {
            String data = StreamUtils.copyToString(input, Charset.forName("UTF-8"));
            Matcher matcher = SHORT_PATTERN_MULTILINE.matcher(data);
            while (matcher.find()) {
                /*
                Matches lines that contain an INDIVIDUAL ("U" class) steam3 id
                    matcher.group();       // matched line
                    matcher.group(1);      // full steam id
                    matcher.group(2);      // universe id
                    matcher.group(3);      // account id
                    matcher.group(4);      // instance id, commonly null for individual ids
                 */
                players.add(matcher.group(1));
            }
            assertEquals(12, players.size());
            assertEquals("[U:1:222415707]", players.get(0));
            assertEquals("[U:1:125785245]", players.get(1));
            assertEquals("[U:1:141866318]", players.get(2));
            assertEquals("[U:1:245292181]", players.get(3));
            assertEquals("[U:1:163687408]", players.get(4));
            assertEquals("[U:1:128988813]", players.get(5));
            assertEquals("[U:1:317693120]", players.get(6));
            assertEquals("[U:1:157813542]", players.get(7));
            assertEquals("[U:1:294908163]", players.get(8));
            assertEquals("[U:1:51827133]", players.get(9));
            assertEquals("[U:1:90257875]", players.get(10));
            assertEquals("[U:1:183189726]", players.get(11));
        }
    }

}
