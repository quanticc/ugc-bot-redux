package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.ScriptService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.util.Map;

@Service
public class ScriptCommand {

    private static final Logger log = LoggerFactory.getLogger(ScriptCommand.class);

    private final CommandService commandService;
    private final ScriptService scriptService;

    @Autowired
    public ScriptCommand(CommandService commandService, ScriptService scriptService) {
        this.commandService = commandService;
        this.scriptService = scriptService;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.startsWith(".eval").description("Runs a script")
            .master().originReplies().queued().noParser().command(this::eval).build());
    }

    private String eval(IMessage message, OptionSet optionSet) {
        String script = message.getContent().split(" ", 2)[1];
        script = script.replace("`", "");
        Map<String, Object> result = scriptService.executeScript(script, message);
        log.debug("eval result: {}", result);
        if (result.containsKey("error")) {
            return "```\n" + result.get("error") + "\n```";
        } else {
            return "" + result.get("result");
        }
    }
}
