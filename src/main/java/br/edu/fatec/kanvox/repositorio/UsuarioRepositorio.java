package br.edu.fatec.kanvox.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Usuario;

/** Acesso ao banco para a entidade Usuario. */
public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

	/**
	 * Busca ignorando maiusculas/minusculas: o e-mail e o identificador de login,
	 * e ninguem lembra se digitou "Fulano@Gmail.com" ou "fulano@gmail.com" no
	 * cadastro. Sem o lower() dos dois lados, quem digitasse com outra caixa
	 * simplesmente nao era encontrado — e na recuperacao de senha (RF-01.3) isso
	 * falhava calado, porque a resposta ao usuario e sempre generica.
	 */
	@Query("select u from Usuario u where lower(u.email) = lower(:email)")
	Optional<Usuario> buscarPorEmail(@Param("email") String email);

	/** Usado na redefinicao de senha (RF-01.3) para localizar o dono do token recebido por e-mail. */
	@Query("select u from Usuario u where u.tokenRecuperacao = :token")
	Optional<Usuario> buscarPorTokenRecuperacao(@Param("token") String token);

}
