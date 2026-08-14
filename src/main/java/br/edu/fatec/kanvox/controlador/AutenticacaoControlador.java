package br.edu.fatec.kanvox.controlador;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

	/**
	 * Solicita a recuperacao de senha (RF-01.3). Corpo: { "email": "..." }
	 * Sempre responde com a mesma mensagem generica, exista ou nao o e-mail —
	 * evita que alguem descubra quais contas estao cadastradas na plataforma.
	 */
	@PostMapping("/esqueci-senha")
	public Map<String, String> esqueciSenha(@RequestBody Map<String, String> corpo) {
		autenticacaoServico.solicitarRecuperacaoDeSenha(corpo.get("email"));
		return Map.of("mensagem", "Se este e-mail estiver cadastrado, enviamos um link de redefinição.");
	}

	/** Redefine a senha usando o token recebido por e-mail. Corpo: { "token": "...", "novaSenha": "..." } */
	@PostMapping("/redefinir-senha")
	public Map<String, String> redefinirSenha(@RequestBody Map<String, String> corpo) {
		autenticacaoServico.redefinirSenha(corpo.get("token"), corpo.get("novaSenha"));
		return Map.of("mensagem", "Senha redefinida com sucesso. Você já pode entrar.");
	}

	/**
	 * Consulta o e-mail associado a um token de recuperacao valido.
	 * Usado pela tela de redefinicao para mostrar de qual conta a senha
	 * esta sendo trocada, antes do usuario digitar a nova senha.
	 */
	@GetMapping("/token-recuperacao/{token}")
	public Map<String, String> consultarTokenDeRecuperacao(@PathVariable String token) {
		String email = autenticacaoServico.buscarEmailPeloTokenDeRecuperacao(token);
		return Map.of("email", email);
	}

	/** Edita o nome do usuario logado. Corpo esperado: { "nome": "..." } */
	@PutMapping("/perfil")
	public Usuario editarPerfil(@AuthenticationPrincipal Usuario usuarioLogado, @RequestBody Map<String, String> corpo) {
		return autenticacaoServico.editarPerfil(usuarioLogado, corpo.get("nome"));
	}

	/** Troca a senha do usuario logado. Corpo esperado: { "senhaAtual": "...", "novaSenha": "..." } */
	@PutMapping("/senha")
	public Map<String, String> alterarSenha(@AuthenticationPrincipal Usuario usuarioLogado,
			@RequestBody Map<String, String> corpo) {
		autenticacaoServico.alterarSenha(usuarioLogado, corpo.get("senhaAtual"), corpo.get("novaSenha"));
		return Map.of("mensagem", "Senha alterada com sucesso.");
	}

}
