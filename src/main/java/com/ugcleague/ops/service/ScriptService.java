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

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

    private final ApplicationContext context;

    @Autowired
    public ScriptService(ApplicationContext context) {
        this.context = context;
    }

    public Map<String, Object> executeScript(String script) {
        log.warn("Executing Script: " + script);
        try {
            return eval(script);
        } catch (Throwable t) {
            log.error("Error while evaluating script", t);
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("error", t.getMessage());
            return resultMap;
        }
    }

    protected Map<String, Object> eval(String script) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("script", script);
        resultMap.put("startTime", ZonedDateTime.now().toString());

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

        resultMap.put("output", outputCollector.getStringBuffer().toString().trim());
        resultMap.put("endTime", ZonedDateTime.now().toString());
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
        bindingValues.put("log", log);
        return new GroovyShell(this.getClass().getClassLoader(), new Binding(bindingValues));
    }
}
