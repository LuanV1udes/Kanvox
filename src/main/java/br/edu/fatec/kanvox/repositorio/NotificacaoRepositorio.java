package br.edu.fatec.kanvox.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Notificacao;

/** Acesso ao banco para a entidade Notificacao. */
public interface NotificacaoRepositorio extends JpaRepository<Notificacao, Long> {

	/** Notificacoes de um usuario, das mais recentes para as mais antigas (RF-06). */
	@Query("select n from Notificacao n where n.usuario.id = :usuarioId order by n.criadoEm desc")
	List<Notificacao> buscarPorUsuario(@Param("usuarioId") Long usuarioId);

}
