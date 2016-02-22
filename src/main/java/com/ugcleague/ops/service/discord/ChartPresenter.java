package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.Chart;
import com.ugcleague.ops.domain.document.Series;
import com.ugcleague.ops.repository.mongo.ChartRepository;
import com.ugcleague.ops.repository.mongo.SeriesRepository;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.DateAxis;
import com.ugcleague.ops.util.DateUtil;
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
import org.springframework.transaction.annotation.Transactional;
import se.maypril.metrics.entity.GaugeEntity;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private OptionSpec<String> chartGetSpec;
    private OptionSpec<String> chartSinceSpec;
    private OptionSpec<String> chartUntilSpec;
    private OptionSpec<Integer> chartWidthSpec;
    private OptionSpec<Integer> chartHeightSpec;
    private OptionSpec<String> manageAddSpec;
    private OptionSpec<String> manageEditSpec;
    private OptionSpec<String> manageRemoveSpec;
    private OptionSpec<String> seriesAddSpec;
    private OptionSpec<String> seriesEditSpec;
    private OptionSpec<String> seriesRemoveSpec;
    private OptionSpec<String> manageTitleSpec;
    private OptionSpec<String> manageSeriesSpec;
    private OptionSpec<String> seriesMetricSpec;
    private OptionSpec<String> manageXLabelSpec;
    private Command chartCommand;
    private OptionSpec<String> manageYLabelSpec;
    private OptionSpec<Void> chartListSpec;
    private OptionSpec<Void> seriesListSpec;
    private OptionSpec<Void> manageListSpec;
    private OptionSpec<String> seriesTitleSpec;

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
        Platform.setImplicitExit(false);
    }

    private void initChartCommand() {
        OptionParser parser = newParser();
        chartGetSpec = parser.acceptsAll(asList("g", "get"), "Chart name to display")
            .withRequiredArg();
        chartSinceSpec = parser.acceptsAll(asList("a", "after", "since"), "Only display metrics after this time-expression")
            .withRequiredArg().defaultsTo("an hour ago");
        chartUntilSpec = parser.acceptsAll(asList("b", "before", "until"), "Only display metrics before this time-expression")
            .withRequiredArg().defaultsTo("now");
        chartWidthSpec = parser.acceptsAll(asList("w", "width"), "Width of the chart")
            .withRequiredArg().ofType(Integer.class).defaultsTo(380).describedAs("px");
        chartHeightSpec = parser.acceptsAll(asList("h", "height"), "Height of the chart")
            .withRequiredArg().ofType(Integer.class).defaultsTo(230).describedAs("px");
        chartListSpec = parser.acceptsAll(asList("l", "list"), "Display a list of available charts");
        Map<String, String> aliases = new HashMap<>();
        aliases.put("get", "--get");
        aliases.put("since", "--since");
        aliases.put("until", "--until");
        aliases.put("width", "--width");
        aliases.put("height", "--height");
        aliases.put("list", "--list");
        chartCommand = commandService.register(CommandBuilder.startsWith(".chart")
            .description("Display a pre-defined chart").support().originReplies().queued()
            .parser(parser).withOptionAliases(aliases).command(this::chart).build());
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
        Map<String, String> aliases = new HashMap<>();
        aliases.put("add", "--add");
        aliases.put("edit", "--edit");
        aliases.put("remove", "--remove");
        aliases.put("title", "--title");
        aliases.put("series", "--series");
        aliases.put("x", "-x");
        aliases.put("y", "-y");
        aliases.put("list", "--list");
        commandService.register(CommandBuilder.startsWith(".beep chart")
            .description("Manage chart definitions").master().originReplies().queued()
            .parser(parser).withOptionAliases(aliases).command(this::manage).build());
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
        Map<String, String> aliases = new HashMap<>();
        aliases.put("add", "--add");
        aliases.put("edit", "--edit");
        aliases.put("remove", "--remove");
        aliases.put("metric", "--metric");
        aliases.put("title", "--title");
        aliases.put("list", "--list");
        commandService.register(CommandBuilder.startsWith(".beep series")
            .description("Manage chart series").master().originReplies().queued()
            .parser(parser).withOptionAliases(aliases).command(this::series).build());
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

        // .chart get <preset> [since <timex>] [until <timex>] [width <px>] [height <px>]
        String name = optionSet.valueOf(chartGetSpec);
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

        // snapshot the chart and send it as a file to the origin channel
        takeSnapshot(generateChart(chart.get(), since, until, width, height), file -> {
            try {
                commandService.fileReplyFrom(message, chartCommand, file);
            } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
                log.warn("Could not send file", e);
            }
        });

        return "";
    }

    private Node generateChart(Chart chartSpec, String since, String until, int width, int height) {
        ZonedDateTime after = Optional.ofNullable(DateUtil.parseTimeDate(since)).orElse(ZonedDateTime.now().minusHours(1));
        ZonedDateTime before = Optional.ofNullable(DateUtil.parseTimeDate(until)).orElse(ZonedDateTime.now());
        log.debug("Generating '{}' ({}x{}) from {} to {}", chartSpec.getName(), width, height, after, before);
        DateAxis x = new DateAxis();
        NumberAxis y = new NumberAxis();
        x.setForceZeroInRange(false);
        x.setMinorTickCount(2);
        x.setAnimated(false);
        y.setAnimated(false);
        LineChart<Long, Number> chart = new LineChart<>(x, y);
        for (Series seriesSpec : chartSpec.getSeriesList()) {
            List<GaugeEntity> points = mongoTemplate.find(
                query(where(NAME).is(seriesSpec.getName())
                    .and(TIMESTAMP).gte(Date.from(after.toInstant()))
                    .and(TIMESTAMP).lte(Date.from(before.toInstant()))), GaugeEntity.class, GAUGES_COLLECTION);
            log.debug("Adding {} data points from series {} (metric: {})",
                points.size(), seriesSpec.getName(), seriesSpec.getMetric());
            XYChart.Series<Long, Number> series = new XYChart.Series<>();
            String title = seriesSpec.getTitle();
            if (title != null && !title.isEmpty()) {
                series.setName(title);
            }
            for (GaugeEntity data : points) {
                if (data.getValue() != null) {
                    long value = (long) data.getValue();
                    long millis = data.getTimestamp().getTime();
                    series.getData().add(new XYChart.Data<>(millis, value));
                }
            }
            chart.getData().add(series);
        }
        Optional.ofNullable(chartSpec.getName()).filter(s -> !s.isEmpty()).ifPresent(chart::setTitle);
        Optional.ofNullable(chartSpec.getXAxisLabel()).filter(s -> !s.isEmpty()).ifPresent(s -> chart.getXAxis().setLabel(s));
        Optional.ofNullable(chartSpec.getYAxisLabel()).filter(s -> !s.isEmpty()).ifPresent(s -> chart.getYAxis().setLabel(s));
        // bound check within reasonable limits
        width = Math.max(320, Math.min(1366, width));
        height = Math.max(180, Math.min(768, height));
        chart.setPrefSize(width, height);
        return chart;
    }

    private void takeSnapshot(Node node, Consumer<File> consumer) {
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

    private String manage(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(manageListSpec)) {
            List<Chart> list = chartRepository.findAll();
            if (list.isEmpty()) {
                return "No charts defined";
            } else {
                return "Available charts: " + list.stream()
                    .map(Chart::toString).collect(Collectors.joining(", "));
            }
        }

        Optional<String> remove = Optional.ofNullable(optionSet.valueOf(manageRemoveSpec));
        if (remove.isPresent()) {
            Optional<Chart> chart = chartRepository.findByName(remove.get());
            if (chart.isPresent()) {
                chartRepository.delete(chart.get());
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
                Chart newChart = chartRepository.save(updateChart(new Chart(), add.get(),
                    Optional.ofNullable(optionSet.valueOf(manageTitleSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageXLabelSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageYLabelSpec)),
                    optionSet.valuesOf(manageSeriesSpec)));
                return "New chart '" + newChart.getName() + "' created";
            }
        }

        Optional<String> edit = Optional.ofNullable(optionSet.valueOf(manageEditSpec));
        if (edit.isPresent()) {
            Optional<Chart> chart = chartRepository.findByName(edit.get());
            if (!chart.isPresent()) {
                return "No chart found by that name, create it first";
            } else {
                Chart updatedChart = chartRepository.save(updateChart(chart.get(), edit.get(),
                    Optional.ofNullable(optionSet.valueOf(manageTitleSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageXLabelSpec)),
                    Optional.ofNullable(optionSet.valueOf(manageYLabelSpec)),
                    optionSet.valuesOf(manageSeriesSpec)));
                return "Chart '" + updatedChart.getName() + "' updated";
            }
        }

        return "";
    }

    private Chart updateChart(Chart chart, String name, Optional<String> title,
                              Optional<String> xLabel, Optional<String> yLabel, List<String> seriesList) {
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
                return "Available series: " + list.stream()
                    .map(s -> s.getName() + " (" + s.getMetric() + ")").collect(Collectors.joining(", "));
            }
        }

        Optional<String> remove = Optional.ofNullable(optionSet.valueOf(seriesRemoveSpec));
        if (remove.isPresent()) {
            Optional<Series> series = seriesRepository.findByName(remove.get());
            if (series.isPresent()) {
                seriesRepository.delete(series.get());
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
                Series newSeries = seriesRepository.save(updateSeries(new Series(), add.get(), metric.get(),
                    Optional.ofNullable(optionSet.valueOf(seriesTitleSpec))));
                return "New series '" + newSeries.getName() + "' created";
            }
        }

        Optional<String> edit = Optional.ofNullable(optionSet.valueOf(seriesEditSpec));
        if (edit.isPresent()) {
            Optional<Series> series = seriesRepository.findByName(edit.get());
            if (!series.isPresent()) {
                return "No series found by that name, create it first";
            } else {
                Optional<String> metric = Optional.ofNullable(optionSet.valueOf(seriesMetricSpec));
                if (!metric.isPresent()) {
                    return "No changes done if metric is not changing";
                }
                Series updatedSeries = seriesRepository.save(updateSeries(series.get(), edit.get(), metric.get(),
                    Optional.ofNullable(optionSet.valueOf(seriesTitleSpec))));
                return "Series '" + updatedSeries.getName() + "' updated";
            }
        }

        return "";
    }

    private Series updateSeries(Series series, String name, String metric, Optional<String> title) {
        series.setName(name);
        series.setMetric(metric);
        if (title.isPresent()) {
            series.setTitle(title.get());
        }
        return series;
    }
}
