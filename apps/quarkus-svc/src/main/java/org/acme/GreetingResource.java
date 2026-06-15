package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.mutiny.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/svc1")
public class GreetingResource {
    private static final Logger logger = LoggerFactory.getLogger(GreetingResource.class);


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> hello(@Context HttpServerRequest request) {

        

        //get the direct raw ip of client(usually it's the ip of kong gaeway)
        String rawRemoteIp = request.remoteAddress().host();
        logger.info("Received request from IP: {}", rawRemoteIp);
        int rawRemotePort = request.localAddress().port();
        logger.info("Received request from port: {}", rawRemotePort);

        //prefer the  ip in x-forwarded-for header
        //if the header is not present, then use the X-Real-Ip header
        //if both headers are not present, then use the raw ip
        String xForwardedFor = request.getHeader("x-forwarded-for");
        String xRealIp = request.getHeader("x-real-ip");
        String clientIp = xForwardedFor != null ? xForwardedFor : (xRealIp != null ? xRealIp : rawRemoteIp);
        logger.info("Determined client IP: {}", clientIp);
      



        return Map.of(
            "client-ip", clientIp,
            "status", "ok",
            "service", "svc1",
            "node", "tencent-cloud",
            "framework", "quarkus"
        );
    }
}
