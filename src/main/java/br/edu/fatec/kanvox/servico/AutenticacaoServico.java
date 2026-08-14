package br.edu.fatec.kanvox.servico;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.UsuarioRepositorio;

/** Regras de negocio de cadastro, login e recuperacao de senha (RF-01). */
@Service
public class AutenticacaoServico {

	/** Validade do link de recuperacao de senha (RF-01.3). */
	private static final long VALIDADE_DO_TOKEN_EM_HORAS = 1;

	private final UsuarioRepositorio usuarioRepositorio;
	private final PasswordEncoder codificadorDeSenha;
	private final TokenServico tokenServico;
	private final EmailServico emailServico;

	public AutenticacaoServico(UsuarioRepositorio usuarioRepositorio,
			PasswordEncoder codificadorDeSenha,
			TokenServico tokenServico,
			EmailServico emailServico) {
		this.usuarioRepositorio = usuarioRepositorio;
		this.codificadorDeSenha = codificadorDeSenha;
		this.tokenServico = tokenServico;
		this.emailServico = emailServico;
	}

	/**
	 * Cadastra um novo usuario com a senha criptografada com BCrypt (RF-01.1, RNF-02).
	 * Um objeto novo e criado aqui para nunca confiar em campos extras
	 * vindos do corpo da requisicao (ex.: id).
	 */
	public Usuario cadastrar(Usuario dadosRecebidos) {
		if (estaVazio(dadosRecebidos.getNome()) || estaVazio(dadosRecebidos.getEmail())
				|| estaVazio(dadosRecebidos.getSenha())) {
			throw new RegraDeNegocioExcecao("Nome, e-mail e senha sao obrigatorios.");
		}
		if (usuarioRepositorio.buscarPorEmail(dadosRecebidos.getEmail()).isPresent()) {
			throw new RegraDeNegocioExcecao("Ja existe um usuario cadastrado com este e-mail.");
		}

		Usuario novoUsuario = new Usuario();
		novoUsuario.setNome(dadosRecebidos.getNome());
		novoUsuario.setEmail(dadosRecebidos.getEmail());
		novoUsuario.setSenha(codificadorDeSenha.encode(dadosRecebidos.getSenha()));
		return usuarioRepositorio.save(novoUsuario);
	}

	/** Confere e-mail e senha e devolve um token JWT (RF-01.1). */
	public String autenticar(String email, String senha) {
		Usuario usuario = usuarioRepositorio.buscarPorEmail(email == null ? "" : email)
				.orElseThrow(() -> new RegraDeNegocioExcecao("E-mail ou senha invalidos."));
		if (senha == null || !codificadorDeSenha.matches(senha, usuario.getSenha())) {
			throw new RegraDeNegocioExcecao("E-mail ou senha invalidos.");
		}
		return tokenServico.gerar(usuario.getEmail());
	}

	/**
	 * Solicita a recuperacao de senha (RF-01.3): gera um token valido por 1h
	 * e envia por e-mail. Sempre "funciona" do ponto de vista de quem chama,
	 * mesmo se o e-mail nao existir na base — assim ninguem descobre, tentando
	 * e-mails ao acaso, quais contas estao cadastradas na plataforma.
	 */
	public void solicitarRecuperacaoDeSenha(String email) {
		usuarioRepositorio.buscarPorEmail(email == null ? "" : email).ifPresent(usuario -> {
			usuario.setTokenRecuperacao(UUID.randomUUID().toString());
			usuario.setTokenExpiraEm(LocalDateTime.now().plusHours(VALIDADE_DO_TOKEN_EM_HORAS));
			usuarioRepositorio.save(usuario);
			emailServico.enviarLinkDeRecuperacao(usuario.getEmail(), usuario.getNome(), usuario.getTokenRecuperacao());
		});
	}

	/**
	 * Devolve o e-mail associado a um token de recuperacao valido — usado pela
	 * tela de redefinicao para mostrar de qual conta a senha esta sendo trocada.
	 * Nao ha risco de enumeracao aqui: so descobre o e-mail quem ja possui o
	 * token (recebido por e-mail), diferente de solicitar a recuperacao.
	 */
	public String buscarEmailPeloTokenDeRecuperacao(String token) {
		return buscarUsuarioComTokenValido(token).getEmail();
	}

	/** Redefine a senha usando o token recebido por e-mail (RF-01.3). */
	public void redefinirSenha(String token, String novaSenha) {
		if (estaVazio(novaSenha) || novaSenha.length() < 6) {
			throw new RegraDeNegocioExcecao("A nova senha deve ter pelo menos 6 caracteres.");
		}
		Usuario usuario = buscarUsuarioComTokenValido(token);

		usuario.setSenha(codificadorDeSenha.encode(novaSenha));
		// o token so vale uma vez: some assim que a senha e trocada
		usuario.setTokenRecuperacao(null);
		usuario.setTokenExpiraEm(null);
		usuarioRepositorio.save(usuario);
	}

	/** Edita o nome do usuario logado. O e-mail nao pode ser alterado por aqui (e o identificador de login). */
	public Usuario editarPerfil(Usuario usuarioLogado, String novoNome) {
		if (estaVazio(novoNome)) {
			throw new RegraDeNegocioExcecao("O nome e obrigatorio.");
		}
		Usuario usuario = buscarUsuarioPorId(usuarioLogado.getId());
		usuario.setNome(novoNome.trim());
		return usuarioRepositorio.save(usuario);
	}

	/** Troca a senha do usuario logado, exigindo a senha atual como confirmacao (RNF-02). */
	public void alterarSenha(Usuario usuarioLogado, String senhaAtual, String novaSenha) {
		Usuario usuario = buscarUsuarioPorId(usuarioLogado.getId());
		if (senhaAtual == null || !codificadorDeSenha.matches(senhaAtual, usuario.getSenha())) {
			throw new RegraDeNegocioExcecao("Senha atual incorreta.");
		}
		if (estaVazio(novaSenha) || novaSenha.length() < 6) {
			throw new RegraDeNegocioExcecao("A nova senha deve ter pelo menos 6 caracteres.");
		}
		usuario.setSenha(codificadorDeSenha.encode(novaSenha));
		usuarioRepositorio.save(usuario);
	}

	private Usuario buscarUsuarioPorId(Long id) {
		return usuarioRepositorio.findById(id)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Usuario nao encontrado."));
	}

	private Usuario buscarUsuarioComTokenValido(String token) {
		Usuario usuario = usuarioRepositorio.buscarPorTokenRecuperacao(token == null ? "" : token)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Link de redefinicao invalido ou ja utilizado."));
		if (usuario.getTokenExpiraEm() == null || usuario.getTokenExpiraEm().isBefore(LocalDateTime.now())) {
			throw new RegraDeNegocioExcecao("Link de redefinicao expirado. Solicite um novo.");
		}
		return usuario;
	}

	private boolean estaVazio(String valor) {
		return valor == null || valor.isBlank();
	}

}
