package com.tests;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateAndDistinct {
    private static final Logger logger = LoggerFactory.getLogger(DuplicateAndDistinct.class);

    public static void main(String[] args) {
        Integer[] numbers = new Integer[] { 18, 28, 18, 38, 40, 40 };
        List<Integer> numbersList = Arrays.asList(numbers);

        logger.info("Original : {}", Arrays.toString(numbers));

        logger.info("Duplicate : {}", findDuplicate(numbers));
        logger.info("Duplicate : {}", findDuplicate(numbersList));

        logger.info("Distinct : {}", findDistinct(numbers));
        logger.info("Distinct : {}", findDistinct(numbersList));
    }

    private static Set<Integer> findDuplicate(Integer[] numbers) {
        Set<Integer> allItems = new HashSet<>();
        return Arrays.stream(numbers).filter(n -> !allItems.add(n)).collect(Collectors.toSet());
    }

    private static Set<Integer> findDistinct(Integer[] numbers) {
        Set<Integer> allItems = new HashSet<>();
        return Arrays.stream(numbers).filter(n -> allItems.add(n)).collect(Collectors.toSet());
    }

    private static Set<Integer> findDuplicate(List<Integer> numbers) {
        return numbers.stream().filter(i -> Collections.frequency(numbers, i) > 1).collect(Collectors.toSet());
    }

    private static Set<Integer> findDistinct(List<Integer> numbers) {
        return numbers.stream().distinct().collect(Collectors.toSet());
    }

}
