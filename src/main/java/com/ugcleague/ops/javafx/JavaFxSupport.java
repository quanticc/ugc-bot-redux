package com.ugcleague.ops.javafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaFxSupport extends Application {

    private static final Logger log = LoggerFactory.getLogger(JavaFxSupport.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        Thread.currentThread().setName("JavaFXAppThread");
        log.debug("Started JavaFX runtime");
        Platform.setImplicitExit(false);
    }
}
