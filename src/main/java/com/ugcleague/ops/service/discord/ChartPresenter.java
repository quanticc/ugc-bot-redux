package com.ugcleague.ops.service.discord;

import com.codahale.metrics.MetricRegistry;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.DateAxis;
import com.ugcleague.ops.service.util.MetricNames;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import se.maypril.metrics.entity.GaugeEntity;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class ChartPresenter {

    private static final Logger log = LoggerFactory.getLogger(ChartPresenter.class);
    private static final String GAUGES_COLLECTION = "metric_gauge";
    private static final String NAME = "name";
    private static final String TIMESTAMP = "timestamp";
    private static final long HOUR = 3600;
    private static final long DAY = 86400;
    private static final long WEEK = 604800;
    private static final long MONTH = 31556952L / 12;

    private final CommandService commandService;
    private final MetricRegistry metricRegistry;
    private final MongoTemplate mongoTemplate;

    private OptionSpec<String> chartNonOptionSpec;
    private Command chartCommand;
    private OptionSpec<String> chartHistogramSpec;
    private OptionSpec<String> chartPresetSpec;
    private Map<String, BiConsumer<IMessage, OptionSet>> presets = new LinkedHashMap<>();

    @Autowired
    public ChartPresenter(CommandService commandService, MetricRegistry metricRegistry, MongoTemplate mongoTemplate) {
        this.commandService = commandService;
        this.metricRegistry = metricRegistry;
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    private void configure() {
        Map<String, String> aliases = new HashMap<>();
//        aliases.put("histogram", "-h");
//        aliases.put("counter", "-c");
//        aliases.put("gauge", "-g");
//        aliases.put("meter", "-m");
//        aliases.put("timer", "-t");
        aliases.put("preset", "-p");
        presets.put("response", this::sendResponseChart);
//        presets.put("players", this::supplyPlayersChart);
//        presets.put("ping", this::supplyPingChart);
//        presets.put("users", this::supplyUsersChart);
        OptionParser parser = newParser();
        chartNonOptionSpec = parser.nonOptions("Time-period to draw the graph for: hour, day, week, month").ofType(String.class);
        chartPresetSpec = parser.acceptsAll(asList("p", "preset"), "Graph one of the preset metrics").withRequiredArg().required();
//        parser.acceptsAll(asList("h", "histogram"), "Graph this histogram metric").withRequiredArg();
//        parser.acceptsAll(asList("c", "counter"), "Graph this counter metric").withRequiredArg();
//        parser.acceptsAll(asList("g", "gauge"), "Graph this gauge metric").withRequiredArg();
//        parser.acceptsAll(asList("m", "meter"), "Graph this meter metric").withRequiredArg();
//        parser.acceptsAll(asList("t", "timer"), "Graph this timer metric").withRequiredArg();
        chartCommand = commandService.register(CommandBuilder.startsWith(".chart").description("Display a chart of a certain metric")
            .support().originReplies().queued().parser(parser).withOptionAliases(aliases).command(this::chart).build());
    }

    private String chart(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        String preset = optionSet.valueOf(chartPresetSpec);
        if (!isValidPreset(preset)) {
            return "Unknown preset";
        }

        presets.get(preset).accept(message, optionSet);
        return "";
    }

    private void takeSnapshot(Node node, Consumer<File> consumer) {
        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            VBox vbox = new VBox(node);
            Stage stage = new Stage();
            Scene scene = new Scene(vbox);
            stage.setScene(scene);
            stage.show();
            SnapshotParameters parameters = new SnapshotParameters();
            node.snapshot(result -> {
                String stamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                File dir = new File("metrics");
                File file;
                try {
                    Files.createDirectories(dir.toPath());
                    file = new File(dir, "chart-" + stamp + ".png");
                } catch (IOException e) {
                    log.warn("Could not create folder, using project dir", e);
                    file = new File("chart-" + stamp + ".png");
                }
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(result.getImage(), null), "png", file);
                    consumer.accept(file);
                } catch (IOException e) {
                    log.warn("Could not create image", e);
                }
                stage.hide();
                return null;
            }, parameters, null);
        });
    }

    private boolean isValidPreset(String name) {
        return presets.containsKey(name);
    }

    private List<String> distinctNonOptions(OptionSet optionSet) {
        return optionSet.valuesOf(chartNonOptionSpec).stream().distinct().collect(Collectors.toList());
    }

    private boolean isFromLastHour(GaugeEntity entity) {
        return isAfterNowMinusHours(entity.getTimestamp(), 1);
    }

    private boolean isFromLast24Hours(GaugeEntity entity) {
        return isAfterNowMinusHours(entity.getTimestamp(), 24);
    }

    private boolean isFromLastWeek(GaugeEntity entity) {
        return isAfterNowMinusHours(entity.getTimestamp(), 168);
    }

    private boolean isAfterNowMinusHours(Date target, long hoursToSubstract) {
        return target.toInstant().isAfter(Instant.now().minusSeconds(HOUR * hoursToSubstract));
    }

    private void sendResponseChart(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = distinctNonOptions(optionSet);
        List<GaugeEntity> entityList = mongoTemplate.find(
            query(where(NAME).is(MetricNames.DISCORD_WS_RESPONSE)
                .and(TIMESTAMP).gte(Date.from(Instant.now().minusSeconds(MONTH)))), GaugeEntity.class, GAUGES_COLLECTION);
        sendChart(message, nonOptions, entityList, "Discord API", "Mean response time", "Milliseconds");
    }

    private void sendPingChart(IMessage message, OptionSet optionSet) {
    }

    private void sendPlayersChart(IMessage message, OptionSet optionSet) {
    }

    private void sendUsersChart(IMessage message, OptionSet optionSet) {

    }

    private void sendHeapChart(IMessage message, OptionSet optionSet) {
    }

    private void sendChart(IMessage message, List<String> options, List<GaugeEntity> entityList,
                           String titlePrefix, String seriesName, String yAxisLabel) {
        log.debug("Data point count: {}", entityList.size());

        if (options.isEmpty()) {
            options.add("hour");
        }

        for (String type : options) {
            List<GaugeEntity> points;
            switch (type) {
                case "hour":
                    points = entityList.stream().filter(this::isFromLastHour).collect(Collectors.toList());
                    break;
                case "day":
                    points = entityList.stream().filter(this::isFromLast24Hours).collect(Collectors.toList());
                    break;
                case "week":
                    points = entityList.stream().filter(this::isFromLastWeek).collect(Collectors.toList());
                    break;
                case "month":
                    points = entityList;
                    break;
                default:
                    log.warn("Input invalid time period: {}", type);
                    continue;
            }
            if (points == null || points.isEmpty()) {
                log.debug("Not enough data to display last {}, showing last month", type);
                chartAndReply(message, entityList, titlePrefix.trim() + " - Last month", seriesName, yAxisLabel);
            } else {
                log.debug("Displaying {} data points from the last {}", points.size(), type);
                chartAndReply(message, points, titlePrefix.trim() + " - Last " + type, seriesName, yAxisLabel);
            }
        }
    }

    private void chartAndReply(IMessage message, List<GaugeEntity> entityList, String title, String seriesName, String yAxisLabel) {
        LineChart<Long, Number> chart = timelineChart(entityList, seriesName);
        chart.setTitle(title);
        chart.getYAxis().setLabel(yAxisLabel);
        takeSnapshot(chart, file -> {
            try {
                commandService.fileReplyFrom(message, chartCommand, file);
            } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
                log.warn("Could not send file", e);
            }
        });
    }

    private LineChart<Long, Number> timelineChart(List<GaugeEntity> dataList, String seriesName) {
        NumberAxis y = new NumberAxis();
        y.setAnimated(false);
        DateAxis x = new DateAxis();
        x.setForceZeroInRange(false);
        x.setMinorTickCount(2);
        x.setAnimated(false);
        LineChart<Long, Number> chart = new LineChart<>(x, y);
        XYChart.Series<Long, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
        for (GaugeEntity data : dataList) {
            long value = (long) data.getValue();
            long millis = data.getTimestamp().getTime();
            series.getData().add(new XYChart.Data<>(millis, value));
        }
        chart.getData().add(series);
        chart.setPrefSize(400, 250); // ideal before Discord starts reducing thumbnail size
        return chart;
    }
}
