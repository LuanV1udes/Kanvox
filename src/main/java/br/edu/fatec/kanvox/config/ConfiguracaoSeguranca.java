package br.edu.fatec.kanvox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuracao do Spring Security (RNF-02).
 * A API e stateless: nao usa sessao nem cookies — toda requisicao
 * autenticada carrega o token JWT no cabecalho Authorization.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracaoSeguranca {

	private final FiltroAutenticacaoJwt filtroAutenticacaoJwt;

	public ConfiguracaoSeguranca(FiltroAutenticacaoJwt filtroAutenticacaoJwt) {
		this.filtroAutenticacaoJwt = filtroAutenticacaoJwt;
	}

	@Bean
	public SecurityFilterChain filtrosDeSeguranca(HttpSecurity http) throws Exception {
		http
				// CSRF protege aplicacoes com sessao/cookie; nossa API usa JWT, entao nao se aplica
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(autorizacao -> autorizacao
						// cadastro, login e recuperacao de senha sao publicos (o /eu exige token)
						.requestMatchers("/api/autenticacao/cadastro", "/api/autenticacao/login",
								"/api/autenticacao/esqueci-senha", "/api/autenticacao/redefinir-senha",
								"/api/autenticacao/token-recuperacao/**").permitAll()
						// arquivos estaticos do frontend (HTML/CSS/JS) — as paginas sao
						// publicas, mas os dados que elas exibem vem da API, que exige token
						.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
						// todo o restante exige token valido
						.anyRequest().authenticated())
				// sem token (ou com token invalido), a API responde 401 em vez do 403 padrao
				.exceptionHandling(excecoes -> excecoes.authenticationEntryPoint(
						(requisicao, resposta, excecao) -> resposta
								.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Autenticacao necessaria")))
				.addFilterBefore(filtroAutenticacaoJwt, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/** BCrypt para o hash das senhas (RNF-02). */
	@Bean
	public PasswordEncoder codificadorDeSenha() {
		return new BCryptPasswordEncoder();
	}

}
