package realworld_backend.common.web.response;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import realworld_backend.common.dto.responseBody.ApiResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class FilterResponseUtil {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public  void write(HttpServletResponse response, ApiResponse<?> result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}

