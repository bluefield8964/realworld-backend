package realworld_backend.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.dto.UserRequest;
import realworld_backend.dto.UserResponse;
import realworld_backend.model.User;
import realworld_backend.service.RegisterService;
import realworld_backend.tool.TokenTool;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class registerController {

    @Autowired
    private RegisterService registerService;
    @Autowired
    private TokenTool tokenTool;

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> register(@RequestBody UserRequest userResponse){
        User isRegister = registerService.register(userResponse.getUser());
        //if user was registed, ruturn will be null
        if (isRegister==null) {
            HashMap<Object, Object> error = new HashMap<>();
            error.put("error","Email or username already exists");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(Collections.singletonMap("errors", error));
        } else {
            Map<String, Object> userResp = new HashMap<>();
            userResp.put("email", isRegister.getEmail());
            userResp.put("username", isRegister.getUsername());
            userResp.put("token", tokenTool.generateToken(isRegister));
            HashMap<String, Object> response = new HashMap<>();
            response.put("user",userResp);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }



}
