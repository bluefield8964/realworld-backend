package realworld_backend.common.web.response;

import jakarta.servlet.http.HttpServletResponse;
import realworld_backend.common.exception.ErrorCode;

import java.io.IOException;

public class ResponseUtil {

    public static void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getCode());
        response.setContentType("application/json;charset=UTF-8");

        String json = String.format(
                "{\"code\":%d,\"message\":\"%s\"}",
                errorCode.getCode(),
                errorCode.getMessage()
        );

        response.getWriter().write(json);
    }
}
