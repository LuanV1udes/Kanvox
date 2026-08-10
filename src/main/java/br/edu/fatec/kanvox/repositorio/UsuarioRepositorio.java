package br.edu.fatec.kanvox.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Usuario;

/** Acesso ao banco para a entidade Usuario. */
public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

	@Query("select u from Usuario u where u.email = :email")
	Optional<Usuario> buscarPorEmail(@Param("email") String email);

}
