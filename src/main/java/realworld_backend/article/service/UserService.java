package realworld_backend.article.service;


import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import realworld_backend.article.model.UserProfile;
import realworld_backend.article.repository.UserProfileRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.responseBody.UserResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;


    public UserResponse updateCurrentUser(JsonNode requestJson, HttpServletRequest httpUserRequest, CurrentAuthUser currentUser) {
        //current user
        Long userId = currentUser.userId();
        UserProfile user = (UserProfile) redisTemplate.opsForValue().get("user:" + userId);
        if (user == null) {
            user = userProfileRepository.findById(userId).orElseThrow(() -> new BizException(ErrorCode.TOKEN_INVALID));
            redisTemplate.opsForValue().set("user:" + userId, user, Duration.ofHours(1));
        }
        JsonNode userNode = requestJson.get("user");
        if (userNode == null || userNode.isNull()) {
            throw new BizException(ErrorCode.USER_JSON_ERROR);
        }


        ObjectNode safeNode = JsonNodeFactory.instance.objectNode();

        if (userNode.has("username")) {
            safeNode.set("username", userNode.get("username"));
        }
        if (userNode.has("email")) {
            safeNode.set("email", userNode.get("email"));
        }
        if (userNode.has("bio")) {
            safeNode.set("bio", userNode.get("bio"));
        }
        if (userNode.has("image")) {
            safeNode.set("image", userNode.get("image"));
        }

        // dont inject password / roles / id
        // merge patch
        try {
            objectMapper.readerForUpdating(user).readValue(safeNode);
        } catch (Exception e) {
            throw new BizException(ErrorCode.USER_JSON_ERROR);
        }

        // Normalize empty bio string to null
        if (user.getBio() != null && user.getBio().isEmpty()) {
            user.setBio(null);
        }

        // restore data into  DB
        userProfileRepository.save(user);

        // update cache
        redisTemplate.opsForValue().set("user:" + user.getId(), user, Duration.ofHours(1));

        UserResponse response = new UserResponse(user);

        //token from header
        String header = httpUserRequest.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            response.setBearer(header.substring(7));
        }

        return response;
    }


    public Optional<UserProfile> findByUserId(Long userId) {
        return userProfileRepository.findById(userId);
    }
}

