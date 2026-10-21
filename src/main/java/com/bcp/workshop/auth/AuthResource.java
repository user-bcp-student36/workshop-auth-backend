package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.text.StringEscapeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String DEFAULT_CLIENT = "angular-workshop";

    @Inject
    LegacyDatabase database;

    @Inject
    AuthTokenService tokens;

    @ConfigProperty(name = "auth.token.ttl-seconds", defaultValue = "3600")
    long tokenTtlSeconds;

    @ConfigProperty(name = "auth.clients.allowed", defaultValue = DEFAULT_CLIENT)
    List<String> allowedClients;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            if (request == null) {
                return Response.status(400).entity(Map.of("message", "Request is null")).build();
            } else {
                if (request.username == null || request.username.trim().isEmpty()) {
                    return Response.status(400).entity(Map.of("message", "Username is required")).build();
                } else {
                    if (request.password == null || request.password.trim().isEmpty()) {
                        return Response.status(400).entity(Map.of("message", "Password is required")).build();
                    } else {
                        UserRecord user = database.findByCredentials(request.username, request.password);
                        if (user == null) {
                            return Response.status(401).entity(Map.of("message", "Invalid user " + request.username + " or password")).build();
                        } else {
                            String client = request.client == null ? DEFAULT_CLIENT : request.client;
                            if (!allowedClients.contains(client)) {
                                return Response.status(400).entity(Map.of("message", "Invalid client")).build();
                            }
                            String token = tokens.issue(user.username(), client);
                            Map<String, Object> response = new HashMap<>();
                            response.put("token", token);
                            response.put("expiresIn", tokenTtlSeconds);
                            response.put("user", Map.of(
                                    "username", user.username(),
                                    "displayName", StringEscapeUtils.escapeHtml4(user.displayName()),
                                    "role", user.role()
                            ));
                            response.put("debug", "authenticated-with-legacy-flow");
                            return Response.ok(response).build();
                        }
                    }
                }
            }
        } catch (Exception error) {
            return Response.status(500).entity(Map.of(
                    "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                    "exception", error.getClass().getName()
            )).build();
        }
    }
}
