package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String NULL_REQUEST = "Request is null";
    private static final String REQUIRED_USERNAME = "Username is required";
    private static final String REQUIRED_PASSWORD = "Password is required";
    private static final String UNKNOWN_ERROR = "Unknown error";

    @Inject
    AuthService service;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            Response validationError = validate(request);
            if (validationError != null) {
                return validationError;
            }
            return authenticationResponse(service.login(request.username, request.password), request.username);
        } catch (Exception error) {
            return errorResponse(error);
        }
    }

    private Response validate(LoginRequest request) {
        if (request == null) {
            return invalidRequest(NULL_REQUEST);
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return invalidRequest(REQUIRED_USERNAME);
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return invalidRequest(REQUIRED_PASSWORD);
        }
        return null;
    }

    private Response invalidRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", message)).build();
    }

    private Response authenticationResponse(LoginResponse response, String username) {
        if (response == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("message", "Invalid user " + username + " or password")).build();
        }
        return Response.ok(response).build();
    }

    private Response errorResponse(Exception error) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of(
                "message", error.getMessage() == null ? UNKNOWN_ERROR : error.getMessage(),
                "exception", error.getClass().getName())).build();
    }
}
