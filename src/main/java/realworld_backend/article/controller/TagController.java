package realworld_backend.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.article.service.TagService;
import realworld_backend.common.dto.responseBody.ApiResponse;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @GetMapping("/tags")
    public ApiResponse<Map<String, Object>> getTags() {
        Map<String, Object> data = new HashMap<>();
        data.put("tags", tagService.getAllTagNames());
        return ApiResponse.success(data);
    }
}
