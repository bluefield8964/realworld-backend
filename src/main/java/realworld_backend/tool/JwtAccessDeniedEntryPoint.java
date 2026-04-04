package realworld_backend.tool;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import realworld_backend.dto.ApiResponse;
import realworld_backend.dto.ErrorCode;

import java.io.IOException;


@Component
public class JwtAccessDeniedEntryPoint implements AccessDeniedHandler {

    @Autowired
    private FilterResponseUtil responseUtil;

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
