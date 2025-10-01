package it.unimore.iot.microfactory.coap.client;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

public class CoapHealthProbe {
  public static void main(String[] args) {
    String uri = "coap://127.0.0.1:5683/.well-known/core";
    if (args.length > 0) uri = args[0];
    System.out.println("[PROBE] GET " + uri);
    CoapClient c = new CoapClient(uri);
    c.setTimeout(5000L); // 5s hard timeout
    try {
      CoapResponse r = c.get();
      if (r == null) {
        System.out.println("[PROBE] NO RESPONSE (timeout).");
        System.exit(3);
      }
      System.out.println("[PROBE] CODE=" + r.getCode() + " CT=" + r.getOptions().getContentFormat()
              + " (" + MediaTypeRegistry.toString(r.getOptions().getContentFormat()) + ")");
      System.out.println(r.getResponseText());
    } catch (Throwable t) {
      System.err.println("[PROBE][ERR] " + t);
      t.printStackTrace();
      System.exit(4);
    } finally {
      c.shutdown();
    }
  }
}
