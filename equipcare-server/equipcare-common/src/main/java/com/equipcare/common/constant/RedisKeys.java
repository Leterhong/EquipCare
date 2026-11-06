package com.equipcare.common.constant;

/**
 * Redis Key 统一管理。禁止在业务代码中散落字符串拼接。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    private static final String PREFIX = "equipcare:";

    /** 登录失败计数：equipcare:auth:fail:{username} */
    public static String loginFail(String username) {
        return PREFIX + "auth:fail:" + username;
    }

    /** 访问令牌黑名单：equipcare:auth:blacklist:{jti} */
    public static String tokenBlacklist(String jti) {
        return PREFIX + "auth:blacklist:" + jti;
    }

    /** 在线登录用户缓存：equipcare:auth:login:{jti} */
    public static String loginUser(String jti) {
        return PREFIX + "auth:login:" + jti;
    }

    /** 用户权限缓存：equipcare:auth:perm:{userId} */
    public static String userPermission(Long userId) {
        return PREFIX + "auth:perm:" + userId;
    }

    /** 资产编号日流水：equipcare:seq:asset:{categoryCode}:{yyyyMMdd} */
    public static String assetNoSeq(String categoryCode, String day) {
        return PREFIX + "seq:asset:" + categoryCode + ":" + day;
    }

    /** 工单编号日流水：equipcare:seq:wo:{yyyyMMdd} */
    public static String workOrderNoSeq(String day) {
        return PREFIX + "seq:wo:" + day;
    }

    /** 消息未读数：equipcare:msg:unread:{userId} */
    public static String unreadMessage(Long userId) {
        return PREFIX + "msg:unread:" + userId;
    }

    /** 用户在线令牌集合：equipcare:auth:user-tokens:{userId} */
    public static String userTokens(Long userId) {
        return PREFIX + "auth:user-tokens:" + userId;
    }

    /** 用户有效刷新令牌集合：equipcare:auth:refresh-tokens:{userId} */
    public static String userRefreshTokens(Long userId) {
        return PREFIX + "auth:refresh-tokens:" + userId;
    }
}
