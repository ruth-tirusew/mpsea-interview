package com.mpsesa.interview.accounts.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_configs")
public class AccountConfig {
    @Id
    @Column(name = "id")
    private String id;

    @Enumerated(EnumType.STRING)
    private AccountConfigStatusEnum status;

    @Column(name="minimum_required_balance")
    private long minimumRequiredBalance;

    @Column(name="opening_fee")
    private long openingFee;
}
