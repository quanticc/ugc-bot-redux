package com.ugcleague.ops.service.discord;

import com.codahale.metrics.MetricRegistry;
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

@Service
public class MetricsQueryService {

    private static final Logger log = LoggerFactory.getLogger(MetricsQueryService.class);

    private final CommandService commandService;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    private OptionSpec<String> nonOptionSpec;
    private Map<String, BiFunction<IMessage, OptionSet, String>> subCommandMap;
    private String subCommandKeys;

    @Autowired
    public MetricsQueryService(CommandService commandService, MetricRegistry metricRegistry,
                               HealthCheckRegistry healthCheckRegistry) {
        this.commandService = commandService;
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @PostConstruct
    private void configure() {
        subCommandMap = new LinkedHashMap<>();
        subCommandMap.put("system", this::jvmSystem);
        subCommandMap.put("runtime", this::jvmRuntime);
        subCommandMap.put("classloading", this::jvmClassloading);
        subCommandMap.put("compilation", this::jvmCompilation);
        subCommandMap.put("heap", this::jvmHeap);
        subCommandMap.put("nonheap", this::jvmNonHeap);
        subCommandMap.put("pools", this::jvmPools);
        subCommandKeys = subCommandMap.keySet().stream().collect(Collectors.joining(", "));
        OptionParser parser = new OptionParser();
        nonOptionSpec = parser.nonOptions("Requested information: " + subCommandKeys).ofType(String.class);
        commandService.register(CommandBuilder.combined(".jvm").permission("master").parser(parser)
            .description("Show information about the JVM").command(this::executeJvmCommand).build());
        commandService.register(CommandBuilder.combined(".metrics").permission("master")
            .description("Show metrics about the application").command(this::executeMetricsCommand).build());
    }

    private String executeMetricsCommand(IMessage message, OptionSet optionSet) {
        return metricRegistry.getNames().toString();
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

    private String jvmPools(IMessage message, OptionSet optionSet) {
        return "MemoryPools: " + ManagementFactory.getMemoryPoolMXBeans().stream()
            .map(MemoryPoolMXBean::getName).collect(Collectors.joining(", "));
    }

    private String executeJvmCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(nonOptionSpec).stream()
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
