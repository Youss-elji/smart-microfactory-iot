package it.unimore.iot.microfactory.adapters.coap;

import it.unimore.iot.microfactory.domain.StateRepository;

public class CoapServerTester {
  public static void main(String[] args) {
    // Set a default exception handler to catch any hidden error
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      System.err.println("[FATAL] Uncaught exception in thread " + t.getName());
      e.printStackTrace();
      System.exit(10);
    });

    try {
      System.out.println("[TEST] Bootstrapping CoAP server only...");
      // For this test, we can use a dummy repository as no state is needed
      StateRepository dummyRepository = StateRepository.getInstance();
      CoapApiServer server = new CoapApiServer(dummyRepository, 5683);
      server.start();
      System.out.println("[TEST] CoAP server started on UDP/5683. Process will run indefinitely.");
      Thread.currentThread().join(); // Keep the main thread alive
    } catch (Throwable t) {
      System.err.println("[FATAL] Failed to start CoAP server in tester.");
      t.printStackTrace();
      System.exit(2);
    }
  }
}