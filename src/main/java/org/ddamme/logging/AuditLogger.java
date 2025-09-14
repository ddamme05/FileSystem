package org.ddamme.logging;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class AuditLogger {

  private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

  private AuditLogger() {}

  public static void log(String action, Map<String, Object> attributes) {
    AUDIT.info(
        "audit",
        StructuredArguments.keyValue("action", action),
        StructuredArguments.entries(attributes));
  }
}
