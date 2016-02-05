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

import static java.util.Arrays.asList;

@Service
public class MetricsQueryService {

    private static final Logger log = LoggerFactory.getLogger(MetricsQueryService.class);

    private final CommandService commandService;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    private Map<String, BiFunction<IMessage, OptionSet, String>> subCommandMap;
    private OptionSpec<String> jvmNonOptionSpec;
    private OptionSpec<String> metricsNonOptionSpec;

    @Autowired
    public MetricsQueryService(CommandService commandService, MetricRegistry metricRegistry,
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
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        String metricSetList = metricRegistry.getNames().stream().collect(Collectors.joining(", "));
        metricsNonOptionSpec = parser.nonOptions("Metric name: " + metricSetList).ofType(String.class);
        commandService.register(CommandBuilder.combined(".metrics").permission("master").parser(parser)
            .description("Show metrics about the application").command(this::metricsCommand).build());
    }

    private String metricsCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(metricsNonOptionSpec).stream()
            .map(String::toLowerCase).collect(Collectors.toSet());
        if (optionSet.has("?") || nonOptions.isEmpty()) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        for (String arg : nonOptions) {
            Counter counter = metricRegistry.getCounters().get(arg);
            Gauge gauge = metricRegistry.getGauges().get(arg);
            Histogram histogram = metricRegistry.getHistograms().get(arg);
            Meter meter = metricRegistry.getMeters().get(arg);
            Timer timer = metricRegistry.getTimers().get(arg);
            response.append("**").append(arg).append("**\n");
            if (counter != null) {
                response.append("count: ``").append(counter.getCount()).append("``\n");
            }
            if (gauge != null) {
                response.append("gauge value: ``").append(gauge.getValue()).append("``\n");
            }
            if (histogram != null) {
                Snapshot snapshot = histogram.getSnapshot();
                response.append("histogram snapshots: ``").append(snapshot.size()).append("`` ")
                    .append("min: ``").append(snapshot.getMin()).append("`` ")
                    .append("avg: ``").append(snapshot.getMean()).append("`` ")
                    .append("max: ``").append(snapshot.getMax()).append("`` ")
                    .append("stdDev: ``").append(snapshot.getStdDev()).append("``\n");
            }
            if (meter != null) {
                response.append("meter count: ``").append(meter.getCount()).append("`` ")
                    .append("mean: ``").append(meter.getMeanRate()).append("`` ")
                    .append("events/min: ``").append(meter.getOneMinuteRate()).append("``\n");
            }
            if (timer != null) {
                Snapshot snapshot = timer.getSnapshot();
                response.append("timer snapshots: ``").append(snapshot.size()).append("`` ")
                    .append("min: ``").append(snapshot.getMin()).append("`` ")
                    .append("avg: ``").append(snapshot.getMean()).append("`` ")
                    .append("max: ``").append(snapshot.getMax()).append("`` ")
                    .append("stdDev: ``").append(snapshot.getStdDev()).append("``\n")
                    .append("timer count: ``").append(timer.getCount()).append("`` ")
                    .append("mean: ``").append(timer.getMeanRate()).append("`` ")
                    .append("events/min: ``").append(timer.getOneMinuteRate()).append("``\n");
            }
        }
        return response.toString();
    }

    private void initHealthCommand() {
        commandService.register(CommandBuilder.equalsTo(".health").permission("master").queued()
            .description("Show health checks about the application").command(this::healthCheckCommand).build());
    }

    private String healthCheckCommand(IMessage message, OptionSet optionSet) {
        StringBuilder response = new StringBuilder();
        if (healthCheckRegistry.getNames().isEmpty()) {
            return "No health checks registered";
        }
        Map<String, HealthCheck.Result> resultMap = healthCheckRegistry.runHealthChecks();
        for (Map.Entry<String, HealthCheck.Result> entry : resultMap.entrySet()) {
            HealthCheck.Result result = entry.getValue();
            String msg = result.getMessage();
            Throwable t = result.getError();
            response.append("**").append(entry.getKey()).append("** ")
                .append(result.isHealthy() ? "OK" : "**ERROR**")
                .append(msg != null ? " with message: " + msg : "")
                .append(t != null ? " and exception: " + t.toString() : "").append("\n");
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
        commandService.register(CommandBuilder.combined(".jvm").permission("master").parser(parser)
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
