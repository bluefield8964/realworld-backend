package realworld_backend.auth.service;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DigestUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.dto.requestBody.UserRequest;
import realworld_backend.common.dto.responseBody.UserResponse;
import realworld_backend.auth.model.User;
import realworld_backend.auth.repository.UserRepository;
import realworld_backend.auth.security.TokenTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenTool tokenTool;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public User register(UserRequest userRequest) {
        UserRequest.UserAcceptor userAcceptor = userRequest.getUserAcceptor();
        userRepository.findByEmail(userAcceptor.getEmail()).ifPresent(u -> {
            throw new BizException(ErrorCode.USER_REGISTERED);
        });
        userRepository.findByUsername(userAcceptor.getUsername()).ifPresent(u -> {
            throw new BizException(ErrorCode.USER_REGISTERED);
        });
        User user = new User();
        user.setUsername(userAcceptor.getUsername());
        user.setEmail(userAcceptor.getEmail());
        user.setPassword(passwordEncoder.encode(userAcceptor.getPassword()));

        user.setBio(userAcceptor.getBio());
        user.setImage(userAcceptor.getImage());

        //make sure no one registered before
        return userRepository.save(user);
    }

    public UserResponse login(@Valid UserRequest userRequest, HttpServletResponse response) {
        String email = userRequest.getUserAcceptor().getEmail();
        String password = userRequest.getUserAcceptor().getPassword();

        User user = userRepository.findByEmail(email).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        //before generateToken , need to make sure whether still last the last token remain in redis
        String key = "Bearer_id:" + user.getId();
        String elderToken = (String) redisTemplate.opsForValue().get(key);
        if (elderToken != null) {
            redisTemplate.delete("Bearer:" + elderToken);
            redisTemplate.delete(key);
        }
        String shortTermToken = tokenTool.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set("refresh_id:" + user.getId(), refreshToken, Duration.ofDays(7));
        //login then generate a user cache into redis
        redisTemplate.opsForValue().set("user:" + user.getId(), user, Duration.ofHours(1));
        response.setHeader("authentication", "Bearer " + shortTermToken);
        UserResponse userResponse = new UserResponse(user);
        userResponse.setBearer(shortTermToken);
        // setting  HttpOnly Cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)    // JS cant access
                .secure(true)      // HTTPS
                .path("/")         // Cookie
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")// prevent CSRF
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return userResponse;
    }


    public User findByEmail(String email) {
        String keyEmail = "user:" + email;
        User user = (User) redisTemplate.opsForValue().get(keyEmail);
        if (user != null) {
            return user;
        }
        user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            redisTemplate.opsForValue().set(keyEmail, user, Duration.ofHours(1));
        }
        return user;
    }

    public void updateUser(User user) {
        userRepository.save(user);
        // update cache
        redisTemplate.opsForValue().set("user:" + user.getId(), user, Duration.ofHours(1));
    }

    public void addBlockList(User user, String token) {
        String blackList = "blackList:";
        redisTemplate.opsForValue().set
                (blackList + blackList + DigestUtils.sha1DigestAsHex(token), "1", Duration.ofHours(1));
        // delete cache
        redisTemplate.delete("user:" + user.getId());
    }

    public boolean checkExistInBlockList(String token) {
        String blackList = "blackList: ";

        // check the blockList situation
        return redisTemplate.hasKey(blackList + DigestUtils.sha1DigestAsHex(token));
    }


    public UserResponse getCurrentUser(HttpServletRequest httpUserRequest, User user) {


        if (redisTemplate.opsForValue().get("user:" + user.getId()) != null) {
            user = (User) redisTemplate.opsForValue().get("user:" + user.getId());
        } else {
            user = userRepository.findById(user.getId()).orElseThrow(() -> new BizException(ErrorCode.TOKEN_INVALID));
            redisTemplate.opsForValue().set("user:" + user.getId(), user, Duration.ofHours(1));
        }

        if (user.getBio() != null && user.getBio().isEmpty()) {
            user.setBio(null);
        }
        if (user.getImage() != null && user.getImage().isEmpty()) {
            user.setImage(null);
        }
        UserResponse userResponse = new UserResponse(user);
        String header = httpUserRequest.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length());
        }
        userResponse.setBearer(token);
        return userResponse;
    }

    public UserResponse updateCurrentUser(JsonNode requestJson, HttpServletRequest httpUserRequest, Jwt jwt) {
        //current user
        String username = jwt.getClaim("username");
        Long userId = jwt.getClaim("userId");
        User user = (User) redisTemplate.opsForValue().get("user:" + userId);
        if (user == null) {
            user = userRepository.findById(userId).orElseThrow(() -> new BizException(ErrorCode.TOKEN_INVALID));
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

        //  handle "" 閳?null
        if (user.getBio() != null && user.getBio().isEmpty()) {
            user.setBio(null);
        }

        // restore data into  DB
        userRepository.save(user);

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


    public void logout(User user, HttpServletRequest userRequest) {
        String token = userRequest.getHeader("Authorization");

        String oldToken = (String) redisTemplate.opsForValue().get("token_id:" + user.getId());
        if (token == null || !token.equals(oldToken)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        redisTemplate.delete("token:" + oldToken);
    }

    public String refreshShortTermToken(String refreshToken, Long userId) {
        if (refreshToken == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        // verify the refreshToken
        String redisToken = (String) redisTemplate.opsForValue().get("refresh:" + userId);
        if (redisToken == null || !redisToken.equals(refreshToken)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        //  generate new Access Token

        return tokenTool.generateToken(user);
    }

    public Optional<User> findByUserId(Long userId) {
        return userRepository.findById(userId);


    }
}

