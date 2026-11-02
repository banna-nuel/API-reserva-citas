package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.q.q.IntegrationTest;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;

// UserRepository contra MySQL real: email en minúsculas y único (RN-11) y el bloqueo de findByIdForUpdate
@IntegrationTest
class UserRepositoryIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void theEmailIsStoredInLowercaseAndIsUniqueWhateverItsCase() {
		String email = uniqueEmail();
		User user = users.saveAndFlush(newUser("  " + email.toUpperCase(Locale.ROOT) + " "));

		// Lo que queda en la columna, leído sin pasar por JPA
		assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, user.getId()))
				.isEqualTo(email);
		assertThat(users.findByEmail(email)).map(User::getId).contains(user.getId());
		assertThat(users.existsByEmail(email)).isTrue();
		// El mismo email con otra caja choca con uq_users_email
		assertThatThrownBy(() -> users.saveAndFlush(newUser(capitalized(email))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// Mientras una transacción tiene al usuario bloqueado, otra que lo pide FOR UPDATE espera a que confirme
	@Test
	void findByIdForUpdateMakesASecondLockerWaitForTheFirstCommit() throws Exception {
		Long id = users.saveAndFlush(newUser(uniqueEmail())).getId();
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch locked = new CountDownLatch(1);
		AtomicLong firstAboutToCommit = new AtomicLong();
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
				assertThat(users.findByIdForUpdate(id)).isPresent();
				locked.countDown();
				sleep(500);
				firstAboutToCommit.set(System.nanoTime());
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			Long acquiredAt = tx.execute(status -> {
				assertThat(users.findByIdForUpdate(id)).map(User::getId).contains(id);
				return System.nanoTime();
			});

			first.get(10, TimeUnit.SECONDS);
			assertThat(acquiredAt).isGreaterThan(firstAboutToCommit.get());
		} finally {
			pool.shutdownNow();
		}
	}

	private static User newUser(String email) {
		User user = new User();
		user.setFullName("Usuario de repositorio");
		user.setEmail(email);
		user.setPasswordHash("hash-sintetico");
		user.setRole(Role.CLIENT);
		return user;
	}

	private static String capitalized(String email) {
		return email.substring(0, 1).toUpperCase(Locale.ROOT) + email.substring(1);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
