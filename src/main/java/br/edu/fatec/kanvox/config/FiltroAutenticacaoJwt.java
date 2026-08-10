package br.edu.fatec.kanvox.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.edu.fatec.kanvox.repositorio.UsuarioRepositorio;
import br.edu.fatec.kanvox.servico.TokenServico;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro executado em toda requisicao: le o token JWT do cabecalho
 * Authorization ("Bearer ...") e, se for valido, marca a requisicao
 * como autenticada, guardando o Usuario logado no contexto de seguranca.
 */
@Component
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

	private final TokenServico tokenServico;
	private final UsuarioRepositorio usuarioRepositorio;

	public FiltroAutenticacaoJwt(TokenServico tokenServico, UsuarioRepositorio usuarioRepositorio) {
		this.tokenServico = tokenServico;
		this.usuarioRepositorio = usuarioRepositorio;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
			FilterChain corrente) throws ServletException, IOException {

		String cabecalho = requisicao.getHeader("Authorization");
		if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
			String token = cabecalho.substring(7);
			String email = tokenServico.validarEObterEmail(token);
			if (email != null) {
				usuarioRepositorio.buscarPorEmail(email).ifPresent(usuario -> {
					var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
					SecurityContextHolder.getContext().setAuthentication(autenticacao);
				});
			}
		}

		corrente.doFilter(requisicao, resposta);
	}

}
