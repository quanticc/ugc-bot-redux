package com.ugcleague.ops.service;

import com.ugcleague.ops.util.DateUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CronUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(CronUtilsTest.class);

    @Test
    public void testSimpleDescription() {
        String patterns = "1-56/5 2-6,12-16,18-23 * * mon,wed,fri|1-31/30 0-1,7-11,17-19 * * mon,wed,fri|1-31/30 * * * tue,thu,sat,sun";
        String pattern = "1-56/5 2-6,12-16,18-23 * * mon,wed,fri";
        log.info("{}", DateUtil.humanizeCronPattern(pattern));
        log.info("{}", DateUtil.humanizeCronPatterns(patterns));
    }
}
