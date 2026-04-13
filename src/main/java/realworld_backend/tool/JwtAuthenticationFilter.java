package realworld_backend.tool;

import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter {
//extends OncePerRequestFilter

//    private final UserService userService;
//    private final TokenTool tokenTool;
//    private final RedisTemplate redisTemplate;
//
//    public JwtAuthenticationFilter(UserService userService, TokenTool tokenTool, RedisTemplate redisTemplate) {
//        this.userService = userService;
//        this.tokenTool = tokenTool;
//        this.redisTemplate = redisTemplate;
//    }
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain) throws ServletException, IOException {
//
//        String header = request.getHeader("Authorization");
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        // 1. No token → anonymous
//        if (header == null || !header.startsWith("Token ")) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//        //SecurityContextHolder still contain auth
//        if (auth != null
//                && auth.isAuthenticated()
//                && !(auth instanceof AnonymousAuthenticationToken)) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//        // jwt logic
//        String token = header.substring("Token ".length());
//        if (userService.checkExistInBlockList(token)) {
//            request.setAttribute("jwt_error", ErrorCode.TOKEN_INVALID);
//            filterChain.doFilter(request, response);
//            return;
//        }
//        //in the parseToken will extract user from redis or from DB
//        LoginUser loginUser = new LoginUser();
//        User user;
//        try {
//            //if decode token fail,will throw the exception
//            //return null or user (this user was injected into redis)
//            //check token available in the redis
//            //user = userService.userDataAnalyzing(token);
//            user = userService.userDataAnalyzing(token);
//        } catch (TokenExpiredException e) {
//            throw new TokenExpiredException(ErrorCode.TOKEN_EXPIRED);
//        } catch (TokenInvalidException e) {
//            throw new TokenInvalidException(ErrorCode.TOKEN_INVALID);
//        }
//        loginUser.setId(user.getId());
//        loginUser.setEmail(user.getEmail());
//        loginUser.setUsername(user.getUsername());
//        //make suer this is a real and exit user
//        List<GrantedAuthority> authorities = new ArrayList<>();
//        for (Role role : user.getRoles()) {
//            authorities.add(
//                    new SimpleGrantedAuthority("ROLE_" + role.getName())
//            );
//        }
//        // construct authentic object
//        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, authorities );// 权限（后面再加）
//        //setGlobleContext
//        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
//        filterChain.doFilter(request, response);
//    }
//
//    @Data
//    @AllArgsConstructor
//    @NoArgsConstructor
//    public class LoginUser {
//        private Long id;
//        private String username;
//        private String email;
//
//    }
}
