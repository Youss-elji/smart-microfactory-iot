package it.unimore.iot.microfactory.adapters.coap;

import it.unimore.iot.microfactory.domain.StateRepository;

public class CoapServerTester {
  public static void main(String[] args) {
    int port = 5683;
    if (args.length > 0) {
      try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {}
    }
    System.out.println("[TEST] Bootstrapping CoAP server ONLY on udp://" + port + " ...");
    try {
      CoapApiServer server = new CoapApiServer(StateRepository.getInstance(), port);
      server.start();
      System.out.println("[TEST] STARTED. Press Ctrl+C to exit.");
      Thread.currentThread().join(); // keep foreground
    } catch (Throwable t) {
      System.err.println("[TEST][FATAL] Can't start CoAP server:");
      t.printStackTrace();
      System.exit(2);
    }
  }
}
