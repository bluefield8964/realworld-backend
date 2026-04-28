分层定义
pre-business：签名校验、raw parse、event normalize、handler 路由前后但尚未拿到补偿锚点。
business-ready：已拿到至少一个可补偿锚点。
可补偿锚点定义：orderNo / subscriptionNo / providerTrackingId(sessionId|invoiceId|providerSubscriptionId)，且能映射到本地实体或可用于 provider retrieve。
三真相职责
State truth：orders/payments/customer_subscriptions/invoices，只管当前状态。
Audit truth：subscription_history append-only，只记发生过什么。
Alert truth：webhook_incidents，只管告警与排障，不改业务状态。
pre-business 规则
默认：只写 incident，不写 abnormal.
incident 必存：provider/requestId/payloadDigest/rawPayload/incidentType/errorMessage/firstOccurredAt/lastOccurredAt/occurrenceCount.
dedupeKey：优先 requestId，其次 eventId，最后 payloadDigest + incidentType.
告警策略：可恢复抖动类默认静默或低级别，仅计数。
business-ready 规则
若错误可补偿：写 abnormalOrder（最小可执行字段），并写 incident。
abnormalOrder 仅承载补偿任务，不承载 full raw。
incident 保留 full raw 做排障。
PROVIDER_TERMINAL_FAILURE 规则
hasReconcileKey == true：incident + abnormal.
hasReconcileKey == false：仅 incident，进入 manual triage。
禁止无锚点直接建 ORDER/SUBSCRIPTION/INVOICE 域 abnormal。
PreBusinessStuck 升级闸门（防丢单）
同一 dedupeKey 满足任一条件触发升级：
10 分钟内 occurrenceCount >= 5
或 持续时间 >= 30 分钟
升级动作：
建一条 SYSTEM 域 abnormal
abnormalType = PRE_BUSINESS_STUCK（若暂不加枚举可临时用 PROVIDER_TERMINAL_FAILURE）
status = MANUAL_REVIEW
reason = pre_business_stuck_threshold_exceeded
目的：从“只告警”升级为“可跟踪工单”，防止无限告警不落地。
后续成功解析的回填规则
某次重试若拿到业务锚点：
回填 incident：trackingId/providerTrackingId/domainType
若存在 SYSTEM stuck abnormal，则 remark 关联并转已处理（或由人工关闭）
新错误走正常 business-ready 补偿链。
错误分类最小矩阵
不告警（默认）：LOCK_CANNOT_ACQUIRE/LOCK_INTERRUPTED/EVENT_PROCESSING/IDEMPOTENCY_LOCK_FAILED
延迟告警（near-exhausted 才告警）：JSON_ERROR/STRIPE_SESSION_NOT_FOUND/STATEMENT_DOES_NOT_MATCH_EVENT_TYPE/EVENT_NOT_FOUND
立即告警+补偿：ORDER_NOT_FOUND/PAYMENT_NOT_FOUND/CUSTOMER_SUBSCRIPTION_NOT_FOUND/SUBSCRIPTION_HISTORY_NOT_FOUND
立即告警：PROVIDER_TERMINAL_FAILURE（是否补偿取决于 hasReconcileKey）
实现约束
incident 写入失败不能阻断主流程状态推进。
abnormal 写入失败必须显式记录并重试（否则真会丢）。
任意策略变更必须只改 PaymentFailureEscalationService（单点策略源）。
建议新增枚举（可选但推荐）
AbnormalOrderType.PRE_BUSINESS_STUCK
WebhookIncidentType.PRE_BUSINESS_PARSE_FAILED（区分 payload parse 与业务缺失）