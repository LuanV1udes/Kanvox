package br.edu.fatec.kanvox.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Projeto;

/** Acesso ao banco para a entidade Projeto. */
public interface ProjetoRepositorio extends JpaRepository<Projeto, Long> {

	/** Projetos em que o usuario e membro ativo (RF-02.3). */
	@Query("select m.projeto from MembroProjeto m where m.usuario.id = :usuarioId"
			+ " and m.situacao = br.edu.fatec.kanvox.modelo.SituacaoNoProjeto.ATIVO")
	List<Projeto> buscarPorMembro(@Param("usuarioId") Long usuarioId);

}
