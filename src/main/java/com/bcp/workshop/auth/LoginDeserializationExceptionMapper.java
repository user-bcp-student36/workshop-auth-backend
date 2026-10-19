package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class LoginDeserializationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException error) {
        if (error.getResponse().getStatus() == Response.Status.BAD_REQUEST.getStatusCode()
                && error.getCause() instanceof JsonProcessingException) {
            return invalidRequest();
        }
        return error.getResponse();
    }

    static Response invalidRequest() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", "Request is null")).build();
    }
}
