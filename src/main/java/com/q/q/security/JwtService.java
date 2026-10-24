package com.q.q.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.q.q.config.AppProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

@Service
public class JwtService {

	private final SecretKey key;
	private final long expirationMinutes;
	private final Clock clock;
	private final JwtParser parser;

	public JwtService(AppProperties props, Clock clock) {
		this.key = signingKey(props.jwt().secret());
		this.expirationMinutes = props.jwt().expirationMinutes();
		this.clock = clock;
		this.parser = Jwts.parser()
				.verifyWith(key)
				.clock(() -> Date.from(clock.instant()))         // la expiración se evalúa con el reloj inyectado
				.build();
	}

	public String generateToken(UserDetails user) {
		Instant now = clock.instant();
		return Jwts.builder()
				.subject(user.getUsername())
				.claim("roles", user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
				.signWith(key)
				.compact();
	}

	public long getExpirationSeconds() {
		return expirationMinutes * 60;
	}

	public String extractUsername(String token) {
		return parse(token).getSubject();
	}

	public boolean isValid(String token, UserDetails user) {
		Claims claims = parse(token);                           // lanza JwtException si la firma es inválida o expiró
		return claims.getSubject().equals(user.getUsername());
	}

	private Claims parse(String token) {
		return parser.parseSignedClaims(token).getPayload();
	}

	private static SecretKey signingKey(String base64Secret) {
		try {
			return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));   // secreto en Base64, ≥ 256 bits
		} catch (DecodingException | WeakKeyException | IllegalArgumentException ex) {
			// Sin la causa: su mensaje puede incluir parte del secreto
			throw new IllegalStateException("app.jwt.secret debe ser Base64 válido y decodificar a 32 bytes o más");
		}
	}

}
