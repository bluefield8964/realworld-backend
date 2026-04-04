package realworld_backend.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import realworld_backend.model.User;
import realworld_backend.repository.UserRepository;
import realworld_backend.tool.TokenTool;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RegisterService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Autowired
    private TokenTool tokenTool;

    public User register(User registerUser) {
        String email = registerUser.getEmail();
        Optional<User> byEmail = userRepository.findByEmail(email);
        //if the user had taken
        if (byEmail.isPresent()) {
            return null;
        } else {
            return userRepository.save(registerUser);
        }
    }

    public User findByEmail(String email){
        String keyEmail = "user:" + email;
        User user = (User) redisTemplate.opsForValue().get(keyEmail);
        if (user!=null){
            return user;
        }
        user = userRepository.findByEmail(email).orElse(null) ;
        if (user!=null){
            redisTemplate.opsForValue().set(keyEmail,user,1, TimeUnit.HOURS);
        }
        return user;
    }

    public void updateUser(User user) {
        userRepository.save(user);

        // delete cache
        redisTemplate.delete("user:" + user.getEmail());
    }

    //this is for JwtAuthenticationFilter to extract token
    public User findByToken(String token){
        String Email = (String)redisTemplate.opsForValue().get("token:" + token);
        if (Email!=null){
            return (User)redisTemplate.opsForValue().get(Email);
        }
        String email = tokenTool.decodeToken(token);
        User byEmail = userRepository.findByEmail(email).orElse(null);
        if (byEmail==null)return null;
        return byEmail;
    }
}
