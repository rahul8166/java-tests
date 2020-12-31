package com.tests.collections;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamsTerminalIntermediate {
    private static final Logger logger = LoggerFactory.getLogger(StreamsTerminalIntermediate.class);

    public static void main(String[] args) {
        Integer[] numbers = new Integer[] { 18, 28, 18, 38, 40, 40 };

        /* 
        -map/filter are intermediate operations which actually return a Stream so that other operations can be carried out on it.
        -sum/Collect/forEach are terminal operations which initiates the Streams processing.
        */

        logger.info("***Intermediate Operation without Terminal Operation: Start");
        Arrays.stream(numbers).map(i -> {
            logger.info("From Intermediate Operation {}", i);
            return i;
        });
        logger.info("***Intermediate Operation without Terminal Operation: End");

        logger.info("");
        logger.info("");

        logger.info("***Intermediate Operation with Terminal Operation: Start");
        Arrays.stream(numbers).map(i -> {
            logger.info("From Terminal Operation {}", i);
            return i;           
        }).collect(Collectors.toSet());
        logger.info("***Intermediate Operation with Terminal Operation: End");
    }
}
