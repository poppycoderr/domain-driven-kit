package com.ddk.archguard.fixture.three.valid.infrastructure;

import com.ddk.archguard.fixture.three.valid.business.Account;
import com.ddk.archguard.fixture.three.valid.business.AccountRepository;

public class AccountRepositoryImpl implements AccountRepository {

    @Override
    public Account find(long id) {
        return new Account();
    }
}
