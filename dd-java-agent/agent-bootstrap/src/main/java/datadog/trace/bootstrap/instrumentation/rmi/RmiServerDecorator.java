package datadog.trace.bootstrap.instrumentation.rmi;

import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.ServerDecorator;

public class RmiServerDecorator extends ServerDecorator {
  public static final CharSequence RMI_REQUEST = UTF8BytesString.createConstant("rmi.request");
  public static final CharSequence RMI_SERVER = UTF8BytesString.createConstant("rmi-server");
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi", "rmi-server"};
  }

  @Override
  protected CharSequence spanType() {
    return InternalSpanTypes.RPC;
  }

  @Override
  protected CharSequence component() {
    return RMI_SERVER;
  }
}
