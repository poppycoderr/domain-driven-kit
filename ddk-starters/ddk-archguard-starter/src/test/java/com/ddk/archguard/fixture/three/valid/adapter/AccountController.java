package com.ddk.archguard.fixture.three.valid.adapter;

import com.ddk.archguard.fixture.three.valid.business.AccountRepository;

public class AccountController {

    private final AccountRepository repository;

    public AccountController(AccountRepository repository) {
        this.repository = repository;
    }

    public Object get(long id) {
        return repository.find(id);
    }
}
