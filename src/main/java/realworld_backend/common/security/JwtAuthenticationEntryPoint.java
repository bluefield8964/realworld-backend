package realworld_backend.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.response.FilterResponseUtil;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {


    private final FilterResponseUtil responseUtil;
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        Object jwtError = request.getAttribute("jwt_error");
        ErrorCode errorCode = (ErrorCode) jwtError;

        if (errorCode == null) {
            errorCode = ErrorCode.UNKNOWN;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        responseUtil.write(response,
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}

