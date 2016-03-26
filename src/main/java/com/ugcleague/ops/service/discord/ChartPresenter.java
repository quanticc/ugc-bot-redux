package com.ugcleague.ops.service.discord;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.ugcleague.ops.domain.document.Chart;
import com.ugcleague.ops.domain.document.Series;
import com.ugcleague.ops.repository.mongo.ChartRepository;
import com.ugcleague.ops.repository.mongo.SeriesRepository;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.DateAxis;
import com.ugcleague.ops.util.DateUtil;
import com.ugcleague.ops.util.Util;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.maypril.metrics.entity.GaugeEntity;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Service
@Transactional
public class ChartPresenter {

    private static final Logger log = LoggerFactory.getLogger(ChartPresenter.class);
    private static final String GAUGES_COLLECTION = "metric_gauge";
    private static final String NAME = "name";
    private static final String TIMESTAMP = "timestamp";

    private final CommandService commandService;
    private final MongoTemplate mongoTemplate;
    private final ChartRepository chartRepository;
    private final SeriesRepository seriesRepository;

    private Command chartCommand;
    private OptionSpec<String> chartGetSpec;
    private OptionSpec<String> chartSinceSpec;
    private OptionSpec<String> chartUntilSpec;
    private OptionSpec<Integer> chartWidthSpec;
    private OptionSpec<Integer> chartHeightSpec;
    private OptionSpec<Void> chartListSpec;
    private OptionSpec<String> chartNonOptionSpec;
    private OptionSpec<Boolean> chartFullSpec;
    private OptionSpec<String> manageAddSpec;
    private OptionSpec<String> manageEditSpec;
    private OptionSpec<String> manageRemoveSpec;
    private OptionSpec<String> manageTitleSpec;
    private OptionSpec<String> manageSeriesSpec;
    private OptionSpec<String> manageXLabelSpec;
    private OptionSpec<String> manageYLabelSpec;
    private OptionSpec<Void> manageListSpec;
    private OptionSpec<Boolean> manageDrawSymbolsSpec;
    private OptionSpec<String> manageFormatSpec;
    private OptionSpec<String> seriesAddSpec;
    private OptionSpec<String> seriesEditSpec;
    private OptionSpec<String> seriesRemoveSpec;
    private OptionSpec<String> seriesMetricSpec;
    private OptionSpec<Void> seriesListSpec;
    private OptionSpec<String> seriesTitleSpec;
    private OptionSpec<Boolean> seriesDrawLastSpec;

    @Autowired
    public ChartPresenter(CommandService commandService, MongoTemplate mongoTemplate,
                          ChartRepository chartRepository, SeriesRepository seriesRepository) {
        this.commandService = commandService;
        this.mongoTemplate = mongoTemplate;
        this.chartRepository = chartRepository;
        this.seriesRepository = seriesRepository;
    }

    @PostConstruct
    private void configure() {
        initChartCommand();
        initChartManageCommand();
        initChartSeriesCommand();
    }

    private void initChartCommand() {
        OptionParser parser = newParser();
        chartNonOptionSpec = parser.nonOptions("A chart name to display").ofType(String.class);
        chartGetSpec = parser.acceptsAll(asList("g", "get"), "Chart name to display")
            .withRequiredArg();
        chartSinceSpec = parser.acceptsAll(asList("a", "after", "since"), "Only display metrics after this time-expression")
            .withRequiredArg().defaultsTo("1 hour ago");
        chartUntilSpec = parser.acceptsAll(asList("b", "before", "until"), "Only display metrics before this time-expression")
            .withRequiredArg().defaultsTo("now");
        chartWidthSpec = parser.acceptsAll(asList("w", "width"), "Width of the chart")
            .withRequiredArg().ofType(Integer.class).defaultsTo(380).describedAs("px");
        chartHeightSpec = parser.acceptsAll(asList("h", "height"), "Height of the chart")
            .withRequiredArg().ofType(Integer.class).defaultsTo(230).describedAs("px");
        chartListSpec = parser.acceptsAll(asList("l", "list"), "Display a list of available charts");
        chartFullSpec = parser.acceptsAll(asList("f", "full", "detailed"), "Display ALL data points available in the specified time range")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        Map<String, String> aliases = newAliasesMap();
        aliases.put("get", "--get");
        aliases.put("since", "--since");
        aliases.put("until", "--until");
        aliases.put("width", "--width");
        aliases.put("height", "--height");
        aliases.put("list", "--list");
        aliases.put("full", "--full");
        aliases.put("detailed", "--detailed");
        chartCommand = commandService.register(CommandBuilder.startsWith(".chart")
            .description("Display a pre-defined chart").support().originReplies().queued()
            .parser(parser).optionAliases(aliases).command(this::chart).build());
    }

    private void initChartManageCommand() {
        OptionParser parser = newParser();
        manageAddSpec = parser.acceptsAll(asList("a", "add"), "Add a new chart definition")
            .withRequiredArg();
        manageEditSpec = parser.acceptsAll(asList("e", "edit"), "Edit an existing chart definition")
            .withRequiredArg();
        manageRemoveSpec = parser.acceptsAll(asList("r", "remove"), "Remove an existing chart definition")
            .withRequiredArg();
        manageTitleSpec = parser.acceptsAll(asList("t", "title"), "Sets the title of this chart")
            .withRequiredArg();
        manageSeriesSpec = parser.acceptsAll(asList("s", "series"), "Defines the series contained in this chart")
            .withRequiredArg().withValuesSeparatedBy(',');
        manageXLabelSpec = parser.acceptsAll(asList("x", "x-label"), "Name of the label for the X axis")
            .withRequiredArg().defaultsTo("");
        manageYLabelSpec = parser.acceptsAll(asList("y", "y-label"), "Name of the label for the Y axis")
            .withRequiredArg().defaultsTo("");
        manageListSpec = parser.acceptsAll(asList("l", "list"), "Display a list of available charts");
        manageDrawSymbolsSpec = parser.accepts("draw-symbols", "Draw symbols (dots) on each data point")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        manageFormatSpec = parser.acceptsAll(asList("f", "format"), "Define a special format of the Y-axis")
            .withOptionalArg().defaultsTo("");
        Map<String, String> aliases = newAliasesMap();
        aliases.put("add", "--add");
        aliases.put("edit", "--edit");
        aliases.put("remove", "--remove");
        aliases.put("title", "--title");
        aliases.put("series", "--series");
        aliases.put("x", "-x");
        aliases.put("y", "-y");
        aliases.put("list", "--list");
        aliases.put("format", "--format");
        commandService.register(CommandBuilder.startsWith(".beep chart")
            .description("Manage chart definitions").master().originReplies().queued()
            .parser(parser).optionAliases(aliases).command(this::manage).build());
    }

    private void initChartSeriesCommand() {
        OptionParser parser = newParser();
        seriesAddSpec = parser.acceptsAll(asList("a", "add"), "Add a new series definition")
            .withRequiredArg();
        seriesEditSpec = parser.acceptsAll(asList("e", "edit"), "Edit an existing series definition")
            .withRequiredArg();
        seriesRemoveSpec = parser.acceptsAll(asList("r", "remove"), "Remove an existing series definition")
            .withRequiredArg();
        seriesMetricSpec = parser.acceptsAll(asList("m", "metric"), "Metric name used to get data for this series")
            .withRequiredArg();
        seriesTitleSpec = parser.acceptsAll(asList("t", "title"), "Series title to be displayed in the chart")
            .withRequiredArg();
        seriesListSpec = parser.acceptsAll(asList("l", "list"), "Display a list of available series");
        seriesDrawLastSpec = parser.accepts("draw-last-value", "Create a numeric node on the last point show the Y-axis value")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        Map<String, String> aliases = newAliasesMap();
        aliases.put("add", "--add");
        aliases.put("edit", "--edit");
        aliases.put("remove", "--remove");
        aliases.put("metric", "--metric");
        aliases.put("title", "--title");
        aliases.put("list", "--list");
        aliases.put("draw-last-value", "--draw-last-value");
        commandService.register(CommandBuilder.startsWith(".beep series")
            .description("Manage chart series").master().originReplies().queued()
            .parser(parser).optionAliases(aliases).command(this::series).build());
    }

    private String chart(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(chartListSpec)) {
            List<Chart> list = chartRepository.findAll();
            if (list.isEmpty()) {
                return "No charts defined";
            } else {
                return "Available definitions: " + list.stream()
                    .map(Chart::getName).collect(Collectors.joining(", "));
            }
        }

        List<String> nonOptions = optionSet.valuesOf(chartNonOptionSpec);
        if (!optionSet.has(chartGetSpec) && nonOptions.isEmpty()) {
            return "Must define a chart name to display";
        }

        // .chart get <preset> [since <timex>] [until <timex>] [width <px>] [height <px>]
        String name = Optional.ofNullable(optionSet.valueOf(chartGetSpec))
            .orElseGet(() -> nonOptions.get(0));
        Optional<Chart> chart = chartRepository.findByName(name);

        if (!chart.isPresent()) {
            return "No chart found by that name";
        }

        if (chart.get().getSeriesList().isEmpty()) {
            return "This chart has no data to display";
        }

        String since = optionSet.valueOf(chartSinceSpec);
        String until = optionSet.valueOf(chartUntilSpec);
        int width = optionSet.valueOf(chartWidthSpec);
        int height = optionSet.valueOf(chartHeightSpec);
        boolean full = optionSet.has(chartFullSpec) ? optionSet.valueOf(chartFullSpec) : false;

        // snapshot the chart and send it as a file to the origin channel
        Node node = generateChart(chart.get(), since, until, width, height, full);
        if (node == null) {
            return "Data set is empty!";
        }
        takeSnapshot(node, file -> {
            try {
                commandService.fileReplyFrom(message, chartCommand, file);
            } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
                log.warn("Could not send file", e);
            }
        });

        return "";
    }

    private Node generateChart(Chart chartSpec, String since, String until, int width, int height, boolean full) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime after = Optional.ofNullable(DateUtil.parseTimeDate(since)).orElse(now.minusHours(1));
        ZonedDateTime before = Optional.ofNullable(DateUtil.parseTimeDate(until)).orElse(now);
        log.debug("Generating '{}' ({}x{}) from {} to {}", chartSpec.getName(), width, height, after, before);
        DateAxis x = new DateAxis();
        NumberAxis y = new NumberAxis();
        x.setForceZeroInRange(false);
        x.setMinorTickCount(4);
        x.setAnimated(false);
        y.setAnimated(false);
        if (chartSpec.getFormat().equals("bytes")) {
            y.setTickLabelFormatter(new StringConverter<Number>() {
                @Override
                public String toString(Number object) {
                    if (object == null) {
                        return null;
                    }
                    return Util.humanizeBytes(object.longValue());
                }

                @Override
                public Number fromString(String string) {
                    return null;
                }
            });
        } else if (chartSpec.getFormat().equals("percent")) {
            y.setTickLabelFormatter(new StringConverter<Number>() {
                @Override
                public String toString(Number object) {
                    if (object == null) {
                        return null;
                    }
                    return (long) (object.doubleValue() * 100) + "%";
                }

                @Override
                public Number fromString(String string) {
                    return null;
                }
            });
        }
        LineChart<Long, Number> chart = new LineChart<>(x, y);
        int pointCount = 0;
        int i = 0;
        for (Series seriesSpec : chartSpec.getSeriesList()) {
            String metric = seriesSpec.getMetric();
            List<GaugeEntity> points;
            if (!full && Duration.between(after, before).abs().toDays() >= 1) {
                // "cleanest" way for now. Spring Data MongoDB needs date aggregation
                log.debug("Aggregating since duration is >1d and detailed mode is not enabled");
                BasicDBList andMatcher = new BasicDBList();
                andMatcher.add(new BasicDBObject(NAME, new BasicDBObject("$eq", metric)));
                andMatcher.add(new BasicDBObject(TIMESTAMP, new BasicDBObject("$gte", Date.from(after.toInstant()))));
                andMatcher.add(new BasicDBObject(TIMESTAMP, new BasicDBObject("$lte", Date.from(before.toInstant()))));
                BasicDBObject matcher = new BasicDBObject("$match", new BasicDBObject("$and", andMatcher));
                BasicDBObject dateToString = new BasicDBObject();
                dateToString.append("format", "%Y-%m-%dT%H:00:00.00Z");
                dateToString.append("date", "$" + TIMESTAMP);
                BasicDBObject grouperContents = new BasicDBObject();
                grouperContents.append("_id", new BasicDBObject("$dateToString", dateToString));
                grouperContents.append("count", new BasicDBObject("$sum", 1));
                grouperContents.append("average", new BasicDBObject("$avg", "$value"));
                BasicDBObject grouper = new BasicDBObject("$group", grouperContents);
                BasicDBObject sorter = new BasicDBObject("$sort", new BasicDBObject("_id", 1));
                AggregationOutput result = mongoTemplate.getCollection(GAUGES_COLLECTION)
                    .aggregate(Arrays.asList(matcher, grouper, sorter));
                // map: Instant -> count, average
                points = new ArrayList<>();
                for (DBObject object : result.results()) {
                    BasicDBObject obj = (BasicDBObject) object;
                    GaugeEntity entity = new GaugeEntity();
                    entity.setName(metric);
                    entity.setValue(obj.get("average"));
                    entity.setTimestamp(Date.from(Instant.parse((String) obj.get("_id"))));
                    points.add(entity);
                }
            } else {
                points = mongoTemplate.find(
                    query(where(NAME).is(metric)
                        .andOperator(
                            where(TIMESTAMP).gte(Date.from(after.toInstant())),
                            where(TIMESTAMP).lte(Date.from(before.toInstant()))
                        )), GaugeEntity.class, GAUGES_COLLECTION);
            }
            pointCount += points.size();
            if (points.isEmpty()) {
                log.debug("No data points from series {} (metric: {})",
                    seriesSpec.getName(), seriesSpec.getMetric());
                continue;
            }
            log.debug("Adding {} data points from series {} (metric: {})",
                points.size(), seriesSpec.getName(), seriesSpec.getMetric());
            XYChart.Series<Long, Number> series = new XYChart.Series<>();
            String title = seriesSpec.getTitle();
            if (title != null && !title.isEmpty()) {
                series.setName(title);
            }
            for (GaugeEntity data : points) {
                Object object = data.getValue();
                if (object != null && object instanceof Number) {
                    Number value = (Number) object;
                    long millis = data.getTimestamp().getTime();
                    series.getData().add(new XYChart.Data<>(millis, value));
                }
            }
            if (seriesSpec.isDrawLastValue()) {
                XYChart.Data<Long, Number> last = series.getData().get(series.getData().size() - 1);
                final Label label = new Label(last.getYValue().toString());
                label.getStyleClass().addAll("default-color" + (i++), "chart-line-symbol", "chart-series-line");
                label.setStyle("-fx-font-size: 9; -fx-font-weight: bold;");
                last.setNode(label);
            }
            chart.getData().add(series);
        }
        if (pointCount == 0) {
            log.warn("No data points to graph!");
            return null;
        }
        Optional.ofNullable(chartSpec.getTitle()).filter(s -> !s.isEmpty()).ifPresent(chart::setTitle);
        Optional.ofNullable(chartSpec.getXAxisLabel()).filter(s -> !s.isEmpty()).ifPresent(s -> chart.getXAxis().setLabel(s));
        Optional.ofNullable(chartSpec.getYAxisLabel()).filter(s -> !s.isEmpty()).ifPresent(s -> chart.getYAxis().setLabel(s));
        // bound check within reasonable limits
        width = Math.max(320, Math.min(1920, width));
        height = Math.max(180, Math.min(1080, height));
        chart.setPrefSize(width, height);
        chart.setCreateSymbols(chartSpec.isDrawSymbols());
        return chart;
    }

    private void takeSnapshot(Node node, Consumer<File> consumer) {
        Platform.runLater(() -> {
            VBox vbox = new VBox(node);
            Stage stage = new Stage(StageStyle.TRANSPARENT);
            Scene scene = new Scene(vbox);
            scene.setFill(null);
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

    private String manage(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(manageListSpec)) {
            List<Chart> list = chartRepository.findAll();
            if (list.isEmpty()) {
                return "No charts defined";
            } else {
                return "Available charts: " + list.toString();
            }
        }

        Optional<String> remove = Optional.ofNullable(optionSet.valueOf(manageRemoveSpec));
        if (remove.isPresent()) {
            Optional<Chart> chart = chartRepository.findByName(remove.get());
            if (chart.isPresent()) {
                chartRepository.delete(chart.get());
                log.debug("Removed chart: {}", remove.get());
                return "Chart '" + chart.get().getName() + "' removed";
            } else {
                return "No chart found by that name";
            }
        }

        Optional<String> add = Optional.ofNullable(optionSet.valueOf(manageAddSpec));
        if (add.isPresent()) {
            Optional<Chart> chart = chartRepository.findByName(add.get());
            if (chart.isPresent()) {
                return "A chart by that name already exists!";
            } else {
                Optional<Boolean> drawSymbols = optionSet.has(manageDrawSymbolsSpec) ?
                    Optional.ofNullable(optionSet.valueOf(manageDrawSymbolsSpec)) : Optional.empty();
                Optional<String> format = optionSet.has(manageFormatSpec) ?
                    Optional.ofNullable(optionSet.valueOf(manageFormatSpec)) : Optional.empty();
                Chart newChart = chartRepository.save(updateChart(new Chart(), add.get(),
                    Optional.ofNullable(optionSet.valueOf(manageTitleSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageXLabelSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageYLabelSpec)), drawSymbols, format,
                    optionSet.valuesOf(manageSeriesSpec)));
                log.debug("New chart: {}", newChart);
                return "New chart '" + newChart.getName() + "' created";
            }
        }

        Optional<String> edit = Optional.ofNullable(optionSet.valueOf(manageEditSpec));
        if (edit.isPresent()) {
            Optional<Chart> chart = chartRepository.findByName(edit.get());
            if (!chart.isPresent()) {
                return "No chart found by that name, create it first";
            } else {
                Optional<Boolean> drawSymbols = optionSet.has(manageDrawSymbolsSpec) ?
                    Optional.ofNullable(optionSet.valueOf(manageDrawSymbolsSpec)) : Optional.empty();
                Optional<String> format = optionSet.has(manageFormatSpec) ?
                    Optional.ofNullable(optionSet.valueOf(manageFormatSpec)) : Optional.empty();
                Chart updatedChart = chartRepository.save(updateChart(chart.get(), edit.get(),
                    Optional.ofNullable(optionSet.valueOf(manageTitleSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageXLabelSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageYLabelSpec)), drawSymbols, format,
                    optionSet.valuesOf(manageSeriesSpec)));
                log.debug("Updated chart: {}", updatedChart);
                return "Chart '" + updatedChart.getName() + "' updated";
            }
        }

        return "";
    }

    private Chart updateChart(Chart chart, String name, Optional<String> title,
                              Optional<String> xLabel, Optional<String> yLabel, Optional<Boolean> drawSymbols,
                              Optional<String> format,
                              List<String> seriesList) {
        chart.setName(name);
        if (title.isPresent()) {
            chart.setTitle(title.get());
        } else if (chart.getTitle() == null) {
            chart.setTitle(name);
        }
        if (xLabel.isPresent()) {
            chart.setXAxisLabel(xLabel.get());
        }
        if (yLabel.isPresent()) {
            chart.setYAxisLabel(yLabel.get());
        }
        if (drawSymbols.isPresent()) {
            chart.setDrawSymbols(drawSymbols.get());
        }
        if (format.isPresent()) {
            chart.setFormat(format.get());
        }
        if (!seriesList.isEmpty()) {
            chart.getSeriesList().clear();
            for (String seriesName : seriesList) {
                Optional<Series> series = seriesRepository.findByName(seriesName);
                if (series.isPresent()) {
                    chart.getSeriesList().add(series.get());
                }
            }
        }
        return chart;
    }

    private String series(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(seriesListSpec)) {
            List<Series> list = seriesRepository.findAll();
            if (list.isEmpty()) {
                return "No series defined";
            } else {
                return "Available series: " + list.toString();
            }
        }

        Optional<String> remove = Optional.ofNullable(optionSet.valueOf(seriesRemoveSpec));
        if (remove.isPresent()) {
            Optional<Series> series = seriesRepository.findByName(remove.get());
            if (series.isPresent()) {
                seriesRepository.delete(series.get());
                log.debug("Removed series: {}", remove.get());
                return "Series '" + series.get().getName() + "' removed";
            } else {
                return "No series found by that name";
            }
        }

        Optional<String> add = Optional.ofNullable(optionSet.valueOf(seriesAddSpec));
        if (add.isPresent()) {
            Optional<Series> series = seriesRepository.findByName(add.get());
            if (series.isPresent()) {
                return "A series by that name already exists!";
            } else {
                Optional<String> metric = Optional.ofNullable(optionSet.valueOf(seriesMetricSpec));
                if (!metric.isPresent()) {
                    return "You must define the metric bound to this series!";
                }
                Optional<Boolean> drawLast = optionSet.has(seriesDrawLastSpec) ?
                    Optional.ofNullable(optionSet.valueOf(seriesDrawLastSpec)) : Optional.empty();
                Series newSeries = seriesRepository.save(updateSeries(new Series(), add.get(), metric,
                    Optional.ofNullable(optionSet.valueOf(seriesTitleSpec)), drawLast));
                log.debug("New series: {}", newSeries);
                return "New series '" + newSeries.getName() + "' created";
            }
        }

        Optional<String> edit = Optional.ofNullable(optionSet.valueOf(seriesEditSpec));
        if (edit.isPresent()) {
            Optional<Series> series = seriesRepository.findByName(edit.get());
            if (!series.isPresent()) {
                return "No series found by that name, create it first";
            } else {
                Optional<Boolean> drawLast = optionSet.has(seriesDrawLastSpec) ?
                    Optional.ofNullable(optionSet.valueOf(seriesDrawLastSpec)) : Optional.empty();
                Series updatedSeries = seriesRepository.save(updateSeries(series.get(), edit.get(),
                    Optional.ofNullable(optionSet.valueOf(seriesMetricSpec)),
                    Optional.ofNullable(optionSet.valueOf(seriesTitleSpec)), drawLast));
                log.debug("Updated series: {}", updatedSeries);
                return "Series '" + updatedSeries.getName() + "' updated";
            }
        }

        return "";
    }

    private Series updateSeries(Series series, String name, Optional<String> metric, Optional<String> title,
                                Optional<Boolean> drawLast) {
        series.setName(name);
        if (metric.isPresent()) {
            series.setMetric(metric.get());
        }
        if (title.isPresent()) {
            series.setTitle(title.get());
        }
        if (drawLast.isPresent()) {
            series.setDrawLastValue(drawLast.get());
        }
        return series;
    }
}
