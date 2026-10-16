package com.ddk.archguard.fixture.four.outsider.support;

import com.ddk.archguard.fixture.four.outsider.infrastructure.MailClient;

/**
 * 分层之外的普通类：不是装配类，不享受例外。
 */
public class Notifications {

    MailClient client;
}
