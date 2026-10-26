package com.mpsesa.interview.accounts;

import com.mpsesa.interview.accounts.core.domain.WalletAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "1500"))
    @Query("select w from WalletAccount w where w.walletId in :ids order by w.walletId")
    List<WalletAccount> lockAllById(@Param("ids") List<String> ids);
}
