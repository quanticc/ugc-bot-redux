package com.ugcleague.ops.service;

import com.ugcleague.ops.service.util.SystemOutputInterceptorClosure;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.ui.SystemOutputInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

    @PersistenceContext
    private final EntityManager entityManager;
    private final ApplicationContext context;
    private final DiscordService discordService;
    private final GameServerService gameServerService;
    private final PermissionService permissionService;

    @Autowired
    public ScriptService(ApplicationContext context, EntityManager entityManager, DiscordService discordService,
                         GameServerService gameServerService, PermissionService permissionService) {
        this.context = context;
        this.entityManager = entityManager;
        this.discordService = discordService;
        this.gameServerService = gameServerService;
        this.permissionService = permissionService;
    }

    public Map<String, Object> executeScript(String script) {
        log.info("Executing Script: " + script);
        try {
            return eval(script);
        } catch (Throwable t) {
            log.warn("Error while evaluating script", t);
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("error", t.getMessage());
            return resultMap;
        }
    }

    public Map<String, Object> eval(String script) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("script", script);
        resultMap.put("startTime", ZonedDateTime.now());

        SystemOutputInterceptorClosure outputCollector = new SystemOutputInterceptorClosure(null);
        SystemOutputInterceptor systemOutputInterceptor = new SystemOutputInterceptor(outputCollector);
        systemOutputInterceptor.start();

        try {
            Map<String, Object> bindingValues = new LinkedHashMap<>();
            resultMap.put("result", eval(script, bindingValues));
        } catch (Throwable t) {
            log.error("Could not bind result", t);
            resultMap.put("error", t.getMessage());
        } finally {
            systemOutputInterceptor.stop();
        }

        String output = outputCollector.getStringBuffer().toString().trim();
        StringBuilder builder = new StringBuilder();
        for (String line : output.split("\n")) {
            String[] tokens = line.split("\\[0;39m ");
            builder.append(tokens[tokens.length - 1]).append("\n");
        }
        resultMap.put("output", builder.toString());
        resultMap.put("endTime", ZonedDateTime.now());
        return resultMap;
    }

    public String eval(String script, Map<String, Object> bindingValues) {
        GroovyShell shell = createShell(bindingValues);
        Object result = shell.evaluate(script);
        String resultString = result != null ? result.toString() : "null";
        log.trace("eval() result: " + resultString);
        return resultString;
    }

    private GroovyShell createShell(Map<String, Object> bindingValues) {
        bindingValues.put("context", context);
        bindingValues.put("entityManager", entityManager);
        bindingValues.put("discord", discordService);
        bindingValues.put("gameServer", gameServerService);
        bindingValues.put("permission", permissionService);
        bindingValues.put("client", discordService.getClient());
        bindingValues.put("bot", discordService.getClient().getOurUser());
        return new GroovyShell(this.getClass().getClassLoader(), new Binding(bindingValues));
    }
}
