package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.ScheduledTask;
import com.ugcleague.ops.service.TaskService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import it.sauronsoftware.cron4j.InvalidPatternException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * Commands to handle scheduled tasks
 * <ul>
 *     <li>task list</li>
 *     <li>task run</li>
 *     <li>task config</li>
 * </ul>
 */
@Service
@Transactional
public class TaskPresenter {

    private final TaskService taskService;
    private final CommandService commandService;

    private OptionSpec<String> nameSpec;
    private OptionSpec<Boolean> enableSpec;
    private OptionSpec<String> patternSpec;
    private OptionSpec<String> runNonOptionSpec;
    private OptionSpec<Integer> runDelaySpec;

    @Autowired
    public TaskPresenter(TaskService taskService, CommandService commandService) {
        this.taskService = taskService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        initRunCommand();
        initListCommand();
        initConfigCommand();
    }

    private void initListCommand() {
        commandService.register(CommandBuilder.equalsTo(".task list")
            .description("Display a list of scheduled tasks").permission("master").command(this::listCommand).build());
    }

    private String listCommand(IMessage message, OptionSet optionSet) {
        return buildTaskList();
    }

    private void initRunCommand() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        runNonOptionSpec = parser.nonOptions("Scheduled task names").ofType(String.class);
        runDelaySpec = parser.acceptsAll(asList("d", "delay"), "Time in milliseconds to delay start")
            .withRequiredArg().ofType(Integer.class);
        commandService.register(CommandBuilder.startsWith(".task run")
            .description("Run scheduled tasks now").permission("master")
            .parser(parser).command(this::runCommand).build());
    }

    private String runCommand(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(runNonOptionSpec);
        if (optionSet.has("?") || nonOptions.isEmpty()) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        for (String taskName : nonOptions) {
            Optional<ScheduledTask> task = taskService.findByName(taskName);
            if (task.isPresent()) {
                response.append("Scheduling task **").append(taskName).append("** to run\n");
                taskService.run(task.get(), optionSet.has(runDelaySpec) ? optionSet.valueOf(runDelaySpec) : 0L);
            } else {
                response.append("Task **").append(taskName).append("** does not exist\n");
            }
        }
        return response.toString();
    }

    private void initConfigCommand() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        nameSpec = parser.acceptsAll(asList("n", "name"), "name of the task")
            .withRequiredArg().describedAs("name").required();
        enableSpec = parser.acceptsAll(asList("e", "enable"), "enable or disable the task").withOptionalArg()
            .ofType(Boolean.class).defaultsTo(true);
        patternSpec = parser.acceptsAll(asList("p", "pattern"), "cron pattern to set").requiredUnless(enableSpec)
            .withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".task config")
            .description("Configure bot scheduled tasks").permission("master")
            .parser(parser).command(this::configCommand).build());
    }

    private String configCommand(IMessage message, OptionSet o) {
        if (o.has("?")) {
            return null;
        }
        boolean enable = o.valueOf(enableSpec);
        String name = o.valueOf(nameSpec);
        Optional<String> pattern = enable ? Optional.ofNullable(o.valueOf(patternSpec)) : Optional.empty();
        return updateTask(name, enable, pattern);
    }

    private String updateTask(String name, boolean enable, Optional<String> pattern) {
        Optional<ScheduledTask> o = taskService.findByName(name);
        if (o.isPresent()) {
            ScheduledTask task = o.get();
            task.setEnabled(enable);
            if (pattern.isPresent()) {
                task.setPattern(pattern.get());
            }
            try {
                task = taskService.reschedule(task);
                return task.humanString();
            } catch (InvalidPatternException e) {
                return "Invalid pattern: " + e.getMessage();
            }
        }
        return "No task found matching: *" + name + "*";
    }

    private String buildTaskList() {
        return "*Scheduled Tasks*\n" + taskService.findAll().stream().map(ScheduledTask::humanString).collect(Collectors.joining("\n"));
    }
}
