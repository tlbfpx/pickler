package com.heypickler.service;

/**
 * 积分钱包（窄接口）：为后续积分商城预留。
 *
 * <p>当前仅暴露读余额能力 ({@link #getBalance})；扣减能力 ({@link #deduct})
 * 仅保留签名，具体实现见积分商城 spec。</p>
 */
public interface PointWallet {

    /**
     * 查询用户指定类型积分余额。
     *
     * @param userId 用户 ID
     * @param type   积分类型 STAR | PARTY
     * @return 当前余额
     */
    int getBalance(Long userId, String type);

    /**
     * 商城兑换扣减 —— 本次仅签名，实现留积分商城 spec。
     *
     * @param userId  用户 ID
     * @param type    积分类型 STAR | PARTY
     * @param amount  扣减数量（正整数）
     * @param itemRef 兑换商品/订单引用
     */
    default void deduct(Long userId, String type, int amount, String itemRef) {
        throw new UnsupportedOperationException("PointWallet.deduct 未实现，见积分商城 spec");
    }
}
