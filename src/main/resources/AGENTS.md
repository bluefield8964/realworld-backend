# AGENTS.md

## Controller / Service / Repository 分层规范

当修改或新增 Controller 时，必须遵守以下规则。

### 1. Controller 层保持轻量

Controller 只负责：

- 路由声明
- HTTP 状态码
- 认证/授权注解
- 请求参数绑定
- 响应包装

Controller 禁止放：

- 核心业务分支
- 状态流转逻辑
- 复杂参数组合校验
- Repository 直接调用

固定响应格式：

- 成功：`ApiResponse.success(data)`
- 创建：`201 Created + data.<resource>`
- 查询/更新：`200 OK + data.<resource>`
- 删除：`204 No Content`

示例：

``java
@PostMapping("/xxx")
public ResponseEntity<ApiResponse<?>> create(...) {
    XxxDto dto = service.create(...);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(Map.of("xxx", dto)));
} 
``

### 2. Service 层承载业务逻辑

Service 负责：

- 业务规则
- 状态流转
- 幂等判断
- 数据一致性
- 参数业务校验：null、互斥字段、组合约束
- Repository 调用编排

要求：

- 每个 public 方法只表达一条业务主线
- 复杂分支拆成私有方法，例如：
- validateXxx
- applyUpdate
- buildResponse
- Service 抛业务异常，不直接处理 HTTP 状态码
### 3. Repository 层保持纯数据访问

Repository 只负责：

- 查询
- 过滤
- 分页
- 排序

禁止：

- 业务决策
- 响应 DTO 组装
- HTTP 语义处理

要求：

- filter 参数使用统一 nullable 语义
- 列表接口必须有稳定排序，避免测试不稳定
### 4. DTO / Response 规范
- 列表响应和详情响应分开 DTO
- hurl 有顺序断言的字段必须使用 List，不要使用 Set
- 使用 @JsonInclude(JsonInclude.Include.NON_NULL) 控制字段显隐
- Entity 不直接返回给 Controller
- DTO 组装优先放在 Mapper / Response factory 中，不堆在 Service 主流程里
### 5. 异常与状态码映射

状态码约定：

- 参数错误：422
- 未认证：401
- 无权限：403
- 资源不存在：404
- 系统异常：500

原则：

- Controller / GlobalExceptionHandler 负责 HTTP 映射
- Service 抛语义明确的业务异常，例如 BizException(ErrorCode.Xxx)
### 6. Hurl 对齐检查清单

修改 Controller 后必须检查：

- 路径和 HTTP 方法是否一致
- Header 是否一致，例如 Authorization: Bearer <token>
- 成功状态码是否一致：200 / 201 / 204
- data 包装层级是否一致：data.article / data.articles
- 列表字段顺序是否稳定，尤其 tagList
- 错误分支返回码和 body 是否一致，例如 404 + errors
### 7. 修改 Controller 时的默认流程

每次操作 Controller 前，先执行：

- 找到对应 Service 方法
- 确认 Controller 是否只做 HTTP 编排
- 确认响应包装层级是否符合 hurl
  - 统一由该controller中的进行错误码响应 实例：
    ``java
    catch (BizException e) {
    return toHttpError(e);
    }
    private ResponseEntity<ApiResponse<?>> toHttpError(BizException e) {
    if (e.getErrorCode() == ErrorCode.FOLLOWING_NOT_FOUND || e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
    if (e.getErrorCode() == ErrorCode.CANNOT_FOLLOW_SELF || e.getErrorCode() == ErrorCode.INVALID_INPUT) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
    if (e.getErrorCode() == ErrorCode.TOKEN_INVALID || e.getErrorCode() == ErrorCode.UNAUTHORIZED) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
    if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }
   ``
- 如发现业务逻辑在 Controller 中，优先下沉到 Service
- 如发现 DTO 手动组装过长，优先抽到 Mapper 或 Response factory
### 8. 完成标准

Controller 相关修改完成后，必须说明：

- 影响的接口
- 返回状态码
- 响应 data 层级
- 是否影响 hurl 测试
- 是否需要新增或更新 hurl 用例