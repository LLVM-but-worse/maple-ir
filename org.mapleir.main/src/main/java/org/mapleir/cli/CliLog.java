package org.mapleir.cli;

import org.apache.log4j.Logger;
import org.mapleir.Boot;

import java.util.Deque;
import java.util.LinkedList;

public class CliLog {
    private final Logger LOGGER = Logger.getLogger(Boot.class);
    private long timer;
    private final Deque<String> sections = new LinkedList<>();

    private double lap() {
        long now = System.nanoTime();
        long delta = now - timer;
        timer = now;
        return (double)delta / 1_000_000_000L;
    }

    private void section0(String endText, String sectionText, boolean quiet) {
        if(sections.isEmpty()) {
            lap();
            if(!quiet)
                LOGGER.info(sectionText);
        } else {
            /* remove last section. */
            sections.pop();
            if(!quiet) {
                LOGGER.info(String.format(endText, lap()));
                LOGGER.info(sectionText);
            } else {
                lap();
            }
        }

        /* push the new one. */
        sections.push(sectionText);
    }

    public void section0(String endText, String sectionText) {
        if(sections.isEmpty()) {
            lap();
            LOGGER.info(sectionText);
        } else {
            /* remove last section. */
            sections.pop();
            LOGGER.info(String.format(endText, lap()));
            LOGGER.info(sectionText);
        }

        /* push the new one. */
        sections.push(sectionText);
    }

    public void section(String text) {
        section0("...took %fs.", text);
    }

    public void print(String text) {
        LOGGER.info(text);
    }
}
