package realworld_backend.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.response.FilterResponseUtil;

import java.io.IOException;


@Component
@RequiredArgsConstructor
public class JwtAccessDeniedEntryPoint implements AccessDeniedHandler {

    private final FilterResponseUtil responseUtil;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        Object jwtError = request.getAttribute("jwt_error");
        if (jwtError == null) {
            jwtError = ErrorCode.UNKNOWN;
        }
        ErrorCode errorCode=(ErrorCode) jwtError;

        responseUtil.write(response,
                ApiResponse.error( errorCode.getCode(), errorCode.getMessage()));
    }
}

