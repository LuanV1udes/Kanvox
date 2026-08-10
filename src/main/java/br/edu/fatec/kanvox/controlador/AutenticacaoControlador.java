package br.edu.fatec.kanvox.controlador;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.AutenticacaoServico;

/**
 * Endpoints publicos de cadastro e login (RF-01.1).
 * O controlador apenas recebe a requisicao e delega ao servico —
 * nenhuma regra de negocio fica aqui.
 */
@RestController
@RequestMapping("/api/autenticacao")
public class AutenticacaoControlador {

	private final AutenticacaoServico autenticacaoServico;

	public AutenticacaoControlador(AutenticacaoServico autenticacaoServico) {
		this.autenticacaoServico = autenticacaoServico;
	}

	/** Cria uma conta nova. Corpo esperado: { "nome": "...", "email": "...", "senha": "..." } */
	@PostMapping("/cadastro")
	public ResponseEntity<Usuario> cadastrar(@RequestBody Usuario usuario) {
		Usuario criado = autenticacaoServico.cadastrar(usuario);
		return ResponseEntity.status(HttpStatus.CREATED).body(criado);
	}

	/** Faz login. Corpo esperado: { "email": "...", "senha": "..." }. Devolve { "token": "..." } */
	@PostMapping("/login")
	public Map<String, String> entrar(@RequestBody Usuario credenciais) {
		String token = autenticacaoServico.autenticar(credenciais.getEmail(), credenciais.getSenha());
		return Map.of("token", token);
	}

	/** Devolve os dados do usuario logado (id, nome, e-mail) — usado pelo frontend. */
	@GetMapping("/eu")
	public Usuario usuarioLogado(@AuthenticationPrincipal Usuario usuarioLogado) {
		return usuarioLogado;
	}

}
