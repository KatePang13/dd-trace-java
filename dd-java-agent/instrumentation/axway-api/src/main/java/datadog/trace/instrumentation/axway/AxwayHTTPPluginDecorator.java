package datadog.trace.instrumentation.axway;

import static java.lang.invoke.MethodType.methodType;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.DefaultURIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.api.URIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;

// request = is com.vordel.circuit.net.State,  connection = com.vordel.dwe.http.ServerTransaction
@Slf4j
public class AxwayHTTPPluginDecorator extends HttpServerDecorator<Object, Object, Object> {
  public static final CharSequence AXWAY_REQUEST = UTF8BytesString.createConstant("axway.request");
  public static final CharSequence AXWAY_TRY_TRANSACTION =
      UTF8BytesString.createConstant("axway.trytransaction");

  public static final AxwayHTTPPluginDecorator DECORATE = new AxwayHTTPPluginDecorator();

  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  private static final String SERVERTRANSACTION_CLASSNAME = "com.vordel.dwe.http.ServerTransaction";
  private static final Class<?> classServerTransaction;
  private static final MethodHandle getRemoteAddr_mh;
  private static final MethodHandle getMethod_mh;
  private static final MethodHandle getURI_mh;

  private static final String STATE_CLASSNAME = "com.vordel.circuit.net.State";
  private static final Class<?> classState;
  private static final MethodHandle hostField_mh;
  private static final MethodHandle portField_mh;
  private static final MethodHandle methodField_mh;
  private static final MethodHandle uriField_mh;

  static {
    classServerTransaction = initClass(SERVERTRANSACTION_CLASSNAME);
    getRemoteAddr_mh =
        initNoArgServerTransactionMethodHandle("getRemoteAddr", InetSocketAddress.class);
    getMethod_mh = initNoArgServerTransactionMethodHandle("getMethod", String.class);
    getURI_mh = initGetURI();

    classState = initClass(STATE_CLASSNAME);
    hostField_mh = initStateFieldGetter("host");
    portField_mh = initStateFieldGetter("port");
    methodField_mh = initStateFieldGetter("verb");
    uriField_mh = initStateFieldGetter("uri");
  }

  private static Class<?> initClass(final String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      log.debug(
          "Can't find class '{}': Axaway integration failed. ", SERVERTRANSACTION_CLASSNAME, e);
    }
    return null;
  }

  private static MethodHandle initNoArgServerTransactionMethodHandle(String name, Class<?> rtype) {
    try {
      return lookup.findVirtual(classServerTransaction, name, methodType(rtype));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      log.debug("Can't find method handler '{}' ", name, e);
    }
    return null;
  }

  private static MethodHandle initGetURI() {
    Method m = null;
    try {
      m = classServerTransaction.getDeclaredMethod("getURI"); // private method
      m.setAccessible(true);
      return lookup.unreflect(m);
    } catch (Throwable e) {
      log.debug("Can't unreflect method '{}': ", m, e);
    }
    return null;
  }

  private static MethodHandle initStateFieldGetter(String fieldName) {
    MethodHandle mh = null;
    try {
      Field field = classState.getDeclaredField(fieldName);
      field.setAccessible(true);
      mh = lookup.unreflectGetter(field);
      log.debug(
          "Initialized field '{}' of class '{}' unreflected to {}", fieldName, classState, mh);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      log.debug(
          "Can't find and unreflect declared field '{}' for class '{}' to mh: '{}'",
          fieldName,
          classState,
          mh,
          e);
    }
    return mh;
  }

  //

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"axway-api"};
  }

  @Override
  protected String component() {
    return "axway-api";
  }

  @Override
  protected String method(final Object serverTransaction) {
    try {
      return (String) getMethod_mh.invoke(serverTransaction);
    } catch (Throwable throwable) {
      log.debug(
          "Can't invoke invoke '{}' on instance '{}' of class '{}'",
          getMethod_mh,
          serverTransaction,
          serverTransaction.getClass(),
          throwable);
    }
    return "UNKNOWN";
  }

  @Override
  protected URIDataAdapter url(final Object serverTransaction) {
    try {
      return new DefaultURIDataAdapter((URI) getURI_mh.invoke(serverTransaction));
    } catch (Throwable e) {
      log.debug("Can't find invoke '{}}' on '{}': ", getURI_mh, serverTransaction, e);
    }
    return new DefaultURIDataAdapter(URI.create(""));
  }

  @Override
  protected String peerHostIP(Object serverTransaction) {
    return getRemoteAddr(serverTransaction).getHostString();
  }

  @Override
  protected int peerPort(Object serverTransaction) {
    return getRemoteAddr(serverTransaction).getPort();
  }

  /** @param serverTransaction instance of {@value #SERVERTRANSACTION_CLASSNAME} */
  @Override
  protected int status(final Object serverTransaction) {
    // TODO will be done manually
    return 0;
  }

  /** @param stateInstance type com.vordel.circuit.net.State */
  public AgentSpan onTransaction(AgentSpan span, Object stateInstance) {
    if (span != null) {
      setStringTagFromStateField(span, Tags.PEER_HOSTNAME, stateInstance, hostField_mh);
      setStringTagFromStateField(span, Tags.PEER_PORT, stateInstance, portField_mh);
      setStringTagFromStateField(span, Tags.HTTP_METHOD, stateInstance, methodField_mh);
      setURLTagFromUriStateField(span, stateInstance);
    }
    return span;
  }

  /**
   *
   *
   * <pre>
   * <code>public class Transaction implements IMetricsTransaction {
   *    public native InetSocketAddress getLocalAddr();
   *    public native InetSocketAddress getRemoteAddr();
   * }
   * public abstract class HTTPTransaction extends Transaction {
   *
   * }
   * public class ServerTransaction extends HTTPTransaction {
   *
   * }
   *
   * </code>
   * </pre>
   *
   * @param obj instance of {@value #SERVERTRANSACTION_CLASSNAME}
   * @return result of com.vordel.dwe.http.ServerTransaction::getRemoteAddr()
   */
  private static InetSocketAddress getRemoteAddr(Object obj) {
    try {
      return (InetSocketAddress) getRemoteAddr_mh.invoke(obj);
    } catch (Throwable throwable) {
      log.debug("Can't invoke '{}' on instance '{}': ", getRemoteAddr_mh, obj, throwable);
    }
    return new InetSocketAddress(0);
  }

  private static void setStringTagFromStateField(
      AgentSpan span, String tag, Object stateInstance, MethodHandle mh) {
    String v = "";
    try {
      v = (String) mh.invoke(stateInstance);
    } catch (Throwable e) {
      log.debug(
          "Can't invoke '{}' on instance '{}'; ; Tag '{}' not set.", mh, stateInstance, tag, e);
    }
    span.setTag(tag, v);
  }

  private static void setURLTagFromUriStateField(AgentSpan span, Object stateInstance) {
    try {
      span.setTag(Tags.HTTP_URL, uriField_mh.invoke(stateInstance).toString());
    } catch (Throwable e) {
      log.debug(
          "Can't invoke '{}' on instance '{}'; Tag '{}' not set.",
          uriField_mh,
          stateInstance,
          Tags.HTTP_URL,
          e);
    }
  }
}
