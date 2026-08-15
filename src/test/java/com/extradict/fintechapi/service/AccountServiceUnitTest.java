package com.extradict.fintechapi.service;

import com.extradict.fintechapi.dto.AccountRequest;
import com.extradict.fintechapi.dto.AccountResponse;
import com.extradict.fintechapi.entity.Account;
import com.extradict.fintechapi.entity.User;
import com.extradict.fintechapi.enums.AccountStatus;
import com.extradict.fintechapi.repository.AccountRepository;
import com.extradict.fintechapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ritik@gmail.com");
        user.setName("Ritik");
    }

    // ── Test 1: Create account successfully ───────────────────
    @Test
    void shouldCreateAccountSuccessfully() {
        AccountRequest request = new AccountRequest();
        request.setCurrency("INR");

        Account savedAccount = new Account();
        savedAccount.setId(UUID.randomUUID());
        savedAccount.setUser(user);
        savedAccount.setBalance(BigDecimal.ZERO);
        savedAccount.setCurrency("INR");
        savedAccount.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("ritik@gmail.com"))
                .thenReturn(Optional.of(user));
        when(accountRepository.save(any()))
                .thenReturn(savedAccount);

        AccountResponse response = accountService
                .createAccount("ritik@gmail.com", request);

        assertNotNull(response);
        assertEquals("INR", response.getCurrency());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());
        verify(accountRepository, times(1)).save(any());
    }

    // ── Test 2: User not found throws exception ───────────────
    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> accountService.createAccount(
                        "unknown@gmail.com", new AccountRequest())
        );

        assertEquals("User not found", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    // ── Test 3: Default currency is INR ──────────────────────
    @Test
    void shouldDefaultToINRWhenCurrencyNotProvided() {
        AccountRequest request = new AccountRequest();
        // currency is null

        Account savedAccount = new Account();
        savedAccount.setId(UUID.randomUUID());
        savedAccount.setUser(user);
        savedAccount.setBalance(BigDecimal.ZERO);
        savedAccount.setCurrency("INR");
        savedAccount.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("ritik@gmail.com"))
                .thenReturn(Optional.of(user));
        when(accountRepository.save(any()))
                .thenReturn(savedAccount);

        AccountResponse response = accountService
                .createAccount("ritik@gmail.com", request);

        assertEquals("INR", response.getCurrency());
    }
}