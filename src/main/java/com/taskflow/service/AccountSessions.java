package com.taskflow.service;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class AccountSessions {
    private final SessionRegistry sessionRegistry;

    AccountSessions(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    void expireAfterCommit(String email) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                var principal = User.withUsername(email).password("").roles("USER").build();
                sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        });
    }
}
