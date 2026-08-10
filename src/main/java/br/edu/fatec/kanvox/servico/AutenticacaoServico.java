package br.edu.fatec.kanvox.servico;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.UsuarioRepositorio;

/** Regras de negocio de cadastro e login (RF-01). */
@Service
public class AutenticacaoServico {

	private final UsuarioRepositorio usuarioRepositorio;
	private final PasswordEncoder codificadorDeSenha;
	private final TokenServico tokenServico;

	public AutenticacaoServico(UsuarioRepositorio usuarioRepositorio,
			PasswordEncoder codificadorDeSenha,
			TokenServico tokenServico) {
		this.usuarioRepositorio = usuarioRepositorio;
		this.codificadorDeSenha = codificadorDeSenha;
		this.tokenServico = tokenServico;
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

	private boolean estaVazio(String valor) {
		return valor == null || valor.isBlank();
	}

}
