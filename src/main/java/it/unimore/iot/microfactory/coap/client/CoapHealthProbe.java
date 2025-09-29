package it.unimore.iot.microfactory.coap.client;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;

public class CoapHealthProbe {
  public static void main(String[] args) {
    String uri = "coap://127.0.0.1:5683/.well-known/core";
    CoapClient c = new CoapClient(uri);
    try {
      System.out.println("Probing CoAP server at: " + uri);
      CoapResponse r = c.get();
      if (r == null) {
        System.err.println("NO RESPONSE - CoAP server is likely down or unreachable.");
        System.exit(3);
      }
      System.out.println("-> RESPONSE RECEIVED");
      System.out.println("CODE=" + r.getCode() + " CT=" + r.getOptions().getContentFormat());
      System.out.println("\n---- PAYLOAD ----\n" + r.getResponseText());
      System.exit(0);
    } catch (Exception e) {
      System.err.println("CRITICAL ERROR during probe:");
      e.printStackTrace();
      System.exit(1);
    } finally {
      c.shutdown();
    }
  }
}