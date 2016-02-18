package com.ugcleague.ops.service.discord;

import com.codahale.metrics.*;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;

/**
 * Commands to retrieve info about application and external services metrics.
 * <ul>
 * <li>jvm</li>
 * <li>metrics</li>
 * <li>health</li>
 * </ul>
 */
@Service
public class MetricsPresenter {

    private static final Logger log = LoggerFactory.getLogger(MetricsPresenter.class);

    private final CommandService commandService;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    private Map<String, BiFunction<IMessage, OptionSet, String>> subCommandMap;
    private OptionSpec<String> jvmNonOptionSpec;
    private OptionSpec<String> metricsNonOptionSpec;

    @Autowired
    public MetricsPresenter(CommandService commandService, MetricRegistry metricRegistry,
                            HealthCheckRegistry healthCheckRegistry) {
        this.commandService = commandService;
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @PostConstruct
    private void configure() {
        initJvmCommand();
        initHealthCommand();
        initMetricsCommand();
    }

    private void initMetricsCommand() {
        OptionParser parser = newParser();
        metricsNonOptionSpec = parser.nonOptions("Metric, list of metrics or metric types to display. " +
            "For instance: \"jvm\" would match all metrics starting with that key. If you enter a metric type " +
            "(meter, counter, timer, gauge, histogram) you will get a list of possible metrics of that kind.").ofType(String.class);
        commandService.register(CommandBuilder.anyMatch(".metrics").master().originReplies().mention().parser(parser)
            .description("Show metrics about the application").command(this::metricsCommand).build());
    }

    private String metricsCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(metricsNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?") || nonOptions.isEmpty()) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        for (String arg : nonOptions) {
            switch (arg) {
                case "meter":
                    response.append("*List of meters:* ")
                        .append(metricRegistry.getMeters().keySet().stream().collect(Collectors.joining(", "))).append("\n");
                    break;
                case "counter":
                    response.append("*List of counters:* ")
                        .append(metricRegistry.getCounters().keySet().stream().collect(Collectors.joining(", "))).append("\n");
                    break;
                case "timer":
                    response.append("*List of timers:* ")
                        .append(metricRegistry.getTimers().keySet().stream().collect(Collectors.joining(", "))).append("\n");
                    break;
                case "gauge":
                    response.append("*List of gauges:* ")
                        .append(metricRegistry.getGauges().keySet().stream().collect(Collectors.joining(", "))).append("\n");
                    break;
                case "histogram":
                    response.append("*List of histograms:* ")
                        .append(metricRegistry.getHistograms().keySet().stream().collect(Collectors.joining(", "))).append("\n");
                    break;
                default:
                    metricRegistry.getMeters().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(arg))
                        .map(e -> String.format("[m] **%s** %s\n", e.getKey(), formatMeter(e.getValue())))
                        .forEach(response::append);
                    metricRegistry.getCounters().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(arg))
                        .map(e -> String.format("[c] **%s** %s\n", e.getKey(), formatCounter(e.getValue())))
                        .forEach(response::append);
                    metricRegistry.getTimers().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(arg))
                        .map(e -> String.format("[t] **%s** %s\n", e.getKey(), formatTimer(e.getValue())))
                        .forEach(response::append);
                    metricRegistry.getGauges().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(arg))
                        .map(e -> String.format("[g] **%s** %s\n", e.getKey(), formatGauge(e.getValue())))
                        .forEach(response::append);
                    metricRegistry.getHistograms().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(arg))
                        .map(e -> String.format("[h] **%s** %s\n", e.getKey(), formatHistogram(e.getValue())))
                        .forEach(response::append);
                    break;
            }
        }
        return response.toString();
    }

    private String formatMeter(Meter meter) {
        return String.format("count: `%s` mean: `%s` events/min: `%s`",
            meter.getCount(), meter.getMeanRate(), meter.getOneMinuteRate());
    }

    private String formatCounter(Counter counter) {
        return String.format("count: `%s`\n", counter.getCount());
    }

    private String formatTimer(Timer timer) {
        Snapshot snapshot = timer.getSnapshot();
        return String.format("snapshots: `%s` min: `%s` avg: `%s` max: `%s` stdDev: `%s` -- count: `%s` mean: `%s` events/min: `%s`",
            snapshot.size(), snapshot.getMin(), snapshot.getMean(), snapshot.getMax(), snapshot.getStdDev(),
            timer.getCount(), timer.getMeanRate(), timer.getOneMinuteRate());
    }

    private String formatGauge(Gauge gauge) {
        return String.format("value: `%s`", gauge.getValue());
    }

    private String formatHistogram(Histogram histogram) {
        Snapshot snapshot = histogram.getSnapshot();
        return String.format("snapshots: `%s` min: `%s` avg: `%s` max: `%s` stdDev: `%s` -- count: `%s`",
            snapshot.size(), snapshot.getMin(), snapshot.getMean(), snapshot.getMax(), snapshot.getStdDev(),
            histogram.getCount());
    }

    private void initHealthCommand() {
        commandService.register(CommandBuilder.equalsTo(".health").support().permissionReplies().mention().queued()
            .description("Show health checks about the application").command(this::healthCheckCommand).build());
    }

    private String healthCheckCommand(IMessage message, OptionSet optionSet) {
        StringBuilder response = new StringBuilder();
        if (healthCheckRegistry.getNames().isEmpty()) {
            return "No health checks registered";
        }
        Map<String, HealthCheck.Result> resultMap = healthCheckRegistry.runHealthChecks();
        response.append("*Health check results*\n");
        for (Map.Entry<String, HealthCheck.Result> entry : resultMap.entrySet()) {
            HealthCheck.Result result = entry.getValue();
            String msg = result.getMessage();
            Throwable t = result.getError();
            response.append(result.isHealthy() ? "[Healthy]" : "[Caution]")
                .append(" **").append(entry.getKey()).append("** ")
                .append(msg != null ? msg : "")
                .append(t != null ? " and exception: *" + t.getMessage() + "*" : "").append("\n");
        }
        return response.toString();
    }

    private void initJvmCommand() {
        subCommandMap = new LinkedHashMap<>();
        subCommandMap.put("system", this::jvmSystem);
        subCommandMap.put("runtime", this::jvmRuntime);
        subCommandMap.put("classloading", this::jvmClassloading);
        subCommandMap.put("compilation", this::jvmCompilation);
        subCommandMap.put("heap", this::jvmHeap);
        subCommandMap.put("nonheap", this::jvmNonHeap);
        String subCommandKeys = subCommandMap.keySet().stream().collect(Collectors.joining(", "));
        OptionParser parser = new OptionParser();
        jvmNonOptionSpec = parser.nonOptions("Requested information: " + subCommandKeys).ofType(String.class);
        commandService.register(CommandBuilder.anyMatch(".jvm").master().parser(parser)
            .description("Show information about the JVM").command(this::executeJvmCommand).build());
    }

    private String jvmRuntime(IMessage message, OptionSet optionSet) {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        return String.format("Name: `%s`, SpecName: `%s`, SpecVendor: `%s`, ManagementSpecVersion: `%s`\n",
            rt.getName(), rt.getSpecName(), rt.getSpecVendor(), rt.getManagementSpecVersion());
    }

    private String jvmSystem(IMessage message, OptionSet optionSet) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        return String.format("Architecture: `%s`, Name: `%s`, Version: `%s`, Processors: `%d`\n",
            os.getArch(), os.getName(), os.getVersion(), os.getAvailableProcessors());
    }

    private String jvmClassloading(IMessage message, OptionSet optionSet) {
        ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
        return String.format("isVerbose: `%s`, LoadedClassCount: `%s`, TotalLoadedClassCount: `%s`, UnloadedClassCount: `%d`\n",
            cl.isVerbose(), cl.getLoadedClassCount(), cl.getTotalLoadedClassCount(), cl.getUnloadedClassCount());
    }

    private String jvmCompilation(IMessage message, OptionSet optionSet) {
        CompilationMXBean comp = ManagementFactory.getCompilationMXBean();
        return String.format("TotalCompilationTime: `%s`\n", comp.getTotalCompilationTime());
    }

    private String jvmHeap(IMessage message, OptionSet optionSet) {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        return String.format("Heap: `%s`\n", mem.getHeapMemoryUsage());
    }

    private String jvmNonHeap(IMessage message, OptionSet optionSet) {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        return String.format("NonHeap: `%s`\n", mem.getNonHeapMemoryUsage());
    }

    private String executeJvmCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(jvmNonOptionSpec).stream()
            .map(String::toLowerCase).collect(Collectors.toSet());
        if (nonOptions.isEmpty()) {
            nonOptions = subCommandMap.keySet();
        }
        StringBuilder response = new StringBuilder();
        for (String arg : nonOptions) {
            BiFunction<IMessage, OptionSet, String> subCommand = subCommandMap.get(arg);
            if (subCommand != null) {
                String subResponse = subCommand.apply(message, optionSet);
                if (subResponse != null) {
                    response.append(subResponse);
                }
            }
        }
        return response.toString();
    }
}
