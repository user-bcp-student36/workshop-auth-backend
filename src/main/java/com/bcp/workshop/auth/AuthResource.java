package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";
    private static final String INVALID_CREDENTIALS = "Invalid credentials";
    private static final String AUTHENTICATION_FAILED = "Authentication failed";

    @Inject
    AuthService service;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        Optional<String> invalidRequest = validationError(request);
        if (invalidRequest.isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", invalidRequest.get())).build();
        }
        try {
            Optional<UserRecord> user = service.authenticate(request.username, request.password);
            if (user.isEmpty()) {
                return unauthorized();
            }
            return success(user.get());
        } catch (PersistenceFailure error) {
            LOG.error("Authentication persistence failure", error);
            return unauthorized();
        } catch (Exception error) {
            LOG.error("Unexpected authentication failure", error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", AUTHENTICATION_FAILED)).build();
        }
    }

    private Optional<String> validationError(LoginRequest request) {
        if (request == null) {
            return Optional.of("Request is null");
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return Optional.of("Username is required");
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return Optional.of("Password is required");
        }
        return Optional.empty();
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("message", INVALID_CREDENTIALS)).build();
    }

    private Response success(UserRecord user) {
        var response = new LoginResponse(service.issueToken(user), TokenService.LIFETIME_SECONDS,
                UserResponse.from(user), DEBUG_FLOW);
        return Response.ok(response).build();
    }
}
