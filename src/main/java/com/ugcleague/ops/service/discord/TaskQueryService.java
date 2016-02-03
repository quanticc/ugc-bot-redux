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
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@Service
@Transactional
public class TaskQueryService {

    private final TaskService taskService;
    private final CommandService commandService;

    private OptionSpec<String> nameSpec;
    private OptionSpec<Boolean> enableSpec;
    private OptionSpec<Void> listSpec;
    private OptionSpec<String> patternSpec;

    @Autowired
    public TaskQueryService(TaskService taskService, CommandService commandService) {
        this.taskService = taskService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        // .beep task -n <name> -r <rate> -e <true|false>
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        listSpec = parser.acceptsAll(asList("l", "list"), "list registered tasks");
        nameSpec = parser.acceptsAll(asList("n", "name"), "name of the task").requiredUnless(listSpec)
            .withRequiredArg().describedAs("taskName");
        enableSpec = parser.acceptsAll(asList("e", "enable"), "enable or disable the task").withOptionalArg()
            .ofType(Boolean.class).defaultsTo(true);
        patternSpec = parser.acceptsAll(asList("p", "pattern"), "cron pattern to set").requiredUnless(listSpec, enableSpec)
            .withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".beep task")
            .description("Configure bot scheduled tasks").permission("master")
            .parser(parser).command(this::executeTaskCommand).build());
    }

    private String executeTaskCommand(IMessage message, OptionSet o) {
        if (!o.has("?")) {
            if (o.has(listSpec)) {
                return buildTaskList();
            } else {
                boolean enable = o.valueOf(enableSpec);
                String name = o.valueOf(nameSpec);
                Optional<String> pattern = enable ? Optional.ofNullable(o.valueOf(patternSpec)) : Optional.empty();
                return updateTask(name, enable, pattern);
            }
        }
        return null;
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
