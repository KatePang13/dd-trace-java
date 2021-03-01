package datadog.smoketest

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

/**
 * Each smoketest application is expected to log four lines to the log file:
 * - BEFORE FIRST SPAN
 * - INSIDE FIRST SPAN
 * - AFTER FIRST SPAN
 * - INSIDE SECOND SPAN
 *
 * Additional, each application prints to std out:
 * FIRSTTRACEID TRACEID SPANID
 * SECONDTRACEID TRACEID SPANID
 */
abstract class LogInjectionSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation, plus request, plus some extra
  static final int TIMEOUT_SECS = 60

  @Shared
  File outputLogFile

  @Shared
  File outputJsonLogFile

  def jsonAdapter = new Moshi.Builder().build().adapter(Types.newParameterizedType(Map, String, Object))

  @Override
  ProcessBuilder createProcessBuilder() {
    def loggingJar = buildDirectory + "/libs/" + getClass().simpleName + ".jar"

    assert new File(loggingJar).isFile()

    outputLogFile = File.createTempFile("logTest", ".log")
    outputJsonLogFile = File.createTempFile("logTest", ".log")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.add("-Ddd.test.logfile=${outputLogFile.absolutePath}" as String)
    command.add("-Ddd.test.jsonlogfile=${outputJsonLogFile.absolutePath}" as String)
    command.addAll(additionalArguments())
    command.addAll((String[]) ["-jar", loggingJar])

    println  "COMMANDS: " + command.join(" ")
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))

    return processBuilder
  }

  List additionalArguments() {
    return []
  }

  def injectsRawLogs() {
    return true
  }

  def supportsJson() {
    return true
  }

  abstract backend()

  def cleanupSpec() {
    outputLogFile?.delete()
    outputJsonLogFile?.delete()
  }

  def assertRawLogLinesWithoutInjection(List<String> logLines, String firstTraceId, String firstSpanId, String secondTraceId, String secondSpanId) {
    // Assert log line starts with backend name.
    // This avoids tests inadvertantly passing because the incorrect backend is logging
    logLines.every { it.startsWith(backend())}
    assert logLines.size() == 4
    assert logLines[0].endsWith("- BEFORE FIRST SPAN")
    assert logLines[1].endsWith("- INSIDE FIRST SPAN")
    assert logLines[2].endsWith("- AFTER FIRST SPAN")
    assert logLines[3].endsWith("- INSIDE SECOND SPAN")

    return true
  }

  def assertRawLogLinesWithInjection(List<String> logLines, String firstTraceId, String firstSpanId, String secondTraceId, String secondSpanId) {
    // Assert log line starts with backend name.
    // This avoids tests inadvertantly passing because the incorrect backend is logging
    logLines.every { it.startsWith(backend())}
    assert logLines.size() == 4
    assert logLines[0].endsWith("- ${SERVICE_NAME} ${ENV} ${VERSION}   - BEFORE FIRST SPAN") || logLines[0].endsWith("- ${SERVICE_NAME} ${ENV} ${VERSION} 0 0 - BEFORE FIRST SPAN")
    assert logLines[1].endsWith("- ${SERVICE_NAME} ${ENV} ${VERSION} ${firstTraceId} ${firstSpanId} - INSIDE FIRST SPAN")
    assert logLines[2].endsWith("- ${SERVICE_NAME} ${ENV} ${VERSION}   - AFTER FIRST SPAN") || logLines[2].endsWith("- ${SERVICE_NAME} ${ENV} ${VERSION} 0 0 - AFTER FIRST SPAN")
    assert logLines[3].endsWith("- ${SERVICE_NAME} ${ENV} ${VERSION} ${secondTraceId} ${secondSpanId} - INSIDE SECOND SPAN")

    return true
  }

  def assertJsonLinesWithInjection(List<String> rawLines, String firstTraceId, String firstSpanId, String secondTraceId, String secondSpanId) {
    def logLines = rawLines.collect { println it; jsonAdapter.fromJson(it) as Map}

    assert logLines.size() == 4

    assert logLines.every { it["backend"] == backend() }
    assert logLines.every { getFromContext(it, "dd.service") == SERVICE_NAME }
    assert logLines.every { getFromContext(it,"dd.version") == VERSION }
    assert logLines.every { getFromContext(it,"dd.env") == ENV }

    assert getFromContext(logLines[0],"dd.trace_id") == null
    assert getFromContext(logLines[0],"dd.span_id") == null
    assert logLines[0]["message"] == "BEFORE FIRST SPAN"

    assert getFromContext(logLines[1], "dd.trace_id") == firstTraceId
    assert getFromContext(logLines[1], "dd.span_id") == firstSpanId
    assert logLines[1]["message"] == "INSIDE FIRST SPAN"

    assert getFromContext(logLines[2],"dd.trace_id") == null
    assert getFromContext(logLines[2],"dd.span_id") == null
    assert logLines[2]["message"] == "AFTER FIRST SPAN"

    assert getFromContext(logLines[3], "dd.trace_id") == secondTraceId
    assert getFromContext(logLines[3], "dd.span_id")  == secondSpanId
    assert logLines[3]["message"] == "INSIDE SECOND SPAN"

    return true
  }

  def getFromContext(Map logEvent, String key) {
    if (logEvent["contextMap"] != null) {
      return logEvent["contextMap"][key]
    }

    return logEvent[key]
  }

  def parseTraceFromStdOut( String line ) {
    if (line == null) {
      throw new IllegalArgumentException("Line is null")
    }
    // there's a race with stdout where lines get combined
    // this fixes that
    def startOfMangle = line.indexOf("[")
    def unmangled = startOfMangle != -1 ? line.substring(0, startOfMangle) : line

    return unmangled.split(" ")[1..2]
  }

  // TODO: once java7 support is dropped use waitFor(timeout)
  @Timeout(value = TIMEOUT_SECS, unit = TimeUnit.SECONDS)
  def "check raw file injection"() {
    when:
    def exitValue = testedProcess.waitFor()
    def count = waitForTraceCount(2)

    def logLines = outputLogFile.readLines()
    println "log lines: " + logLines

    def jsonLogLines = outputJsonLogFile.readLines()
    println "json log lines: " + jsonLogLines

    def stdOutLines = new File(logFilePath).readLines()
    def (String firstTraceId, String firstSpanId) = parseTraceFromStdOut(stdOutLines.find { it.startsWith("FIRSTTRACEID")})
    def (String secondTraceId, String secondSpanId) = parseTraceFromStdOut(stdOutLines.find { it.startsWith("SECONDTRACEID")})

    then:
    exitValue == 0
    count == 2
    firstTraceId && firstTraceId != "0"
    firstSpanId && firstSpanId != "0"
    secondTraceId && secondTraceId != "0"
    secondSpanId && secondSpanId != "0"

    if (injectsRawLogs()) {
      assertRawLogLinesWithInjection(logLines, firstTraceId, firstSpanId, secondTraceId, secondSpanId)
    } else {
      assertRawLogLinesWithoutInjection(logLines, firstTraceId, firstSpanId, secondTraceId, secondSpanId)
    }

    if (supportsJson()) {
      assertJsonLinesWithInjection(jsonLogLines, firstTraceId, firstSpanId, secondTraceId, secondSpanId)
    }
  }
}

abstract class JULBackend extends LogInjectionSmokeTest {
  @Shared
  def propertiesFile = File.createTempFile("julConfig", ".properties")

  def backend() { "JUL" }

  def injectsRawLogs() { false }
  def supportsJson() { false }

  def setupSpec() {
    // JUL doesn't support reading a properties file from the classpath so everything needs
    // to be specified in a temp file
    propertiesFile.withPrintWriter {
      it.println ".level=INFO"
      it.println "handlers=java.util.logging.FileHandler"
      it.println "java.util.logging.FileHandler.pattern=${outputLogFile.absolutePath}"
      it.println "java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter"
      it.println "java.util.logging.SimpleFormatter.format=JUL:%1\$tF %1\$tT [%4\$-7s] - %5\$s%n"
    }
  }

  def cleanupSpec() {
    propertiesFile?.delete()
  }

  List additionalArguments() {
    return ["-Djava.util.logging.config.file=${propertiesFile.absolutePath}" as String]
  }
}

class JULInterfaceJULBackend extends JULBackend {
}

class JULInterfaceLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }

  List additionalArguments() {
    return ["-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"]
  }
}

class JCLInterfaceJULBackend extends JULBackend {
  def backend() { "JUL" }
}

class JCLInterfaceLog4j1Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j1" }
  def supportsJson() { false }
}

class JCLInterfaceLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }
}

class Log4j1InterfaceLog4j1Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j1" }
  def supportsJson() { false }
}

class Log4j1InterfaceLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }
}

class Log4j2InterfaceLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }
}

class Slf4jInterfaceLogbackBackend extends LogInjectionSmokeTest {
  def backend() { "Logback" }
}

class Slf4jInterfaceLog4j1Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j1" }
  def supportsJson() { false }
}

class Slf4jInterfaceLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }
}

class Slf4jInterfaceSlf4jSimpleBackend extends LogInjectionSmokeTest {
  def backend() { "Slf4jSimple" }
  def injectsRawLogs() { false }
  def supportsJson() { false }

  List additionalArguments() {
    return ["-Dorg.slf4j.simpleLogger.logFile=${outputLogFile.absolutePath}" as String]
  }
}

class Slf4jInterfaceJULBackend extends JULBackend {
}

class Slf4jInterfaceJCLToLog4j1Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j1" }
  def supportsJson() { false }
}

class Slf4jInterfaceJCLToLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }
}

class JULInterfaceSlf4jToLogbackBackend extends LogInjectionSmokeTest {
  @Shared
  def propertiesFile = File.createTempFile("julConfig", ".properties")

  def backend() { "Logback" }

  def setupSpec() {
    // JUL doesn't support reading a properties file from the classpath so everything needs
    // to be specified in a temp file
    propertiesFile.withPrintWriter {
      it.println ".level=INFO"
      it.println "handlers=org.slf4j.bridge.SLF4JBridgeHandler"
    }
  }

  def cleanupSpec() {
    propertiesFile?.delete()
  }

  List additionalArguments() {
    return ["-Djava.util.logging.config.file=${propertiesFile.absolutePath}" as String]
  }
}

class JCLInterfaceSlf4jToLogbackBackend extends LogInjectionSmokeTest {
  def backend() { "Logback" }
}

class Log4j1InterfaceSlf4jToLogbackBackend extends LogInjectionSmokeTest {
  def backend() { "Logback" }
}

class Log4j2InterfaceSlf4jToLogbackBackend extends LogInjectionSmokeTest {
  def backend() { "Logback" }
}

class JBossInterfaceJBossBackend extends LogInjectionSmokeTest {
  def backend() { "JBoss" }
  def supportsJson() { false }

  List additionalArguments() {
    return ["-Djava.util.logging.manager=org.jboss.logmanager.LogManager"]
  }
}

class JBossInterfaceLog4j1Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j1" }
  def supportsJson() { false }
}

class JBossInterfaceLog4j2Backend extends LogInjectionSmokeTest {
  def backend() { "Log4j2" }
}

class JBossInterfaceSlf4jToLogbackBackend extends LogInjectionSmokeTest {
  def backend() { "Logback" }
}

class JBossInterfaceJULBackend extends JULBackend {}

class FloggerInterfaceJULBackend extends JULBackend {}

class FloggerInterfaceSlf4jToLogbackBackend extends LogInjectionSmokeTest {
  def backend() { "Logback" }

  List additionalArguments() {
    return ["-Dflogger.backend_factory=com.google.common.flogger.backend.slf4j.Slf4jBackendFactory#getInstance"]
  }
}
