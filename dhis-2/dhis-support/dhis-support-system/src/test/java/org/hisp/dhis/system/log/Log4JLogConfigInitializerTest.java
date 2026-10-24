/*
 * Copyright (c) 2004-2026, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors 
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.system.log;

import static org.hisp.dhis.log.MdcKeys.MDC_REQUEST_ID;
import static org.hisp.dhis.log.MdcKeys.MDC_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

/**
 * @author Jason P. Pickering <jason@dhis2.org>
 */
class Log4JLogConfigInitializerTest {
  private static final PatternLayout LAYOUT =
      PatternLayout.newBuilder().withPattern(Log4JLogConfigInitializer.LOG_PATTERN).build();

  @Test
  void testLineWithoutRequestContextIsUnchanged() {
    assertEquals(
        " (DataValueController.java [tomcat-http-18])\n",
        format(Map.of()).replaceAll("^.*Saved", ""));
  }

  @Test
  void testRequestIdAndUserIdAreAppended() {
    String line =
        format(
            Map.of(MDC_REQUEST_ID, "18e4a760fb1faa95373747c03e000ccd", MDC_USER_ID, "xE7jOejl9FI"));

    assertEquals(
        " (DataValueController.java [tomcat-http-18]) rid=18e4a760fb1faa95373747c03e000ccd"
            + " uid=xE7jOejl9FI\n",
        line.replaceAll("^.*Saved", ""));
  }

  @Test
  void testOnlyPresentValuesAreAppended() {
    assertEquals(
        " (DataValueController.java [tomcat-http-18]) uid=xE7jOejl9FI\n",
        format(Map.of(MDC_USER_ID, "xE7jOejl9FI")).replaceAll("^.*Saved", ""));
  }

  @Test
  void testLinePrefixIsUnchanged() {
    assertEquals(
        "* INFO  ", format(Map.of(MDC_USER_ID, "xE7jOejl9FI")).substring(0, "* INFO  ".length()));
  }

  private static String format(Map<String, String> mdc) {
    var contextData = ContextDataFactory.createContextData();
    mdc.forEach(contextData::putValue);
    LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage("Saved"))
            .setThreadName("tomcat-http-18")
            .setSource(
                new StackTraceElement(
                    "org.hisp.dhis.webapi.controller.DataValueController",
                    "save",
                    "DataValueController.java",
                    42))
            .setContextData(contextData)
            .build();
    return LAYOUT.toSerializable(event);
  }
}
