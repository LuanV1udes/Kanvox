package br.edu.fatec.kanvox.servico;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Gera e valida os tokens JWT usados na autenticacao (RNF-02).
 * Token unico com expiracao — sem refresh token no MVP:
 * quando expira, o usuario simplesmente faz login de novo.
 */
@Service
public class TokenServico {

	private final SecretKey chave;
	private final long validadeHoras;

	public TokenServico(@Value("${kanvox.jwt.chave}") String chave,
			@Value("${kanvox.jwt.validade-horas}") long validadeHoras) {
		this.chave = Keys.hmacShaKeyFor(chave.getBytes(StandardCharsets.UTF_8));
		this.validadeHoras = validadeHoras;
	}

	/** Gera um token assinado contendo o e-mail do usuario e o prazo de expiracao. */
	public String gerar(String email) {
		Date agora = new Date();
		Date expiracao = new Date(agora.getTime() + Duration.ofHours(validadeHoras).toMillis());
		return Jwts.builder()
				.subject(email)
				.issuedAt(agora)
				.expiration(expiracao)
				.signWith(chave)
				.compact();
	}

	/** Valida o token e devolve o e-mail contido nele; devolve null se for invalido ou expirado. */
	public String validarEObterEmail(String token) {
		try {
			Claims corpo = Jwts.parser()
					.verifyWith(chave)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return corpo.getSubject();
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

}
