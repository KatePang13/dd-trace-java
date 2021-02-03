package datadog.trace.instrumentation.axway;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.axway.AxwayHTTPPluginDecorator.DECORATE;
import static datadog.trace.instrumentation.axway.AxwayHTTPPluginDecorator.HOST;
import static datadog.trace.instrumentation.axway.AxwayHTTPPluginDecorator.METHOD;
import static datadog.trace.instrumentation.axway.AxwayHTTPPluginDecorator.PORT;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;

public class StateAdvice {
  public static final Logger log = org.slf4j.LoggerFactory.getLogger(StateAdvice.class);

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.This final Object stateInstance) {
    final AgentSpan span = startSpan("axway.trytransaction");
    final AgentScope scope = activateSpan(span);
    span.setMeasured(true);
    // manually DECORATE.onRequest(span, stateInstance) :
    setTag(span, Tags.HTTP_METHOD, stateInstance, "verb");
    setTag(span, Tags.HTTP_URL, stateInstance, "uri");
    setTag(span, Tags.PEER_HOSTNAME, stateInstance, HOST);
    setTag(span, Tags.PEER_PORT, stateInstance, PORT);
    DECORATE.afterStart(span);

    // to propagate host and port to com.vordel.dwe.http.ServerTransaction:
    try {
      Object headers = getFieldValue(stateInstance, "headers");

      String hostVal = (String) getFieldValue(stateInstance, HOST);
      String portVal = (String) getFieldValue(stateInstance, PORT);
      String verb = (String) getFieldValue(stateInstance, "verb");

      Method m = headers.getClass().getDeclaredMethod("setHeader", String.class, Object.class);
      m.setAccessible(true);
      m.invoke(headers, HOST, hostVal);
      m.invoke(headers, PORT, portVal);
      m.invoke(headers, METHOD, verb);
      log.debug("StateAdvice headers after:{}}", headers);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      log.debug("Can't set header", e);
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    final AgentSpan span = scope.span();
    try {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
    } finally {
      scope.close();
      span.finish();
    }
  }

  public static void setTag(AgentSpan span, String tag, Object obj, String field) {
    span.setTag(tag, getFieldValue(obj, field).toString());
  }

  public static Object getFieldValue(Object obj, String fieldName) {
    try {
      Field field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      Object v = field.get(obj);
      log.debug("field '{}': {}", fieldName, v);
      return v;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      log.debug("Can't find field '" + fieldName + "': ", e);
    }
    return "null";
  }
}
