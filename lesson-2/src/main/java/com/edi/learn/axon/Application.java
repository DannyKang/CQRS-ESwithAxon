package com.edi.learn.axon;


import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.slf4j.LoggerFactory.getLogger;


@SpringBootApplication
@ComponentScan(basePackages = {"com.edi.learn"})
public class Application {

    private static final Logger LOGGER = getLogger(Application.class);

    public static void main(String args[]){
        SpringApplication.run(Application.class, args);
    }
}
